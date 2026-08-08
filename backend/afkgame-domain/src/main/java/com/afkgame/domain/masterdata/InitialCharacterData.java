package com.afkgame.domain.masterdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * プレイヤー初期化で作成する初期キャラクターのマスターデータ。
 *
 * <p>値の正は docs/data/master/character.md §1.1、参照関係の正は
 * docs/tech/detail/tech_auth.md §8.1。ステータスは本 record に持たず、
 * {@code type} から {@link CharacterTypeData} を引いて得る（同 §8.2 手順4）。
 *
 * @param id    キャラクターマスターのID（例: {@code hero_001}）
 * @param name  表示名
 * @param type  タイプID（{@link CharacterTypes} に実在すること）
 * @param level 初期レベル
 */
public record InitialCharacterData(
        @NotBlank String id,
        @NotBlank String name,
        @NotBlank String type,
        @Positive int level) {
}
