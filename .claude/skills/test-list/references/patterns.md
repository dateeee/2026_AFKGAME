# テスト実装パターン（一般）

分岐を確実に選ぶための定型。**具体的なクラス名・フィクスチャ名・エラーコードはプロジェクト固有プロファイルを参照**すること。

## クラスの骨格

Javadoc に「仕様の参照先」と「分岐観点」を書く。これがテストと設計の対応表になる。

```java
/**
 * 単体テスト: <対象>（<実装クラス>）
 *
 * 仕様: <参照する仕様書のパスとセクション>
 * 分岐観点:
 *   - <観点1>
 *   - <観点2>
 */
@Tag("unit")
class <対象>Test {

    @Nested
    @DisplayName("<対象の振る舞い>")
    class <振る舞い> {
        @Test
        @DisplayName("条件を満たせばこうなる")
        void 条件を満たす() { ... }
    }
}
```

## 外部要因の固定

**分岐を選べないテストは書かない。** 乱数・時刻・外部I/O は必ず固定する。

### 乱数

乱数は**コンストラクタで注入**する設計にし、テストではスタブへ差し替える。実装が内部で `new Random()` する形なら、まずその実装を直す。

```java
RandomGenerator rng = mock(RandomGenerator.class);
when(rng.nextDouble()).thenReturn(0.0);   // 確率分岐に必ず入る
when(rng.nextDouble()).thenReturn(1.0);   // 確率分岐に入らない
```

`rng.nextDouble() < rate` は**両側**を通す。`0.0` で必ず発生、`1.0` で必ず非発生。
シード固定（`new Random(N)`）は「どの枝を通るか」がコードを読まないと分からないため、**戻り値のスタブを優先**する。
連続して異なる値を返したいときは `thenReturn(a, b, c)` で消費順序どおりに並べる。抽選ロジックごと差し替える場合は、その協調オブジェクトを `@Mock` にする。

### 時刻

`OffsetDateTime.now()` を直接呼ぶ実装では、**時刻を進めるのではなく起点を過去へずらす**ほうが壊れにくい。

```java
record.setLastUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
```

期限切れ・タイムアウトは、レコードの期限カラムを過去に設定して再現する。実装側が現在時刻に依存する場合は `Clock` を注入し、テストで `Clock.fixed(...)` を渡す。

### 到達困難な分岐

DB制約違反・外部I/O失敗など通常経路で作れない分岐は、モックで例外を強制する。

```java
doThrow(new DuplicateKeyException("boom")).when(mapper).insert(any());
```

それでも作れない場合のみ JaCoCo の `<excludes>` で除外する。**理由コメントは必須。**

## 境界値は @ParameterizedTest に集約

境界そのものの値を必ず含め、各ケースに意図のコメントを付ける。

```java
@ParameterizedTest(name = "入力={0} 上限={1} → {2}")
@CsvSource({
    " 0, 20,  1",   // 下限
    "19, 20, 20",   // 上限の1つ手前
    "20, 20, 20",   // 上限ちょうど（クランプ）
    "25, 20, 20",   // 上限超過（クランプ）
})
void 上限でクランプされる(int 入力, int 上限, int 期待値) {
    assertThat(cap(入力, 上限)).isEqualTo(期待値);
}
```

## 例外の検証

例外は**型だけでなく識別子・ステータスまで**検証する。型だけだと別の原因で落ちても通ってしまう。

```java
var ex = assertThrows(<例外型>.class, () -> doSomething());
assertThat(ex.getCode()).isEqualTo("<エラーコード>");
assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
```

## Controller の検証

MockMvc を使い、**ステータスコードと統一エラーボディの両方**を検証する。

```java
mockMvc.perform(post("/api/xxx").contentType(MediaType.APPLICATION_JSON).content("{...}"))
    .andExpect(status().isNotFound())
    .andExpect(jsonPath("$.error.code").value("<エラーコード>"));
```

未認証は認証ヘッダを付けずに再現する。

```java
mockMvc.perform(get("/api/xxx"))   // Authorization ヘッダなし
    .andExpect(status().isUnauthorized());
```

リクエスト／レスポンスのキー形式（camelCase / snake_case）はプロファイルで確認する。

## フィクスチャ

| 方針 | 内容 |
|------|------|
| 共通 | 全テストで使うものだけ共通の基底クラス・`@TestConfiguration` に置く |
| ローカル | 特定クラスでしか使わないものは `@BeforeEach` または private ヘルパで定義する |
| 一覧 | 利用可能な共通フィクスチャはプロファイルを参照する |

フィクスチャで作る初期データは**テストが依存する値だけ**を明示的に上書きする。既定値に暗黙依存したテストは、フィクスチャ変更で一斉に壊れる。
