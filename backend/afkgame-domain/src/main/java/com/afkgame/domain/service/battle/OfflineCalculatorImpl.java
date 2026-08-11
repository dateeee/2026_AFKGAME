package com.afkgame.domain.service.battle;

import java.util.ArrayList;
import java.util.List;

import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.InventoryItem;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.repository.PlayerRepository;
import com.afkgame.env.config.GameSettings;

/**
 * {@link OfflineCalculator} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。本クラスは周回ループの制御（全滅・在庫・レベルアップ・
 * 残tickの判定）だけを担い、1周回ぶんの期待値計算は {@link LapAnalyzer} へ閉じ込める。
 * 制御の正は docs/tech/detail/tech_offline.md §4 手順3〜5、全滅ペナルティは同§4 の表。
 *
 * <p>周回は「次のレベルアップまで」を1バッチとして一括で消化する（§4 手順3d〜3f）。
 * バッチを跨ぐのはレベルアップだけなので、{@link LapAnalyzer} を呼ぶのは初回とレベルアップ直後
 * （§4 手順3g「期待報酬を再計算」）に限られる。
 *
 * <p><b>{@code @Service} はまだ付けない。</b>{@link LapAnalyzer} の実装が {@link FloorCatalog} を
 * 必要とし、その実体がセグメント②（tower）まで無いため。付与はセグメント②で行う
 * （docs/backlog/java_migration.md STEP 3-B）。
 *
 * <p><b>Phase 1 の範囲に絞った未実装</b>（いずれも分岐一覧に行が無く、テストも持たない）:
 * ①減算したポーション在庫と獲得報酬の永続化は行わない（更新の集約は呼び出し側のトランザクション
 * 境界が持つ）。②LV上限到達時の成長停止（§4「転生の扱い」）は {@link CharacterGrowth} 側の
 * 未実装と対で、キャラ成長のテストリスト作成で分岐ごと起こす。③全滅ペナルティの適用順は
 * tech_state.md §3（セグメント②）が正で、本クラスは §4 の表の内容だけを適用する。
 */
public class OfflineCalculatorImpl implements OfflineCalculator {

    /** 回復に使うポーションのアイテムID（docs/data/master/item.md）。 */
    private static final String POTION_ITEM_ID = "hp_potion";

    /** 全滅時に減算する蓄積EXPの割合の分母（§4 ペナルティ #1 の50%）。 */
    private static final long EXP_LOSS_DIVISOR = 2L;

    /** HP自然回復の maxHP 係数（§4 手順4 の {@code maxHP × 0.02}）。 */
    private static final double REGEN_MAX_HP_RATIO = 0.02;

    /** HP自然回復の DEF 係数（§4 手順4 の {@code DEF × 0.5}）。 */
    private static final double REGEN_DEF_FACTOR = 0.5;

    /** 回復量の下限（tech_numeric.md §2）。 */
    private static final long MIN_HEAL = 1L;

    private final LapAnalyzer lapAnalyzer;

    private final CharacterGrowth characterGrowth;

    private final PlayerRepository playerRepository;

    private final GameSettings gameSettings;

    /**
     * 依存を受け取る。
     *
     * @param lapAnalyzer      1周回ぶんの期待値計算
     * @param characterGrowth  レベルアップの適用
     * @param playerRepository ポーション在庫の参照
     * @param gameSettings     オフライン効率などの供給元
     */
    public OfflineCalculatorImpl(LapAnalyzer lapAnalyzer, CharacterGrowth characterGrowth,
            PlayerRepository playerRepository, GameSettings gameSettings) {
        this.lapAnalyzer = lapAnalyzer;
        this.characterGrowth = characterGrowth;
        this.playerRepository = playerRepository;
        this.gameSettings = gameSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OfflineSummary calculate(Player player, List<Character> party, int ticks) {
        int remaining = ticks;
        int potionStock = potionStock(player.getId());
        long partyHp = party.stream().mapToLong(Character::getMaxHp).sum();
        long gold = 0L;
        long exp = 0L;
        List<String> itemIds = new ArrayList<>();
        int laps = 0;
        LapAnalysis analysis = lapAnalyzer.analyze(player, party, potionStock);
        // 1周に満たない端数は周回できないので、残tickが1周ぶんを割ったら抜ける（§4 手順3f・5）
        while (remaining >= analysis.ticksPerLap()) {
            if (analysis.netDamagePerLap() > partyHp) {
                applyWipePenalty(player, party, remaining);
                // ゴールドとアイテムは今回の探索分をすべて失う（§4 ペナルティ #2・#3）
                return new OfflineSummary(0L, exp, List.of(), true, ticks - remaining, laps);
            }
            int batchLaps = batchLaps(analysis, remaining);
            gold += reward(analysis.goldPerLap(), batchLaps);
            exp += grantExp(party, reward(analysis.expPerLap(), batchLaps));
            for (int lap = 0; lap < batchLaps; lap++) {
                itemIds.addAll(analysis.itemIdsPerLap());
            }
            potionStock -= analysis.potionsPerLap() * batchLaps;
            laps += batchLaps;
            remaining -= batchLaps * analysis.ticksPerLap();
            followTargetFloor(player, analysis);
            if (batchLaps >= analysis.lapsToLevelUp()) {
                party.forEach(characterGrowth::applyLevelUp);
                // §4 手順3g: 据え置きの期待値を使い回さず、新しいステータスで分析し直す
                analysis = lapAnalyzer.analyze(player, party, potionStock);
            }
        }
        return new OfflineSummary(gold, exp, itemIds, false, ticks - remaining, laps);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyIdleRegen(List<Character> party, int ticks) {
        for (Character ally : party) {
            double perTick = ally.getMaxHp() * REGEN_MAX_HP_RATIO
                    + ally.getBaseDef() * REGEN_DEF_FACTOR;
            long healed = Math.max(MIN_HEAL, (long) Math.floor(perTick * ticks));
            ally.setHp((int) Math.min(ally.getMaxHp(), ally.getHp() + healed));
        }
    }

    /**
     * このバッチで消化する周回数を求める（§4 手順3c・3d）。
     *
     * <p>レベルアップまでのtick数を上限に切り、残tickのほうが少なければそちらで頭打ちにする。
     * {@code lapsToLevelUp} は到達しないとき {@link Integer#MAX_VALUE} なので {@code long} で掛ける。
     *
     * @param analysis  現在のステータスでの分析結果
     * @param remaining 残りtick数
     * @return 消化する周回数（1以上）
     */
    private int batchLaps(LapAnalysis analysis, int remaining) {
        long ticksToLevelUp = (long) analysis.lapsToLevelUp() * analysis.ticksPerLap();
        return (int) (Math.min(remaining, ticksToLevelUp) / analysis.ticksPerLap());
    }

    /**
     * 目標階が上限と一致した状態で新しい階をクリアしたら目標階を+1する（§5 #9・#10）。
     *
     * <p>上限より低い目標階はプレイヤーが意図して下げた設定なので追従しない（§4「目標階の固定」）。
     */
    private void followTargetFloor(Player player, LapAnalysis analysis) {
        if (analysis.clearsNewFloor() && player.getTargetFloor() == analysis.targetFloorCap()) {
            player.setTargetFloor(player.getTargetFloor() + 1);
        }
    }

    /**
     * 全滅ペナルティのうち、キャラとプレイヤーの状態へ及ぶものを適用する（§4 ペナルティ表）。
     *
     * <p>#1 経験値ロスト（現在レベル内の蓄積EXPの50%。レベルダウンはしない）、#4 強制撤退、
     * #5 残tickの破棄（塔外待機としてHP自然回復のみ適用）を行う。#2・#3 のゴールド・アイテムは
     * 呼び出し元が空のサマリーを返すことで失わせる。
     *
     * @param player         対象プレイヤー
     * @param party          パーティ
     * @param discardedTicks 周回に使わず破棄する残tick数
     */
    private void applyWipePenalty(Player player, List<Character> party, int discardedTicks) {
        for (Character ally : party) {
            ally.setExp(ally.getExp() / EXP_LOSS_DIVISOR);
        }
        player.setCurrentTowerId(null);
        applyIdleRegen(party, discardedTicks);
    }

    /**
     * 周回EXPをパーティ全員へ加算する（§4 手順3e）。
     *
     * @param party  パーティ
     * @param amount キャラ1体あたりの加算量
     * @return 付与した経験値の総量
     */
    private long grantExp(List<Character> party, long amount) {
        for (Character ally : party) {
            ally.setExp(ally.getExp() + amount);
        }
        return amount * party.size();
    }

    /**
     * 周回ぶんの報酬へオフライン効率を掛ける。
     *
     * <p>効率は設定値（tech_backend.md §4.2。既定はオンラインと同一の1.0）で、丸めは
     * tech_numeric.md §2「報酬倍率適用後の報酬」に従い倍率適用後に1回だけ行う。
     *
     * @param perLap 1周回あたりの報酬
     * @param laps   周回数
     * @return 効率適用後の報酬
     */
    private long reward(long perLap, int laps) {
        return (long) Math.floor(perLap * laps * gameSettings.offlineEfficiency());
    }

    /** 周回の起点になるポーション在庫（§4.1「所持数から周回ごとに減算し」）。 */
    private int potionStock(String playerId) {
        return playerRepository.findAllItemsByPlayerId(playerId).stream()
                .filter(item -> POTION_ITEM_ID.equals(item.getItemId()))
                .mapToInt(InventoryItem::getQuantity)
                .sum();
    }
}
