# AFK GAME — 運用設計（環境・監視・データライフサイクル）

> [tech_spec.md](../tech_spec.md) §12。性能設計は [tech_performance.md](tech_performance.md)、秘密情報の扱いは [tech_security.md](tech_security.md) §11.8、ログ仕様は [tech_logging.md](../basic/tech_logging.md) を参照。
>
> **運用「要件」（バランス改定ポリシー・補填・告知・サポート窓口）は [operation_requirements.md](../../design/requirements/operation_requirements.md) が正**。本書はその実現方式（環境・設定・監視・ジョブ・手順）のみを扱う。
> デプロイ先は **AWS**（EC2 1台 + S3/CloudFront）。構成は §12.1 が正。

---

## 12.1 環境区分

| 環境 | 用途 | DB | ログ形式 | CORS |
|------|------|-----|---------|------|
| `local` | 開発・デバッグ | PostgreSQL（Docker Compose） | `text` | `http://localhost:5173` |
| `production` | 本番 | PostgreSQL（EC2 同居） | `json` | 本番フロントのオリジンのみ |

- DBMS は **`local`・`production` とも PostgreSQL** に統一する（dev/prod で DBMS を揃え、型・ロック挙動の差異を持ち込まない）。`local` の起動はリポジトリ同梱の Docker Compose 定義による

- ステージング環境は設けない（個人開発のため）。本番反映前の確認は `local` で行う
- 環境の識別は環境変数 `APP_ENV`（`local` / `production`）。**本番でのみ有効化される制約**（HTTPS必須・CORSワイルドカード禁止・`USE_API` 強制）はこの値で判定する

### 本番構成（AWS）

| 層 | 構成 |
|----|------|
| フロント（SPA） | S3（静的ホスティング）+ CloudFront。HTTPS は CloudFront が終端する |
| API | EC2 1台。Nginx をリバースプロキシに Spring Boot 実行可能 jar を systemd で常駐させる |
| DB | 同一 EC2 上に PostgreSQL を常駐させ、データディレクトリを EBS に置く（マネージドDBは使わない） |
| 定期ジョブ | 同一 EC2 の OS cron（§12.6） |
| バックアップ | EBS の日次スナップショットを取得する。方式・頻度・保持期間・保管先は **§12.5 が正** |

- フロントとバックエンドは**別オリジン**（CloudFront / EC2）になる。許可オリジンは §12.2 の `CORS_ORIGINS` が正
- マネージドコンテナ（App Runner・ECS Fargate）は採用しない。ファイルシステムが揮発し、DBのデータディレクトリと OS cron を同一ノードで継続できないため

## 12.2 環境変数一覧

設定値の参照は `@ConfigurationProperties` クラス（`afkgame-env`）に集約し、アプリケーションコードから環境変数を直接読まない。値は `application.yml` の既定値を環境変数で上書きする。

| 変数名 | 用途 | 既定値 | 本番必須 |
|--------|------|--------|---------|
| `APP_ENV` | 環境識別 | `local` | ○ |
| `DATABASE_URL` | DB接続文字列（JDBC） | `jdbc:postgresql://localhost:5432/afkgame` | ○ |
| `DATABASE_USER` | DB接続ユーザー | `afkgame` | ○ |
| `DATABASE_PASSWORD` | DB接続パスワード | なし（未設定なら起動失敗） | ○ |
| `JWT_SECRET` | JWT署名鍵 | なし（未設定なら起動失敗） | ○ |
| `CORS_ORIGINS` | 許可オリジン（カンマ区切り） | `http://localhost:5173` | ○ |
| `FRONTEND_BASE_URL` | メール内リンクの生成元 | `http://localhost:5173` | ○ |
| `LOG_LEVEL` | ログレベル | `INFO` | — |
| `LOG_FORMAT` | ログ形式（`text` / `json`） | `text` | ○ |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth | なし | Phase 2〜 ○ |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USER` / `SMTP_PASSWORD` | 確認メール・パスワードリセット送信 | なし | Phase 2〜 ○ |
| `BATTLE_RNG_SEED` | 戦闘乱数のシード固定（[tech_rng.md §2](../detail/tech_rng.md) の調査用。本番では未設定） | なし | — |

- **起動時バリデーション**: `APP_ENV=production` かつ必須変数が未設定・既定値のままの場合は、起動を中止して ERROR ログを出す（設定漏れのまま本番稼働することを防ぐ）
- 変数を追加したら `application.yml`（サンプル設定）と本表を同時に更新する

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
| 5xx 率 | 1% 超で調査 | Filter/Interceptor の `status_code` |
| `POST /api/battle/tick` の p95 | [non_functional_requirements.md](../../design/requirements/non_functional_requirements.md) §1 の目標超過で調査 | Filter/Interceptor の `duration_ms` |
| ERROR ログ件数 | 1件でも出たら内容を確認 | ロガー `afkgame.*` |
| DBサイズ（`pg_database_size`） | [tech_performance.md](tech_performance.md) §10.3 の試算を上回る増加傾向で調査 | 日次バッチ（§12.6）で記録 |

- アラート通知はベストエフォート（個人運用）。定期的にログを確認する運用とし、SLA は提示しない

## 12.4 DBマイグレーション運用

移行時に**満たすべき要件**（Phase進行時の既存データ引き継ぎ・マスタIDの再利用禁止・リセット禁止）は [operation_requirements.md](../../design/requirements/operation_requirements.md) §2 が正。本節はその適用手順を定める。

| 項目 | 仕様 |
|------|------|
| ツール | Flyway（`afkgame-initdb` モジュール配下のマイグレーションSQL。接続先は `afkgame-env` の DataSource 設定から取得する） |
| 粒度 | 1リリース = 1バージョン（`V<n>__説明.sql`）。ファイルには対応するPhase・変更概要を記す |
| 互換方針 | **前方互換**を守る。列追加は `NULL許容` または既定値を付与し、既存行の移行はマイグレーションSQL内で行う |
| 破壊的変更 | 列・テーブルの削除は **2段階リリース**（①アプリが参照しなくなる → ②次リリースで削除）。ダウングレードでデータを復元できないため |
| 適用手順 | ①バックアップ取得（§12.5）→ ②アプリ停止 → ③新バージョンの jar を起動（起動時に Flyway が自動適用。`flyway migrate` 相当）→ ④`GET /health` 確認 → ⑤アプリ再開 |
| ロールバック | Flyway に `downgrade` 相当の機能はないため、バックアップからの復元で対応する（破壊的変更を含むリビジョンの復元方針は変更なし） |
| DDLのロック | `ALTER TABLE ... DROP COLUMN` と列追加（既定値なし）は即時だが、**型変更はテーブル全体の書き換えと排他ロック**を伴う。破壊的変更の②削除リリースは停止時間を見積もって適用する |

**初期化**: 既存 Alembic の4リビジョンは Flyway の `V1` 初期スキーマへ統合する（移行前後で同一スキーマになることを確認する。[java_migration.md §5](../../backlog/java_migration.md)）。

**マスターデータの扱い**: マスターデータは YAML リソース（`afkgame-domain` の `resources/masterdata/`）であり、DBマイグレーションの対象外。差し替えは**再ビルドなしで反映できる**が、既存IDの**変更・削除は禁止**（プレイヤーの所持データが参照するため）。変更が必要な場合は新IDを追加し、旧IDは非表示にする。ローダは起動時にスキーマを検証し、不正なら起動を中止する。

## 12.5 バックアップ・リストア

| 項目 | 仕様 |
|------|------|
| 頻度 | 日次（`production` のみ）。§12.6 と同じ OS cron で実行する |
| 方式 | **論理バックアップ**（`pg_dump`）と、**EBS 日次スナップショット**（ボリューム全体）を併用する |
| 保持期間 | 14日（論理バックアップ・スナップショットとも） |
| 保管先 | アプリ稼働ノードとは別のストレージ（論理バックアップは S3、スナップショットは EBS の保管領域） |
| 検証 | 月1回、バックアップからのリストアを実施して復元可能性を確認する |
| RPO / RTO | [non_functional_requirements.md](../../design/requirements/non_functional_requirements.md) §3 に従う |

- 両方式を持つのは、稼働中ボリュームのスナップショットが書き込み途中の状態を含みうるのに対し、論理バックアップはDBの整合を保証できるため。**復旧は論理バックアップを第一手段**とし、ボリューム障害時にスナップショットから復元する

**復元時の注意**: 復元すると `lastTickAt` が過去に巻き戻る。次回のtick処理で「復元時点〜現在」が未処理tickとして再計算されるため、**報酬の二重付与にはならず、消失もしない**（[tech_tick.md](../detail/tech_tick.md) §1）。ただし巻き戻し幅が `MAX_OFFLINE_HOURS`（24時間）を超える場合、超過分は切り捨てられる。

## 12.6 定期ジョブとデータライフサイクル

| 対象 | 契機 | 内容 |
|------|------|------|
| ゲストアカウント | 日次バッチ | 最終アクセスから90日（`GUEST_ACCOUNT_EXPIRE_DAYS`）超過で関連データごと削除 |
| リフレッシュトークン | 日次バッチ | 期限切れ・revoked のものを物理削除 |
| メール確認 / パスワードリセットトークン | 日次バッチ | 期限切れ（24時間）を物理削除 |
| DBサイズ記録 | 日次バッチ | サイズをINFOログに出力（§12.3 の監視指標） |
| 戦闘ログ | tick処理内 | 100件超を古い順に削除（バッチ不要） |
| 日替わりショップ | 遅延評価 | `GET /api/shop/lineup` 取得時に 00:00 UTC を跨いだかを判定して再生成（[systems/economy.md](../../design/systems/economy.md)。バッチ不要） |

- 実行方式は本番 EC2 の OS cron（1日1回 03:00 UTC。§12.1）。実装は Phase 2 のテスト工程以降に行う
- ジョブは **べき等**とし、二重実行しても結果が変わらないよう実装する（削除対象を条件で特定する形にする）
- **ゲスト削除は事前告知できない**（連絡先を持たないため）。緩和策（本登録導線の常設・データ復旧不可の明示）は [non_functional_requirements.md](../../design/requirements/non_functional_requirements.md) §5 が正
- 退会（アカウント削除）要求も本節のジョブと同じ削除処理を用いる（**Phase 2** で実装。要件は [non_functional_requirements.md](../../design/requirements/non_functional_requirements.md) §5）

## 12.7 リリース時の技術チェックと障害対応

リリースの**フロー**（仕様反映 → Phase完了ゲート → 告知 → バックアップ・マイグレーション・デプロイ → スモークテスト）は [operation_requirements.md](../../design/requirements/operation_requirements.md) §4 が正。本節は各手順で実行する技術的な確認項目を補う。

| 手順 | 技術チェック |
|------|------------|
| ゲート通過前 | `python scripts/check_doc_size.py` が exit 0 |
| デプロイ直前 | `application.yml` / 環境変数の必須項目が揃っている（§12.2 の起動時バリデーションで検知） |
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

- 問い合わせ対応では `X-Request-ID`（[tech_logging.md](../basic/tech_logging.md)）を起点にログを追跡する。プレイヤーには画面上のエラー表示にリクエストIDを含めて伝える
