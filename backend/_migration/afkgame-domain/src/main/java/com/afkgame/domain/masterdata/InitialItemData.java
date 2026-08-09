package com.afkgame.domain.masterdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * プレイヤー初期化で付与する初期所持アイテムのマスターデータ。
 *
 * <p>値の正は docs/data/master/item.md §3.5、参照関係の正は
 * docs/tech/detail/tech_auth.md §8.1。付与の処理フローと分岐は同 §8.2 手順6・§8.3 #7〜#10。
 *
 * @param id       アイテムID（{@link Items} に実在すること）
 * @param quantity 付与する個数
 */
public record InitialItemData(
        @NotBlank String id,
        @Positive int quantity) {
}
