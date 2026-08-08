# AFK GAME — 工程定義

> [development_process.md](development_process.md) §3 の担当ファイル。工程モデル・工程ゲート・現況・変更管理は親を参照。節番号 §3.x は分割前を維持している。

## 3.1 要件定義

| 項目 | 内容 |
|------|------|
| 目的 | ゲームとして「何を作るか」を確定する |
| 主な作業 | ゲームシステム・バランス・UI要件の定義、未確定仕様の解消 |
| 成果物 | [game_spec.md](../design/game_spec.md)（索引）＋ [design/systems/](../design/systems/)、要件3種（[product](../design/requirements/product_requirements.md) / [nfr](../design/requirements/non_functional_requirements.md) / [operation](../design/requirements/operation_requirements.md)）、[glossary.md](../glossary.md) |
| 完了基準 | game_spec.md 系へ反映され、仕様確定ゲート（[development_process.md](development_process.md) §4）を通過している |
| レビュー | `doc-review` スキル → 指摘は `fix-specs` スキルで反映 |

## 3.2 基本設計（ハイレベル設計）

| 項目 | 内容 |
|------|------|
| 目的 | システム構造・API・データモデル・画面構成と、非機能要件の実現方式を確定する |
| 主な作業 | アーキテクチャ設計、API一覧・共通仕様の定義、**DB設計（テーブル定義・キー・インデックス・制約）とER図作成**、画面遷移設計、性能／セキュリティ／運用の実現方式の設計、**コーディング規約の策定・改訂**（§3.2.2） |
| 成果物 | [tech_spec.md](../tech/tech_spec.md)（索引）配下の**構造**（data / **db** / structure / api / architecture / logging / auth）と**非機能の実現方式**（performance / security / operations）、[docs/diagrams/](../diagrams/) 6点、**コーディング規約**（[coding_standards_backend.md](coding_standards_backend.md)） |
| 完了基準 | 仕様書・設計図間の矛盾がない（`diagrams-review` の指摘解消）。要件定義の非機能・運用要件がすべて、実現方式を定めたいずれかの成果物に対応づいている。新規・変更テーブルが**テーブル定義書とER図の双方**へ反映されている（§3.2.1）。技術スタック・レイヤ構成を変えた場合はコーディング規約が追随している（§3.2.2） |
| レビュー | `diagrams-review` スキル、`doc-review` スキル |

### 3.2.1 DB設計とER図

DBスキーマは**テキスト（テーブル定義書）を正、ER図を視覚化**とする（[spec_ownership.md](spec_ownership.md)）。両者は必ず同じ変更で更新する。

| 成果物 | 内容 | 位置づけ |
|-------|------|---------|
| `docs/tech/basic/tech_db.md`（索引）+ `tech_db/` | 物理テーブル名・列の物理型・NULL/既定・主キー/外部キー/一意制約・インデックス・外部キー動作・命名規約・導入Phase | **正** |
| [er_diagram.md](../diagrams/er_diagram.md) + `er_diagram/` | エンティティ・関連・カーディナリティの一望図（属性は視覚化としての再掲） | 視覚化 |
| `afkgame-domain` の Entity/Mapper + `afkgame-initdb` の Flyway マイグレーション | 実装。製造（§3.5）で定義書どおりに作る | 実装 |

- **差し戻しルール**: 詳細設計以降で「定義書にないテーブル・列が要る」と判明したら、実装を先に書かず**基本設計へ戻して定義書とER図を更新**してから進む（§3.4 の分岐一覧と同じ扱い）
- インデックスは**それを使う検索パターンとセット**で書く。パターンの無いインデックスは作らない
- 列の追加は `nullable` または `server_default` を付ける（前方互換。[tech_operations.md](../tech/nonfunctional/tech_operations.md) §12.4）

### 3.2.2 コーディング規約

**コーディング規約は基本設計の成果物**とする。技術スタックとレイヤ構成が決まって初めて書ける一方、製造（§3.5）の全コードが従う前提であり、実装が始まってから決めると既存コードとの整合コストが出るため。

**フロントエンドとバックエンドで別々の規約を持つ**（言語・フレームワーク・レイヤ構成が異なり、1冊にまとめると双方で読み飛ばしが起きるため）。

| 規約 | 対象 | 状態 |
|------|------|------|
| [coding_standards_backend.md](coding_standards_backend.md)（索引 + [coding_standards_backend/](coding_standards_backend/) の7分冊: basis / layering / common / domain / domain_service / web / test） | `backend/` の Java（Terasoluna / MyBatis3）。**TERASOLUNA 開発ガイドライン 5.11.0.RELEASE 日本語版をベースとし、規約はその差分だけを持つ**（準拠元URLと逸脱一覧は [basis.md](coding_standards_backend/basis.md) §1・§3） | 整備済み |
| `coding_standards_frontend.md` | `frontend/` の Vue 3 / TypeScript | **未整備**。フロントエンドの製造再開前に作成する |

- **正は `docs/process/` 側**。エージェントが実装・レビュー時に読む要約を [.claude/references/coding-standards-backend.md](../../.claude/references/coding-standards-backend.md) へ派生させる。改訂は**正 → 派生の順に同じ変更で**行う（派生側に固有値を書かない）
- 規約の**境界**: テストコードの記述規約は [coding_standards_backend/test.md](coding_standards_backend/test.md)、レビュー観点は [.claude/project/review-code.md](../../.claude/project/review-code.md) §2、技術スタックの一覧は [.claude/project/profile.md](../../.claude/project/profile.md) §3 が正。規約側で再掲しない（[spec_ownership.md](spec_ownership.md)）
- **改訂の起点**: ①技術スタック・レイヤ構成の変更 ②`backend-review` / `frontend-review` で同じ指摘が繰り返された（規約へ昇格させる）③実装で新しい流儀が必要になった（先に規約を直してから実装する）
- 既存コードの一括是正は行わない。逸脱は [known_issues.md](../backlog/known_issues.md) へ記録し、その箇所を触るときに直す

## 3.3 詳細設計（ローレベル設計）

| 項目 | 内容 |
|------|------|
| 目的 | 対象Phaseの機能を「実装可能な粒度」まで具体化する |
| 主な作業 | 処理フロー・アルゴリズム・計算式の定義、マスターデータの数値確定 |
| 成果物 | [tech_spec.md](../tech/tech_spec.md) 配下の**処理**（battle / offline / tick / polling）と**横断規約**（rng / numeric / state）、**数値**は [master_data.md](../data/master_data.md)（索引）＋ [data/](../data/) 配下 |
| 完了基準 | 対象Phase機能の数値・計算式・分岐条件が仕様書から一意に実装できる（数値は仮置き可、ただし「仮置き」と明記）。各処理仕様に**分岐一覧（単体テスト観点）**が記載され、§3.4 のテストリストと §3.6 のC1網羅の導出元になっている |
| レビュー | `doc-review` スキル（詳細仕様の整合確認） |

## 3.4 テストリスト作成

| 項目 | 内容 |
|------|------|
| 目的 | 実装前にテストを確定し、製造をTDDで進められる状態にする |
| 対象 | **バックエンドのみ**（`services/`・`master_data/` は厳格に、`routers/` は TestClient で先行作成）。フロントエンドは対象外 |
| 入力 | 詳細設計の**分岐一覧（単体テスト観点）**（§3.3 完了基準） |
| 主な作業 | 分岐一覧を1観点1テストへ展開し、`backend/tests/unit/` に**失敗するテスト**として記述する |
| 成果物 | テストコード（実装前。全件 FAIL または ERROR） |
| 完了基準 | 分岐一覧の全項目にテストが対応し、実行して**期待どおりに失敗する**（Red の確認） |
| 禁止 | 実装を先に書くこと、テストを実装の後追いで書くこと |

- 分岐一覧に無い分岐を製造中に発見した場合は、**詳細設計へ追記してからテストを追加**する（実装を先に直さない）

## 3.5 製造

| 項目 | 内容 |
|------|------|
| 目的 | §3.4 のテストを満たす実装をTDDで作る |
| 主な作業 | backend/（Terasoluna(Spring Boot)）は Red-Green-Refactor を1テストずつ回す。frontend/（Vue 3）は従来どおり実装 |
| 成果物 | 実装コード一式（テーブルの追加・変更を伴う場合は Flyway マイグレーションを含む） |
| 規約 | **バックエンドは [coding_standards_backend.md](coding_standards_backend.md) に従う**（層の責務・命名・例外・ログ・Javadoc）。フロントエンドは規約整備まで既存コードの流儀に倣う（§3.2.2）。Entity/Mapper は §3.2.1 のテーブル定義書どおりに作り、定義書に無い列を足さない |
| 完了基準 | §3.4 の全テストがPASS、`backend-review`・`frontend-review` の指摘対応完了（コーディング規約からの逸脱ゼロ）。テーブル変更がある場合は Flyway マイグレーションが存在し `flyway migrate` が通る |
| レビュー | `backend-review` スキル、`frontend-review` スキル（仕様↔コードの統合整合は §3.7 の `full-review`） |

- **TDDサイクル**: Red（テストが失敗する）→ Green（**最小の実装**で通す）→ Refactor（テストを保ったまま整理）
- テストを通すために期待値のほうを書き換えない。テストが誤りなら詳細設計に戻って分岐一覧を正す
- フロントエンドはTDD対象外。`vue-tsc` の型チェックと結合テスト（§3.7）で検証する

## 3.6 単体テスト

| 項目 | 内容 |
|------|------|
| 目的 | TDDで作成したテスト群のC1網羅を測定し、漏れた分岐を補完する |
| 対象 | バックエンド（`services/`・`master_data/`・`routers/` の関数・分岐） |
| フレームワーク | JUnit 5 + Mockito + JaCoCo |
| 配置 | `backend/tests/unit/` |
| カバレッジ基準 | **C1（分岐網羅）100%**: JaCoCo branch カバレッジ100%（`mvn test jacoco:check`） |
| 除外規則 | JaCoCo の `<excludes>` 指定は理由コメント必須（例: 起動クラス `AfkgameApplication` のみ許容） |
| 完了基準 | 全テストPASS かつ C1カバレッジ100% |

- TDDのテストだけではC1 100%に届かないことがある（分岐一覧の漏れ）。本工程で測定し、補完した分岐は詳細設計の分岐一覧へ反映する
- 乱数を含むロジック（ダメージ分散・ドロップ抽選・エンカウント抽選）は乱数を固定（シード固定の `java.util.Random` を注入、または Mockito のスタブ）して分岐を網羅する
- フロントエンドの単体レベル検証は Playwright（§3.7）に統合する（型検証は `vue-tsc` を製造工程で実施）

## 3.7 結合テスト

| 項目 | 内容 |
|------|------|
| 目的 | 基本設計どおりにAPI・画面が連携して動作することを検証する |
| レイヤー1: API統合テスト | MockMvc（`@SpringBootTest`）+ テスト用 PostgreSQL。認証→塔選択→tick→報酬などのAPIシーケンスを検証。配置: 各モジュールの `src/test/java`（統合テストパッケージ） |
| レイヤー2: E2Eテスト | **Playwright**。フロントエンド＋バックエンドを通しで起動し、画面操作ベースで検証。配置: `frontend/tests/e2e/` |
| シナリオの導出元 | [docs/diagrams/screen_transition.md](../diagrams/screen_transition.md)（画面遷移図）、[docs/diagrams/api_sequence.md](../diagrams/api_sequence.md)（APIシーケンス図） |
| 完了基準 | 対象Phaseの主要シナリオが全PASS |
