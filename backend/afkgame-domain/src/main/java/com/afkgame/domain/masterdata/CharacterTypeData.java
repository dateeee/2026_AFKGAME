package com.afkgame.domain.masterdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * キャラクタータイプ別の LV1 基礎ステータスのマスターデータ。
 *
 * <p>数値の正は docs/data/master/character.md §1.2、参照関係の正は
 * docs/tech/detail/tech_auth.md §8.1。本 record は YAML（
 * {@code src/main/resources/masterdata/character_types.yml}）のスキーマ定義を兼ね、
 * 制約に反する値があれば起動時に {@link MasterDataException} で起動を中止する。
 *
 * <p><strong>成長率は持たない</strong>。レベルアップ機能を移植するまで参照側が無いため、
 * 成長率のキーは同機能の移植時に追加する（tech_auth.md §8.1）。
 *
 * @param id  タイプID（YAML 内で一意。例: {@code melee}）
 * @param hp  LV1 の最大HP
 * @param atk LV1 の攻撃力
 * @param def LV1 の防御力
 * @param spd LV1 の素早さ
 */
public record CharacterTypeData(
        @NotBlank String id,
        @Positive int hp,
        @Positive int atk,
        @Positive int def,
        @Positive int spd) {
}
