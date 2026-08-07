# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](development_process.md) §5、書式の正は [.claude/project/next.md](../.claude/project/next.md)。

最終更新: 2026-08-08 / 対応コミット: bc167b4 の次（DB設計の doc-review 実施のコミット）

## 1. 次回（コピペ用）

```
/fix-specs 基本設計の仕様確定ゲート（DB設計3セグメントの指摘反映）: doc-review 4件（ISSUE-1201〜1204）と diagrams-review 4件（ISSUE-601〜604）を1回の修正パスで仕様書・設計図へ反映する
完了条件: 8件の反映・python scripts/check_schema_triple.py が exit 0・check_doc_size.py / check_docs.py が exit 0・docs/changelog.md へ追記・コミット
参照: docs/reviews/doc-review/2026-08-08_003406.md（最新・ISSUE-1201〜1204）、docs/reviews/diagrams-review/2026-08-07_232135.md（ISSUE-601〜604 の修正案）
前提: 両レビュー済み・仕様書は無改稿（bc167b4）。**着手時にユーザー判断が1件ある** — ISSUE-1204（一意制約名 `uq_shop_daily_slot_index` の命名規約違反）は実装済みテーブルのため、案A（定義書を `uq_shop_daily_slots_state_slot` へ改名し models/shop.py 追従を known_issues.md へ登録。Alembic のテーブル再構築が必要）か案B（`battle_logs.timestamp` と同じ「規約の例外」注記 + check_schema_triple.py の例外リスト対応）かを先に選ぶ。ISSUE-601〜604 は修正案どおりに直せば check_schema_triple.py が exit 0 になることを複製ツリーで検証済み（目視照合は不要）。ISSUE-1201 は spec_ownership.md への正の登録も伴う。fix-specs は docs/reviews/doc-review/ 直下の最新1件を自動で拾うため、diagrams-review 側のパスは明示して渡す
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | Phase 3 製造①（パーティ・スキル操作の基盤: models（LearnedSkill / ActiveSkillSlot / PartyMember）・master_data/skills.py・party_service・routers/party・routers/skill。test_party_service.py 17件・test_skill_progression.py 26件・test_encounter_exp.py のEXP3件） | `dev` |
| 2 | Phase 3 製造②（スキル戦闘処理: skill_service・environment_service。test_skill_service / test_skill_targeting / test_environment_service = 96件） | `dev` |
| 3 | Phase 3 製造③（offline_service の期待値計算。test_offline_simplified 15件。既存 process_pending_ticks のサンプリング方式を置換＝ISSUE-106） | `dev` |
| 4 | `review_prep.py` の SHA基準差分の初回観測 — doc-review も `2026-08-08_003406.md` で `HEAD: bc167b4` を記録済み（その回自体はタイムスタンプ代替）。次回の doc-review / diagrams-review が SHA基準の初実行になるため、変更ファイル一覧が妥当かを確認する（残り3ディレクトリも次回が移行回） | 各レビュースキル実行時に確認 |
| 5 | `.claude/references/review-procedure.md` §5 へ使い捨てスクリプトの実行方法1行を追加（scratchpad へ Write + `PYTHONIOENCODING=utf-8 python <path>`。Bash heredoc の `\` 欠落と cp932 の UnicodeEncodeError が再発している。効率メモ 2026-08-08 00:40 のエントリ） | `retro` |
