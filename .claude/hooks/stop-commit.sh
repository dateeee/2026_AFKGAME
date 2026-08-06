#!/usr/bin/env bash
# Stop hook — 作業終了時に未コミットの変更が残っていれば Claude にコミットさせる。
#
# settings.json からは直接呼ばれず、`.claude/hooks/stop-chain.sh` が
# efficiency_check.py の後に実行する（効率メモへの追記が済んだ状態で
# git status を読むため、メモの変更も同じコミットに含まれる）。
#
# 動作:
#   変更なし          → exit 0（何もしない）
#   未コミット変更あり → exit 2 + stderr（応答をブロックし、Claude にコミットを指示）
#
# ループしない理由:
#   Claude がコミットすると作業ツリーがクリーンになり、次の Stop で exit 0 になる。
#   コミットに失敗した場合も stop_hook_active ガードで2回目は素通りする。
#
# 一時的に無効化したいとき:
#   touch .claude/.no-auto-commit   （再開は rm .claude/.no-auto-commit）

input=$(cat)

# 既に Stop hook 由来で継続中なら再ブロックしない（無限ループ防止）
case "$input" in
  *'"stop_hook_active":true'* | *'"stop_hook_active": true'*) exit 0 ;;
esac

# プロジェクトルートへ移動（Windows パスは cygpath で POSIX 形式へ変換）
dir="${CLAUDE_PROJECT_DIR:-}"
if [ -n "$dir" ]; then
  if command -v cygpath >/dev/null 2>&1; then
    dir=$(cygpath -u "$dir" 2>/dev/null || printf '%s' "$dir")
  fi
  cd "$dir" 2>/dev/null || true
fi

# git リポジトリ外なら何もしない
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || exit 0

# 無効化フラグ
[ -f .claude/.no-auto-commit ] && exit 0

changes=$(git status --porcelain 2>/dev/null)
[ -z "$changes" ] && exit 0

count=$(printf '%s\n' "$changes" | grep -c .)

cat >&2 <<EOF
未コミットの変更が ${count} 件あります。作業を終える前にコミットしてください。

1. 上に効率メモ（docs/reviews/efficiency_memo.md）への記入指示が出ている場合は、
   コミットより先に記入を済ませる。その変更もこのコミットに含める
   （メモだけを別コミットにしない。上の件数にはメモの変更も含まれている）
2. git status --short と git diff --stat で変更内容を把握する
   1列目が空白でない行は既に index にステージ済み。git commit はこれも巻き込む
3. 今回の作業による変更だけであれば git add -A ですべてをステージする
   無関係な変更（前セッションの残り・ステージ済みの別作業）が混ざっている場合は
   git add -A を使わず、git commit -- <path>... で対象パスを明示して範囲を限定する
4. タスクが完了した場合、コミット前に docs/next_session.md の「次回」を次のタスクへ
   更新する（書式は .claude/project/next.md。未確定なら「ユーザー判断待ち」+ 候補を残す）
5. CLAUDE.md の変更履歴と同じ形式でコミットする
   （docs: / feat: / fix: / chore: + 日本語の要約。必要なら本文で詳細を補足）

変更が試行錯誤の残骸や一時ファイルでコミットすべきでない場合、
または複数テーマにまたがっていて分割方針の判断が必要な場合は、
コミットせずにユーザーへその旨を伝えて判断を仰いでください。
EOF

exit 2
