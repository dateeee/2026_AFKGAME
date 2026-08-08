package com.afkgame.domain.masterdata;

import jakarta.validation.constraints.NotBlank;

/**
 * 装備スロット定義のマスターデータ。
 *
 * <p>スロットの正は docs/design/systems/equipment.md §2.4「装備スロット（9スロット）」、
 * スロットIDの正は docs/tech/basic/tech_db/item.md §1 の {@code equipment.slot}。
 * 本 record は YAML（{@code src/main/resources/masterdata/equipment_slots.yml}）の
 * スキーマ定義を兼ね、制約に反する値があれば起動時に {@link MasterDataException} で起動を中止する。
 *
 * @param id   スロットID（YAML 内で一意。例: {@code weapon}）
 * @param name 表示名
 */
public record EquipmentSlotData(
        @NotBlank String id,
        @NotBlank String name) {
}
