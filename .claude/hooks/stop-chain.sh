#!/usr/bin/env bash
# Stop hook chain — ブロックする2つのフックを「順番どおり」に実行するラッパー。
#
# 実行順（この順が本質。settings.json に個別登録すると並列実行され順序が保証されない）:
#   1. efficiency_check.py … 効率メモへ仮エントリを追記し、記入を指示する
#   2. stop-commit.sh      … 未コミット変更のコミットを指示する
#
# この順にする理由:
#   メモへの追記を先に済ませてから git status を読むので、追記されたメモが
#   必ずコミット対象に含まれる。並列実行だと「作業ツリーは空 → コミット指示なし →
#   メモの変更だけが残る」が起き、次セッションでメモだけの追いコミットになる。
#
# 出力:
#   どちらもブロックなし → exit 0
#   いずれかがブロック   → 理由を「メモ記入 → コミット」の順に連結して stderr + exit 2
#
# 各子フックは自前で stop_hook_active ガードを持つため、ここでは判定しない。

input=$(cat)

hook_dir=$(cd "$(dirname "$0")" 2>/dev/null && pwd)
memo_hook="$hook_dir/efficiency_check.py"
commit_hook="$hook_dir/stop-commit.sh"

# 1. 効率メモ（block 時は {"decision":"block","reason":"..."} を stdout に出す）
memo_reason=""
if [ -f "$memo_hook" ]; then
  memo_json=$(printf '%s' "$input" | python "$memo_hook" 2>/dev/null)
  if [ -n "$memo_json" ]; then
    memo_reason=$(printf '%s' "$memo_json" | python -c \
      'import json,sys
raw = sys.stdin.read().strip()
try:
    print(json.loads(raw).get("reason", ""))
except Exception:
    pass' 2>/dev/null)
  fi
fi

# 2. コミット確認（block 時は stderr + exit 2）
commit_reason=""
if [ -f "$commit_hook" ]; then
  commit_reason=$(printf '%s' "$input" | bash "$commit_hook" 2>&1 >/dev/null)
fi

[ -z "$memo_reason" ] && [ -z "$commit_reason" ] && exit 0

{
  [ -n "$memo_reason" ] && printf '%s\n\n' "$memo_reason"
  [ -n "$commit_reason" ] && printf '%s\n' "$commit_reason"
} >&2

exit 2
