# 基本設計 — プロジェクト固有プロファイル

> 一般手順は [.claude/skills/basic-design/SKILL.md](../skills/basic-design/SKILL.md) を参照。本書は AFK GAME 固有の値のみを持つ。
> 共通の値は [profile.md](profile.md)。

## 1. 成果物

| 成果物 | パス | 役割 |
|-------|------|------|
| 技術仕様（索引） | `docs/tech/tech_spec.md` | 個別ファイルへの索引 |
| DB設計 | `docs/tech/basic/tech_db.md` + `tech_db/` | 物理テーブル名・列の型・キー・一意制約・インデックス・命名規約。**DBスキーマの正** |
| データ構造 | `docs/tech/basic/tech_data.md` | API レスポンス／マスターデータの JSON 構造（永続化スキーマは持たない） |
| 実装配置 | `docs/tech/basic/tech_structure.md` | ディレクトリ構成・モジュール責務 |
| API設計 | `docs/tech/basic/tech_api.md` | エンドポイント一覧 |
| API共通仕様 | `docs/tech/basic/tech_api_common.md` | 規約・共通ヘッダ・ステータスコード |
| アーキテクチャ | `docs/tech/basic/tech_architecture.md` | 層構成・依存方向 |
| ログ設計 | `docs/tech/basic/tech_logging.md` | ログレベル・出力項目 |
| 認証方式 | `docs/tech/detail/tech_auth.md` | 認証フロー・トークン |
| 性能・容量 | `docs/tech/nonfunctional/tech_performance.md` | 非機能要件の実現方式 |
| セキュリティ | `docs/tech/nonfunctional/tech_security.md` | 非機能要件の実現方式 |
| 運用 | `docs/tech/nonfunctional/tech_operations.md` | 運用要件の実現方式 |

**執筆前の分量見積もり**: 表形式の定義書は1テーブル約550字。区分Cの8,000字を超える構成は、**書き上げてから測るのではなく執筆前に**系統単位へ分割する（`tech_db/` が認証系・プレイヤー系・装備系…と分かれているのはこのため）。

### 設計図（6点・索引 + 同名ディレクトリ構成）

| 図 | パス | 検証対象 |
|----|------|---------|
| ER図 | `docs/diagrams/er_diagram.md` + `er_diagram/` | `tech_db.md`（正）・`afkgame-domain` の Entity + MyBatis3 Mapper |
| クラス図 | `docs/diagrams/class_diagram.md` + `class_diagram/` | `afkgame-domain`・`afkgame-web`・`frontend/src/` の構造 |
| 画面遷移図 | `docs/diagrams/screen_transition.md` + `screen_transition/` | `design/systems/ui*.md`・`frontend/src/router/` |
| 戦闘フロー図 | `docs/diagrams/battle_flow.md` + `battle_flow/` | `tech_battle.md`・`afkgame-domain` の戦闘 Service |
| システム構成図 | `docs/diagrams/system_architecture.md` + `system_architecture/` | `tech_architecture.md`・`tech_operations.md` §12 |
| APIシーケンス図 | `docs/diagrams/api_sequence.md` + `api_sequence/` | `tech_api.md`・`afkgame-web` の `@RestController` |

**索引で担当ファイルを特定し、必要な子ファイルのみ読む**（全図の一括読み込みは禁止）。

## 2. 参照先（読む順）

| 順 | 参照先 | 読む範囲 |
|----|--------|---------|
| 1 | `docs/design/game_spec.md` → `systems/` | 設計対象の機能セクションのみ |
| 2 | `docs/design/requirements/non_functional_requirements.md` | 要求値の該当行のみ |
| 3 | `docs/tech/tech_spec.md` | 索引から対象ファイルを特定 |
| 4 | 既存の Java モジュール（`afkgame-domain`・`afkgame-web`）・`frontend/src/` | 対応するモジュールのみ（現状のパターン把握） |

## 3. 固有の観点

| # | 観点 | 判定基準 |
|---|------|---------|
| 1 | サーバー権威 | 戦闘計算・報酬決定のAPIがすべてバックエンド側にあるか。フロントに計算を持たせていないか |
| 2 | tick制の一貫性 | 60秒固定間隔の前提が `tech_tick.md`・`tech_polling.md`・API設計で揃っているか |
| 3 | API網羅性 | `game_spec.md` の各機能に対応するエンドポイントが `tech_api.md` に存在するか |
| 4 | データ構造の表現力 | `tech_db.md` のテーブル・列が `game_spec.md` の仕様を表現できているか |
| 5 | 図とテキストの一致 | ER図のPK/FK・APIシーケンスのエンドポイント名が `tech_*.md` と一致しているか |
| 8 | DBスキーマ三者一致 | テーブル定義書 ↔ ER図 ↔ `afkgame-domain` の Entity + Mapper（テーブル名・列・キーの対応）が一致しているか（正は定義書） |
| 9 | インデックスの根拠 | 各インデックスに、それを使う検索パターン（サービス層のクエリ）が定義書へ書かれているか |
| 6 | オフライン復帰 | 復帰時の一括計算が API・データ構造の両面で成立しているか |
| 7 | 非機能の実現方式 | 非機能・運用要件の各項目が `tech_performance` / `tech_security` / `tech_operations` のいずれかに対応づいているか |

## 4. 機械検証（Mermaid・DBスキーマ）

設計図の構文とスキーマの一致は**目視しない**。常設スクリプトを先に実行し、使い捨ては常設で賄えない検証だけに書く（[profile.md](profile.md) §7-5）。

| 検証項目 | 方法 |
|---------|------|
| DBスキーマ一致 | `python scripts/check_schema_triple.py`。`tech_db/`（正）↔ `er_diagram/` ↔ Python models ↔ Flyway DDL を、列名・並び順・PK/FK/UK タグ・一意制約・FKなし宣言・nullable・制約の命名規約・ER索引の7観点で照合する |
| 相対リンク切れ・索引到達性 | `python scripts/check_docs.py --links --reach` |
| 文字数上限 | `python scripts/check_doc_size.py` |
| コードフェンスの閉じ漏れ | ` ```mermaid ` と ` ``` ` の対応を数える（使い捨て） |
| `end` の対応 | `subgraph` / `loop` / `alt` / `opt` と `end` の数を照合（使い捨て） |
| ER図のリレーション整合 | リレーション両端のエンティティが定義済みか（使い捨て） |

**常設スクリプトが解析するドキュメント行**（`check_schema_triple.py` のアンカー。書式を変えると照合が停止する）:

| 行 | 役割 |
|----|------|
| `tech_db/<領域>.md` の `` ## N `table_name` `` 見出し | テーブルの特定 |
| その直下の `` 実装: `backend/...` `Class` `` 行 | 実装クラスの特定 |

**実装側が変わるまでこの2行を書き換えない**（技術スタック移行等の一括改訂で置換対象に含めない）。Flyway DDL の照合はテーブル名で対応づけるため、この2行に依存しない。

`check_schema_triple.py` は定義書にあるテーブルを起点に照合する。定義書に無い ER図エンティティはマスターデータの論理設計とみなして「対象外」へ列挙するだけなので（[tech_db.md](../../docs/tech/basic/tech_db.md) §4-6）、**DBテーブルにすべきものが定義書から漏れている場合は検出できない**。出力の「対象外」一覧が想定どおりかは目視で確かめる。

## 5. 完了基準

一般スキルの完了基準に加え、以下を満たすこと。

- 仕様書・設計図間に矛盾がない（`diagrams-review` の指摘解消）
- DB変更時は §4「DBスキーマ三者一致」が差分ゼロ。追加した列は `nullable` または `server_default` を持つ（前方互換）
- 要件定義の非機能・運用要件がすべて、実現方式を定めたいずれかの成果物に対応づいている
- 新規の仕様ファイルは索引（`README.md`）へ登録し、既存ファイルと記述が重なるトピックの正を [docs/process/spec_ownership.md](../../docs/process/spec_ownership.md) へ宣言している
- `python scripts/check_doc_size.py`・`python scripts/check_docs.py` が exit 0

## 6. 次工程

| 次にやること | 手段 |
|------------|------|
| 設計整合・仕様確定ゲート | `diagrams-review` → `doc-review` を続けて実行し、両レポートの指摘を**1回の修正パス**で反映する |
| 詳細設計へ | `detail-design` スキル |
