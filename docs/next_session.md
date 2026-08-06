# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](development_process.md) §5、書式の正は [.claude/project/next.md](../.claude/project/next.md)。

最終更新: 2026-08-07 / 対応コミット: 6e7dff3 の直後（Phase 3 テストリスト作成のコミット）

## 1. 次回（コピペ用）

```
/basic-design DB設計の成果物化: docs/tech/basic/tech_db.md（索引）+ tech_db/{player,item,battle}.md を新設し、実装済み13テーブルと Phase 3〜5 の未実装テーブルの物理定義（物理テーブル名・列・型・NULL/既定・主キー/外部キー/一意制約・インデックス・導入Phase・命名規約）を記載する
完了条件: check_doc_size.py / check_docs.py が exit 0・.claude/project/basic-design.md §4「DBスキーマ三者一致」の差分ゼロ・tech_spec.md / README.md / CLAUDE.md の索引へ登録・ER図とテーブル名の食い違い（ER図 Party ↔ テストが要求する app.models.party.PartyMember）を解消・コミット
参照: diagrams/er_diagram/{player,item,battle}.md（現状の唯一のスキーマ記述）、backend/app/models/*.py（実装済み13テーブル）、docs/process/phases.md §3.2.1
前提: 工程整理 完了（phases.md §3.2.1 / development_process.md §4 / .claude/project/basic-design.md・review-diagrams.md / spec_ownership.md へ反映済み）。DBスキーマの正はテーブル定義書、ER図は視覚化。子ファイルが8,000字を超える場合は認証系を分離して4分割にする。Phase 3 製造①は本タスクの完了後に着手する（差し戻しルール: 定義書に無いテーブルを実装しない）
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | Phase 3 製造①（パーティ・スキル操作の基盤: models（LearnedSkill / ActiveSkillSlot / PartyMember）・master_data/skills.py・party_service・routers/party・routers/skill。test_party_service.py 17件・test_skill_progression.py 26件・test_encounter_exp.py のEXP3件） | `dev` |
| 2 | Phase 3 製造②（スキル戦闘処理: skill_service・environment_service。test_skill_service / test_skill_targeting / test_environment_service = 96件） | `dev` |
| 3 | Phase 3 製造③（offline_service の期待値計算。test_offline_simplified 15件。既存 process_pending_ticks のサンプリング方式を置換＝ISSUE-106） | `dev` |
| 4 | 効率メモの反映（`check_doc_size.py --sections` の単一ファイル指定・`review_prep.py` の効果測定の記入） | `retro` |
| 5 | 逼迫2ファイルの圧縮（tech_structure.md 残3字 / tech_shop.md 残26字） | —（改稿時に随時） |
