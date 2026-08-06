# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](development_process.md) §5、書式の正は [.claude/project/next.md](../.claude/project/next.md)。

最終更新: 2026-08-07 / 対応コミット: cfda8eb の次（DB設計 セグメント1のコミット）

## 1. 次回（コピペ用）

```
/basic-design DB設計の成果物化（セグメント2/3）: docs/tech/basic/tech_db/item.md を新設し、装備・アイテム系5テーブル（equipment / character_equip_slots / inventory_items / shop_daily_states / shop_daily_slots）の物理定義（列・型・NULL/既定・主キー/外部キー/一意制約・インデックスと検索パターン・導入Phase）を記載し、tech_db.md §1 の子ファイル索引へ登録する
完了条件: check_doc_size.py / check_docs.py が exit 0・DBスキーマ三者一致（定義書↔ER図↔models）の差分ゼロ・ER図との食い違いを定義書側へ揃えて解消・コミット
参照: docs/tech/basic/tech_db.md（命名規約・型マッピング・共通の列規約は親が正）、docs/tech/basic/tech_db/player.md（記述の型）、diagrams/er_diagram/item.md、backend/app/models/{equipment,item,shop}.py、docs/tech/detail/tech_shop.md §5
前提: セグメント1完了（tech_db.md + tech_db/{auth,player}.md を新設。認証3 + プレイヤー系4テーブル + 未実装4テーブルを収録し、三者一致は差分ゼロ。ER図の Party→PartyMember 改名・LearnedSkill.cooldown_remaining 追加も反映済み）。子ファイルは8,000字上限のため超過時は系統で分割する。Phase 3 製造①はセグメント3の完了後に着手する（差し戻しルール: 定義書に無いテーブル・列を実装しない）
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | DB設計 セグメント3/3（`tech_db/battle.md`: `battle_logs` + Phase 3〜5 の戦闘系未実装テーブル。`er_diagram/battle.md` との突合を含む） | `basic-design` |
| 2 | Phase 3 製造①（パーティ・スキル操作の基盤: models（LearnedSkill / ActiveSkillSlot / PartyMember）・master_data/skills.py・party_service・routers/party・routers/skill。test_party_service.py 17件・test_skill_progression.py 26件・test_encounter_exp.py のEXP3件） | `dev` |
| 3 | Phase 3 製造②（スキル戦闘処理: skill_service・environment_service。test_skill_service / test_skill_targeting / test_environment_service = 96件） | `dev` |
| 4 | Phase 3 製造③（offline_service の期待値計算。test_offline_simplified 15件。既存 process_pending_ticks のサンプリング方式を置換＝ISSUE-106） | `dev` |
| 5 | DBスキーマ三者一致チェックの常設化（`scripts/check_schema_triple.py`。セグメント1で使い捨てスクリプトを作成済み・DB変更のたびに再作成が要る）＋ 効率メモの反映 | `retro` |
