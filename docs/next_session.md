# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](development_process.md) §5、書式の正は [.claude/project/next.md](../.claude/project/next.md)。

最終更新: 2026-08-07 / 対応コミット: 6e7dff3 の直後（Phase 3 テストリスト作成のコミット）

## 1. 次回（コピペ用）

```
/dev Phase 3 製造①（パーティ・スキル操作の基盤）: models（LearnedSkill / ActiveSkillSlot / PartyMember）・master_data/skills.py・party_service・routers/party・routers/skill を実装して該当テストを Green にする
完了条件: test_party_service.py（17件）・test_skill_progression.py（26件）・test_encounter_exp.py のEXP3件が Green・既存418件が Green のまま・コミット
参照: docs/tech/detail/tech_party.md §1〜§7、backend/tests/unit/test_skill_progression.py（テストが実装の表層を定義済み）
前提: Phase 3 テストリスト作成 完了（新規7モジュール161テストが ImportError で Red・check_branch_list.py --tests が exit 0・WARN 0件）。実装対象モジュール名はテストの docstring「本工程で定義する実装の表層」が正。tech_structure.md の services 一覧へ新規サービスの行を追加すること
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | Phase 3 製造②（スキル戦闘処理: skill_service・environment_service。test_skill_service / test_skill_targeting / test_environment_service = 96件） | `dev` |
| 2 | Phase 3 製造③（offline_service の期待値計算。test_offline_simplified 15件。既存 process_pending_ticks のサンプリング方式を置換＝ISSUE-106） | `dev` |
| 3 | 効率メモの反映（`check_doc_size.py --sections` の単一ファイル指定・`review_prep.py` の効果測定の記入） | `retro` |
| 4 | 逼迫2ファイルの圧縮（tech_structure.md 残3字 / tech_shop.md 残26字） | —（改稿時に随時） |
