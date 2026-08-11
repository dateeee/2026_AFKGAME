package com.afkgame.domain.service.battle;

import java.util.List;

/**
 * 簡略計算の1周回ぶんの分析結果。
 *
 * <p>仕様: docs/tech/detail/tech_offline.md §4 手順3a〜3c・§4.1（期待値計算式）。
 * 周回ループの制御は {@link OfflineCalculator} が持ち、本 record は「1周回すと何が起きるか」の
 * 期待値だけを運ぶ。
 *
 * @param ticksPerLap     1周回に要するtick数（{@code ceil(Σ階の撃破ターン数 ÷ 1tickあたりのターン数)}）
 * @param netDamagePerLap 1周回で受ける正味ダメージ（回復を差し引いた後）
 * @param potionsPerLap   1周回で消費するポーション数
 * @param goldPerLap      1周回で得るゴールド
 * @param expPerLap       1周回で得る経験値（**キャラ1体あたり**）
 * @param itemIdsPerLap   1周回で得るアイテムIDの一覧。1件も無ければ空リスト
 * @param lapsToLevelUp   最も早いLVアップまでの周回数。到達しないなら {@link Integer#MAX_VALUE}
 * @param clearsNewFloor  1周回で未到達の階を新たに踏破するか
 * @param targetFloorCap  目標階の上限（{@code min(塔別highestFloor + 1, 総階数)}）
 */
public record LapAnalysis(int ticksPerLap, long netDamagePerLap, int potionsPerLap,
        long goldPerLap, long expPerLap, List<String> itemIdsPerLap, int lapsToLevelUp,
        boolean clearsNewFloor, int targetFloorCap) {
}
