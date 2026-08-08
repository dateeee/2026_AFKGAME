package com.afkgame.domain.masterdata;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * プレイヤー初期化に使うマスターデータ（初期キャラ + 初期所持アイテム）。
 *
 * <p>値の正は docs/data/master/character.md §1.1 と docs/data/master/item.md §3.5、
 * 参照関係の正は docs/tech/detail/tech_auth.md §8.1。IDをキーに持たない単一オブジェクトのため
 * {@link MasterDataLoader#loadSingle} で読む。ネストした record まで検証するため
 * {@code @Valid} を付ける。
 *
 * <p>{@code items} は<strong>空リストを許可する</strong>（初期所持アイテムを持たない構成は
 * tech_auth.md §8.3 #7 が扱う正当な経路のため）。
 *
 * @param character 初期キャラクター1体の定義
 * @param items     初期所持アイテムの定義（種類ごとに1件）
 */
public record InitialPlayerData(
        @NotNull @Valid InitialCharacterData character,
        @NotNull @Valid List<InitialItemData> items) {
}
