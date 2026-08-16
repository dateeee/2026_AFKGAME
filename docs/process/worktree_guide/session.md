# git worktree 運用 — セッション運用（§5）

> [worktree_guide.md](../worktree_guide.md) の分冊。**§5.1〜§5.4 の正**（節番号は分割前を維持）。
> 作成・統合コマンドは親の §1、並行させる判断は §2、ファイル別の競合ポリシーは §3、ポート・DB の分離は §4。

**ファイルを編集する作業は worktree 内で行う**。セッション自身が §5.2〜§5.3 の手順で worktree を作り・入り・統合して片付けるため、人が別ウィンドウを開く必要はない。

## 5.1 main のまま進めてよい作業

| # | ケース |
|---|-------|
| 1 | 読み取りのみ（調査・質問への回答・レビュー系スキルの分析） |
| 2 | 親 §3 で「main でのみ更新」とした単独更新ファイルだけを触る作業（`next_session.md`・`java_migration.md` の進捗・`docs/backlog/**`） |
| 3 | 統合そのもの（§5.3 の main 側手順） |

上記以外で編集が発生すると分かった時点で §5.2 へ移る。**依頼が仕様・規約・コードの「変更」なら、調査を始める前に** worktree を作る。**main 可の判定は着手時点のファイルではなく、着手後に広がりうる範囲で行う**（`docs/backlog/**` の整理として始まった依頼が docs・コードの修正へ広がる例）。main で先に読んだファイルは worktree 側では**未読扱いになり `Edit` が「File has not been read yet」で弾かれる**（コスト増ではなく着手不能。5ファイルまとめて失敗した実績あり）。

## 5.2 開始手順

| 順 | 操作 |
|----|------|
| 0 | `python scripts/worktree.py list` で着手状況を確認する。取ろうとしている行の worktree が既にあれば別セッションが着手中（`next_session.md` §0） |
| 1 | `python scripts/worktree.py add <名前>`（名前は `next_session.md` が採番済み。無い場合は親 §1 の命名。作成先パスが標準出力に出る） |
| 2 | `EnterWorktree` にそのパスを **`path` で渡す**（`name` を使わない ＝ §5.4）。セッションの作業ディレクトリが worktree へ移る。**入った直後に §5.4 を通読する**（Bash の複合コマンド・`ctx_execute_file`・main への `cd` はいずれも拒否される。main と同じ流儀で叩くとエラーを繰り返す） |
| 3 | frontend を触るタスクは worktree 側で `cd frontend; npm install`（親 §1） |
| 4 | 以降の読み込み・編集・コミットはすべて worktree 内で行い、**パスは worktree 側の絶対パスで統一する**（main 側で読んだファイルは §5.1 のとおり `Edit` が弾かれる）。**すでに main で読んでしまったら再 Read せず、使い捨ての置換スクリプトで一括適用する**（置換前に出現数を assert して誤爆を防ぐ） |

## 5.3 完了手順（統合）

worktree の作成・統合（main への merge）・削除は**ユーザーへの確認なしで進めてよい**。確認が要るのは競合解消の方針など機械的に決められない判断だけ。

| 順 | 場所 | 操作 |
|----|------|------|
| 1 | worktree | 成果物をコミットし、テストが通ることを確認 |
| 2 | — | `ExitWorktree`（`action: "keep"`）で main へ戻る。**`"remove"` は効かない**（§5.4） |
| 3 | main | **先に `git status --short` で main 側の未コミット変更を確認する**（残っていると ff 統合が止まる）。**担当外のファイルが混じっていたらコミットせずユーザーへ確認する**（別セッションが作業中の可能性。「前セッションのステージ漏れ」と誤認して他人の作業を巻き込んだ実績あり。進行中かは `git log -1 --format=%cr` と mtime で判定し、差分が小出しなら先に `git update-index --really-refresh`）。自分の残りだけならコミット → `python scripts/worktree.py merge <名前>`（main 取り込み → ff 統合 → worktree・ブランチ削除まで一括） |
| 4 | main | `next_session.md` の更新（親 §3 のとおり main でのみ・ここで1回）: §1 を次のタスクへ書き換え、**消化した候補キューの行を消す** → コミット |

`merge` が「競合」で止まったら: worktree 側のファイルを編集して解消 → worktree でコミット → main から `merge` を再実行（worktree へ入り直す必要はない）。

## 5.4 前提と注意

- **内蔵 worktree 機能とは別物**: `EnterWorktree` を `name` 付きで呼ぶと `.claude/worktrees/`（gitignore 済み）に作られ、親 §1 の配置規約・`settings.local.json` のコピー・rerere が効かない。必ず `add` してから `path` で入る。`ExitWorktree` が削除できるのは `name` で作ったものだけなので、`path` で入ったら `"keep"` で戻る（実体は §5.3 の 3 の `merge` が消す）
- `.claude/`（スキル・フック・プロファイル）はコミット済みなので worktree にも同梱されそのまま動く。`settings.local.json` は `add` がコピーする
- 効率メモ・Stop フックは各 worktree 内のファイルへ書き、統合時に `merge=union` で合流する（`efficiency_check.py` はフック入力の `cwd` から作業ツリーの根を解決するため、main から起動されても worktree 側へ書く）
- **worktree 中の Bash は1コマンド1目的に割る**。`&&` 連結・ループ・`$(...)`・リダイレクト・複数パスを1つに詰めると分離ガードが「worktree 内に留まるか検証できない」として拒否する。cwd は既に worktree なので `cd <worktree> &&` の前置も不要（付けると弾かれる）。main を指す `git -C <mainのパス>` や main への `cd` も拒否されるため、**main 側の状態確認は入る前に済ませる**（§5.3 の 2 で戻ってから見る）
- **一括移送（`git mv`・`cp`）はディレクトリ単位でまとめる**（上の制約でファイル単位に展開すると呼び出し回数が跳ねる。実績: 数回で済む移送を21回に割った）
- **`worktree.py merge` は main で実行する**（worktree の中から呼ぶと相対パス解決に失敗する。§5.3 の 3）
- **worktree 内のファイル解析は `ctx_execute` にパスを直書きして行う**。`ctx_execute_file` はプロジェクトルート外として拒否される
- 工程の区切りで `/clear` を提案する既定ルール（CLAUDE.md）は worktree 内でも同じで、`/clear` しても作業ディレクトリは worktree のまま。自動メモリはディレクトリパス単位のため worktree では別になるが、正はリポジトリ内ドキュメントに置く方針（`MEMORY.md`）なので実害はない
