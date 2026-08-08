# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)。

最終更新: 2026-08-08 / 対応コミット: 4694917 の次。**移行 STEP 2-A（骨格の土台）を実装**したコミット。`backend/` に Maven 4モジュール・Flyway `V1`（16テーブル）・`GET /health` が入り `mvn verify` が通る。手順・進捗の正は [java_migration.md](java_migration.md)

## 1. 次回（コピペ用）

```
/dev 移行 STEP 2-B（骨格の横断基盤）: 統一エラーレスポンス・共通例外ハンドラ（@RestControllerAdvice）・X-Request-ID の MDC ログ（logback-spring.xml）・CORS 設定と、Spring Security によるゲスト認証/JWT（JJWT）を実装する。マスターデータの YAML ローダ・RNG・@ConfigurationProperties バインドは 2-C のため実装しない
完了条件: mvn verify が成功（JaCoCo branch 100%・既存7件を含め全PASS）・POST /api/auth/guest → POST /api/auth/refresh が統合テストで通る・エラー応答が tech_logging.md のエラーコード体系と一致・java_migration.md §4 の 2-B を完了へ更新・changelog へ1行追記・コミット
参照: docs/tech/detail/tech_auth.md（認証方式・トークン期限の正）、docs/tech/basic/tech_logging.md（エラーコード体系・X-Request-ID）、docs/tech/basic/tech_api_common.md（共通エラー応答の契約）、docs/tech/nonfunctional/tech_security.md §11（入力検証・CORS・bcrypt strength）
前提: STEP 2-A 完了。`backend/` に afkgame-{env,initdb,domain,web}（Spring Boot 3.5.16 / Java 17）と Flyway `V1`（16テーブル）・`GET /health` があり `mvn verify` が BUILD SUCCESS。**環境**: `mvn`・`java` は PATH に無い。`JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"` と `"C:\Users\tubas\AppData\Local\Programs\apache-maven-3.9.11\bin\mvn"` のフルパスで実行する。Docker は未検証のため統合テストのDBは zonky 埋め込み PostgreSQL（`@AutoConfigureEmbeddedDatabase(provider = ZONKY)`）を使う。**Terasoluna の BOM は import しない**（Spring Boot 3.5 の管理版を上書きするため、`terasoluna-gfw-*` は親POMの `terasoluna.version`=5.10.1.RELEASE で個別に版指定）。親POMに JaCoCo branch しきい値100%が入っているため、追加した分岐はすべてテストで通す必要がある
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | 移行 STEP 2-C（＝§4 の 2-C。RNG・設定プロパティ `@ConfigurationProperties`・マスターデータの YAML ローダ基盤。起動時にスキーマ検証し不正なら起動失敗）。`docs/data/master/` の数値を YAML へ起こす | `dev` |
| 2 | 移行 STEP 3（Phase 1 スコープの移植: auth / game / battle / tower）。分岐一覧から JUnit テストを起こす Red → Green | `test-list` → `dev` |
| 3 | 移行 STEP 4（Phase 2 スコープの移植: equipment / shop・日替わり含む） | `test-list` → `dev` |
| 4 | 移行 STEP 5（Phase 3 製造①の実装済み分＝パーティ・スキル操作を移植）。続けて製造②（スキル戦闘処理: skill / environment）・製造③（オフライン期待値計算＝ISSUE-106）を Java で実装する。製造②では `SkillData` へダメージ倍率・対象・状態異常のフィールドを追加する | `dev` |

- 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。character.md §7.1 の3体目）を追加する。製造①では塔IDがどの仕様書にも宣言されておらず ID を発明しないため見送った（着手は優先1または4に合流させる）
- 移行 STEP 6（切替と後始末: Vite プロキシ・`.vscode/launch.json`・デプロイ手順・`backend/` の Python 削除・本ファイル群の整理）は上記すべての完了後。手順は [java_migration.md](java_migration.md) §4 が正
- `docker-compose.yml` は成果物として作成済みだが **Docker 環境が未検証**のため未起動確認。`local` で実際に `docker compose up -d` → `mvn spring-boot:run` → `GET /health` を通すのは Docker が使えるタイミングで行う
