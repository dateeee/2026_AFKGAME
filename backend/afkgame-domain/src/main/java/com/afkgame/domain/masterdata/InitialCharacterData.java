package com.afkgame.domain.masterdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * プレイヤー初期化で作成する初期キャラクターのマスターデータ。
 *
 * <p>値の正は docs/data/master/character.md §1.1、参照関係の正は
 * docs/tech/detail/tech_auth/init.md §8.1。ステータスは本 record に持たず、
 * {@code type} から {@link CharacterTypeData} を引いて得る（同 §8.2 手順4）。
 *
 * @param id    キャラクターマスターのID（例: {@code hero_001}）。
 *              現在は永続化していない。Phase 4 で {@code characters.master_id}
 *              （tech_db/player.md §4）を追加する際に手順4で書き込む
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
