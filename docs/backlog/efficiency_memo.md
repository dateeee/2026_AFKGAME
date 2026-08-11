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

## 2026-08-11 16:25 | session 4a974fcf | 自動検出
- シグナル: same-command('python scripts/check_doc_size.'×6, 'python scripts/check_docs.py'×3)
- ターン概要: ツール91回・エラー0回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: `check_doc_size.py` 6回のうち4回は `next_session.md` §2 が H2 2,000字を 23字超えた後の**目分量トリムの往復**（2023→2009→2012→2001→1981。1回は削ったつもりが増えた）。事前の残量測定4件と `check_docs.py` 3回（着手前の鮮度確認・worktree での修正後・main での引き継ぎ更新後）はゲートとして正当。改善: [doc-size.md](../../.claude/project/doc-size.md) §3.1 と [profile.md](../../.claude/project/profile.md) §7 ルール7 の「`len()` で実測」を**超過分の削減にも適用する**と明記する（必要削減字数を先に出し、候補文の `len()` 差分の合計がそれを上回る組み合わせを**1回の編集で**当てる。1箇所ずつ削って測り直さない）

## 2026-08-11 18:12 | session 6f7ef241 | 自動検出
- シグナル: same-command('python scripts/check_doc_size.'×5, 'python scripts/check_branch_li'×3)
- ターン概要: ツール99回・エラー1回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: `check_doc_size.py` 5回のうち3回は `next_session.md` §2 が H2 2,000字を超えた後の**目分量トリムの往復**（2,198→2,131→2,016→1,994）で、**16:25 のエントリと同一原因の再発**。前回の改善案（`len()` 実測を削減にも適用）は `doc-size.md` §3.1・`profile.md` §7 ルール7 に書いたが、`next`→工程スキルの経路ではどちらも読まないため届いていない。改善案: **[next.md](../../.claude/project/next.md) §4（引き継ぎ更新時のチェック）へ「書き換え前後の差分字数を `len()` で実測し、超過分の削減を同じ編集にまとめる」を1行追加する**（同§は既に `--sections` の事前実行を求めているので、その直後が置き場所）。`check_branch_list.py` 3回は worktree での追加後・マーカー重複解消後・統合後の確認で、うち統合後の1回は ff マージで内容が変わらないため省ける。

## 2026-08-11 20:01 | session e707fe00 | 自動検出（フックの追記が消えたため手で復元）
- シグナル: same-command('python scripts/check_branch_li'×5, 'python scripts/check_docs.py'×4)
- ターン概要: ツール147回・エラー1回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: `check_docs.py` 4回のうち1往復は**正の逸脱の踏み抜き**（`tech_numeric.md` に新設した §6 へ `0.1〜0.5` を再掲 → `--owner` で ERROR → 書き直して再実行）。書く前に [spec_ownership.md](../process/spec_ownership.md) の**検出パターン列**を引いていれば0往復だった。改善案: **[detail-design.md](../../.claude/project/detail-design.md) §4 の記載ルールへ「数値・範囲・選択肢を仕様書へ書く前に `spec_ownership.md` の検出パターン列を grep し、正でなければリンクに替える」を1行追加する**（`profile.md` §7 #4 は「正は1ファイル」までで、機械照合の正規表現が存在することに触れていない）。`check_branch_list.py` 5回（着手前の基準・battle/rng 追加後・§5 再構成後・numeric 追加後・統合後）は状態が変わるたびのゲートで正当だが、**統合後の1回は ff マージで内容が変わらないため省ける**（18:12 と同じ再発）。
- 付随して観測: **別セッションが main の作業ツリーを restore し、他セッションの未コミット（本エントリの自動追記）まで巻き込んだ**。`worktree_guide.md` §5.1 の「main のまま進めてよい作業」に沿わない編集が main に出ていたのが発端で、[worktree_guide.md](../process/worktree_guide.md) §2 ルール7 の「main の進行を確認する」を**復旧操作（`restore`・`checkout --`）の側にも**書くと再発を防げる。

