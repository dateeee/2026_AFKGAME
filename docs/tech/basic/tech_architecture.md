# AFK GAME — アーキテクチャ方針

> [tech_spec.md](../tech_spec.md) §6〜§7。ログ設計は [tech_logging.md](tech_logging.md)、システム構成図は [system_architecture.md](../../diagrams/system_architecture.md)。
> 性能・容量設計は [tech_performance.md](../nonfunctional/tech_performance.md)、セキュリティは [tech_security.md](../nonfunctional/tech_security.md)、運用は [tech_operations.md](../nonfunctional/tech_operations.md)、tick進行制御は [tech_tick.md](../detail/tech_tick.md)。

```
[Vue.js SPA]  ←── REST API (polling) ──→  [FastAPI]  ←── ORM ──→  [DB]
   │                                          │
   ├─ 60秒ごとにポーリング                  ├─ tick処理（戦闘計算の権威）
   ├─ 戦闘ログのテキスト表示                    ├─ オフライン復帰時のまとめ計算
   ├─ UI状態管理（Pinia）                      ├─ データ永続化
   └─ オフラインキャッシュ（一時的）             └─ 不正防止（サーバー権威）
```

- **本番ではすべての戦闘計算はサーバー側（FastAPI）で実行**。チート対策のためフロントでは計算しない
- フロントは **ポーリングで結果を取得** → テキストログとして表示するだけ
- オフライン中はサーバーで何もせず、**復帰時に経過tick数分をまとめてシミュレーション** する

## ゲストアカウントによるデータ永続化方針

> **注**: Phase 1 の旧方式（UUID識別トークンを LocalStorage キー `guest_token` に保存）は、Phase 2 の認証実装により **JWT方式に置き換え済み**。以下は現行仕様。

初回アクセス時にサーバーがゲストアカウントを自動作成し、JWT（[tech_auth.md](../detail/tech_auth.md) 参照）で識別する。

| 項目 | 仕様 |
|------|------|
| 方式 | 初回アクセス時に `POST /api/auth/guest` でゲストアカウントを自動作成 |
| 識別 | JWT（アクセストークン30分 + リフレッシュトークン30日） |
| トークン保存先 | アクセストークン: メモリ保持 / リフレッシュトークン: LocalStorage（キー: `refresh_token`） |
| APIリクエスト | `Authorization: Bearer <access_token>` ヘッダーで識別 |
| サーバー側 | アカウントに紐づくプレイヤーデータをSQLiteに保存 |
| 本登録移行 | ゲスト→本登録フロー（[tech_auth.md](../detail/tech_auth.md) 参照）で既存データを引き継ぎ |
| データロスト | リフレッシュトークン消失時、ゲストのままではデータ復旧不可（本登録で回避可能） |

```
■ 初回アクセスフロー
  1. フロント: LocalStorageに refresh_token が存在するか確認
  2. なければ POST /api/auth/guest → JWT（accessToken/refreshToken）とユーザー情報を返却
  3. フロント: refreshToken を LocalStorage に保存、accessToken はメモリ保持
  4. 以降のAPIリクエストに Authorization ヘッダーを付与

■ 再訪問フロー
  1. フロント: LocalStorageから refresh_token を取得
  2. POST /api/auth/refresh → 新しいトークンペアを取得
  3. GET /api/game/state（Authorization ヘッダー付き）→ 既存データをロード
```

## エラーハンドリング・通信切断時の挙動

| 項目 | 仕様 |
|------|------|
| リトライ回数 | 最大3回 |
| リトライ間隔 | 指数バックオフ（1秒 → 2秒 → 4秒） |
| 3回失敗時 | 画面上部に「接続エラー」バナーを表示。次のtickタイミング（60秒後）で自動リトライ再開 |
| 切断中の表示 | 最後に取得したデータをそのまま表示（更新停止） |
| 復帰時 | サーバーから最新状態を取得して画面を更新（通常のtick処理と同じ） |
| ユーザー操作 | 切断中のAPI操作（装備変更等）は即座にエラー表示。復帰後に再操作が必要 |

```
■ 通信エラー時のフロー
  1. API呼び出し失敗
  2. 1秒後にリトライ（1回目）
  3. 2秒後にリトライ（2回目）
  4. 4秒後にリトライ（3回目）
  5. 3回失敗 → 「接続エラー」バナー表示、ポーリング継続（次tick=60秒後に再試行）
  6. 成功時 → バナー消去、最新状態を反映
```

## 同時実行制御・tickの冪等性

tick処理の排他・トランザクション境界・端数繰り越しは [tech_tick.md](../detail/tech_tick.md) を正とする。アーキテクチャ上の不変条件として、以下だけを本書で押さえる。

- **時刻の権威はサーバー（UTC）のみ**。クライアントが送る時刻・経過秒は一切採用しない
- **`lastTickAt` は単調増加**し、その更新は排他される。巻き戻す処理を実装してはならない（バックアップ復元は例外。[tech_operations.md](../nonfunctional/tech_operations.md) §12.5）
- **tickの冪等性は `lastTickAt` の排他更新のみに依存する**。通信リトライ（前節）で同じtickが再送されても、先行リクエストのコミット後は `pending_ticks = 0` となり二重付与は起きない
- 操作系API（装備変更・購入など）も同一プレイヤー行のロックを取得し、tick処理中の操作は待機後に更新済みの状態へ適用する

## アクセシビリティ対応方針

WCAG準拠レベルは明示的に定めず、ベストエフォートで以下を実装する。

| 項目 | 方針 |
|------|------|
| HTML | セマンティックHTML要素を使用（`<button>`, `<nav>`, `<main>`, `<h1>`〜`<h6>` 等） |
| キーボード操作 | Tab移動・Enter実行で全機能にアクセス可能にする |
| 色非依存 | 色だけに依存しない情報表示（テキストラベル・アイコンを併用） |
| フォーカス | フォーカスインジケータを視認可能に保つ（ブラウザデフォルトを削除しない） |

- テキストベースUIのため、スクリーンリーダーとの親和性は自然に高い
- 正式なWCAG準拠テスト・認証は行わない

## MVP開発方針
Phase 1 から **フロントエンド（Vue + Vite）とバックエンド（FastAPI + SQLite）を同時開発** する。

| 機能 | Phase 1（MVP） | 備考 |
|------|---------------|------|
| tick計算 | FastAPI `/api/battle/tick` | サーバー権威 |
| データ保存 | SQLite | サーバーDB |
| オフライン報酬 | サーバー側で計算 | 復帰時にまとめて処理 |
| フロント | Vue 3 SPA | ポーリングで結果取得・表示 |

## 開発時フォールバック構成
バックエンド未起動時のフロント単体テスト用として、ローカル計算モードも用意する。

- フロントの `api/client.ts` にフラグ（`USE_API: boolean`）を設け、`false` 時はローカル計算に切り替え
- ローカル計算のロジックは `composables/useBattleLocal.ts` に配置
- あくまで **開発・デバッグ用のフォールバック** であり、本番ではAPI連携を使用する

## 7. ゲームループ（ハイブリッドtick制）

```
■ 起動時
  1. API: GET /api/game/state → ゲーム状態ロード
  2. API: POST /api/battle/tick → 未処理tick（＝オフライン分）をまとめて計算
  3. tickレスポンスにオフライン分が含まれていれば、報酬サマリーモーダルを表示
  4. Piniaに最新状態を反映 → Vue描画

■ オンライン中（ポーリングループ）
  5. setInterval（60秒間隔）で繰り返し:
     a. API: POST /api/battle/tick → 前回からの未処理tickを計算
     b. レスポンスの戦闘ログ・ステータスをPiniaに反映
     c. Vue が自動再描画 → テキストログ表示更新
     d. 階クリア・レベルアップ等のイベント表示

■ 離脱時
  6. visibilitychange で検知（最終アクセス時刻はサーバー側の lastTickAt で管理）
```
