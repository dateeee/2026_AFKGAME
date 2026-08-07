# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](development_process.md) §5、書式の正は [.claude/project/next.md](../.claude/project/next.md)。

最終更新: 2026-08-07 / 対応コミット: 2501156 の次（diagrams-review レポートのコミット）

## 1. 次回（コピペ用）

```
/doc-review 基本設計の仕様確定ゲート（DB設計3セグメントの通し確認・後半）: tech_db.md + tech_db/{auth,player,item,battle}.md を起点に差分モードでレビューし、diagrams-review の未反映4件（ISSUE-601〜604）と合わせて /fix-specs で1回の修正パスに統合する
完了条件: doc-review レポート作成・ISSUE-601〜604 と doc-review 指摘の反映・check_doc_size.py / check_docs.py が exit 0・コミット
参照: docs/reviews/diagrams-review/2026-08-07_232135.md（未反映4件と「プロセスへの還元」）、docs/tech/basic/tech_db.md（子ファイル索引）
前提: diagrams-review 完了・指摘4件（高0/中3/低1）は未反映。機械検証は全合格（三者一致20テーブルで列名・並び順とも差分0／Mermaid不備0／APIエンドポイント差分0）。指摘はいずれもER図の制約タグ層（UK欠落・item_idのFK誤記・索引のエンティティ配置・nullable注記）。doc-review へ持ち越した言及1件 = 一意制約名 `uq_shop_daily_slot_index` が tech_db.md §2 の命名規約違反（定義書とmodelsは一致・図とは無関係）。レビューと修正適用を分ける場合は /fix-specs を別セッションにする（profile.md §6 規律5）
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | Phase 3 製造①（パーティ・スキル操作の基盤: models（LearnedSkill / ActiveSkillSlot / PartyMember）・master_data/skills.py・party_service・routers/party・routers/skill。test_party_service.py 17件・test_skill_progression.py 26件・test_encounter_exp.py のEXP3件） | `dev` |
| 2 | Phase 3 製造②（スキル戦闘処理: skill_service・environment_service。test_skill_service / test_skill_targeting / test_environment_service = 96件） | `dev` |
| 3 | Phase 3 製造③（offline_service の期待値計算。test_offline_simplified 15件。既存 process_pending_ticks のサンプリング方式を置換＝ISSUE-106） | `dev` |
| 4 | DBスキーマ三者一致チェックの常設化（`scripts/check_schema_triple.py`）**← 効率メモ2件・レビュー1件が Phase 3 製造より前への前倒しを推奨（使い捨てスクリプトが4本目）**。未実装列の除外・ER図のエンティティ名→テーブル名の対応に加え、PK/FK/UK タグ・一意制約の構成列・FKなし宣言・nullable・制約の命名規約・ER索引の整合を照合項目に含める（仕様は 2026-08-07_232135.md「プロセスへの還元」） | `basic-design` |
| 5 | 効率メモの見送り2件（`check_doc_size.py --sections` の単一ファイル指定 / `.claude/scripts/` の pytest 規約）＋ `review_prep.py` 効果観測（初回レビュー実行後） | `retro` |
