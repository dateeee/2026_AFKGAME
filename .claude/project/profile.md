# AFK GAME — プロジェクトプロファイル

全スキルが最初に読む共通プロファイル。工程固有の値は [INDEX.md](INDEX.md) の対応表から開く。

## 1. 基本情報

| 項目 | 値 |
|------|-----|
| 種別 | 放置系ファンタジーRPG（Webブラウザゲーム） |
| 構成 | フロントエンド（Vue 3 SPA）+ バックエンド（Java/Terasoluna・war + Tomcat）の2層 |
| 開発工程 | 7工程。工程の定義と**Phase進捗の正**は [development_process.md](../../docs/process/development_process.md)（進捗は §5） |

## 2. ディレクトリ

| パス | 内容 |
|------|------|
| `backend/` | `afkgame-domain`（Entity・Repository・Service・マスターデータ）、`afkgame-web`（Controller・Resource・Security）、`afkgame-env`（DataSource・設定）、`afkgame-initdb`（Flyway） |
| 各モジュールの `src/test/java/` | 単体（JUnit5+Mockito）と統合（`SpringExtension`+MockMvc）をパッケージで分離 |
| `frontend/src/` | `components/` `views/` `stores/` `api/` `types/` `composables/` `router/` `utils/` `assets/` |
| `frontend/tests/e2e/` | E2Eテスト（Playwright） |
| `docs/` | `design/` 要件定義 / `tech/` 基本・詳細設計 / `data/` マスターデータ / `diagrams/` 設計図6点（各図は索引 + 同名ディレクトリ）/ `reviews/` レビュー結果アーカイブ（上限の対象外）。全ファイルは [docs/INDEX.md](../../docs/INDEX.md) |

## 3. 技術スタック

記述規約の正は [coding_standards_backend.md](../../docs/process/coding_standards_backend.md)（索引 + 分冊。**ベースは TERASOLUNA 開発ガイドライン 5.11.0**）、要約は [coding-standards-backend.md](../references/coding-standards-backend.md)。本表は**採用技術の一覧**に限り、配置は §2。

| 層 | 技術 | 規約 |
|----|------|------|
| DBアクセス | MyBatis3 | Entity + Repository（インタフェース + マッピングXML） |
| スキーマ(DTO) | Resource + Bean Validation（Jakarta） | Jackson が camelCase を維持（変換不要） |
| ロジック | Java 17 | Service に集約。Controller にビジネスロジックを書かない |
| API | Spring MVC（Terasoluna） | `@RestController`、DIはコンストラクタ注入 |
| ログ | Logback + MDC | `logback.xml` 準拠。`X-Request-ID` は MDC で引き回す |
| UI | Vue 3 | `<script setup lang="ts">` + Composition API |
| 状態管理 | Pinia | `defineStore` の **Setup Store 形式** |
| 型 | TypeScript | 厳密な型定義。`any` を避ける |
| テスト | JUnit 5 + Mockito + JaCoCo | C1（分岐）カバレッジ100%（設定は [unit-test.md](unit-test.md) §1） |

## 4. 常用コマンド

コマンド表は [commands.md](commands.md) が正。本表は全セッションで必要なコミット作法のみ。

| 目的 | コマンド |
|------|---------|
| 複数行コミットメッセージ | Bash ツール: `git commit -F - <<'MSG'`／PowerShell ツール: `git commit -m @'...'@`。**取り違えると `@` が本文へ混入し amend が必要になる** |

## 5. アーキテクチャ不変条件

破ってはいけない前提。設計・実装・レビューのすべてで守る。

| # | 不変条件 | 理由 |
|---|---------|------|
| 1 | **サーバー権威**: 戦闘計算はバックエンドで実行する。フロントはログ表示のみ | チート対策 |
| 2 | **ハイブリッドtick制**: 60秒固定間隔。オンライン中はポーリング、オフライン中は復帰時に一括計算 | 放置ゲームの中核 |
| 3 | **シングルプレイ専用** | マルチプレイ前提の設計を持ち込まない |
| 4 | **開発時フォールバック**: バックエンド未起動でもフロント単体で動作する（`frontend/src/composables/useBattleLocal.ts`） | デバッグ用途 |
| 5 | **Phase厳守**: 対象Phaseより後のPhaseの機能を実装しない（将来拡張を考慮した設計は可） | 段階的リリース |
| 6 | **データ駆動**: マスターデータの数値をコードにハードコードしない | バランス調整の分離 |

## 6. コスト規律

`.claude/skills/**` の全スキルに共通して適用する。**本表が正**（CLAUDE.md「コスト規律」は要約 + リンク。[docs/process/spec_ownership.md](../../docs/process/spec_ownership.md)）。

| # | 規律 |
|---|------|
| 1 | サブエージェントは**並列化の価値がある場合のみ**（同時最大4体）。1体で済む調査・修正はメインコンテキストで行う |
| 2 | 機械的な作業（一括置換・リンク修正・定型データ生成・構文検証）は `model: sonnet` のサブエージェントか使い捨てスクリプトで処理する |
| 3 | サブエージェントには**担当ファイルのみを列挙**し、「列挙外は読まない」「戻り値は結論のみ」を明示する |
| 4 | 仕様書・コードは**必要なセクションだけ読む**。索引で担当ファイルを特定 → 該当ファイルのみ（大きいファイルは `Read` の offset/limit で節単位）。**同一セッション内で同じファイルを再 Read しない**（Edit 失敗時の再確認、worktree 統合等でファイルが変わった場合を除く） |
| 5 | 工程の区切り（レビュー完了・コミット後）で `/clear` を既定として提案する（同一タスクを続ける場合のみ `/compact`）。レビュー→修正適用は別セッションに分ける |
| 6 | 大きな出力（ログ・テスト結果・git履歴・集計・検索）の処理は context-mode（`ctx_batch_execute` / `ctx_execute`）で行い、生出力を会話に持ち込まない。ファイルの分析・要約は `ctx_execute_file`。`Read` の全文読みは Edit 前提のときのみ |

レビュー系スキル固有の規律（差分モード既定・分担・照合範囲）は [review-procedure.md](../references/review-procedure.md) §1 が正。

## 7. ドキュメント規約

| # | 規約 |
|---|------|
| 1 | 文字数上限は [docs/process/documentation_rules.md](../../docs/process/documentation_rules.md) §3（`.claude/**` は区分D = 5,000字） |
| 2 | **変更履歴セクションを個々のファイルに置かない**。改稿時は [docs/changelog.md](../../docs/changelog.md) の先頭へ1行追記する（§5.1） |
| 3 | 作成・改稿後は §4 の規約チェックと機械検証を実行する。超過は `documentation_rules.md` §7 の台帳運用（B・Cは登録して一括是正へ、A・Dはセッション内是正） |
| 4 | 同じ数値・仕様の正は1ファイル。トピックごとの正は `docs/process/spec_ownership.md` で宣言する |
| 5 | 機械検証は §4 の常設スクリプトを優先し、使い捨ては常設で賄えない検証のみ（繰り返すなら常設化を提案する） |
| 6 | CLAUDE.md と `.claude/project/**` で重複するルールを改稿する際は、もう一方を必ず突合して同時に更新する |
| 7 | **書く前に残量を測る**（`check_doc_size.py --sections <path>`）。**追記予定の字数も `len()` で実測**（目分量禁止）し、超えるなら完了済みセクションの圧縮を**同じ編集にまとめる**。`len()` は見積もりなので**編集後に `--sections` で確定する**。詳細は [doc-size.md](doc-size.md) §3.1 |

## 8. worktree 運用

**ファイルを編集する作業は worktree 内で行う**。手順・競合ポリシー・注意点の正は [worktree_guide.md](../../docs/process/worktree_guide.md) §5（main のままでよい作業 §5.1 / 開始 §5.2 / 統合 §5.3。作成・統合・削除にユーザー確認は不要）。

