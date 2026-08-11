package com.afkgame.domain.masterdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 敵のマスターデータ。
 *
 * <p>列の正は docs/data/towers/000_テンプレート.md §2、個々の塔の値は同ディレクトリの各ファイル。
 * 本 record は YAML のスキーマ定義を兼ね、制約に反する値があれば起動時に
 * {@link MasterDataException} で起動を中止する（{@link ItemData} と同じ形）。
 *
 * @param id       敵ID（YAML 内で一意）
 * @param name     表示名
 * @param level    レベル
 * @param hp       最大HP
 * @param atk      攻撃力
 * @param def      防御力
 * @param spd      素早さ（行動順の決定に使う）
 * @param gold     撃破時に得られるゴールド
 * @param exp      撃破時に得られる経験値
 * @param critRate クリティカル率（0.0〜1.0）。味方と共通の定数にしない（tech_rng.md §6）
 */
public record EnemyData(
        @NotBlank String id,
        @NotBlank String name,
        @Positive int level,
        @Positive int hp,
        @PositiveOrZero int atk,
        @PositiveOrZero int def,
        @PositiveOrZero int spd,
        @PositiveOrZero long gold,
        @PositiveOrZero long exp,
        @PositiveOrZero double critRate) {
}
