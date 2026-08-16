package com.afkgame.domain.masterdata;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 塔1件のマスターデータ。
 *
 * <p>列の正は docs/data/towers/TOWERS_OVERVIEW.md「塔一覧」と各塔ファイル §1 基本情報、
 * 定義形式は docs/tech/basic/tech_data.md §1.4。解放判定・{@code cap} の解決規則は
 * docs/tech/detail/tech_tower.md §2。本 record は YAML（
 * {@code src/main/resources/masterdata/towers.yml}）のスキーマ定義を兼ね、制約に反する値が
 * あれば起動時に {@link MasterDataException} で起動を中止する（{@link ItemData} と同じ形）。
 *
 * <p>階のエンカウント定義（{@code floorEncounters}）は {@code FloorCatalog} を実装する
 * 回に足す（本 record を読むのは一覧・入塔だけで、階の敵は読まないため）。
 * {@code modifiers} は階クリア後の回復を読む②-b が足した（同 progress.md §9 手順2）。
 *
 * @param id           塔ID
 * @param name         塔名
 * @param dungeonName  所属ダンジョン名
 * @param totalFloors  総階数。{@code null} は階数無限（深淵の塔。Phase 5〜）
 * @param unlockTowerId 解放条件となる前提塔のID。{@code null} は最初から解放
 * @param difficulties 難易度の一覧。空リストは通常塔で、イベントダンジョン（Phase 5〜）だけが
 *                     {@code beginner} / {@code intermediate} / {@code advanced} の3件を持つ
 * @param modifiers    環境効果の一覧。空リストは効果なし（ダンジョン1の塔。Phase 3〜で中身を持つ）
 */
public record TowerData(
        @NotBlank String id,
        @NotBlank String name,
        @NotBlank String dungeonName,
        @Positive Integer totalFloors,
        String unlockTowerId,
        @NotNull List<String> difficulties,
        @NotNull @Valid List<TowerModifier> modifiers) {
}
