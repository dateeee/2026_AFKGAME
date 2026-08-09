# テスト実装パターン — AFK GAME 固有の具体例

> 一般形（クラスの骨格・外部要因の固定・parametrize・例外の検証・共通ユーティリティ方針）は
> [.claude/skills/test-list/references/patterns.md](../skills/test-list/references/patterns.md)。
> **本書は一般形へ当てはめる固有の値のみ**を持つ。ユーティリティ一覧は [test-list.md](test-list.md) §4、除外規則は [unit-test.md](unit-test.md) §4。

参考にする既存テスト: Java化後に `afkgame-domain` の Service テストとして整備する（STEP 2 骨格構築後）。

## 対象クラスと共通ユーティリティ

| 一般形の箇所 | AFK GAME での値 |
|------------|---------------|
| 対象クラスの import | `afkgame-domain` の `BattleService`（Javadoc の実装ファイルは Service クラス） |
| テストメソッドの引数 | 共通ユーティリティ `db` / `player` / `character` / `client` / `towerRecord` |

## 乱数の固定

差し替える実体（一般形の `m.random` ・ `<抽選関数>` に対応）。Mockito で `Random` と抽選系メソッドをスタブする。

```java
@BeforeEach
void noVariance() {
    // ダメージ分散・クリティカル・装備ドロップを固定する
    when(random.nextDouble()).thenReturn(0.0);
    when(random.nextBoolean()).thenReturn(false); // クリティカル発生せず
    when(dropService.tryDrop(any(), any())).thenReturn(Optional.empty());
    when(encounterService.roll(anyString(), anyInt())).thenReturn(enemy);
}
```

## 時刻の固定

過去へずらす対象は `player.getLastTickAt()`（tick は60秒間隔）。トークン期限切れは DBレコードの `expiresAt` を過去に設定する。

```java
@Test
void 経過時間ぶんのtickが処理される() {
    player.setLastTickAt(OffsetDateTime.now().minusMinutes(5));
    db.commit();
    // → 60秒tick × 5回ぶんが処理される
}
```

## 境界値の実例（`targetFloorCap`）

```java
@ParameterizedTest
@CsvSource({
    "0, 20, 1",   // 未挑戦 → 1階のみ
    "19, 20, 20", // 最上階の1つ手前
    "20, 20, 20", // 総階数でクランプ（+1 しない）
    "25, 20, 20", // 記録が総階数を超えていてもクランプ
})
void 有限塔は総階数でクランプされる(int highest, int total, int expected) {
    assertThat(targetFloorCap(highest, total)).isEqualTo(expected);
}
```

## 例外・エラーレスポンス

Service層が送出する例外は `terasoluna-gfw` の2種類（3分類の正は [coding_standards_backend/exception.md](../../docs/process/coding_standards_backend/exception.md)）。ビジネス例外 `BusinessException`（404 は `ResourceNotFoundException`）は `ResultMessages` にコードだけを持ち、**HTTP ステータスは持たない**（Web層の対応表が決める）。システム例外 `SystemException` は `code` を持ち、応答は 500 + `INTERNAL_UNEXPECTED_ERROR` に丸まる。

Service の検証は**コードだけ**を見る（ステータスは Web 層のテストで見る）。`getMessage()` は `ResultMessages#toString()` を返すので**アサートに使わない**。

```java
@Test
void gold不足なら購入できない() {
    player.setGold(0);
    BusinessException ex = assertThrows(BusinessException.class,
        () -> shopService.buyItem(player, "hp_potion", 1, db));
    assertThat(ex.getResultMessages().getList())
        .extracting(ResultMessage::getCode)
        .containsExactly("SHOP_INSUFFICIENT_GOLD");
}
```

コントローラ経由は統一エラーボディ `error.code` を検証する。リクエスト／レスポンスは **camelCase**（Jackson）。

```java
@Test
void 未知の塔IDは404() throws Exception {
    mockMvc.perform(post("/api/tower/select")
            .contentType(APPLICATION_JSON)
            .content("{\"towerId\":\"unknown\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TOWER_NOT_FOUND"));
}

@Test
void トークンなしは401() throws Exception {
    mockMvc.perform(get("/api/game/state")) // Authorization ヘッダなし
        .andExpect(status().isUnauthorized());
}
```
