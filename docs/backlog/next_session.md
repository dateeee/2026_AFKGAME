# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)。

最終更新: 2026-08-08 / 対応コミット: b441d39 の次（Phase 3 製造①「パーティ・スキル操作の基盤」のコミット）。製造①は完了し、対象54件 + 全体472件が Green・C1 100%

## 1. 次回（コピペ用）

```
/dev known_issues §2 #17（shop_daily_slots の一意制約名を models へ追従）: models/shop.py の UniqueConstraint 名を uq_shop_daily_slot_index → uq_shop_daily_slots_state_slot へ変更し、SQLite は制約名を直接 RENAME できないためテーブル再構築を伴う Alembic マイグレーションを追加する
完了条件: check_schema_triple.py が exit 0・pytest 全体で回帰なし（test_shop_daily_service / test_shop_daily_router 含む）・known_issues §2 #17 の行を削除・コミット（docs 変更を伴うため changelog へ1行追記）
参照: backend/app/models/shop.py（ShopDailySlot の __table_args__）、docs/tech/basic/tech_db/item.md §5（定義書が正の制約名）、backend/alembic/versions/e5b71c9d4a02_phase3_party_and_skill_tables.py（直近のマイグレーション = down_revision の起点）
前提: Phase 3 製造①完了。check_schema_triple.py は**本件2件のみで exit 1**（製造①で party_members / learned_skills / active_skill_slots は三者一致検証の対象に入り全項目OK）。check_doc_size.py・check_docs.py は exit 0。**test_skill_service / test_skill_targeting / test_environment_service / test_offline_simplified の4モジュールは収集エラーのまま**（skill_service・environment_service・offline_service が未実装。製造②③の対象で、着手前からRed = 正常）
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | Phase 3 製造②（スキル戦闘処理: skill_service・environment_service。test_skill_service / test_skill_targeting / test_environment_service = 96件）。製造①の `master_data/skills.py` はダメージ倍率・対象・状態異常を持たないため、本セグメントで `SkillData` へ効果フィールドを追加する | `dev` |
| 2 | Phase 3 製造③（offline_service の期待値計算。test_offline_simplified 15件。既存 process_pending_ticks のサンプリング方式を置換＝ISSUE-106） | `dev` |
| 3 | `check_schema_triple.py` へ `--enum` 検証を追加（備考欄に列挙も「正は〜」リンクも持たない `VARCHAR` 列を検出。ISSUE-1203 の再発防止）。あわせて `.claude/project/basic-design.md` のDB設計チェックリストへ「新しい全プレイヤー横断クエリを足したら検索パターン表に行を足したか」を追加（ISSUE-1202 の型） | `basic-design` |
| 4 | `review_prep.py` の SHA基準差分の初回観測 — doc-review も `2026-08-08_003406.md` で `HEAD: bc167b4` を記録済み（その回自体はタイムスタンプ代替）。次回の doc-review / diagrams-review が SHA基準の初実行になるため、変更ファイル一覧が妥当かを確認する（残り3ディレクトリも次回が移行回） | 各レビュースキル実行時に確認 |
| 5 | 獣の塔（`docs/data/towers/003_獣の塔.md`）を `master_data/towers.py` へ追加する際、`master_data/characters.py` の `FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。character.md §7.1 の3体目）を追加する。製造①では塔IDがどの仕様書にも宣言されておらず ID を発明しないため見送った | `dev` |
