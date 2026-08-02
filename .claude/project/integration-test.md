# 結合テスト — プロジェクト固有プロファイル

> 一般手順は [.claude/skills/integration-test/SKILL.md](../skills/integration-test/SKILL.md) を参照。本書は AFK GAME 固有の値のみを持つ。
> 共通の値は [profile.md](profile.md)。

## 1. 成果物

| レイヤー | 内容 | 配置 | 状態 |
|---------|------|------|------|
| L1: API統合テスト | FastAPI TestClient + インメモリSQLite。APIシーケンスを検証 | `backend/tests/integration/` | **整備済み**（Phase 1〜2） |
| L2: E2Eテスト | Playwright。フロント＋バックを通しで起動し画面操作で検証 | `frontend/tests/e2e/` | **未整備**（Playwright 未導入） |

L2 着手時に Playwright を `frontend/` へ導入し、手順を本節へ追記する。

### 1.1 L1 の記述規約

| 項目 | 規約 |
|------|------|
| マーカー | `pytestmark = pytest.mark.integration` |
| 実行 | `cd backend && python -m pytest tests/integration -q --no-cov` |
| ファイル分割 | 導線ごとに1ファイル（`test_auth_flow` / `test_tower_flow` / `test_battle_flow` / `test_shop_flow` / `test_equipment_flow`） |
| プレイヤー生成 | フィクスチャで直接作らず **`POST /api/auth/guest` から始める** |
| DBセッション | `tests/integration/conftest.py` の `db` を使う（単体用の `expire_on_commit=False` はコミット後もリレーションが古いまま残り、本番と挙動が変わる） |
| 乱数 | `fixed_rng` フィクスチャで固定シードを与える |
| 時刻 | `rewind(player, 秒)` で `last_tick_at` を過去へ戻す。スリープしない |
| ドロップ | `always_drop` フィクスチャで抽選を成立させる（ドロップ率は検証対象外） |
| ログ経由の値 | `app_logs` フィクスチャ（`afkgame` ロガーは `propagate=False` のため caplog 単体では拾えない） |

## 2. シナリオの導出元

| 順 | 参照先 | 読む範囲 |
|----|--------|---------|
| 1 | `diagrams/screen_transition.md` | 対象Phaseの画面遷移のみ |
| 2 | `diagrams/api_sequence.md` → `api_sequence/` | 対象シーケンスの子ファイルのみ |
| 3 | `docs/tech/tech_api.md` | 対象エンドポイントの行のみ |
| 4 | `docs/design/systems/ui.md` | 対象画面のセクションのみ |

**単体テストの分岐一覧からシナリオを作らない**。結合テストが検証するのは**基本設計**（API設計・画面遷移・データ構造）であって、詳細設計の分岐ではない。

## 3. 必須シナリオ（Phase 1〜2）

| # | シナリオ | レイヤー | 検証内容 | L1 |
|---|---------|---------|---------|----|
| 1 | 認証 → ゲーム状態取得 | L1・L2 | トークン発行、初期状態の返却 | `test_auth_flow` |
| 2 | 塔選択 → 目標階設定 | L1・L2 | 塔別クリア記録の独立、上限追従 | `test_tower_flow` |
| 3 | tick進行 → 戦闘ログ取得 | L1 | 60秒tickの進行、サーバー権威（フロントに計算がない） | `test_battle_flow` |
| 4 | オフライン復帰 → 一括計算 | L1 | 経過時間ぶんのtickが一括処理され、上限でクランプされる | `test_battle_flow` |
| 5 | 装備ドロップ → 装備変更 → ステータス反映 | L1・L2 | ドロップ〜装備〜ステータス計算の連結 | `test_equipment_flow` |
| 6 | 常設ショップ購入 → 所持金・在庫の反映 | L1・L2 | gold不足時のエラー、購入後の整合 | `test_shop_flow` |
| 7 | ゲスト → 正規ユーザー移行 | L1 | データ引き継ぎ | `test_auth_flow` |

Phase 3〜5 のシナリオは、該当Phaseの詳細設計完了時に本表へ追加する。

### 3.1 意図的に L1 で扱わない経路

| 経路 | 理由 |
|------|------|
| `POST /api/auth/google` | 実装が未着手（`GOOGLE_CLIENT_ID` 有無にかかわらず 501） |
| `GET /api/health` | 仕様との乖離が未解決（[known_issues.md](../../docs/known_issues.md) #6）。パス・応答形式の確定後にテストを追加する |

## 4. 固有の観点

| # | 観点 | 判定基準 |
|---|------|---------|
| 1 | サーバー権威 | 戦闘結果がバックエンドの返却値どおりか。フロントで再計算していないか |
| 2 | tick整合 | 60秒間隔の前提でオンライン（ポーリング）とオフライン（一括計算）の結果が一致するか |
| 3 | 画面遷移 | `screen_transition.md` の遷移がすべて実際に到達できるか。到達不能な画面がないか |
| 4 | エラー表示 | API エラー時にフロントが統一エラーボディ（`error.code`）を解釈して表示するか |
| 5 | 開発時フォールバック | バックエンド未起動時に `useBattleLocal.ts` で単体動作するか |
| 6 | camelCase | API の Request/Response が camelCase（CamelModel）で往復しているか |
| 7 | データ永続 | 再起動・再ログイン後に進行状態が保持されるか |

## 5. 完了基準

一般スキルの完了基準に加え、以下を満たすこと。

- 対象Phaseの必須シナリオ（§3）が **L1・L2 とも全PASS**
- `full-review` で仕様との乖離ゼロ

## 6. 次工程

| 次にやること | 手段 |
|------------|------|
| Phase完了ゲート | `full-review` スキル |
| 乖離が出た場合 | `docs/known_issues.md` へ記録し、「仕様書を実装に合わせる」か「実装を修正する」かを判断する |
