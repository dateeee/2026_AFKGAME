#!/usr/bin/env python3
"""Stop フック — セッションのトークン使用量を logs/token_usage.csv へ記録する。

起動方法:
  1. フック起動（通常）: Claude Code の Stop フック（応答完了ごと）から呼ばれ、
     stdin の JSON（session_id / transcript_path）を受け取り、そのセッションの
     累計を再集計して記録する。毎ターン上書きされるため常に最新の値になり、
     クラッシュや強制終了でも直前のやり取りまでは記録が残る。
  2. 一括取り込み: python scripts/log_token_usage.py --all
     ~/.claude/projects/<プロジェクトslug>/ の全トランスクリプトのうち、
     未記録のセッションをまとめて記録する（過去分の取り込み用）。

記録先: logs/token_usage.csv（1行 = 1セッション × 1モデル。同一 session_id は上書き）
サブエージェント分（<セッションID>/subagents/*.jsonl）も同セッションの消費に合算する。

注意:
  - トランスクリプト JSONL は Claude Code の内部仕様でありバージョンで変わりうる。
    パースは防御的に行い、失敗してもセッションを妨げない（常に exit 0）。
  - 同一 API 応答が複数行に現れるため message.id で重複排除する。
  - resume で引き継いだセッションは引き継ぎ前の消費も含むため、行の単純合算は
    その分を二重計上しうる（既知の制限）。
"""
import csv
import json
import re
import sys
from datetime import datetime
from pathlib import Path

LOG_PATH = Path(__file__).resolve().parent.parent / "logs" / "token_usage.csv"
COLUMNS = [
    "end_time", "session_id", "reason", "task", "model",
    "input_tokens", "output_tokens", "cache_read_tokens",
    "cache_creation_tokens", "total_tokens", "api_calls",
]
CMD_RE = re.compile(r"<command-name>\s*/?([^<\s]+)\s*</command-name>")
UUID_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

# タスク名として無意味な組み込みコマンド（これらはスキップして次の候補を探す）
SKIP_COMMANDS = {
    "clear", "compact", "model", "cost", "usage", "context", "config", "help",
    "export", "login", "logout", "status", "doctor", "mcp", "memory", "resume",
    "rewind", "fast", "ide", "hooks", "todos", "statusline", "permissions",
    "add-dir", "agents", "bashes", "output-style", "privacy-settings",
    "release-notes", "terminal-setup", "vim", "bug", "init", "exit", "quit",
}


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


def user_text(entry):
    if entry.get("type") != "user" or entry.get("isSidechain") or entry.get("isMeta"):
        return ""
    content = (entry.get("message") or {}).get("content")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = [b.get("text", "") for b in content
                 if isinstance(b, dict) and b.get("type") == "text"]
        return "\n".join(parts)
    return ""


def task_label(entries):
    """タスク名を推定する。スラッシュコマンド > Skillツール呼び出し > 最初のプロンプト。"""
    fallback = ""
    for entry in entries:
        text = user_text(entry)
        if not text:
            continue
        for m in CMD_RE.finditer(text):
            name = m.group(1)
            if name.split(":")[-1].lower() not in SKIP_COMMANDS:
                return "/" + name
        if not fallback:
            first = text.strip().splitlines()[0].strip()
            if first and not first.startswith(
                    ("<", "Caveat:", "[", "This session is being continued")):
                fallback = first[:60]
    for entry in entries:
        if entry.get("type") != "assistant":
            continue
        content = (entry.get("message") or {}).get("content")
        if not isinstance(content, list):
            continue
        for block in content:
            if (isinstance(block, dict) and block.get("type") == "tool_use"
                    and block.get("name") == "Skill"):
                skill = (block.get("input") or {}).get("skill")
                if skill:
                    return "/" + str(skill)
    return fallback or "(不明)"


def collect_usage(entries):
    """モデル別のトークン集計。同一応答の重複行は message.id で排除する。"""
    per_msg = {}
    for entry in entries:
        if entry.get("type") != "assistant":
            continue
        msg = entry.get("message") or {}
        usage = msg.get("usage")
        if not isinstance(usage, dict):
            continue
        key = msg.get("id") or entry.get("uuid")
        per_msg[key] = (msg.get("model") or "unknown", usage)

    models = {}
    for model, usage in per_msg.values():
        agg = models.setdefault(
            model, {"input": 0, "output": 0, "cache_read": 0, "cache_creation": 0, "calls": 0})
        agg["input"] += int(usage.get("input_tokens") or 0)
        agg["output"] += int(usage.get("output_tokens") or 0)
        agg["cache_read"] += int(usage.get("cache_read_input_tokens") or 0)
        agg["cache_creation"] += int(usage.get("cache_creation_input_tokens") or 0)
        agg["calls"] += 1
    return {m: a for m, a in models.items()
            if a["input"] + a["output"] + a["cache_read"] + a["cache_creation"] > 0}


def upsert_rows(rows, session_id):
    existing = []
    if LOG_PATH.exists():
        with open(LOG_PATH, encoding="utf-8-sig", newline="") as f:
            existing = [r for r in csv.DictReader(f) if r.get("session_id") != session_id]
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(LOG_PATH, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(existing + rows)


def log_session(transcript_path, session_id, reason):
    path = to_local_path(transcript_path)
    if not session_id or not path.is_file():
        return False
    entries = list(iter_entries(path))
    label = task_label(entries)
    # サブエージェント分（<セッションID>/subagents/*.jsonl）も消費に含める
    for sub in sorted((path.parent / path.stem / "subagents").glob("*.jsonl")):
        entries.extend(iter_entries(sub))
    models = collect_usage(entries)
    if not models:
        return False
    end_time = datetime.now().strftime("%Y-%m-%d %H:%M")
    rows = []
    for model, agg in sorted(models.items()):
        total = agg["input"] + agg["output"] + agg["cache_read"] + agg["cache_creation"]
        rows.append({
            "end_time": end_time, "session_id": session_id, "reason": reason,
            "task": label, "model": model,
            "input_tokens": agg["input"], "output_tokens": agg["output"],
            "cache_read_tokens": agg["cache_read"],
            "cache_creation_tokens": agg["cache_creation"],
            "total_tokens": total, "api_calls": agg["calls"],
        })
    upsert_rows(rows, session_id)
    return True


def logged_session_ids():
    if not LOG_PATH.exists():
        return set()
    with open(LOG_PATH, encoding="utf-8-sig", newline="") as f:
        return {r.get("session_id") for r in csv.DictReader(f)}


def backfill():
    slug = re.sub(r"[^0-9A-Za-z-]", "-", str(Path.cwd()))
    project_dir = Path.home() / ".claude" / "projects" / slug
    if not project_dir.is_dir():
        print(f"トランスクリプトが見つかりません: {project_dir}", file=sys.stderr)
        return
    done = logged_session_ids()
    count = 0
    for path in sorted(project_dir.glob("*.jsonl")):
        if not UUID_RE.match(path.stem) or path.stem in done:
            continue
        if log_session(path, path.stem, "backfill"):
            count += 1
    print(f"{count} セッションを記録しました -> {LOG_PATH}")


def main():
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass
    if "--all" in sys.argv:
        backfill()
        return
    data = json.load(sys.stdin)
    # Stop フックの stdin に reason は無い（SessionEnd 互換で残しつつ既定は "stop"）
    reason = data.get("reason") or "stop"
    log_session(data.get("transcript_path", ""),
                data.get("session_id", ""),
                reason)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # フックの失敗でセッションを妨げない
        print(f"log_token_usage: {exc}", file=sys.stderr)
    sys.exit(0)
