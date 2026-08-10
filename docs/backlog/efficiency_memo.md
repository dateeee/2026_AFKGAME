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

## 2026-08-09 16:53 | session 8dabb13c | 自動検出
- シグナル: errors×4
- ターン概要: ツール104回・エラー4回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: 4件とも worktree 特有の制約で、**その回避策は `worktree_guide.md` §5.4 に既出**（複合コマンド・ループの拒否、`ctx_execute_file` がルート外で拒否）。`/next` から worktree へ入った後に §5.4 を読まず、main と同じ流儀で Bash の相対パス・`for` ループ・`ctx_execute_file` を使ったのが原因で、規約の不足ではない。
  → `worktree_guide.md` §5.2 の表（開始手順）へ「手順2の直後に §5.4 の注意を読む」を1行足し、`next` スキル §3 の worktree 移行手順からも §5.4 を指す（入った直後に必ず目に入る位置へ置く）。

## 2026-08-09 21:24 | session 5d30ecd6 | 自動検出
- シグナル: same-command('git status --short --untracked'×6, 'python scripts/worktree.py mer'×4) / errors×5
- ターン概要: ツール81回・エラー5回・拒否0回。開始:「<ide_opened_file>The user opened the file c:\GIT\2026_AFKGAM」
- 原因と改善案: main の working tree で**別セッションがログ規約作業を進行中**だったため、`worktree.py merge` の「未コミット変更あり」が解消せず `status` → コミット → `merge` 失敗を4周した。他人の作業中ファイルを「前セッションのステージ漏れ」と誤認して 5件コミットしてしまった（`3bed24b`〜`eb574c5`）。`worktree_guide.md` §5.3 手順3「main の未コミット変更はコミットする」は**単独セッション前提**で、§0 の並行作業ルールと噛み合っていない。
  → §5.3 手順3を「未コミット変更が**自分の担当外**なら**コミットせずユーザーへ確認**する（`git log -1 --format=%cr` と mtime で進行中か判定。stat キャッシュが古いと差分が小出しに出るので `git update-index --really-refresh` を先に実行）」へ改め、`worktree.py merge` のエラーにも同案内を出す。

## 2026-08-09 22:15 | session 0c6a9603 | 自動検出
- シグナル: correction(間違)
- ターン概要: ツール11回・エラー0回・拒否0回。開始:「待って、間違えて直近の作業をとめてしまったかも」
- 原因と改善案: 誤検出: 「間違えて」は**ユーザー自身が直近の作業を中断させたかも**という発話で、Claude の出力への訂正ではない。中断箇所の特定は `git worktree list` → worktree 側の `git status` → `check_docs.py` の3手で完了しており、`next_session.md` §0 ルール1（着手状態は git 側に持たせる）が意図どおり機能した例。

## 2026-08-10 03:25 | session 1eef5f4f | 自動検出
- シグナル: errors×3 / long-turn(calls=167)
- ターン概要: ツール167回・エラー3回・拒否0回。開始:「着手して」
- 原因と改善案: エラー3回はすべて **PowerShell ツールへの引数の渡し方**。①`mvn -q -Dsurefire.printSummary=false test` が `-D` 以降を別トークンとして解釈され「Unknown lifecycle phase」②`python -c @'...'@` の here-string で `"` が崩れて SyntaxError（結局スクラッチパッドへ `.py` を書いて回避）③`javap ... | Select-Object -First 3` がパイプ早期終了で exit 255。call=167 は25ファイル・単体+結合269件の規模ぶんが大半だが、機械的置換を1件ずつ `Edit` した分（AuthServiceImplTest の15か所等）は `replace_all` のグループ化でさらに減らせた。
  → `commands/adhoc.md` §4（使い捨て調査の作法）へ「PowerShell ツールでは **`-D`/`-X` 付きの引数と複数行スクリプトを直接渡さない**（`-D...` は `"` で括る、`python -c` は使わずスクラッチパッドへ `.py` を書いて実行、native exe のパイプに `Select-Object -First` を付けない）」を追記する。あわせて `dev.md` §5 の動作確認表に **PowerShell 版のコマンド列**（`mvn -f backend/pom.xml ...`）を併記し、`cd backend && ...` の Bash 記法をそのまま貼って崩す往復をなくす。

## 2026-08-10 13:38 | session 66b774d9 | 自動検出
- シグナル: same-command('git status --short'×3)
- ターン概要: ツール65回・エラー0回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: 3回のうち2回は**別々の工程ゲートが個別に要求**したもの（`dev/SKILL.md` §6.1 差分の範囲 / `worktree_guide.md` §5.3 手順3 main 側の未コミット確認 ＝ 実質誤検出）。無駄だったのは1回で、**変異テストの裏取り**（`check_branch_list.py` を一時的に壊して新設テストが赤くなることを確認 → `git checkout --` で復元）のあとに復元確認として単独で叩いた分。直後に全382件の再実行とコミット前の差分確認が控えており、そこで同じことが分かった。
  → `dev.md` §5「確認時の注意」へ「**変異による裏取りは復元後に単独の `git status` を挟まず、§6.1 のコミット前チェックで一括確認する**（`git checkout -- <path>` は失敗すれば非0で返るため、復元それ自体の確認は不要）」を1行追記する。

## 2026-08-10 15:13 | session 96b02c43 | 自動検出
- シグナル: errors×3
- ターン概要: ツール124回・エラー3回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: 3件とも**ツールの選び分けの誤り**で、内容の誤りではない。①`pom.xml` を Bash の `cat` で読んでから `Edit` して「File has not been read yet」で弾かれ、`Read` からやり直した（**編集する気のあるファイルを `cat`／`ctx` で読まない**）②`curl` で Maven Central の版を引いて context-mode フックにリダイレクトされた ③`mvn help:effective-pom` も同じくリダイレクトされた。②③は `profile.md` §6 規律6（大きな出力は context-mode）と `adhoc.md` §6（版は maven-metadata.xml を見る）を**両方守っていれば1回で済んだ**もので、規律は既にあるのに Bash を既定で選んだのが原因。
  → `adhoc.md` §4 へ「**外部問い合わせ（`curl`）とビルド（`mvn`）は最初から `ctx_execute` で叩く**（Bash はフックでリダイレクトされ1往復が無駄になる）」と「**`Edit` する予定のファイルは `Read` で読む**（`cat`／`ctx_execute_file` で読んでも Edit は通らない）」の2行を追記する。後者は `profile.md` §6 規律6 の「`Read` の全文読みは Edit 前提のときのみ」の裏返しで、規律側にも「逆に Edit 前提なら必ず `Read`」と補うと片側だけ守る事故が減る。
