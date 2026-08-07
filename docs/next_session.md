# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](development_process.md) §5、書式の正は [.claude/project/next.md](../.claude/project/next.md)。

最終更新: 2026-08-07 / 対応コミット: 37442e2 の次（DB設計 セグメント3のコミット）

## 1. 次回（コピペ用）

```
/diagrams-review 基本設計の設計整合ゲート（DB設計3セグメントの通し確認）: セグメント1〜3 で追加・改稿した tech_db.md + tech_db/{auth,player,item,battle}.md と diagrams/er_diagram/{player,item,battle}.md を差分モードでレビューし、続けて /doc-review を同一セッションで実行して両レポートの指摘を1回の修正パスに統合する
完了条件: diagrams-review・doc-review の指摘ゼロ（または反映済み）・check_doc_size.py / check_docs.py が exit 0・修正した場合はコミット
参照: docs/development_process.md §4（設計整合ゲート・仕様確定ゲートの判定手段）、docs/tech/basic/tech_db.md（子ファイル索引）、docs/reviews/diagrams-review/（前回 2026-08-03。以降の差分が対象）
前提: DB設計は全3セグメント完了。全20テーブルの三者一致（定義書↔ER図↔models）は列名・並び順とも差分ゼロを機械検証済み。ゲート通過後は候補キュー1の Phase 3 製造①へ進む。修正パスを分けたい場合はレビューと修正適用を別セッションにする（profile.md §6 規律5）
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | Phase 3 製造①（パーティ・スキル操作の基盤: models（LearnedSkill / ActiveSkillSlot / PartyMember）・master_data/skills.py・party_service・routers/party・routers/skill。test_party_service.py 17件・test_skill_progression.py 26件・test_encounter_exp.py のEXP3件） | `dev` |
| 2 | Phase 3 製造②（スキル戦闘処理: skill_service・environment_service。test_skill_service / test_skill_targeting / test_environment_service = 96件） | `dev` |
| 3 | Phase 3 製造③（offline_service の期待値計算。test_offline_simplified 15件。既存 process_pending_ticks のサンプリング方式を置換＝ISSUE-106） | `dev` |
| 4 | DBスキーマ三者一致チェックの常設化（`scripts/check_schema_triple.py`。セグメント1〜3とも使い捨てスクリプトを作成しており、DB変更のたびに再作成が要る。未実装列の除外・ER図のエンティティ名→テーブル名の対応まで実装済みの内容を移植する） | `basic-design` |
| 5 | 効率メモの見送り2件（`check_doc_size.py --sections` の単一ファイル指定 / `.claude/scripts/` の pytest 規約）＋ `review_prep.py` 効果観測（初回レビュー実行後） | `retro` |
