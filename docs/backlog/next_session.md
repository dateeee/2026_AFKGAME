# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)。

最終更新: 2026-08-08 / 対応コミット: cc251bf の次。STEP 2 を**セグメント 2-A / 2-B / 2-C へ分割**し、前提に書かれていた環境情報の誤り（JDK・Maven は未導入だった）を実測へ訂正したコミット。STEP 2 の実装は未着手。手順・進捗の正は [java_migration.md](java_migration.md)

## 1. 次回（コピペ用）

```
/dev 移行 STEP 2-A（骨格の土台）: Terasoluna MyBatis3 blank project 準拠で backend/ のマルチモジュール（afkgame-domain / web / env / initdb）を生成し、local 用 PostgreSQL の docker-compose.yml、afkgame-env の DataSource・application.yml（プロファイル別）、Flyway 初期スキーマ V1、GET /health（db:ok）までを実装する。STEP 2 の 2（例外ハンドラ・リクエストIDログ）と 3（Security/JWT）は 2-B のため実装しない
完了条件: mvn verify が成功（統合テストは zonky embedded-postgres）・GET /health が 200（db:ok）・java_migration.md §4 の STEP 2 を「着手中」へ更新し §2 へ埋め込みPostgreSQL方針を1行追記・known_issues.md #17 を対応済みへ移動・changelog へ1行追記・コミット
参照: docs/backlog/java_migration.md §2・§4（正）、docs/tech/basic/tech_structure.md §2・§4（構成と設定値）、docs/tech/basic/tech_db.md と tech_db/（auth・battle・item・player＝V1 の起こし元）、docs/tech/nonfunctional/tech_operations.md §12.1〜§12.3（環境変数・/health の契約）
前提: STEP 1 完了（cc251bf）。DBは PostgreSQL に統一済み。V1 は現行 Alembic の**5リビジョン**（§5 の「4リビジョン」は誤記）を畳んだ16テーブルで、未実装テーブル・列は含めない。一意制約名は定義書の命名規約へ統一し known_issues #17 を解消する。backend/ の Python 実装は残置（削除は STEP 6）。**環境（2026-08-08 実測）**: Temurin JDK 17.0.20 と Maven 3.9.11 を本セッションで導入済み（`mvn -v` で確認。JAVA_HOME・MAVEN_HOME・PATH をユーザー環境変数へ設定済み）。Maven Central 疎通あり。Docker は未検証のため compose は成果物として作るのみで、テストは zonky embedded-postgres（test スコープ）で回す。採用バージョン: spring-boot-starter-parent 3.5.16 / terasoluna-gfw-*-dependencies 5.10.1.RELEASE（5.11.0 は Boot 4 系のため不可）/ mybatis-spring-boot-starter 3.0.5
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | 移行 STEP 2-B（骨格の横断基盤: 統一エラーレスポンス・共通例外ハンドラ・`X-Request-ID` の MDC ログ + Spring Security によるゲスト認証/JWT）。完了判定は `POST /api/auth/guest` → `POST /api/auth/refresh` が通ること | `dev` |
| 2 | 移行 STEP 2-C（＝§4 の STEP 2 の 5。RNG・設定プロパティ・マスターデータの YAML ローダ基盤。起動時にスキーマ検証し不正なら起動失敗）。`docs/data/master/` の数値を YAML へ起こす | `dev` |
| 3 | 移行 STEP 3（Phase 1 スコープの移植: auth / game / battle / tower）。分岐一覧から JUnit テストを起こす Red → Green | `test-list` → `dev` |
| 4 | 移行 STEP 4（Phase 2 スコープの移植: equipment / shop・日替わり含む） | `test-list` → `dev` |
| 5 | 移行 STEP 5（Phase 3 製造①の実装済み分＝パーティ・スキル操作を移植）。続けて製造②（スキル戦闘処理: skill / environment）・製造③（オフライン期待値計算＝ISSUE-106）を Java で実装する。製造②では `SkillData` へダメージ倍率・対象・状態異常のフィールドを追加する | `dev` |

- 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。character.md §7.1 の3体目）を追加する。製造①では塔IDがどの仕様書にも宣言されておらず ID を発明しないため見送った（着手は優先2または5に合流させる）
- 移行 STEP 6（切替と後始末: Vite プロキシ・`.vscode/launch.json`・デプロイ手順・`backend/` の Python 削除・本ファイル群の整理）は上記すべての完了後。手順は [java_migration.md](java_migration.md) §4 が正
