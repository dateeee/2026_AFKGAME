# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)。

最終更新: 2026-08-08 / 対応コミット: 1433cc2 の次。**バックエンドを Java/Terasoluna(MyBatis3) へ全面移行することを決定**し、移行 STEP 1（ドキュメント改訂）を実施したコミット。手順・進捗の正は [java_migration.md](java_migration.md)

## 1. 次回（コピペ用）

```
java_migration.md §6 の未決事項2件をユーザーと確定し、STEP 2（Java 骨格構築）へ着手する
確定するもの: ①ローカル開発DBを SQLite のまま維持するか PostgreSQL(Docker) へ寄せるか ②マスターデータを Java 定数で持つか YAML リソースへ外出しするか
確定後: java_migration.md §2 の技術選定表へ反映し §6 の行を削除 → STEP 2 の 1〜5（blank project 生成・例外/ログ基盤・Spring Security 認証・Flyway 初期スキーマ・RNG/設定/マスター基盤）を順に実施
完了条件: GET /health が 200（db:ok）・ゲスト認証（POST /api/auth/guest → POST /api/auth/refresh）が通る・mvn verify が成功・java_migration.md §4 の STEP 2 を「完了」へ更新・changelog へ1行追記
参照: docs/backlog/java_migration.md（正）、docs/tech/basic/tech_structure.md §2・§4（構成と設定値）、docs/tech/basic/tech_db.md（Flyway V1 の起こし元）、docs/tech/detail/tech_auth.md（認証仕様）
前提: STEP 1 完了（README・CLAUDE.md・docs/tech・docs/diagrams・docs/process・.claude/project を Java 前提へ改訂済み）。backend/ の Python 実装はまだ残っており、削除は STEP 6。Phase 3 製造②③は未着手のまま STEP 5 以降へ繰り延べ
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | 移行 STEP 3（Phase 1 スコープの移植: auth / game / battle / tower）。分岐一覧から JUnit テストを起こす Red → Green | `test-list` → `dev` |
| 2 | 移行 STEP 4（Phase 2 スコープの移植: equipment / shop・日替わり含む） | `test-list` → `dev` |
| 3 | 移行 STEP 5（Phase 3 製造①の実装済み分＝パーティ・スキル操作を移植）。続けて製造②（スキル戦闘処理: skill / environment）・製造③（オフライン期待値計算＝ISSUE-106）を Java で実装する。製造②では `SkillData` へダメージ倍率・対象・状態異常のフィールドを追加する | `dev` |
| 4 | 移行 STEP 6（切替と後始末: Vite プロキシ・`.vscode/launch.json`・デプロイ手順・`backend/` の Python 削除・本ファイル群の整理） | — |
| 5 | 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。character.md §7.1 の3体目）を追加する。製造①では塔IDがどの仕様書にも宣言されておらず ID を発明しないため見送った | `dev` |
