# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)。

最終更新: 2026-08-08 / 対応コミット: ef9e0e1 の次（`docs/` 直下の未分類9件を `process/`・`backlog/` へ分類し、要件3件を `design/requirements/` へ移設したコミット）。基本設計の仕様確定ゲートは通過し、指摘残ゼロ。§1 のタスクは前回から未着手のまま

## 1. 次回（コピペ用）

```
/dev Phase 3 製造①（パーティ・スキル操作の基盤）: models（LearnedSkill / ActiveSkillSlot / PartyMember）・master_data/skills.py・party_service・routers/party・routers/skill を、用意済みの失敗するテストが Green になるまで実装する
完了条件: test_party_service.py 17件・test_skill_progression.py 26件・test_encounter_exp.py のEXP3件が Green・pytest 全体で回帰なし・コミット（コード変更のみのため changelog 追記は不要）
参照: docs/tech/basic/tech_db/player.md（learned_skills / active_skill_slots / party_members のテーブル定義）、docs/tech/detail/tech_skill.md（スキル処理の詳細設計）
前提: 仕様書・設計図は fix-specs 反映済みで指摘残ゼロ。check_doc_size.py / check_docs.py は exit 0。**check_schema_triple.py は exit 1（2件）** — `shop_daily_slots` の一意制約名を定義書だけ `uq_shop_daily_slots_state_slot` へ改名し models/shop.py が未追従（known_issues §2 #17・候補キュー1で解消予定）。今回の実装対象テーブルとは無関係なので、この2件だけが残っている状態を正常として進める
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | `shop_daily_slots` の一意制約名を models へ追従（known_issues §2 #17）。`models/shop.py` の `UniqueConstraint` 名を `uq_shop_daily_slots_state_slot` へ変更し、SQLite の制約名変更（テーブル再構築）を伴う Alembic マイグレーションを追加する。完了で `check_schema_triple.py` が exit 0 へ戻る | `dev` |
| 2 | Phase 3 製造②（スキル戦闘処理: skill_service・environment_service。test_skill_service / test_skill_targeting / test_environment_service = 96件） | `dev` |
| 3 | Phase 3 製造③（offline_service の期待値計算。test_offline_simplified 15件。既存 process_pending_ticks のサンプリング方式を置換＝ISSUE-106） | `dev` |
| 4 | `check_schema_triple.py` へ `--enum` 検証を追加（備考欄に列挙も「正は〜」リンクも持たない `VARCHAR` 列を検出。ISSUE-1203 の再発防止）。あわせて `.claude/project/basic-design.md` のDB設計チェックリストへ「新しい全プレイヤー横断クエリを足したら検索パターン表に行を足したか」を追加（ISSUE-1202 の型） | `basic-design` |
| 5 | `review_prep.py` の SHA基準差分の初回観測 — doc-review も `2026-08-08_003406.md` で `HEAD: bc167b4` を記録済み（その回自体はタイムスタンプ代替）。次回の doc-review / diagrams-review が SHA基準の初実行になるため、変更ファイル一覧が妥当かを確認する（残り3ディレクトリも次回が移行回） | 各レビュースキル実行時に確認 |
