package com.afkgame.domain.masterdata;

import java.util.List;

/**
 * 塔1件のマスターデータ。
 *
 * <p>列の正は docs/data/towers/TOWERS_OVERVIEW.md「塔一覧」と各塔ファイル §1 基本情報、
 * 定義形式は docs/tech/basic/tech_data.md §1.4。解放判定・{@code cap} の解決規則は
 * docs/tech/detail/tech_tower.md §2。
 *
 * <p><b>本 record はテストリスト作成②-a で用意した表層であり、YAML との対応づけは未実装。</b>
 * 階のエンカウント定義（{@code floorEncounters}）は {@code FloorCatalog} を実装する
 * セグメント②で足す（本 record を読むのは一覧・入塔だけで、階の敵は読まないため）。
 *
 * @param id           塔ID
 * @param name         塔名
 * @param dungeonName  所属ダンジョン名
 * @param totalFloors  総階数。{@code null} は階数無限（深淵の塔。Phase 5〜）
 * @param unlockTowerId 解放条件となる前提塔のID。{@code null} は最初から解放
 * @param difficulties 難易度の一覧。空リストは通常塔で、イベントダンジョン（Phase 5〜）だけが
 *                     {@code beginner} / {@code intermediate} / {@code advanced} の3件を持つ
 */
public record TowerData(
        String id,
        String name,
        String dungeonName,
        Integer totalFloors,
        String unlockTowerId,
        List<String> difficulties) {
}
