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
  反映する。**反映済み・対応不要のエントリは削除する**（open_specs と同じ運用。履歴は Git が持つ）。
  **削除は main で行う**（`merge=union` は削除を伝播せず、worktree 側で消した行は統合時に復活する）

## エントリ書式

    ## YYYY-MM-DD HH:MM | session xxxxxxxx | 自動検出 or 手動
    - シグナル: <検出内容 or 状況の一言>
    - ターン概要: ツールN回・エラーN回・拒否N回。開始:「<プロンプト冒頭60字>」
    - 原因と改善案: <原因 + どのスキル/プロファイル/成果物をどう直すか（1〜2行）>

---

## 2026-08-09 16:53 | session 8dabb13c | 自動検出
- シグナル: errors×4
- ターン概要: ツール104回・エラー4回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: 4件とも worktree 特有の制約で、**その回避策は `worktree_guide.md` §5.4 に既出**（複合コマンド・ループの拒否、`ctx_execute_file` がルート外で拒否）。`/next` から worktree へ入った後に §5.4 を読まず、main と同じ流儀で Bash の相対パス・`for` ループ・`ctx_execute_file` を使ったのが原因で、規約の不足ではない。
  → `worktree_guide.md` §5.2 の表（開始手順）へ「手順2の直後に §5.4 の注意を読む」を1行足し、`next` スキル §3 の worktree 移行手順からも §5.4 を指す（入った直後に必ず目に入る位置へ置く）。
