# バックエンドコーディング規約 — テスト

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。全層共通の規約は [common.md](common.md) が先（テストコードも同じ記述規約に従う）。
> ベースは TERASOLUNA 開発ガイドライン 5.11.0.RELEASE 日本語版（[basis.md](basis.md) §1）の `UnitTest`。本書はそこからの差分だけを持つ。
> **テストコードの書き方の正は本書**（`.claude/project/test-list.md` §5 から移管）。工程側が持つものは §4 の分担表を参照。

---

## 1. 配置と分離

| 項目 | 規約 |
|------|------|
| 配置 | 各モジュールの `src/test/java/.../<対象クラス>Test.java`（対象クラスと同じパッケージ） |
| 単体テスト | `@Tag("unit")`。Service・Controller・フィルタ・マスターデータが対象。依存は Mockito でモックする |
| 統合テスト | `@Tag("integration")`。Mapper・`@SpringBootTest` + MockMvc。DB は埋め込み PostgreSQL |
| 実行の分離 | surefire が `integration` を除外し、failsafe が `integration` だけを回す。**C1（分岐カバレッジ）は単体テストだけで測る** |
| パッケージ | 単体と統合をパッケージで分ける（[profile.md](../../../.claude/project/profile.md) §2） |

## 2. 記述規約

| # | 規約 |
|---|------|
| 1 | テストクラスに `@Tag("unit")` を付ける（統合テストは `@Tag("integration")`） |
| 2 | クラス Javadoc に**仕様書の参照先**と**分岐観点**を書く |
| 3 | 観点ごとに `class Test<対象>` でグループ化する |
| 4 | テストメソッド名は**日本語**で期待する振る舞いを書く（例: `test_目標階が上限と一致していれば追従する`） |
| 5 | 境界値・等価クラスは `@ParameterizedTest` + `@CsvSource` / `@MethodSource` にまとめ、各ケースにコメントで意図を書く |
| 6 | テストメソッドの Javadoc に対応マーカー「`分岐: tech_<対象>.md §<節> #<行番号>`」を書く（一覧が1つの文書は §省略可、`#3,4` と複数可）。`check_branch_list.py --tests` がこれで対応を照合する |

## 3. 再現性

| # | 規約 |
|---|------|
| 1 | 時刻・乱数は必ず固定する（モック・シード）。実装側が「外から受ける」形になっているのはこのため（[common.md](common.md) §4 #2） |
| 2 | テスト間で状態を共有しない（static な可変フィールドを持たない）。統合テストは `@SpringBootTest` ごとにスキーマを作り直す |
| 3 | マスターデータ・エラーコード・列挙値は定義元のドキュメントを読んでから書く（推測で書かない） |

## 4. 本書が持たないもの（分担）

| 内容 | 正 |
|------|-----|
| TDD の適用範囲・分岐一覧からの展開手順・共通テストユーティリティの台帳 | [.claude/project/test-list.md](../../../.claude/project/test-list.md) |
| カバレッジ測定コマンド・JaCoCo 設定・固有の分岐観点 | [.claude/project/unit-test.md](../../../.claude/project/unit-test.md) |
| 結合テストのレイヤー構成・必須シナリオ・E2E | [.claude/project/integration-test.md](../../../.claude/project/integration-test.md) |
| 実装パターンの実例 | [.claude/project/test-patterns.md](../../../.claude/project/test-patterns.md) |

## 5. ガイドラインとの差分

ガイドラインの `@SpringJUnitConfig` + `test-context.xml` 構成は **Boot 流儀（`@SpringBootTest`・`@AutoConfigureMockMvc`）へ読み替えて適用する**。読み替えは逸脱ではない（10.1.2.2 の OSS 表も JUnit・AssertJ・Mockito・Spring Test を Boot 管理としている）。そのうえで**意図して採らない**と決めたものは以下。ここに無い差分を見つけたらガイドライン側へ寄せる。

| # | ガイドライン | 本書の決定と理由 |
|---|------------|----------------|
| 1 | Repository の単体テストは DBUnit / Spring Test DBUnit で書く（10.2.2.1.1.2） | 採らない。埋め込み PostgreSQL（zonky）+ `JdbcTemplate` でフィクスチャを作る。`@Transactional` ロールバック・固定時刻・親レコード生成が既に成立済みで、Excel のデータ定義ファイルは保守対象を増やすだけ。Boot 管理外の依存2件も避けられる |
| 2 | Repository はインフラストラクチャ層の**単体**テスト（10.2.2.1） | Mapper は `@Tag("integration")`（結合側）へ分類する。実 DB 起動を伴うため。**C1 の分母を実 DB なしで閉じる**ための線引き（§1「実行の分離」） |
| 3 | モックの注入は `@InjectMocks`（10.2.4.3.3.1） | テスト内でコンストラクタへ手渡す。本体がコンストラクタ注入（[common.md](common.md) §4 #1）のため、手渡しなら依存の欠落がコンパイルエラーで出る。`@InjectMocks` はリフレクション注入で失敗が静かになる |
| — | `MockMvcTester`（10.2.4.2.3） | 既存は旧 `MockMvc#perform().andExpect()` のままとし、新規テストでの採用は任意。旧 API は非推奨ではなく、移行の得は記述の簡潔さのみ。**決め切っていないため差分として確定させない**（採否は書き手に委ねる） |

**新規実装から適用する**もの（既存は書き直さない）。

| # | ルール |
|---|-------|
| 1 | [tech_logging.md](../../tech/basic/tech_logging.md) にログ要件があるクラスは `ListAppender<ILoggingEvent>` でログレベル・メッセージ・MDC を検証する（10.2.3 準拠） |
| 2 | カスタム制約アノテーションを作ったら `jakarta.validation.Validator` を直接使う単体テストを必ず添える（10.2.3.1）。標準制約だけの Resource は `ApiExceptionHandler` 経由の 422 検証で足りる |
