# AFK GAME — 運用設計（環境・監視・データライフサイクル）

> [tech_spec.md](tech_spec.md) §12。性能設計は [tech_performance.md](tech_performance.md)、秘密情報の扱いは [tech_security.md](tech_security.md) §11.8、ログ仕様は [tech_logging.md](tech_logging.md) を参照。
>
> **運用「要件」（バランス改定ポリシー・補填・告知・サポート窓口）は [operation_requirements.md](../design/operation_requirements.md) が正**。本書はその実現方式（環境・設定・監視・ジョブ・手順）のみを扱う。
> デプロイ先は未確定（[open_specs.md](../open_specs.md)）。本書はデプロイ先に依存しない運用方針を定める。

---

## 12.1 環境区分

| 環境 | 用途 | DB | ログ形式 | CORS |
|------|------|-----|---------|------|
| `local` | 開発・デバッグ | SQLite（ファイル） | `text` | `http://localhost:5173` |
| `production` | 本番 | SQLite → PostgreSQL（§12.4 の移行判断ライン） | `json` | 本番フロントのオリジンのみ |

- ステージング環境は設けない（個人開発のため）。本番反映前の確認は `local` で行う
- 環境の識別は環境変数 `APP_ENV`（`local` / `production`）。**本番でのみ有効化される制約**（HTTPS必須・CORSワイルドカード禁止・`USE_API` 強制）はこの値で判定する

## 12.2 環境変数一覧

設定値の参照は `config.py` に集約し、アプリケーションコードから `os.environ` を直接読まない。

| 変数名 | 用途 | 既定値 | 本番必須 |
|--------|------|--------|---------|
| `APP_ENV` | 環境識別 | `local` | ○ |
| `DATABASE_URL` | DB接続文字列 | `sqlite:///./afkgame.db` | ○ |
| `JWT_SECRET` | JWT署名鍵 | なし（未設定なら起動失敗） | ○ |
| `CORS_ORIGINS` | 許可オリジン（カンマ区切り） | `http://localhost:5173` | ○ |
| `FRONTEND_BASE_URL` | メール内リンクの生成元 | `http://localhost:5173` | ○ |
| `LOG_LEVEL` | ログレベル | `INFO` | — |
| `LOG_FORMAT` | ログ形式（`text` / `json`） | `text` | ○ |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth | なし | Phase 2〜 ○ |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USER` / `SMTP_PASSWORD` | 確認メール・パスワードリセット送信 | なし | Phase 2〜 ○ |

- **起動時バリデーション**: `APP_ENV=production` かつ必須変数が未設定・既定値のままの場合は、起動を中止して ERROR ログを出す（設定漏れのまま本番稼働することを防ぐ）
- 変数を追加したら `.env.example` と本表を同時に更新する

## 12.3 ヘルスチェック・監視

| 項目 | 仕様 |
|------|------|
| エンドポイント | `GET /health`（認証不要・レート制限対象外） |
| 正常時 | `200` / `{"status":"ok","version":"x.y.z","db":"ok"}` |
| 異常時 | `503` / `{"status":"degraded","db":"error"}`（DBへの `SELECT 1` が失敗した場合） |
| 用途 | デプロイ先のヘルスチェック、外部死活監視 |

監視項目（ログ集計ベース。専用の監視基盤は導入しない）:

| 指標 | しきい値の目安 | 取得元 |
|------|--------------|--------|
| 5xx 率 | 1% 超で調査 | ミドルウェアの `status_code` |
| `POST /api/battle/tick` の p95 | [non_functional_requirements.md](../design/non_functional_requirements.md) §1 の目標超過で調査 | ミドルウェアの `duration_ms` |
| ERROR ログ件数 | 1件でも出たら内容を確認 | ロガー `afkgame.*` |
| DBファイル/テーブルサイズ | 850MB 接近で PostgreSQL 移行を検討 | 日次バッチ（§12.6）で記録 |

- アラート通知はベストエフォート（個人運用）。定期的にログを確認する運用とし、SLA は提示しない

## 12.4 DBマイグレーション運用

移行時に**満たすべき要件**（Phase進行時の既存データ引き継ぎ・マスタIDの再利用禁止・リセット禁止）は [operation_requirements.md](../design/operation_requirements.md) §2 が正。本節はその適用手順を定める。

| 項目 | 仕様 |
|------|------|
| ツール | Alembic（**未セットアップ** → [open_specs.md](../open_specs.md)） |
| 粒度 | 1リリース = 1リビジョン。リビジョンには対応するPhase・変更概要をメッセージに記す |
| 互換方針 | **前方互換**を守る。列追加は `nullable` または `server_default` を付与し、既存行の移行はマイグレーション内で行う |
| 破壊的変更 | 列・テーブルの削除は **2段階リリース**（①アプリが参照しなくなる → ②次リリースで削除）。ダウングレードでデータを復元できないため |
| 適用手順 | ①バックアップ取得（§12.5）→ ②アプリ停止 → ③`alembic upgrade head` → ④`GET /health` 確認 → ⑤アプリ再開 |
| ロールバック | `alembic downgrade -1`。破壊的変更を含むリビジョンはダウングレード不可のため、バックアップからの復元で対応する |
| SQLite の制約 | 列削除・型変更は「新テーブル作成 → データ移行 → 差し替え」となる（Alembic の `batch_alter_table` を使用） |

**マスターデータの扱い**: `master_data/`（Python定数）はマイグレーション対象外。ただし**既存IDの変更・削除は禁止**（プレイヤーの所持データが参照するため）。変更が必要な場合は新IDを追加し、旧IDは非表示にする。

**PostgreSQL 移行判断**: DBサイズ 850MB 接近（≒1万人規模。[tech_performance.md](tech_performance.md) §10.3）、または書き込みロック競合が観測された時点で移行する。`DATABASE_URL` の差し替えで切り替わるよう、SQLite 固有機能に依存しない実装を維持する。

## 12.5 バックアップ・リストア

| 項目 | 仕様 |
|------|------|
| 頻度 | 日次フルバックアップ（`production` のみ） |
| 方式 | SQLite: `VACUUM INTO` でのファイルコピー（稼働中でも整合が取れる）／PostgreSQL: `pg_dump` |
| 保持期間 | 14日 |
| 保管先 | アプリ稼働ノードとは別のストレージ |
| 検証 | 月1回、バックアップからのリストアを実施して復元可能性を確認する |
| RPO / RTO | [non_functional_requirements.md](../design/non_functional_requirements.md) §3 に従う |

**復元時の注意**: 復元すると `lastTickAt` が過去に巻き戻る。次回のtick処理で「復元時点〜現在」が未処理tickとして再計算されるため、**報酬の二重付与にはならず、消失もしない**（[tech_tick.md](tech_tick.md) §1）。ただし巻き戻し幅が `MAX_OFFLINE_HOURS`（24時間）を超える場合、超過分は切り捨てられる。

## 12.6 定期ジョブとデータライフサイクル

| 対象 | 契機 | 内容 |
|------|------|------|
| ゲストアカウント | 日次バッチ | 最終アクセスから90日（`GUEST_ACCOUNT_EXPIRE_DAYS`）超過で関連データごと削除 |
| リフレッシュトークン | 日次バッチ | 期限切れ・revoked のものを物理削除 |
| メール確認 / パスワードリセットトークン | 日次バッチ | 期限切れ（24時間）を物理削除 |
| DBサイズ記録 | 日次バッチ | サイズをINFOログに出力（§12.3 の監視指標） |
| 戦闘ログ | tick処理内 | 100件超を古い順に削除（バッチ不要） |
| 日替わりショップ | 遅延評価 | `GET /api/shop/lineup` 取得時に 00:00 UTC を跨いだかを判定して再生成（[systems/economy.md](../design/systems/economy.md)。バッチ不要） |

- 実行方式は OS の cron（1日1回 03:00 UTC）。デプロイ先が未定のため、実装は Phase 2 のテスト工程以降に行う
- ジョブは **べき等**とし、二重実行しても結果が変わらないよう実装する（削除対象を条件で特定する形にする）
- **ゲスト削除は事前告知できない**（連絡先を持たないため）。緩和策（本登録導線の常設・データ復旧不可の明示）は [non_functional_requirements.md](../design/non_functional_requirements.md) §5 が正
- 退会（アカウント削除）要求への対応も本節のジョブと同じ削除処理を用いる（実装は未着手 → [open_specs.md](../open_specs.md)）

## 12.7 リリース時の技術チェックと障害対応

リリースの**フロー**（仕様反映 → Phase完了ゲート → 告知 → バックアップ・マイグレーション・デプロイ → スモークテスト）は [operation_requirements.md](../design/operation_requirements.md) §4 が正。本節は各手順で実行する技術的な確認項目を補う。

| 手順 | 技術チェック |
|------|------------|
| ゲート通過前 | `python scripts/check_doc_size.py` が exit 0 |
| デプロイ直前 | `.env` の必須変数が揃っている（§12.2 の起動時バリデーションで検知） |
| デプロイ直後 | `GET /health` が 200 かつ `db: ok` |
| スモークテスト | 起動 → tick → 報酬取得 の主要導線を通す |
| リリース後10分 | ERROR ログ件数・5xx 率・tick の p95 を確認（§12.3） |

**障害時の切り分け順**

| 症状 | 確認 | 一次対応 |
|------|------|---------|
| 全リクエストが失敗 | `GET /health` | アプリ再起動 → 復旧しなければロールバック |
| 特定APIのみ 500 | `X-Request-ID` でログを追跡 | 該当リビジョンのロールバック |
| 応答が遅い | `duration_ms` の p95 | [tech_performance.md](tech_performance.md) §10.4 の劣化対処を順に適用 |
| データ不整合 | 対象プレイヤーのtickログ | 該当プレイヤーのみバックアップから復元 |

- 問い合わせ対応では `X-Request-ID`（[tech_logging.md](tech_logging.md)）を起点にログを追跡する。プレイヤーには画面上のエラー表示にリクエストIDを含めて伝える
