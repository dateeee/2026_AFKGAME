# 製造 — プロジェクト固有プロファイル

> 一般手順は [.claude/skills/dev/SKILL.md](../skills/dev/SKILL.md) を参照。本書は AFK GAME 固有の値のみを持つ。
> 技術スタック・不変条件は [profile.md](profile.md) §3・§5。
> **コードを書く前に [.claude/references/coding-standards-backend.md](../references/coding-standards-backend.md) と、正の規約から「[common.md](../../docs/process/coding_standards_backend/common.md) + 書く層の分冊（[domain](../../docs/process/coding_standards_backend/domain.md) / [domain_service](../../docs/process/coding_standards_backend/domain_service.md) / [web](../../docs/process/coding_standards_backend/web.md) / [test](../../docs/process/coding_standards_backend/test.md)）」を読む。**クラスの置き場・呼び出し方向に迷ったら [layering.md](../../docs/process/coding_standards_backend/layering.md)。規約のベースは TERASOLUNA 開発ガイドライン 5.11.0.RELEASE 日本語版（[basis.md](../../docs/process/coding_standards_backend/basis.md) §1。ガイドラインとの差分は各分冊が持つ）。

## 1. 成果物

### バックエンド（実装順）

| 順 | 層 | パス | 規約 |
|----|----|------|------|
| 1 | モデル | `afkgame-domain` の Entity + MyBatis3 Mapper | インタフェース + XML |
| 2 | スキーマ | `afkgame-web` の Resource | Bean Validation（Jakarta）でフィールド制約 |
| 3 | サービス | `afkgame-domain` の Service | ビジネスロジックを集約 |
| 4 | コントローラ | `afkgame-web` の `@RestController` | Spring MVC（マッピング・バリデーション） |

### フロントエンド（実装順）

| 順 | 層 | パス | 規約 |
|----|----|------|------|
| 1 | 型定義 | `frontend/src/types/` | バックエンドスキーマに対応する TypeScript 型 |
| 2 | API層 | `frontend/src/api/` | API通信 |
| 3 | ストア | `frontend/src/stores/` | Pinia Setup Store 形式 |
| 4 | コンポーネント | `frontend/src/components/` | `<script setup lang="ts">` |
| 5 | ページ | `frontend/src/views/` | 必要な場合のみ |

**バックエンドを先に完成させてからフロントエンドへ移る。**

## 2. 参照先（読む順）

該当セクションのみを読む。索引から必要ファイルを特定し、全文読み込みは避ける。

| 順 | 参照先 | 内容 |
|----|--------|------|
| 1 | `docs/design/game_spec.md` → `design/systems/` | ゲームシステム・バランス仕様 |
| 2 | `docs/tech/tech_spec.md` → `tech_api.md` / `tech_data.md` / `tech_structure.md` | API設計・データ構造・実装配置 |
| 3 | `docs/tech/detail/tech_<機能>.md` | 対象機能の処理フローと**分岐一覧の正**（機能名は索引 `tech_spec.md` で特定する） |
| 4 | `docs/tech/basic/tech_db/<領域>.md` | 列・NULL・一意制約の正（**モデルを新設・変更する場合**。`check_schema_triple.py` の照合元） |
| 5 | `docs/tech/detail/tech_battle.md`・`tech_offline.md`・`tech_auth.md` | 戦闘処理・オフライン計算・認証（該当する場合の補助） |
| 6 | `docs/tech/detail/tech_rng.md`・`tech_numeric.md`・`tech_state.md` | 乱数・丸め・状態（該当する場合） |
| 7 | `docs/data/master_data.md` → `data/master/` | マスターデータ（数値定義） |
| 8 | `docs/data/towers/`・`docs/data/skills/` | 塔・スキルの個別データ（該当する場合のみ） |
| 9 | `docs/diagrams/` | ER図・クラス図・APIシーケンス図等 |
| 10 | `docs/backlog/open_specs.md` | 未確定仕様の確認（**存在する場合のみ**。不在＝未確定ゼロ） |

**未確定仕様チェック**: `open_specs.md` が存在し、実装対象に未確定項目が含まれる場合、ユーザーに通知し、実装を進めるか仕様確定（`resolve-specs` スキル）を先にするか確認する。

## 3. TDD の適用範囲

| 対象 | 適用 |
|------|------|
| `backend/` | **TDD適用**。`test-list` 工程のテストに対し Red-Green-Refactor を1テストずつ回す |
| `frontend/` | **TDD非適用**。従来どおり実装し、`vue-tsc` の型チェックと結合テストで検証する |

| # | ルール |
|---|-------|
| 1 | Red（テストが失敗する）→ Green（**最小の実装**で通す）→ Refactor（テストを保ったまま整理） |
| 2 | テストを通すために**期待値のほうを書き換えない**。テストが誤りなら詳細設計に戻って分岐一覧を正す |
| 3 | 分岐一覧に無い分岐を発見したら、**詳細設計へ追記してからテストを追加**する（実装を先に直さない） |

## 4. 固有の観点

| # | 観点 | 判定基準 |
|---|------|---------|
| 1 | Phase厳守 | 対象Phaseより後のPhaseの機能を実装していないか（将来拡張を考慮した設計は可） |
| 2 | 仕様外機能 | 仕様書に記載のない機能を追加していないか |
| 3 | データ駆動 | マスターデータの数値をハードコードしていないか |
| 4 | サーバー権威 | 戦闘計算・報酬決定をフロント側に置いていないか |
| 5 | 開発時フォールバック | `frontend/src/composables/useBattleLocal.ts` の単体動作を壊していないか |
| 6 | 既存パターン踏襲 | 命名規則・ディレクトリ構造・import規約がコーディング規約どおりか（バックエンドは [common.md](../../docs/process/coding_standards_backend/common.md) §2〜§5 + 層別の分冊、フロントは既存コードの流儀） |
| 7 | ログ | `logback-spring.xml` 準拠か（ロガー名体系は `tech_logging.md` が正） |

## 5. 動作確認

| 対象 | コマンド |
|------|---------|
| バックエンド構文 | `cd backend && mvn -q compile` |
| バックエンドテスト | `cd backend && mvn test`（TDDの全テストがGreen） |
| フロント型チェック | `cd frontend && npm run type-check` |

| # | 確認時の注意 |
|---|------------|
| 1 | テスト件数・失敗は `target/surefire-reports/*.xml`（`tests=` / `<failure`）で数える。`*.txt` は JUnit 5 の `@Nested` 配下を「Tests run: 0」と表示するため読み違える |
| 2 | 外部依存の版は**推測で書かない**。Maven Central へ1回問い合わせてから POM へ入れる（誤った版はビルド1回ぶんの空振りになる） |
| 3 | 既存 POM の版調査は properties と dependencyManagement を**まとめて1回**で出す（キーを推測した grep の空振りを繰り返さない） |

## 6. 完了基準

一般スキルの完了基準に加え、以下を満たすこと。

- `test-list` 工程の全テストが PASS（Green）
- `vue-tsc` の型チェックが PASS

## 7. 次工程

| 次にやること | 手段 |
|------------|------|
| 製造完了ゲート | `backend-review` スキル、`frontend-review` スキル（指摘対応まで完了してゲート通過） |
| 単体テストへ | `unit-test` スキル（C1網羅の測定と補完） |
