# 効率メモ（efficiency memo）

非効率だったやり取りの記録。反映したら消す作業用の台帳（[documentation_rules.md](../process/documentation_rules.md) §10）。
運用の正は [.claude/project/retro.md](../../.claude/project/retro.md)。
区分Cの文字数上限（8,000字）の対象。**超過したら `/retro` を回す合図**（溜め込まずに反映して消す）。

- **自動追記**: Stop フック `.claude/hooks/efficiency_check.py` が直近ターンの
  非効率シグナル（同一Readの再読・同一コマンドの連発・エラー多発・許可拒否・
  過大なツール呼び出し・手戻り発話）を検出すると仮エントリを追記し、
  Claude が「原因と改善案」を記入して完成させる
- **手動追記**: ユーザー・Claude が下の書式で直接追記してよい
  （「今のやり取りは非効率だったのでメモして」等）
- **反映**: `/retro` スキルがエントリを読み、スキル・プロファイル・成果物の改善へ
  反映する。**反映済み・対応不要のエントリは削除する**（open_specs と同じ運用。履歴は Git が持つ）

## エントリ書式

    ## YYYY-MM-DD HH:MM | session xxxxxxxx | 自動検出 or 手動
    - シグナル: <検出内容 or 状況の一言>
    - ターン概要: ツールN回・エラーN回・拒否N回。開始:「<プロンプト冒頭60字>」
    - 原因と改善案: <原因 + どのスキル/プロファイル/成果物をどう直すか（1〜2行）>

---

## 2026-08-09 09:42 | session 3543ebbc | 自動検出
- シグナル: long-turn(calls=71)
- ターン概要: ツール71回・エラー1回・拒否0回。開始:「<command-message>retro</command-message>」
- 原因と改善案: **`/retro` でメモ16件を消化し10ファイル改稿 + チェッカー改修 + テスト9件追加 + 統合まで完走した正当な分量**（long-turn は誤検出。errors×1 は測定スクリプトのラベル解析バグで、必要な数値は取得済み）。ただし**実在の欠陥が1件**: 効率メモの**エントリ削除を worktree 内で行ったため、`merge=union`（`.gitattributes`）が両側の行を残し、統合後に全エントリが復活した**。union は追加行の自動統合が目的で**削除を伝播しない**。→ ① [.claude/project/retro.md](../../.claude/project/retro.md) の「エントリの寿命」行へ「**削除は main で行う**（worktree 側の削除は `merge=union` で復活する）」を追記 ② [worktree_guide.md](../process/worktree_guide.md) §3 の「`merge=union` の注意」へ「**削除は伝播しない**。行を消す作業（効率メモの消化・changelog の重複畳み）は統合後に main で行う」を追記。同じ罠は `carryover_notes.md` の行削除にもかかる。
