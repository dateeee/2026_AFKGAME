#!/usr/bin/env python3
"""Stop フック — 直近ターンの非効率シグナルを検出し、効率メモへ追記する。

settings.json からは直接呼ばれず、`.claude/hooks/stop-chain.sh` が
stop-commit.sh より先に実行する（メモの追記をコミット確認より前に済ませ、
メモの変更を同じコミットへ含めるため）。

動作:
  シグナルなし       → exit 0（何もしない）
  シグナルあり       → docs/reviews/efficiency_memo.md へ仮エントリを追記し、
                       {"decision": "block"} で Claude に「原因と改善案」の
                       記入だけを指示する（新たな作業はさせない）

ループしない理由:
  stop_hook_active が true の Stop（フック起因の継続後）では何もしない。

検出シグナル（しきい値は下の定数。誤検出が続くものはここを調整する）:
  same-read     同一パラメータの Read が同一ターンに複数回（再Read禁止の違反）
  same-command  同一コマンドの Bash/PowerShell 実行の繰り返し（試行錯誤の空回り）
  errors        ツールエラーの多発（検査・リンタ・テストの「違反あり exit 1」は除く）
  denials       ユーザーによる許可拒否が複数（進め方の食い違い）
  long-turn     ターン内のツール呼び出し総数が過大
  correction    ターン冒頭のユーザー発話に手戻り語（前ターンの手戻りを示す）

トランスクリプトのパースは log_token_usage.py と同様に防御的に行い、
失敗してもセッションを妨げない（常に exit 0）。
テスト時は環境変数 EFFICIENCY_MEMO でメモの出力先を差し替えられる。
"""
import json
import os
import re
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MEMO_PATH = Path(os.environ.get("EFFICIENCY_MEMO")
                 or ROOT / "docs" / "reviews" / "efficiency_memo.md")

# ── しきい値 ──
SAME_READ_MIN = 2   # 同一 (file_path, offset, limit) の Read 回数
SAME_CMD_MIN = 3    # 同一コマンド文字列の実行回数
ERRORS_MIN = 3      # is_error なツール結果の数（拒否を除く）
DENIALS_MIN = 2     # ユーザーによる拒否の数
CALLS_MIN = 30      # ターン内のツール呼び出し総数

# 手戻りを示す語（ターン冒頭のユーザー発話に含まれたら前ターンの手戻りを疑う）
CORRECTION_WORDS = ("違う", "ちがう", "そうじゃな", "やり直", "やりなおし",
                    "間違", "戻して", "また同じ", "無駄", "非効率", "何度も")

# ユーザー拒否の判定（is_error だがツール自体の失敗ではない）
DENIAL_MARKS = ("doesn't want to proceed", "user rejected", "User rejected")

# 「違反を見つけたら非ゼロ終了」が仕様のコマンド（検査・リンタ・テスト）。
# その exit 1 は正常な報告であり、試行錯誤の空回りではないため errors に数えない。
# プロジェクト固有の追加は環境変数 EFFICIENCY_EXPECTED_NONZERO（正規表現）で行う。
EXPECTED_NONZERO = re.compile(
    os.environ.get("EFFICIENCY_EXPECTED_NONZERO")
    or r"check_\w+\.py|pytest|ruff|mypy|eslint|vue-tsc|--noEmit|type-check|\blint\b",
    re.IGNORECASE)

PLACEHOLDER = "（未記入 — Claude が1〜2行で追記する）"

FALLBACK_HEADER = (
    "# 効率メモ（efficiency memo）\n\n"
    "書式・運用は .claude/project/retro.md を参照。\n\n---\n"
)


def to_local_path(raw):
    """Git Bash 等の POSIX 形式 (/c/Users/...) を Windows 形式へ変換する。"""
    m = re.match(r"^/([A-Za-z])/(.*)$", str(raw))
    if m and not Path(raw).exists():
        return Path(f"{m.group(1)}:/{m.group(2)}")
    return Path(raw)


def iter_entries(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue
            if isinstance(entry, dict):
                yield entry


def text_of(content):
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "\n".join(b.get("text", "") for b in content
                         if isinstance(b, dict) and b.get("type") == "text")
    return ""


def find_boundary(entries):
    """直近ターンの起点（最後のユーザー入力）の添字を返す。"""
    boundary = None
    for i, e in enumerate(entries):
        if e.get("type") != "user" or e.get("isSidechain") or e.get("isMeta"):
            continue
        content = (e.get("message") or {}).get("content")
        has_tool_result = isinstance(content, list) and any(
            isinstance(b, dict) and b.get("type") == "tool_result" for b in content)
        if text_of(content).strip() and not has_tool_result:
            boundary = i
    return boundary


def analyze(entries):
    """直近ターンを解析し、シグナル一覧と概要を返す。ターンが無ければ None。"""
    boundary = find_boundary(entries)
    if boundary is None:
        return None
    reads, cmds = Counter(), Counter()
    tool_cmds = {}  # tool_use_id -> コマンド文字列（エラーの発生元を引くため）
    calls = errors = denials = 0
    for e in entries[boundary:]:
        content = (e.get("message") or {}).get("content")
        if not isinstance(content, list):
            continue
        if e.get("type") == "assistant":
            for b in content:
                if not (isinstance(b, dict) and b.get("type") == "tool_use"):
                    continue
                calls += 1
                inp = b.get("input") or {}
                name = b.get("name") or ""
                if name == "Read":
                    reads[(str(inp.get("file_path", "")).replace("\\", "/").lower(),
                           inp.get("offset"), inp.get("limit"))] += 1
                elif name in ("Bash", "PowerShell"):
                    cmd = str(inp.get("command", "")).strip()
                    if cmd:
                        cmds[cmd] += 1
                        tool_cmds[b.get("id")] = cmd
        elif e.get("type") == "user":
            for b in content:
                if isinstance(b, dict) and b.get("type") == "tool_result" \
                        and b.get("is_error"):
                    body = json.dumps(b.get("content", ""), ensure_ascii=False)
                    if any(m in body for m in DENIAL_MARKS):
                        denials += 1
                    elif not EXPECTED_NONZERO.search(tool_cmds.get(b.get("tool_use_id"), "")):
                        errors += 1

    signals = []
    rereads = sorted(((k, c) for k, c in reads.items() if c >= SAME_READ_MIN),
                     key=lambda x: -x[1])
    if rereads:
        detail = ", ".join(f"{Path(k[0]).name}×{c}" for k, c in rereads[:3])
        signals.append(f"same-read({detail})")
    recmds = sorted(((k, c) for k, c in cmds.items() if c >= SAME_CMD_MIN),
                    key=lambda x: -x[1])
    if recmds:
        detail = ", ".join(f"'{k[:30]}'×{c}" for k, c in recmds[:2])
        signals.append(f"same-command({detail})")
    if errors >= ERRORS_MIN:
        signals.append(f"errors×{errors}")
    if denials >= DENIALS_MIN:
        signals.append(f"denials×{denials}")
    if calls >= CALLS_MIN:
        signals.append(f"long-turn(calls={calls})")

    btext = text_of((entries[boundary].get("message") or {}).get("content")).strip()
    if btext and not btext.startswith("<"):
        hits = [w for w in CORRECTION_WORDS if w in btext]
        if hits:
            signals.append("correction(" + ",".join(hits[:3]) + ")")

    head = next((ln.strip() for ln in btext.splitlines() if ln.strip()), "")[:60]
    return {"signals": signals, "calls": calls, "errors": errors,
            "denials": denials, "prompt": head}


def append_memo(session_id, stats):
    MEMO_PATH.parent.mkdir(parents=True, exist_ok=True)
    if not MEMO_PATH.exists():
        MEMO_PATH.write_text(FALLBACK_HEADER, encoding="utf-8")
    stamp = datetime.now().strftime("%Y-%m-%d %H:%M")
    entry = (
        f"\n## {stamp} | session {(session_id or 'unknown')[:8]} | 自動検出\n"
        f"- シグナル: {' / '.join(stats['signals'])}\n"
        f"- ターン概要: ツール{stats['calls']}回・エラー{stats['errors']}回・"
        f"拒否{stats['denials']}回。開始:「{stats['prompt']}」\n"
        f"- 原因と改善案: {PLACEHOLDER}\n"
    )
    with open(MEMO_PATH, "a", encoding="utf-8") as f:
        f.write(entry)


def block_reason(stats):
    memo_rel = MEMO_PATH.relative_to(ROOT).as_posix() \
        if MEMO_PATH.is_relative_to(ROOT) else str(MEMO_PATH)
    return (
        f"Stopフック（効率チェック）: このターンに非効率シグナル "
        f"[{' / '.join(stats['signals'])}] を検出し、{memo_rel} の末尾に仮エントリを"
        "追記した。最後のエントリの「原因と改善案」の行だけを、このターンで実際に"
        "起きたことに基づき1〜2行（原因 + どのスキル/プロファイル/成果物をどう直すか）"
        "へ書き換えよ。正当な作業への誤検出なら「誤検出: <理由>」と書く。"
        "この記入はコミットより先に行い、続くコミット指示に従う際は "
        f"{memo_rel} の変更も同じコミットへ含めること（メモだけの追いコミットを作らない）。"
        "コミット指示が続かない場合（自動コミット無効時）は記入だけで終える。"
        "それ以外の新しい作業・調査はしない。"
    )


def main():
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass
    data = json.load(sys.stdin)
    if data.get("stop_hook_active"):
        return
    path = to_local_path(data.get("transcript_path", ""))
    if not path.is_file():
        return
    stats = analyze(list(iter_entries(path)))
    if not stats or not stats["signals"]:
        return
    append_memo(data.get("session_id", ""), stats)
    print(json.dumps({"decision": "block", "reason": block_reason(stats)},
                     ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # フックの失敗でセッションを妨げない
        print(f"efficiency_check: {exc}", file=sys.stderr)
    sys.exit(0)
