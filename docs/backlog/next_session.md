# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)。

最終更新: 2026-08-08 / 対応コミット: cda76c1 の次。**Java 移行の未決事項2件を確定**（①DBは local・production とも PostgreSQL に統一 ②マスターデータは YAML リソースへ外出し）し、波及する仕様書・設計図を改訂して STEP 1 を完了にしたコミット。手順・進捗の正は [java_migration.md](java_migration.md)

## 1. 次回（コピペ用）

```
/dev 移行 STEP 2（骨格構築の 1〜4）: Terasoluna MyBatis3 blank project から backend/ のモジュール（afkgame-domain / web / env / initdb）を生成し、local 用 PostgreSQL の docker-compose.yml、統一エラーレスポンス・例外ハンドラ・リクエストIDログ、Spring Security によるゲスト認証（JWT）、Flyway 初期スキーマ V1 までを実装する
完了条件: mvn verify が成功（統合テストは埋め込みPostgreSQLで起動）・GET /health が 200（db:ok）・POST /api/auth/guest → POST /api/auth/refresh が通る・java_migration.md §4 の STEP 2 を「着手中」へ更新・changelog へ1行追記・コミット
参照: docs/backlog/java_migration.md §2・§4（正）、docs/tech/basic/tech_structure.md §2・§4（構成と設定値）、docs/tech/basic/tech_db.md（Flyway V1 の起こし元）、docs/tech/detail/tech_auth.md（認証仕様）
前提: STEP 1 完了。DBは PostgreSQL に統一済み（SQLite 記述は仕様書から削除済み）。V1 を定義書から起こす際、known_issues.md #17（ShopDailySlot の一意制約名 uq_shop_daily_slots_state_slot）を定義書どおりに採用して解消する。backend/ の Python 実装はまだ残っており、削除は STEP 6。**環境（確認済み）**: この端末に Docker は無い。docker-compose.yml は仕様どおり成果物として作るが、テストは zonky embedded-postgres（test スコープ）で回す方針をユーザーが選択済み（java_migration.md §2 へ1行追記する）。JDK は 17（Adoptium 17.0.7）と 25 が入っており、ビルドは 17 を使う。Maven 3.9.11・Maven Central 疎通あり
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | 移行 STEP 2 の 5（RNG・設定プロパティ・マスターデータの YAML ローダ基盤。起動時にスキーマ検証し不正なら起動失敗）。`docs/data/master/` の数値を YAML へ起こす | `dev` |
| 2 | 移行 STEP 3（Phase 1 スコープの移植: auth / game / battle / tower）。分岐一覧から JUnit テストを起こす Red → Green | `test-list` → `dev` |
| 3 | 移行 STEP 4（Phase 2 スコープの移植: equipment / shop・日替わり含む） | `test-list` → `dev` |
| 4 | 移行 STEP 5（Phase 3 製造①の実装済み分＝パーティ・スキル操作を移植）。続けて製造②（スキル戦闘処理: skill / environment）・製造③（オフライン期待値計算＝ISSUE-106）を Java で実装する。製造②では `SkillData` へダメージ倍率・対象・状態異常のフィールドを追加する | `dev` |
| 5 | 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。character.md §7.1 の3体目）を追加する。製造①では塔IDがどの仕様書にも宣言されておらず ID を発明しないため見送った | `dev` |

- 移行 STEP 6（切替と後始末: Vite プロキシ・`.vscode/launch.json`・デプロイ手順・`backend/` の Python 削除・本ファイル群の整理）は上記すべての完了後。手順は [java_migration.md](java_migration.md) §4 が正
