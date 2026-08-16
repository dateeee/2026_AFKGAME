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

## 2026-08-16 15:51 | session cfb3c5e2 | 自動検出
- シグナル: same-command('python scripts/check_docs.py'×5, 'python scripts/check_doc_size.'×3)
- ターン概要: ツール111回・エラー0回・拒否0回。開始:「<ide_opened_file>The user opened the file c:\GIT\2026_AFKGAM」
- 原因と改善案: `check_doc_size --sections`×3 は対象ファイルが毎回異なる正当な計測（分割前・分割後・申し送り）だが、`check_docs.py`×5 は**編集の波ごとに走らせた**のが過剰。リンクを壊しうるのは「ファイル分割と参照の付け替え」「changelog・申し送りへの追記」の2箇所だけで、2回に畳めた。`fix-specs` SKILL §5「修正後の検証」が実行タイミングを定めていないのが原因なので、`.claude/project/review/docs.md` §5 へ「常設チェックは①分割・参照付け替えの直後 ②全修正完了後 の2回にまとめる（編集の波ごとに走らせない）」を1行足す。
- 補足: 直前エントリ（13:42）と**同じ `carryover_notes.md` の残量計測漏れを再発**させた（ファイル合計 5,379字だけ見て H2 を見ず、追記先 §2 が 1,998字＝上限まで2字だったため超過）。前回の是正案は `.claude/project/detail-design.md` §5 限定だったが、申し送りを書くのは詳細設計工程に限らない。**`profile.md` §7 ルール7 の本文へ「`carryover_notes.md`・`next_session.md` への追記も対象。合計ではなく追記先 H2 の残量を測る」を入れる**のが再発防止として正しい位置。
## 2026-08-16 14:49 | session 1bb2e400 | 自動検出
- シグナル: same-read(next_session.md×2) / same-command('python scripts/check_branch_li'×3, 'python scripts/check_docs.py'×3) / errors×3
- ターン概要: ツール121回・エラー3回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: シグナルの大半は誤検出（`next_session.md` の再読は別セッションが `d85ded9` で書き換えた後の再取得＝`profile.md` §6 規律4 の除外、`check_branch_list`／`check_docs` の複数回はベースライン → 是正後 → 統合後の退行確認、errors 3件のうち2件は引き継ぎの誤った JDK パス検出と Red の `RESULT FAIL` で正常）。**実際の手戻りは分岐一覧へ行を足す際の2件**: ①新設した分岐点4つを1行ずつで書き `check_branch_list.py` が「真偽の片側欠落」WARN 4件 → 行の追加と**テスト側マーカーの番号ずらし**をやり直した ②`tech_numeric.md` へ経験値計算式を再掲して `check_docs.py`「正の逸脱」違反 → リンク参照へ書き直した。どちらも**書く前に一度走らせれば分かる制約**なので、`.claude/project/test-list.md` §3（分岐一覧へ行を足す回のルール）へ「新設する分岐点は真偽の両側を1回で書く（片側だけだと WARN。マーカー付与後の是正は番号ずれを伴う）」「他ファイルが正の計算式・数値は再掲せずリンクする」の2行を足すのが最小の是正。

## 2026-08-16 16:30 | session 56969d38 | 手動
- シグナル: Maven の実行を1回やり直した（`-pl` 単独指定でコンパイルエラー） / 分岐一覧の追記を1回やり直した（WARN 1件）
- ターン概要: `/next` → キュー4「キャラ成長の製造」。開始:「<command-message>next</command-message>」
- 原因と改善案: ①**`.claude/project/commands/backend.md` §3 ルール1「`-pl` には必ず `-am` を付ける」を読まずに `mvn -pl afkgame-domain test` を叩き**、`~/.m2` の古い `afkgame-env` を解決して `LoggerName.BATTLE` 未解決のコンパイルエラー → 再実行。規約は既に正しく存在しており**適用漏れ**。`dev.md` §5 は「コマンドは commands.md が正」とリンクしているが、`dev` SKILL §0 の読み込み表に `commands/backend.md` が無いため、Red 確認で初めて Maven を叩く段になって参照が抜けた。**`.claude/project/dev.md` §0 相当（本書冒頭の読み込み指示）へ「TDD を回す前に `commands/backend.md` §3 を読む」を1行足す**のが最小の是正。②`tech_offline.md` §6 へ足した行の条件列を「1周回のEXP」と書いたところ、`check_branch_list.py` の `LAP_COUNT = \d+\s*周` がループと誤判定して「0周・2周 の行がない」WARN → 用語を「周回EXP」へ統一して解消。**分岐一覧の条件・期待列に「数字+周」を書くとループ判定に入る**のは `check_branch_list.py` の docstring §4 にあるが、書式ルール側（`.claude/project/detail-design.md` の分岐一覧の記法）に無い。同§へ「ループでない分岐点の条件列に『1周回』のような数字+周を書かない（`LAP_COUNT` がループ判定に入り 0周・2周 を要求する）」を1行足す。

## 2026-08-16 20:10 | session 66970fd1 | 自動検出
- シグナル: same-read(next_session.md×2)
- ターン概要: ツール125回・エラー2回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: `/next` 冒頭で読んだ `next_session.md` を worktree 統合後にそのまま Edit したため、並行セッション（`f30cdc7`）が消していた §2 行と食い違い、読み直しになった（`profile.md` §6 規律4「統合等でファイルが変わった場合」の例外には当たるが、順序で避けられる。1回目の Edit は古い読みのまま通っており、アンカー次第では他セッションの行を巻き込んでいた）。**`.claude/project/next.md` §4 へ「統合の直後・書き換える前に `next_session.md` を読み直す（並行セッションが §2 行を消していることがある）」を1行足す**のが最小の是正。

## 2026-08-16 20:37 | session ba8894b9 | 自動検出
- シグナル: same-read(next_session.md×2) / same-command('python scripts/check_branch_li'×3)
- ターン概要: ツール92回・エラー2回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: 誤検出: `next_session.md` の再読は worktree 統合で main が進んだ後の再取得（並行セッションの `87f9a42`・`fef8870` が §2 行を消していた）で、`profile.md` §6 規律4 の除外かつ 20:10 エントリが提案した「統合の直後・書き換える前に読み直す」順序どおり。`check_branch_list.py`×3 もベースライン → §5 分割直後の構造検証 → テスト追加後のマーカー照合で、判定内容が毎回違う（分割だけ先に検証したので、テストを書く投資の前に構造の誤りを潰せている）。
- 補足: **same-read(next_session.md×2) は 14:49・20:10 に続き3回目の誤検出**で、`/next` → 統合 → 引き継ぎ更新という**正しい手順を踏むと必ず出る**シグナルになっている。retro のたびに仕分ける手間を無くすため、`efficiency_check.py` の same-read 判定から `next_session.md` を除外する（または「統合コミットを挟んだ再読は数えない」）のが最小の是正。
