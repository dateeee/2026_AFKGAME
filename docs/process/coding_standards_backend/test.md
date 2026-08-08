# バックエンドコーディング規約 — テスト

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。全層共通の規約は [common.md](common.md) が先（テストコードも同じ記述規約に従う）。
> ベースは TERASOLUNA 開発ガイドライン 5.11.0.RELEASE 日本語版（索引 §1）の `UnitTest`。本書はそこからの差分だけを持つ。
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
