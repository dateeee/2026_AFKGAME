package com.afkgame.domain.masterdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * キャラクタータイプ別の LV1 基礎ステータスのマスターデータ。
 *
 * <p>数値の正は docs/data/master/character.md §1.2、参照関係の正は
 * docs/tech/detail/tech_auth/init.md §8.1。本 record は YAML（
 * {@code src/main/resources/masterdata/character_types.yml}）のスキーマ定義を兼ね、
 * 制約に反する値があれば起動時に {@link MasterDataException} で起動を中止する。
 *
 * <p>{@code growth*} は1LVあたりの上昇量（線形加算）。LV n のステータスは
 * {@code floor(base + growth × (n - 1))} で求め、LVごとの増分を足し込まない
 * （docs/tech/detail/tech_numeric.md §5 #21）。holy の {@code growthSpd}・agile の
 * {@code growthDef} が 1.5 のため {@code double} で受ける。
 *
 * @param id        タイプID（YAML 内で一意。例: {@code melee}）
 * @param hp        LV1 の最大HP
 * @param atk       LV1 の攻撃力
 * @param def       LV1 の防御力
 * @param spd       LV1 の素早さ
 * @param critRate  クリティカル率（0.0〜1.0。Phase 1 は基礎値のみ）
 * @param growthHp  1LVあたりの最大HP上昇量
 * @param growthAtk 1LVあたりの攻撃力上昇量
 * @param growthDef 1LVあたりの防御力上昇量
 * @param growthSpd 1LVあたりの素早さ上昇量
 */
public record CharacterTypeData(
        @NotBlank String id,
        @Positive int hp,
        @Positive int atk,
        @Positive int def,
        @Positive int spd,
        @PositiveOrZero double critRate,
        @Positive double growthHp,
        @Positive double growthAtk,
        @Positive double growthDef,
        @Positive double growthSpd) {
}
