package com.afkgame.domain.service.battle;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.annotation.Transactional;
import org.terasoluna.gfw.common.exception.BusinessException;
import org.terasoluna.gfw.common.message.ResultMessages;

import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.repository.CharacterRepository;
import com.afkgame.domain.repository.PlayerRepository;
import com.afkgame.env.config.GameSettings;
import com.afkgame.env.logging.AppLogger;
import com.afkgame.env.logging.LogReason;
import com.afkgame.env.logging.LoggerName;

/**
 * {@link BattleService} の実装。
 *
 * <p>仕様: docs/tech/detail/tech_tick.md §1〜§4（未処理tick数の決定・上限クランプ・同時実行制御・
 * トランザクション境界）、処理方式の切り替えは docs/tech/detail/tech_offline.md §2。
 *
 * <p>本クラスは未処理tick数の決定と処理方式の振り分けを担い、1tick内の戦闘の中身は
 * {@link BattleSimulator}・{@link OfflineCalculator} へ委譲する。戻り値の
 * {@link BattleOutcome} は素通しで、中身を見ない。
 *
 * <p><b>{@code @Service} はまだ付けない。</b>協調先の {@link BattleSimulator} が
 * {@code FloorProgression}・{@code Enemies} の実体を必要とし、それらはセグメント②（tower）まで
 * 無いため、先に Bean 登録するとコンテキスト起動そのものが失敗する。付与はセグメント②で行う
 * （docs/backlog/java_migration.md STEP 3-B）。{@code @Transactional}（1リクエスト =
 * 1トランザクション。§4）は Bean 登録時にそのまま効くよう先に付けてある。
 */
public class BattleServiceImpl implements BattleService {

    private static final AppLogger logger = AppLogger.of(LoggerName.BATTLE);

    /** ロック競合のエラーコード。ステータス（503）の対応付けは Web 層の対応表が持つ。 */
    private static final String TICK_BUSY = "BATTLE_TICK_BUSY";

    /** tick数の上限を「最大放置時間 × 3600 ÷ tick間隔」で求めるための時間→秒の換算値。 */
    private static final int SECONDS_PER_HOUR = 3600;

    /** 1tickも成立しなかった場合の結果。戦闘処理を行わないので中身は持たない。 */
    private static final TickResult NO_TICK = new TickResult(0, false, null, null);

    private final PlayerRepository playerRepository;

    private final CharacterRepository characterRepository;

    private final BattleSimulator battleSimulator;

    private final OfflineCalculator offlineCalculator;

    private final GameSettings gameSettings;

    private final Clock clock;

    /**
     * 依存を受け取る。
     *
     * @param playerRepository    プレイヤーの参照・更新
     * @param characterRepository パーティの参照
     * @param battleSimulator     正規シミュレーション
     * @param offlineCalculator   簡略計算
     * @param gameSettings        tick間隔・24時間上限・簡略計算しきい値の供給元
     * @param clock               現在時刻の供給元
     */
    public BattleServiceImpl(PlayerRepository playerRepository,
            CharacterRepository characterRepository, BattleSimulator battleSimulator,
            OfflineCalculator offlineCalculator, GameSettings gameSettings, Clock clock) {
        this.playerRepository = playerRepository;
        this.characterRepository = characterRepository;
        this.battleSimulator = battleSimulator;
        this.offlineCalculator = offlineCalculator;
        this.gameSettings = gameSettings;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public TickResult tick(String playerId) {
        Player player = lockPlayer(playerId);
        Instant now = clock.instant();
        long elapsedSeconds = Duration.between(player.getLastTickAt(), now).getSeconds();
        if (elapsedSeconds < 0) {
            // 巻き戻し・データ不整合。握りつぶすと原因が追えないので切り分け用に残す（§1.1）
            logger.warn("基準時刻が現在時刻より未来のため未処理tickを0として扱う")
                    .reason(LogReason.CLOCK_SKEW).log();
            return NO_TICK;
        }

        long rawTicks = elapsedSeconds / gameSettings.tickIntervalSeconds();
        if (rawTicks == 0) {
            // 端数は切り捨てず次回へ繰り越すため、基準時刻を動かさない（§1）
            return NO_TICK;
        }

        int maxTicks = maxPendingTicks();
        boolean capped = rawTicks > maxTicks;
        int pendingTicks = capped ? maxTicks : (int) rawTicks;

        List<Character> party = characterRepository.findAllByPlayerId(playerId);
        TickResult result = process(player, party, pendingTicks, capped);
        advanceBaseTime(player, now, pendingTicks, capped);
        playerRepository.updateTickState(player);
        return result;
    }

    /**
     * 対象プレイヤーの行をロックして読む（§3.1）。
     *
     * @param playerId プレイヤーID
     * @return ロックしたプレイヤー
     * @throws BusinessException ロック待ちが {@code lock_timeout} を超えた場合（{@code BATTLE_TICK_BUSY}）
     */
    private Player lockPlayer(String playerId) {
        try {
            return playerRepository.findByIdForUpdate(playerId);
        } catch (CannotAcquireLockException e) {
            throw new BusinessException(ResultMessages.error().add(TICK_BUSY));
        }
    }

    /**
     * 未処理tickを消化する。戦闘が成立しない経路では状態だけを返す（§5 #9・#10）。
     */
    private TickResult process(Player player, List<Character> party, int pendingTicks,
            boolean capped) {
        if (player.getCurrentTowerId() == null) {
            // 塔外待機。戦闘は行わずHP自然回復だけを適用する（tech_offline.md §4 手順4）
            offlineCalculator.applyIdleRegen(party, pendingTicks);
            return new TickResult(pendingTicks, capped, null, null);
        }
        if (party.isEmpty()) {
            return new TickResult(pendingTicks, capped, null, null);
        }
        if (pendingTicks <= gameSettings.fastCalcThreshold()) {
            BattleOutcome outcome = battleSimulator.simulate(player, party, pendingTicks);
            return new TickResult(pendingTicks, capped, outcome, null);
        }
        // しきい値超過は個別ログを生成せずサマリーのみ返す（tech_offline.md §2）
        OfflineSummary summary = offlineCalculator.calculate(player, party, pendingTicks);
        return new TickResult(pendingTicks, capped, null, summary);
    }

    /**
     * 基準時刻を消化した分だけ進める。クランプが起きた回だけ繰り越し規則の例外で
     * {@code now} を代入し、超過分を破棄する（§2）。
     */
    private void advanceBaseTime(Player player, Instant now, int pendingTicks, boolean capped) {
        if (capped) {
            player.setLastTickAt(now);
            return;
        }
        player.setLastTickAt(player.getLastTickAt()
                .plusSeconds((long) pendingTicks * gameSettings.tickIntervalSeconds()));
    }

    /** 最大放置時間から求めた未処理tick数の上限（既定 24時間 = 1,440tick）。 */
    private int maxPendingTicks() {
        return gameSettings.maxOfflineHours() * SECONDS_PER_HOUR
                / gameSettings.tickIntervalSeconds();
    }
}
