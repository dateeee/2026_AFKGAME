package com.afkgame.domain.service.battle;

import java.util.List;
import java.util.Random;

/**
 * エンカウントする敵を重み付きで抽選する。
 *
 * <p>仕様: docs/tech/detail/tech_battle.md §3.2「エンカウント抽選ロジック」、
 * 確率判定の境界規約と乱数の消費は docs/tech/detail/tech_rng.md §1 #7・§3。
 *
 * <p>実装は {@link EncounterSelectorImpl}。
 */
public interface EncounterSelector {

    /**
     * プールから敵を1体抽選する。
     *
     * <p>{@code rng.nextInt(重み合計)} を1回だけ消費し、プールの並び順に重みを累積して、
     * 最初に {@code roll < 累積} となった要素を返す。重み合計が0以下のプールは
     * マスターデータ不正としてシステム例外になる。
     *
     * @param pool 抽選プール（階層データから組み立てる）
     * @param rng  このリクエストの乱数源
     * @return 抽選された敵ID
     */
    String select(List<EncounterEntry> pool, Random rng);

    /**
     * 抽選プールの1要素。
     *
     * <p>階層データ（{@code floorEncounters}）を読んでプールを組み立てるのは塔マスター側の担当で、
     * 本インタフェースは組み立て済みのプールだけを受け取る。
     *
     * @param enemyId 敵ID
     * @param weight  抽選の重み
     */
    record EncounterEntry(String enemyId, int weight) {
    }
}
