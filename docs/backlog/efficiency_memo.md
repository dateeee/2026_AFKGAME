# 効率メモ（efficiency memo）

非効率だったやり取りの記録。反映したら消す作業用の台帳（[documentation_rules/directories.md](../process/documentation_rules/directories.md) §10）。
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

（エントリなし）

## 2026-08-16 13:42 | session d96e45a5 | 自動検出
- シグナル: same-command('python scripts/check_docs.py'×3)
- ターン概要: ツール101回・エラー2回・拒否0回。開始:「その作業は実施中だからほかの作業をして」
- 原因と改善案: 3回のうち2回は別 worktree（`p5-detail-design` / `p5-erdiagram-note`）での正当な検証。残り1回は手戻りで、**`carryover_notes.md` へ申し送りを追記する前に残量を測らなかった**ため 8,205字で超過し、圧縮 → 再検証をやり直した（新規作成した `tech_bossrush` 側は測ってから書いたので超過なし）。`profile.md` §7 ルール7「書く前に残量を測る」は成果物だけでなく**申し送り・引き継ぎへの追記にも適用する**ものなので、規約の新設ではなく適用漏れ。`.claude/project/detail-design.md` §5（完了基準）へ「carryover_notes.md へ追記する場合も `--sections` で残量を測ってから書く」を1行足すのが最小の是正。

## 2026-08-16 14:49 | session 1bb2e400 | 自動検出
- シグナル: same-read(next_session.md×2) / same-command('python scripts/check_branch_li'×3, 'python scripts/check_docs.py'×3) / errors×3
- ターン概要: ツール121回・エラー3回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: シグナルの大半は誤検出（`next_session.md` の再読は別セッションが `d85ded9` で書き換えた後の再取得＝`profile.md` §6 規律4 の除外、`check_branch_list`／`check_docs` の複数回はベースライン → 是正後 → 統合後の退行確認、errors 3件のうち2件は引き継ぎの誤った JDK パス検出と Red の `RESULT FAIL` で正常）。**実際の手戻りは分岐一覧へ行を足す際の2件**: ①新設した分岐点4つを1行ずつで書き `check_branch_list.py` が「真偽の片側欠落」WARN 4件 → 行の追加と**テスト側マーカーの番号ずらし**をやり直した ②`tech_numeric.md` へ経験値計算式を再掲して `check_docs.py`「正の逸脱」違反 → リンク参照へ書き直した。どちらも**書く前に一度走らせれば分かる制約**なので、`.claude/project/test-list.md` §3（分岐一覧へ行を足す回のルール）へ「新設する分岐点は真偽の両側を1回で書く（片側だけだと WARN。マーカー付与後の是正は番号ずれを伴う）」「他ファイルが正の計算式・数値は再掲せずリンクする」の2行を足すのが最小の是正。
