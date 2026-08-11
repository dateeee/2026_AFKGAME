package com.afkgame.domain.service.battle;

import java.time.Clock;

import com.afkgame.domain.repository.CharacterRepository;
import com.afkgame.domain.repository.PlayerRepository;
import com.afkgame.env.config.GameSettings;
import com.afkgame.env.logging.AppLogger;
import com.afkgame.env.logging.LoggerName;

/**
 * {@link BattleService} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。本クラスは未処理tick数の決定と処理方式の振り分けを担い、
 * 1tick内の戦闘の中身は {@link BattleSimulator}・{@link OfflineCalculator} へ委譲する。
 *
 * <p><b>本クラスは製造①-i で用意した表層であり、処理は未実装。</b>
 * 解消は製造①-iii（tick・オフラインの Green。docs/backlog/java_migration.md STEP 3-B）で、
 * そのとき {@code @Service} と {@code @Transactional}（1リクエスト = 1トランザクション。
 * tech_tick.md §4）を付けて DI に載せる。協調先の {@link BattleSimulator} 等が実装を持たない
 * うちに Bean 登録すると、コンテキスト起動そのものが失敗するため段階を分けている。
 */
public class BattleServiceImpl implements BattleService {

    private static final AppLogger logger = AppLogger.of(LoggerName.BATTLE);

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
    public TickResult tick(String playerId) {
        throw new UnsupportedOperationException("製造①-iii（tick・オフラインの Green）で実装する");
    }
}
