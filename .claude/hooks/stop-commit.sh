#!/usr/bin/env bash
# Stop hook — 作業終了時に未コミットの変更が残っていれば Claude にコミットさせる。
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

1. git status --short と git diff --stat で変更内容を把握する
2. git add -A ですべての変更をステージする
3. CLAUDE.md の変更履歴と同じ形式でコミットする
   （docs: / feat: / fix: / chore: + 日本語の要約。必要なら本文で詳細を補足）

変更が試行錯誤の残骸や一時ファイルでコミットすべきでない場合は、
コミットせずにユーザーへその旨を伝えて判断を仰いでください。
EOF

exit 2
