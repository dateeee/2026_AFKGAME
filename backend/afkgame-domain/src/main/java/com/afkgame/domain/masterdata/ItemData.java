package com.afkgame.domain.masterdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * アイテムのマスターデータ。
 *
 * <p>数値の正は docs/data/master/item.md §3.1。本 record は YAML（
 * {@code src/main/resources/masterdata/items.yml}）のスキーマ定義を兼ね、
 * 制約に反する値があれば起動時に {@link MasterDataException} で起動を中止する。
 *
 * @param id         アイテムID（YAML 内で一意）
 * @param name       表示名
 * @param category   分類（例: {@code potion}）
 * @param price      購入価格（ゴールド）
 * @param healRatio  maxHP に対する回復割合
 * @param stackLimit 所持上限
 */
public record ItemData(
        @NotBlank String id,
        @NotBlank String name,
        @NotBlank String category,
        @PositiveOrZero int price,
        @PositiveOrZero double healRatio,
        @Positive int stackLimit) {
}
