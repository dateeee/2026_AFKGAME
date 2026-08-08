# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)。

最終更新: 2026-08-08 / 対応コミット: 887e0c0 の次。**移行 STEP 2-B（骨格の横断基盤）を実装**したコミット。統一エラー応答・例外ハンドラ・リクエストIDログ（MDC）・CORS・Spring Security + JJWT（`POST /api/auth/{guest,refresh}`）が入り `mvn verify` が通る（55件 PASS・branch 100%）。手順・進捗の正は [java_migration.md](java_migration.md)

## 1. 次回（コピペ用）

```
/dev 移行 STEP 2-C（骨格の設定・データ基盤）: RNG（java.util.Random の注入）・ゲーム定数の @ConfigurationProperties バインド（application.yml の afkgame.* 9項目）・マスターデータの YAML ローダ基盤（afkgame-domain の src/main/resources/masterdata/ を record へ読み込み不変Mapで公開。起動時にスキーマ検証し不正なら起動失敗）を実装する。個々のマスターデータのYAML化と Phase 1 機能の移植は STEP 3 のため行わない（ローダの動作確認に足る最小の1ファイルのみ用意する）
完了条件: mvn verify が成功（JaCoCo branch 100%・既存55件を含め全PASS）・不正なYAMLで起動が失敗することを統合テストで確認・java_migration.md §4 の 2-C を完了へ更新（あわせて STEP 2 を完了へ）・changelog へ1行追記・コミット
参照: docs/tech/detail/tech_rng.md（乱数の実装と再現性の前提）、docs/tech/basic/tech_structure.md §4「設定値」（afkgame.* の一覧）、docs/tech/nonfunctional/tech_operations.md §12.2（環境変数と起動時バリデーション）、docs/data/master_data.md（マスターデータの索引）
前提: STEP 2-B 完了。`backend/` は afkgame-{env,initdb,domain,web}（Spring Boot 3.5.16 / Java 17）で `mvn verify` が BUILD SUCCESS。**環境**: `mvn`・`java` は PATH に無い。`JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"` と `"C:\Users\tubas\AppData\Local\Programs\apache-maven-3.9.11\bin\mvn"` のフルパスで実行する。統合テストのDBは zonky 埋め込み PostgreSQL（`@AutoConfigureEmbeddedDatabase(provider = ZONKY)`）。**@ConfigurationProperties クラスは `afkgame-env` の `com.afkgame.env.config`** に置く（domain・web の双方から参照するため。2-B の `AuthProperties`・`CorsProperties` と同じ場所。走査は `AfkgameApplication` の `@ConfigurationPropertiesScan`）。親POMに JaCoCo branch しきい値100%が入っているため、追加した分岐はすべてテストで通す必要がある
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | 移行 STEP 3-A（Phase 1 の auth 移植）。register / login / logout / link-account / verify-email / password-reset と、ゲスト作成時の Player・キャラクター・装備スロット・初期ポーション初期化。`SecurityConfig` の認証不要パスと `BCryptPasswordEncoder`(strength 12) も本セグメントで追加（持ち越しの正は java_migration.md §4 の 2-B 表） | `test-list` → `dev` |
| 2 | 移行 STEP 3-B（Phase 1 の game / battle / tower 移植）。分岐一覧から JUnit テストを起こす Red → Green | `test-list` → `dev` |
| 3 | 移行 STEP 4（Phase 2 スコープの移植: equipment / shop・日替わり含む） | `test-list` → `dev` |
| 4 | 移行 STEP 5（Phase 3 製造①の実装済み分＝パーティ・スキル操作を移植）。続けて製造②（スキル戦闘処理: skill / environment）・製造③（オフライン期待値計算＝ISSUE-106）を Java で実装する。製造②では `SkillData` へダメージ倍率・対象・状態異常のフィールドを追加する | `dev` |

- 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。character.md §7.1 の3体目）を追加する。製造①では塔IDがどの仕様書にも宣言されておらず ID を発明しないため見送った（着手は優先1または4に合流させる）
- 移行 STEP 6（切替と後始末: Vite プロキシ・`.vscode/launch.json`・デプロイ手順・`backend/` の Python 削除・本ファイル群の整理）は上記すべての完了後。手順は [java_migration.md](java_migration.md) §4 が正
- `docker-compose.yml` は成果物として作成済みだが **Docker 環境が未検証**のため未起動確認。`local` で実際に `docker compose up -d` → `mvn spring-boot:run` → `GET /health` を通すのは Docker が使えるタイミングで行う
