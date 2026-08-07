# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](development_process.md) §5、書式の正は [.claude/project/next.md](../.claude/project/next.md)。

最終更新: 2026-08-07 / 対応コミット: 5fcf165 の次（三者一致チェック常設化のコミット）

## 1. 次回（コピペ用）

```
/doc-review 基本設計の仕様確定ゲート（DB設計3セグメントの通し確認・後半）: tech_db.md + tech_db/{auth,player,item,battle}.md を起点に差分モードでレビューし、diagrams-review の未反映4件（ISSUE-601〜604）と合わせて /fix-specs で1回の修正パスに統合する
完了条件: doc-review レポート作成・ISSUE-601〜604 と doc-review 指摘の反映・python scripts/check_schema_triple.py が exit 0・check_doc_size.py / check_docs.py が exit 0・コミット
参照: docs/reviews/diagrams-review/2026-08-07_232135.md（未反映4件の修正案）、`python scripts/check_schema_triple.py` の出力（修正対象13件がファイル名・行番号つきで並ぶ）
前提: 三者一致チェックを常設化済み。現行 HEAD で13件を検出し、内訳は ISSUE-601（UKタグ不足・8エンティティ）+ ISSUE-602（InventoryItem.item_id の FK タグ）+ ISSUE-603（ER索引2件）+ ISSUE-604（Character.rarity の nullable 注記）+ 一意制約名 `uq_shop_daily_slot_index` の命名規約違反1件（レビューが doc-review へ持ち越した言及）。レビューの修正案どおりに直せば exit 0 になることは複製ツリーで検証済みなので、**修正後は同スクリプトで確認する**（目視照合は不要）。命名規約違反のみ実装済みテーブルのため、改名は models/shop.py と SQLite の制約名変更（テーブル再構築）を伴う点を判断材料にする。レビューと修正適用を分ける場合は /fix-specs を別セッションにする（profile.md §6 規律5）
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | Phase 3 製造①（パーティ・スキル操作の基盤: models（LearnedSkill / ActiveSkillSlot / PartyMember）・master_data/skills.py・party_service・routers/party・routers/skill。test_party_service.py 17件・test_skill_progression.py 26件・test_encounter_exp.py のEXP3件） | `dev` |
| 2 | Phase 3 製造②（スキル戦闘処理: skill_service・environment_service。test_skill_service / test_skill_targeting / test_environment_service = 96件） | `dev` |
| 3 | Phase 3 製造③（offline_service の期待値計算。test_offline_simplified 15件。既存 process_pending_ticks のサンプリング方式を置換＝ISSUE-106） | `dev` |
| 4 | 効率メモの見送り2件（`check_doc_size.py --sections` の単一ファイル指定 / `.claude/scripts/` の pytest 規約）＋ `review_prep.py` 効果観測（初回レビュー実行後）。**`scripts/` 常設スクリプトの検証方法も併せて判断する** — `check_schema_triple.py` は緑パス + 13種の変異テストで検証したが、`scripts/` にテストを置く規約が無いため使い捨て（スクラッチパッド）に留めた | `retro` |
