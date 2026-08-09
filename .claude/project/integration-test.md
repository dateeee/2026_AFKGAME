# 結合テスト — プロジェクト固有プロファイル

> 一般手順は [.claude/skills/integration-test/SKILL.md](../skills/integration-test/SKILL.md) を参照。本書は AFK GAME 固有の値のみを持つ。
> 共通の値は [profile.md](profile.md)。

## 1. 成果物

| レイヤー | 内容 | 配置 | 状態 |
|---------|------|------|------|
| L1: API統合テスト | MockMvc（`@SpringBootTest`）+ 埋め込み PostgreSQL（`@AutoConfigureEmbeddedDatabase(provider = ZONKY)`）。APIシーケンスを検証 | Maven の `src/test/java`（統合テストパッケージ） | **整備済み**（Phase 1〜2） |
| L2: E2Eテスト | Playwright。フロント＋バックを通しで起動し画面操作で検証 | `frontend/tests/e2e/` | **整備済み**（Phase 1〜2） |

### 1.1 L1 の記述規約

| 項目 | 規約 |
|------|------|
| マーカー | `@Tag("integration")` |
| 実行 | `cd backend && mvn test -Dgroups=integration` |
| ファイル分割 | 導線ごとに1クラス（`AuthFlowIntegrationTest` / `TowerFlowIntegrationTest` / `BattleFlowIntegrationTest` / `ShopFlowIntegrationTest` / `EquipmentFlowIntegrationTest`） |
| プレイヤー生成 | 直接作らず **`POST /api/auth/guest` から始める** |
| DBセッション | 実際の Repository（`@SpringBootTest` の `SqlSessionFactory` が実装を供給）を使う。単体テストのモック Repository とは差し替え、本番と同じマッピング設定で検証する |
| 乱数 | 固定シードの `Random` を DI で注入する（`tech_rng.md`） |
| 時刻 | `rewind(player, 秒)` で `last_tick_at` を過去へ戻す。スリープしない |
| ドロップ | 固定した `Random` 注入で抽選を成立させる（ドロップ率は検証対象外） |
| ログ経由の値 | Logback の `ListAppender`（テストユーティリティ）で検証する（`afkgame` ロガーは `additivity=false`） |
| 構成のネスト | テストクラスに `@Configuration` を**ネストしない**。Spring Boot がそれをテスト本体の構成として採用し、壊れた構成でコンテキストが起動する。起動失敗を検証したい場合は `new ApplicationContextRunner().withBean(...)` で別コンテキストとして組む |

### 1.2 L2 の記述規約

| 項目 | 規約 |
|------|------|
| 実行 | `cd frontend && npm run test:e2e`（`playwright.config.ts` がフロント・バックを自動起動） |
| サーバー | バック :8100（`DATABASE_URL=jdbc:postgresql://localhost:5432/afkgame_e2e`）／フロント :5174。開発用の :8000 / :5173 とDBを分ける |
| 起動確認 | バックは `GET /health` が通るまで待つ。`reuseExistingServer` は使わない（開発用DBを掴む事故を防ぐ） |
| 実行順 | 1つのDBを共有するため `workers: 1` の直列。独立性は**テストごとにゲストを作る**ことで担保 |
| ヘルパー | `tests/e2e/support/harness.ts`。画面操作はUI経由、DB直接操作は**時刻の巻き戻しだけ** |
| 時刻 | `advanceTicks(page, n)` で `last_tick_at` を戻して再読み込み。tick を起こすのはアプリ側 |
| 乱数 | ドロップ・報酬は固定できないため `advanceUntil(page, 条件)` で条件成立まで進める。回数を決め打ちしない |
| リトライ | `retries: 0`。不安定なテストはリトライで隠さず原因を直す |
| セレクタ | `data-testid` は使わない。role・表示文言と、意味のあるクラス（`.tower-card` 等）で引く |
| 注意 | 正規表現マッチは空白を正規化しない。改行を含む要素は文字列マッチで引く |

## 2. シナリオの導出元

| 順 | 参照先 | 読む範囲 |
|----|--------|---------|
| 1 | `docs/diagrams/screen_transition.md` | 対象Phaseの画面遷移のみ |
| 2 | `docs/diagrams/api_sequence.md` → `api_sequence/` | 対象シーケンスの子ファイルのみ |
| 3 | `docs/tech/basic/tech_api.md` | 対象エンドポイントの行のみ |
| 4 | `docs/design/systems/ui*.md` | 対象画面のセクションのみ |

**単体テストの分岐一覧からシナリオを作らない**。結合テストが検証するのは**基本設計**（API設計・画面遷移・データ構造）であって、詳細設計の分岐ではない。

### 2.1 経路の網羅の抽出元

工程内検証（SKILL §5.1「経路の網羅」）は目視せず、下表を使い捨てスクリプトで抽出して差分を取る。

| 対象 | 設計側の抽出元 | テスト側の突き合わせ先 |
|------|-------------|------------------|
| エンドポイント | `afkgame-web` の `@RestController` のマッピングアノテーション（`@GetMapping` 等）。仕様との差は `docs/tech/basic/tech_api.md` のエンドポイント表で確認する | 統合テスト内のリクエストパス |
| 画面 | `frontend/src/router/index.ts` の `path` | `frontend/tests/e2e/` 内の遷移先パス（`goto` ・リンク操作後のURL） |

差分に出た経路は §3.1「意図的に扱わない経路」に宣言があるか確認し、無ければ**不足として報告**する。

## 3. 必須シナリオ（Phase 1〜2）

| # | シナリオ | 検証内容 | L1 | L2 |
|---|---------|---------|----|----|
| 1 | 認証 → ゲーム状態取得 | トークン発行、初期状態の返却 | `test_auth_flow` | `auth.spec` |
| 2 | 塔選択 → 目標階設定 | 塔別クリア記録の独立、上限追従 | `test_tower_flow` | `tower.spec` |
| 3 | tick進行 → 戦闘ログ取得 | 60秒tickの進行、サーバー権威（フロントに計算がない） | `test_battle_flow` | — |
| 4 | オフライン復帰 → 一括計算 | 経過時間ぶんのtickが一括処理され、上限でクランプされる | `test_battle_flow` | — |
| 5 | 装備ドロップ → 装備変更 → ステータス反映 | ドロップ〜装備〜ステータス計算の連結 | `test_equipment_flow` | `equipment.spec` |
| 6 | 常設ショップ購入 → 所持金・在庫の反映 | gold不足時のエラー、購入後の整合 | `test_shop_flow` | `shop.spec` |
| 7 | ゲスト → 正規ユーザー移行 | データ引き継ぎ | `test_auth_flow` | — |

Phase 3〜5 のシナリオは、該当Phaseの詳細設計完了時に本表へ追加する。

**移行 STEP で追加したエンドポイントは、実DBの行を検証する L1 テストを最低1本持つ**。Repository をモックした単体テストが分岐一覧を覆っていても、マッピングXML・一意制約・コミットはそこを通らない（ISSUE-707）。

### 3.1 意図的に扱わない経路

| 経路 | 理由 |
|------|------|
| `POST /api/auth/google` | 実装が未着手（`GOOGLE_CLIENT_ID` 有無にかかわらず 501）。L1・L2 とも対象外 |
| #3・#4・#7 の L2 | 検証内容がサーバー内部の計算・データ引き継ぎで、画面操作を挟んでも増える情報がない |

## 4. 固有の観点

| # | 観点 | 判定基準 |
|---|------|---------|
| 1 | サーバー権威 | 戦闘結果がバックエンドの返却値どおりか。フロントで再計算していないか |
| 2 | tick整合 | 60秒間隔の前提でオンライン（ポーリング）とオフライン（一括計算）の結果が一致するか |
| 3 | 画面遷移 | `screen_transition.md` の遷移がすべて実際に到達できるか。到達不能な画面がないか |
| 4 | エラー表示 | API エラー時にフロントが統一エラーボディ（`error.code`）を解釈して表示するか |
| 5 | 開発時フォールバック | バックエンド未起動時に `useBattleLocal.ts` で単体動作するか |
| 6 | camelCase | API の Request/Response が camelCase（Jackson）で往復しているか |
| 7 | データ永続 | 再起動・再ログイン後に進行状態が保持されるか |

## 5. 完了基準

一般スキルの完了基準に加え、以下を満たすこと。

- 対象Phaseの必須シナリオ（§3）が **L1・L2 とも全PASS**

## 6. 次工程

| 次にやること | 手段 |
|------------|------|
| Phase完了ゲート | `full-review` スキル（期待結果: 仕様との乖離ゼロ） |
| 乖離が出た場合 | `docs/backlog/known_issues.md` へ記録し、「仕様書を実装に合わせる」か「実装を修正する」かを判断する |
