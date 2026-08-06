# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](development_process.md) §5、書式の正は [.claude/project/next.md](../.claude/project/next.md)。

最終更新: 2026-08-06 / 対応コミット: 93278aa の直後（fix-specs 反映のコミット）

## 1. 次回（コピペ用）

```
/test-list Phase 3: 分岐一覧を失敗するテストへ展開する（tech_skill / tech_party / tech_offline）
完了条件: 対象分岐がすべてテストとして存在し Red を確認・check_branch_list.py --tests の対応照合・コミット
参照: docs/tech/detail/tech_skill.md §2〜§8（分岐一覧）、.claude/project/test-list.md（配置・記述規約）
前提: Phase 3 仕様確定ゲート完了（doc-review 6件を fix-specs で反映）。分岐一覧の WARN 16件（例外経路の片側欠落の注記・ループの0周/1周/2周行・tech_shop 旧形式5件の標準形式移行）は本工程で解消する
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | Phase 3 製造（テストリスト完了後。Red → Green → Refactor） | `dev` |
| 2 | 逼迫4ファイルの圧縮（profile.md 残5字・CLAUDE.md 残7字・tech_structure.md / tech_shop.md 残3字） | —（改稿時に随時） |
