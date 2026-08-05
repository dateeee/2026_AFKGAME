# 次タスク開始 — プロジェクト固有プロファイル

> 一般手順は [.claude/skills/next/SKILL.md](../skills/next/SKILL.md) を参照。本書は AFK GAME 固有の値のみを持つ。
> 共通の値は [profile.md](profile.md)。

## 1. 引き継ぎファイル

| 項目 | 値 |
|------|-----|
| パス | `docs/next_session.md` |
| 構成 | §1「次回（コピペ用）」1件 + §2 候補キュー最大5行（**ポインタ専用**。Phase 進捗は転記しない） |
| 更新タイミング | タスク完了のコミット前（Stop フック `stop-commit.sh` がリマインドする） |
| 役割の宣言 | [docs/spec_ownership.md](../../docs/spec_ownership.md) §2 |

## 2. 開始プロンプトの定型（5要素）

```
/<工程スキル> <Phase・対象>: <作業内容を1行>
完了条件: <コミット・Red確認・C1 100% など>
参照: <ファイル1〜2点>（起点。追加はここから索引経由で）
前提: <直前の状態1行・コミットID>
```

設計の経緯と敵対的検証は [logs/session_start_prompt_design_2026-08-06.md](../../logs/session_start_prompt_design_2026-08-06.md)。

## 3. 使いどころの制限

- `/clear` 後の**新セッション冒頭専用**。セッション途中の別タスク開始には使わない（[profile.md](profile.md) §6 規律6: 工程の区切りで /clear）
- 委譲先の工程スキル ↔ プロファイル対応は [INDEX.md](INDEX.md) の対応表を使う
