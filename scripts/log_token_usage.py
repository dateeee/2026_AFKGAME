#!/usr/bin/env python3
"""Stop フック — セッションのトークン使用量を logs/ 配下の CSV へ記録する。

起動方法:
  1. フック起動（通常）: Claude Code の Stop フック（応答完了ごと）から呼ばれ、
     stdin の JSON（session_id / transcript_path）を受け取り、そのセッションの
     累計を再集計して記録する。毎ターン上書きされるため常に最新の値になり、
     クラッシュや強制終了でも直前のやり取りまでは記録が残る。
  2. 一括取り込み: python scripts/log_token_usage.py --all
     ~/.claude/projects/<プロジェクトslug>/ の全トランスクリプトのうち、
     未記録のセッションをまとめて記録する（過去分の取り込み用）。
     worktree（git worktree list に載るもの）の slug も走査する。

記録先（worktree から起動された場合も main リポジトリの logs/ に書く）:
  - logs/token_usage.csv        1行 = 1セッション × 1モデルの累計（同一 session_id は上書き）
  - logs/token_usage_deltas.csv 1行 = 1やり取り × 1モデルの差分（追記のみ・フック起動時のみ）。
    直前のユーザープロンプトへ帰属させるため、タスク別コストの分析はこちらを使う
サブエージェント分（<セッションID>/subagents/*.jsonl）も同セッションの消費に合算する。

付随機能（削減計画 2026-08-06 ①-2）: フック起動時にコンテキスト長・呼び出し回数が
閾値を超えていたら {"systemMessage": ...} を stdout へ出して /clear を促す（exit 0）。

注意:
  - トランスクリプト JSONL は Claude Code の内部仕様でありバージョンで変わりうる。
    パースは防御的に行い、失敗してもセッションを妨げない（常に exit 0）。
  - 同一 API 応答が複数行に現れるため message.id で重複排除する。
  - resume で引き継いだセッションは引き継ぎ前の消費も含むため、行の単純合算は
    その分を二重計上しうる（既知の制限）。deltas は負の差分を検出したら
    今回累計をそのまま記録する。
  - 複数セッション（main + worktree）が同時に Stop すると token_usage.csv の
    書き直しが競合しうる（最後の書き込みが勝つ。次の Stop で自己修復する）。
"""
import csv
import json
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path


def repo_root():
    """スクリプトを含むリポジトリのルート。worktree なら main リポジトリのルートを返す。

    worktree では <root>/.git が「gitdir: <main>/.git/worktrees/<名前>」を書いた
    ファイルになることを利用する（git を起動せずに解決できる）。
    """
    root = Path(__file__).resolve().parent.parent
    gitfile = root / ".git"
    if gitfile.is_file():
        try:
            m = re.search(r"gitdir:\s*(.+)", gitfile.read_text(encoding="utf-8", errors="replace"))
            if m:
                gitdir = Path(m.group(1).strip())
                if not gitdir.is_absolute():
                    gitdir = (root / gitdir).resolve()
                # <main>/.git/worktrees/<名前> → <main>
                if gitdir.parent.name == "worktrees" and gitdir.parent.parent.name == ".git":
                    return gitdir.parent.parent.parent
        except OSError:
            pass
    return root


ROOT = repo_root()
LOG_PATH = ROOT / "logs" / "token_usage.csv"
DELTA_PATH = ROOT / "logs" / "token_usage_deltas.csv"
COLUMNS = [
    "end_time", "session_id", "reason", "task", "model",
    "input_tokens", "output_tokens", "cache_read_tokens",
    "cache_creation_tokens", "total_tokens", "api_calls", "cost_usd",
]
DELTA_COLUMNS = [
    "time", "session_id", "model", "prompt",
    "input_d", "output_d", "cache_read_d", "cache_creation_d", "calls_d", "cost_usd",
]
CMD_RE = re.compile(r"<command-name>\s*/?([^<\s]+)\s*</command-name>")
UUID_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

# 単価（$/MTok, input/output）。cache_read は入力単価×0.1、cache_creation は×2.0
# （本環境は1時間TTL運用のため上限側で計上する。削減計画 2026-08-06 ⑤-2）
PRICES = {
    "claude-opus-5": (5.0, 25.0),
    "claude-fable-5": (10.0, 50.0),
    "claude-mythos-5": (10.0, 50.0),
    "claude-sonnet-5": (3.0, 15.0),
    "claude-haiku-4-5": (1.0, 5.0),
}
FALLBACK_PRICE = PRICES["claude-opus-5"]  # 不明モデルは opus-5 単価
CACHE_READ_RATE = 0.1
CACHE_WRITE_RATE = 2.0

# コンテキスト警告の閾値（削減計画 2026-08-06 ①-2）
CTX_WARN_L1 = 120_000
CTX_WARN_L2 = 180_000
CALLS_WARN = 100

# タスク名として無意味な組み込みコマンド（これらはスキップして次の候補を探す）
SKIP_COMMANDS = {
    "clear", "compact", "model", "cost", "usage", "context", "config", "help",
    "export", "login", "logout", "status", "doctor", "mcp", "memory", "resume",
    "rewind", "fast", "ide", "hooks", "todos", "statusline", "permissions",
    "add-dir", "agents", "bashes", "output-style", "privacy-settings",
    "release-notes", "terminal-setup", "vim", "bug", "init", "exit", "quit",
}

# プロンプトとして無意味な行頭（システム由来・引き継ぎ・フック出力）
BOILERPLATE_PREFIXES = (
    "<", "Caveat:", "[", "This session is being continued", "Stop hook feedback",
)


def cost_usd(model, input_tokens, output_tokens, cache_read, cache_creation):
    """1行分のUSD換算コスト。モデル名は前方一致で単価表を引く。"""
    p_in, p_out = FALLBACK_PRICE
    for prefix, price in PRICES.items():
        if str(model).startswith(prefix):
            p_in, p_out = price
            break
    usd = (input_tokens * p_in + output_tokens * p_out
           + cache_read * p_in * CACHE_READ_RATE
           + cache_creation * p_in * CACHE_WRITE_RATE) / 1e6
    return round(usd, 4)


def row_cost_usd(row):
    """CSV行（文字列辞書）から cost_usd を再計算する。失敗時は空文字。"""
    try:
        return cost_usd(row.get("model", ""),
                        int(row.get("input_tokens") or 0),
                        int(row.get("output_tokens") or 0),
                        int(row.get("cache_read_tokens") or 0),
                        int(row.get("cache_creation_tokens") or 0))
    except (TypeError, ValueError):
        return ""


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
            if first and not first.startswith(BOILERPLATE_PREFIXES):
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


def last_prompt_label(entries):
    """直前のユーザープロンプト（先頭60字）。deltas の帰属先に使う。

    逆順に走査し、スラッシュコマンドはコマンド名（SKIP_COMMANDS は読み飛ばし）、
    システム由来（BOILERPLATE_PREFIXES）はスキップする。
    """
    for entry in reversed(entries):
        text = user_text(entry)
        if not text:
            continue
        m = CMD_RE.search(text)
        if m:
            name = m.group(1)
            if name.split(":")[-1].lower() in SKIP_COMMANDS:
                continue
            return "/" + name
        first = text.strip().splitlines()[0].strip()
        if not first or first.startswith(BOILERPLATE_PREFIXES):
            continue
        return first[:60]
    return "(不明)"


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


def read_log_rows():
    if not LOG_PATH.exists():
        return []
    with open(LOG_PATH, encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def upsert_rows(rows, session_id, existing=None):
    if existing is None:
        existing = read_log_rows()
    kept = [r for r in existing if r.get("session_id") != session_id]
    for r in kept:  # 旧形式の行には cost_usd が無いので埋める
        if not r.get("cost_usd"):
            r["cost_usd"] = row_cost_usd(r)
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(LOG_PATH, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(kept + rows)


def append_deltas(existing, rows, session_id, entries):
    """前回累計との差分を token_usage_deltas.csv へ追記する（フック起動時のみ呼ぶ）。"""
    prev = {r.get("model"): r for r in existing if r.get("session_id") == session_id}
    prompt = last_prompt_label(entries)
    time_s = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    pairs = (("input_tokens", "input_d"), ("output_tokens", "output_d"),
             ("cache_read_tokens", "cache_read_d"),
             ("cache_creation_tokens", "cache_creation_d"), ("api_calls", "calls_d"))
    out = []
    for row in rows:
        try:
            old = prev.get(row["model"]) or {}
            deltas = {dcol: int(row[col]) - int(old.get(col) or 0) for col, dcol in pairs}
            if min(deltas.values()) < 0:
                # resume 引き継ぎ等で累計が巻き戻った場合は今回累計をそのまま記録
                deltas = {dcol: int(row[col]) for col, dcol in pairs}
            if all(v == 0 for v in deltas.values()):
                continue
            out.append({
                "time": time_s, "session_id": session_id, "model": row["model"],
                "prompt": prompt, **deltas,
                "cost_usd": cost_usd(row["model"], deltas["input_d"], deltas["output_d"],
                                     deltas["cache_read_d"], deltas["cache_creation_d"]),
            })
        except (KeyError, TypeError, ValueError):
            continue
    if not out:
        return
    DELTA_PATH.parent.mkdir(parents=True, exist_ok=True)
    new_file = not DELTA_PATH.exists()
    with open(DELTA_PATH, "a", encoding="utf-8-sig" if new_file else "utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=DELTA_COLUMNS, extrasaction="ignore")
        if new_file:
            writer.writeheader()
        writer.writerows(out)


def context_warning(entries, models):
    """/clear を促す警告文（閾値未満なら空文字）。削減計画 ①-2。

    現在コンテキスト = 最後の assistant 応答の input + cache_read + cache_creation。
    """
    ctx = 0
    for entry in reversed(entries):
        if entry.get("type") != "assistant":
            continue
        usage = (entry.get("message") or {}).get("usage")
        if isinstance(usage, dict):
            ctx = (int(usage.get("input_tokens") or 0)
                   + int(usage.get("cache_read_input_tokens") or 0)
                   + int(usage.get("cache_creation_input_tokens") or 0))
            break
    calls = sum(a["calls"] for a in models.values())
    if ctx >= CTX_WARN_L2:
        return (f"⚠ コンテキスト{ctx / 10000:.1f}万tok — cache_read コストが毎呼び出しに比例。"
                "/clear を強く推奨")
    if ctx >= CTX_WARN_L1 or calls >= CALLS_WARN:
        return (f"⚠ コンテキスト{ctx / 10000:.1f}万tok / {calls}回 — "
                "工程の区切りで /clear を検討")
    return ""


def log_session(transcript_path, session_id, reason):
    path = to_local_path(transcript_path)
    if not session_id or not path.is_file():
        return False
    main_entries = list(iter_entries(path))
    label = task_label(main_entries)
    # サブエージェント分（<セッションID>/subagents/*.jsonl）も消費に含める
    entries = list(main_entries)
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
            "cost_usd": cost_usd(model, agg["input"], agg["output"],
                                 agg["cache_read"], agg["cache_creation"]),
        })
    existing = read_log_rows()
    if reason != "backfill":
        # 差分は upsert（累計の上書き）前に確定させる
        append_deltas(existing, rows, session_id, main_entries)
        warning = context_warning(main_entries, models)
        if warning:
            print(json.dumps({"systemMessage": warning}, ensure_ascii=False))
    upsert_rows(rows, session_id, existing=existing)
    return True


def logged_session_ids():
    return {r.get("session_id") for r in read_log_rows()}


def worktree_roots():
    """git worktree list に載る全ルート（main 含む）。git が使えなければ空。"""
    try:
        out = subprocess.run(["git", "worktree", "list", "--porcelain"],
                             cwd=ROOT, capture_output=True, text=True, timeout=10)
        return [Path(line[len("worktree "):].strip())
                for line in out.stdout.splitlines() if line.startswith("worktree ")]
    except (OSError, subprocess.SubprocessError):
        return []


def slug_candidates(root):
    """パスからトランスクリプト slug の候補を作る（ドライブ文字の大小両方）。"""
    s = str(root)
    variants = {s}
    if re.match(r"^[A-Za-z]:", s):
        variants.add(s[0].lower() + s[1:])
        variants.add(s[0].upper() + s[1:])
    return {re.sub(r"[^0-9A-Za-z-]", "-", v) for v in variants}


def project_dirs():
    """走査対象のトランスクリプトディレクトリ（cwd・main・全 worktree の slug）。"""
    roots = [Path.cwd(), ROOT] + worktree_roots()
    seen, dirs = set(), []
    for root in roots:
        for slug in sorted(slug_candidates(root)):
            d = Path.home() / ".claude" / "projects" / slug
            if d.is_dir() and d not in seen:
                seen.add(d)
                dirs.append(d)
    return dirs


def fill_missing_costs():
    """cost_usd が空の既存行（列追加前の旧形式）を再計算して埋める。"""
    rows = read_log_rows()
    if not rows or all(r.get("cost_usd") for r in rows):
        return
    with open(LOG_PATH, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS, extrasaction="ignore")
        writer.writeheader()
        for r in rows:
            if not r.get("cost_usd"):
                r["cost_usd"] = row_cost_usd(r)
            writer.writerow(r)


def backfill():
    dirs = project_dirs()
    if not dirs:
        slug = re.sub(r"[^0-9A-Za-z-]", "-", str(Path.cwd()))
        print(f"トランスクリプトが見つかりません: {Path.home() / '.claude' / 'projects' / slug}",
              file=sys.stderr)
        return
    done = logged_session_ids()
    count = 0
    for project_dir in dirs:
        for path in sorted(project_dir.glob("*.jsonl")):
            if not UUID_RE.match(path.stem) or path.stem in done:
                continue
            if log_session(path, path.stem, "backfill"):
                done.add(path.stem)
                count += 1
    fill_missing_costs()
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
