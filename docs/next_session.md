# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](development_process.md) §5、書式の正は [.claude/project/next.md](../.claude/project/next.md)。

最終更新: 2026-08-07 / 対応コミット: 057c71d の次（DB設計 セグメント2のコミット）

## 1. 次回（コピペ用）

```
/basic-design DB設計の成果物化（セグメント3/3・最終）: docs/tech/basic/tech_db/battle.md を新設し、戦闘系テーブル（battle_logs + Phase 3〜5 の未実装分）の物理定義（列・型・NULL/既定・主キー/外部キー/一意制約・インデックスと検索パターン・導入Phase）を記載して tech_db.md §1 の子ファイル索引へ登録する。あわせて diagrams/er_diagram/battle.md へ「DBスキーマの正は tech_db/battle.md」の宣言を追加する（セグメント1・2で auth/player/item へ入れた書式に揃える）
完了条件: check_doc_size.py / check_docs.py が exit 0・DBスキーマ三者一致（定義書↔ER図↔models）の差分ゼロ・ER図との食い違いを定義書側へ揃えて解消・コミット
参照: docs/tech/basic/tech_db.md（命名規約・型マッピング・共通の列規約は親が正）、docs/tech/basic/tech_db/item.md（記述の型）、diagrams/er_diagram/battle.md、backend/app/models/item.py（`BattleLog` はここに同居。`models/battle.py` は存在しない）
前提: セグメント2完了（tech_db/item.md を索引へ登録。装備・アイテム系5テーブル + facilities の三者一致は列名・並び順とも差分ゼロ。tech_shop.md §5 と er_diagram/item.md の「正の所在」誤り2件も是正済み）。JSON列（battle_logs.entries）の型表記は tech_db.md §3 に無いため、追加の要否を判断すること。本セグメント完了で DB設計は完了し Phase 3 製造①へ進める（差し戻しルール: 定義書に無いテーブル・列を実装しない）
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | Phase 3 製造①（パーティ・スキル操作の基盤: models（LearnedSkill / ActiveSkillSlot / PartyMember）・master_data/skills.py・party_service・routers/party・routers/skill。test_party_service.py 17件・test_skill_progression.py 26件・test_encounter_exp.py のEXP3件） | `dev` |
| 2 | Phase 3 製造②（スキル戦闘処理: skill_service・environment_service。test_skill_service / test_skill_targeting / test_environment_service = 96件） | `dev` |
| 3 | Phase 3 製造③（offline_service の期待値計算。test_offline_simplified 15件。既存 process_pending_ticks のサンプリング方式を置換＝ISSUE-106） | `dev` |
| 4 | DBスキーマ三者一致チェックの常設化（`scripts/check_schema_triple.py`。セグメント1・2とも使い捨てスクリプトを作成しており、DB変更のたびに再作成が要る） | `basic-design` |
| 5 | 効率メモの見送り2件（`check_doc_size.py --sections` の単一ファイル指定 / `.claude/scripts/` の pytest 規約）＋ `review_prep.py` 効果観測（初回レビュー実行後） | `retro` |
