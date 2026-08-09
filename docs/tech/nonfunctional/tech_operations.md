# AFK GAME — 運用設計（環境・設定・監視）

> [tech_spec.md](../tech_spec.md) §12.1〜§12.3。マイグレーション・バックアップ・定期ジョブ・リリース手順は [tech_maintenance.md](tech_maintenance.md) §12.4〜§12.7。
> 性能設計は [tech_performance.md](tech_performance.md)、秘密情報の扱いは [tech_security.md](tech_security.md) §11.8、ログ仕様は [tech_logging.md](../basic/tech_logging.md) を参照。
>
> **運用「要件」（バランス改定ポリシー・補填・告知・サポート窓口）は [operation_requirements.md](../../design/requirements/operation_requirements.md) が正**。本書はその実現方式（環境・設定・監視）のみを扱う。
> デプロイ先は **AWS**（EC2 1台 + S3/CloudFront）。構成は §12.1 が正。

---

## 12.1 環境区分

| 環境 | 用途 | DB | ログ形式 | CORS |
|------|------|-----|---------|------|
| `local` | 開発・デバッグ | PostgreSQL（Docker Compose） | `text` | `http://localhost:5173` |
| `production` | 本番 | PostgreSQL（EC2 同居） | `json` | 本番フロントのオリジンのみ |

- DBMS は **`local`・`production` とも PostgreSQL** に統一する（dev/prod で DBMS を揃え、型・ロック挙動の差異を持ち込まない）。`local` の起動はリポジトリ同梱の Docker Compose 定義による
- ステージング環境は設けない（個人開発のため）。本番反映前の確認は `local` で行う
- 環境の識別は環境変数 `SPRING_PROFILES_ACTIVE`（`local` / `production`）。素の Spring がこれを `spring.profiles.active` として解決し `@Profile` の Bean 選択に直結するため、識別用の変数を別に持たない。**本番でのみ有効化される制約**（HTTPS必須・CORSワイルドカード禁止・`USE_API` 強制）はこの値で判定する。Maven プロファイル（雛形の `configs/<env>/resources`）による切替は使わない

### 本番構成（AWS）

| 層 | 構成 |
|----|------|
| フロント（SPA） | S3（静的ホスティング）+ CloudFront。HTTPS は CloudFront が終端する |
| API | EC2 1台。Nginx をリバースプロキシに **Tomcat 11.0（Servlet 6.1）を systemd で常駐**させ、`afkgame-web` の war を配備する（実行可能 jar は作らない） |
| DB | 同一 EC2 上に PostgreSQL を常駐させ、データディレクトリを EBS に置く（マネージドDBは使わない） |
| 定期ジョブ | 同一 EC2 の OS cron（`tech_maintenance.md` §12.6） |
| バックアップ | EBS の日次スナップショットを取得する。方式・頻度・保持期間・保管先は **`tech_maintenance.md` §12.5 が正** |

- フロントとバックエンドは**別オリジン**（CloudFront / EC2）になる。許可オリジンは §12.2 の `CORS_ORIGINS` が正
- マネージドコンテナ（App Runner・ECS Fargate）は採用しない。ファイルシステムが揮発し、DBのデータディレクトリと OS cron を同一ノードで継続できないため

### 配備とコンテキストパス

war は **ROOT コンテキスト**へ配備する（`webapps/ROOT.war`）。`/health`・`/api/**` を仕様どおりの絶対パスで受けるためで、`local` と `production` でパスが一致し、本番の Nginx にパス書き換えを持たせずに済む。`local` の起動手順は次のとおり（Windows は `catalina.bat`）。

```bash
cd backend && mvn clean install
cp afkgame-web/target/afkgame-web.war "$CATALINA_HOME/webapps/ROOT.war"
SPRING_PROFILES_ACTIVE=local "$CATALINA_HOME/bin/catalina.sh" run   # :8080
```

- 起動確認は `curl localhost:8080/health`（§12.3）。`SPRING_PROFILES_ACTIVE` が未設定なら §12.2 の起動時バリデーションで落ちる
- E2E は開発用 Tomcat と分けるため、専用の `CATALINA_BASE`（`frontend/tests/e2e/.tomcat`）を組み立てて :8100 で起動する（`frontend/tests/e2e/support/serve-backend.mjs`）

## 12.2 環境変数一覧

設定値の参照は `afkgame-env` の設定保持 Bean に集約し、アプリケーションコードから環境変数を直接読まない（`@ConfigurationProperties` は Boot 機能のため使わない。受け取り方は [tech_backend.md](../basic/tech_backend.md) §4.2）。値は `META-INF/spring/*.properties` の既定値を環境変数で上書きする。

| 変数名 | 用途 | 既定値 | 本番必須 |
|--------|------|--------|---------|
| `SPRING_PROFILES_ACTIVE` | 環境識別 | なし（未設定なら起動失敗） | ○ |
| `DATABASE_URL` | DB接続文字列（JDBC） | `jdbc:postgresql://localhost:5432/afkgame` | ○ |
| `DATABASE_USER` | DB接続ユーザー | `afkgame` | ○ |
| `DATABASE_PASSWORD` | DB接続パスワード | なし（未設定なら起動失敗） | ○ |
| `JWT_SECRET` | JWT署名鍵 | なし（未設定なら起動失敗） | ○ |
| `CORS_ORIGINS` | 許可オリジン（カンマ区切り） | `http://localhost:5173` | ○ |
| `FRONTEND_BASE_URL` | メール内リンクの生成元 | `http://localhost:5173` | ○ |
| `LOG_LEVEL` | ログレベル | `INFO` | — |
| `LOG_FORMAT` | ログ形式（`text` / `json`） | `text` | ○ |
| `LOG_DIR` | ログ3種別（通信 / アプリケーション / エラー）の出力先ディレクトリ | `${catalina.base:-.}/logs` | — |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth | なし | Phase 2〜 ○ |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USER` / `SMTP_PASSWORD` | 確認メール・パスワードリセット送信 | なし | Phase 2〜 ○ |
| `BATTLE_RNG_SEED` | 戦闘乱数のシード固定（[tech_rng.md §2](../detail/tech_rng.md) の調査用。本番では未設定） | なし | — |

- **上書きの仕組み**: 素の Spring は `database.url` の解決時に `DATABASE_URL` を探す（`.` → `_` と大文字化のみ）。**プロパティキーはドット区切りのみで組む** — ハイフンは変換されず、上書きが黙って効かなくなる
- **起動時バリデーション**: 起動時に、アクティブなプロファイルが `local` / `production` のいずれかであること、および `production` で必須変数が未設定・既定値のままでないことを検査し、満たさなければ起動を中止して ERROR ログを出す（設定漏れのまま本番稼働することを防ぐ）
- `SPRING_PROFILES_ACTIVE` 自体にも既定値を置かない。既定値があると設定漏れが `local` へのフォールバックとして黙って成立し、開発用の既定署名鍵で本番が起動してしまうため（素の Spring は未設定なら default プロファイルで起動するので、上記の検査で落とす）
- 変数を追加したら `META-INF/spring/*.properties`（サンプル設定）と本表を同時に更新する

## 12.3 ヘルスチェック・監視

| 項目 | 仕様 |
|------|------|
| エンドポイント | `GET /health`（認証不要・レート制限対象外） |
| 正常時 | `200` / `{"status":"ok","version":"x.y.z","db":"ok"}` |
| 異常時 | `503` / `{"status":"degraded","db":"error"}`（DBへの `SELECT 1` が失敗した場合） |
| 用途 | デプロイ先のヘルスチェック、外部死活監視 |

- `version` の取得方法（Maven のリソースフィルタ）は `tech_backend.md` §4.1 が正

監視項目（ログ集計ベース。専用の監視基盤は導入しない）:

| 指標 | しきい値の目安 | 取得元 |
|------|--------------|--------|
| 5xx 率 | 1% 超で調査 | Filter/Interceptor の `status_code` |
| `POST /api/battle/tick` の p95 | [non_functional_requirements.md](../../design/requirements/non_functional_requirements.md) §1 の目標超過で調査 | Filter/Interceptor の `duration_ms` |
| ERROR ログ件数 | 1件でも出たら内容を確認 | ロガー `afkgame.*` |
| DBサイズ（`pg_database_size`） | `tech_performance.md` §10.3 の試算を上回る増加傾向で調査 | 日次バッチ（`tech_maintenance.md` §12.6）で記録 |

- アラート通知はベストエフォート（個人運用）。定期的にログを確認する運用とし、SLA は提示しない
