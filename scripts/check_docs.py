#!/usr/bin/env python3
"""ドキュメント機械検証（リンク・索引到達性・曖昧語・正の逸脱・台帳整合）

レビュー工程で繰り返し検出されてきた機械判定可能な指摘クラスを、
使い捨てスクリプトではなく常設チェックとして固定する。

検証項目:
    1. リンク       相対Markdownリンクの参照先が実在するか
    2. 索引到達性   docs/** の全ファイルが README.md / CLAUDE.md から辿れるか
    3. 曖昧語       仕様書本文に「適宜」「おおよそ」「TBD」「後日検討」「未定」が残っていないか
    4. 正の逸脱     docs/process/spec_ownership.md の検出パターンが正・許可ファイル以外に現れていないか
    5. 決定先送り   「Phase N の基本設計で確定する」形の先送りが台帳（open_specs.md）へリンクしているか
    6. 台帳存否     open_specs.md の実在と、本文の「不在」「実在」の断定が一致するか

使い方:
    python scripts/check_docs.py            # 全検証（ERROR があれば exit 1）
    python scripts/check_docs.py --links    # リンク検査のみ（--reach / --words / --owner / --pending / --ledger も同様）
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OWNERSHIP = ROOT / "docs" / "process" / "spec_ownership.md"

# 検査対象外（check_doc_size.py と同基準 + 追記型アーカイブ）
EXCLUDE = (
    "node_modules/",
    ".git/",
    ".claude/worktrees/",
    ".venv/",
    "venv/",
    "dist/",
    "docs/reviews/",   # 追記型アーカイブ。過去レポートのリンク切れは修正しない（documentation_rules/reviews.md §9）
    "docs/changelog.md",  # 追記型アーカイブ。過去行は当時のパスを保持する
)

# 曖昧語（detail-design スキル §5.1 / doc-review 観点9 を常設化）
AMBIGUOUS = re.compile(r"適宜|おおよそ|TBD|後日検討|未定(?!義)")
# 曖昧語検査の対象は仕様書系のみ（管理台帳・プロセス文書は「未定」を扱う場でありうるため除外）
AMBIGUOUS_DIRS = ("docs/design/", "docs/tech/", "docs/data/", "docs/diagrams/")
AMBIGUOUS_EXCLUDE = ("docs/backlog/open_specs.md", "docs/backlog/balance_backlog.md", "docs/backlog/known_issues.md")

LINK = re.compile(r"!?\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")


def targets() -> list[Path]:
    result = []
    for path in sorted(ROOT.rglob("*.md")):
        rel = path.relative_to(ROOT).as_posix()
        if any(rel.startswith(x) or f"/{x}" in f"/{rel}" for x in EXCLUDE):
            continue
        result.append(path)
    return result


def body_lines(path: Path):
    """コードフェンス外の (行番号, 行) を返す。"""
    in_code = False
    for no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if line.lstrip().startswith("```"):
            in_code = not in_code
            continue
        if not in_code:
            yield no, line


def extract_links(path: Path) -> list[tuple[int, str]]:
    """(行番号, リンク先) の一覧。外部URL・アンカーのみ・テンプレート表記は除く。"""
    links = []
    for no, line in body_lines(path):
        for target in LINK.findall(line):
            if target.startswith(("http://", "https://", "mailto:", "#")) or "{" in target:
                continue
            links.append((no, target.split("#")[0]))
    return [(no, t) for no, t in links if t]


def check_links(files: list[Path]) -> list[str]:
    errors = []
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        for no, target in extract_links(path):
            resolved = (path.parent / target).resolve()
            if not resolved.exists():
                errors.append(f"ERROR {rel}:{no}: リンク切れ → {target}")
    return errors


def check_reachability(files: list[Path]) -> list[str]:
    """README.md / CLAUDE.md を起点に、docs/** の全 .md へ到達できるか。"""
    nodes = {p.resolve() for p in files}
    graph: dict[Path, set[Path]] = {}
    for path in files:
        edges = set()
        for _, target in extract_links(path):
            resolved = (path.parent / target).resolve()
            if resolved in nodes:
                edges.add(resolved)
        graph[path.resolve()] = edges

    seen: set[Path] = set()
    stack = [p.resolve() for p in (ROOT / "README.md", ROOT / "CLAUDE.md") if p.exists()]
    while stack:
        node = stack.pop()
        if node in seen:
            continue
        seen.add(node)
        stack.extend(graph.get(node, ()))

    errors = []
    for path in sorted(nodes - seen):
        rel = path.relative_to(ROOT).as_posix()
        if rel.startswith("docs/"):
            errors.append(f"ERROR {rel}: README.md / CLAUDE.md の索引から到達できない（索引未登録）")
    return errors


def check_ambiguous(files: list[Path]) -> list[str]:
    errors = []
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        if not rel.startswith(AMBIGUOUS_DIRS) or rel in AMBIGUOUS_EXCLUDE:
            continue
        for no, line in body_lines(path):
            if "open_specs" in line or "balance_backlog" in line:  # 管理台帳への誘導行は正当な言及
                continue
            m = AMBIGUOUS.search(line)
            if m:
                errors.append(f"ERROR {rel}:{no}: 曖昧語「{m.group()}」が残っている（未確定なら open_specs.md へ登録する）")
    return errors


# 決定先送り（doc-review ISSUE-801/803 の検出パターンを常設化）
# 「定義する」「追記する」「行う」は選択肢が未決なのではなく後工程で書くだけなので対象外
PENDING = re.compile(r"Phase\s*[0-9]+\s*の(基本設計|詳細設計)で.{0,40}(確定|決める|決定)")
PENDING_DIRS = ("docs/",)
PENDING_EXCLUDE = ("docs/backlog/open_specs.md", "docs/backlog/balance_backlog.md")

LEDGER = ROOT / "docs" / "backlog" / "open_specs.md"
# 台帳の存否を事実として断定するパターン（「不在なら」「不在＝」等の条件文は対象外）
LEDGER_ABSENT = re.compile(r"(現在は不在|ため不在|ため現在は不在|全解消済み|未確定ゼロのため)")
LEDGER_PRESENT = re.compile(r"(現在|現時点).{0,8}([0-9]+\s*(件|項目)|実在)")


def check_pending(files: list[Path]) -> list[str]:
    """決定先送りの行が台帳へリンクしているか（走査対象: docs/**）。"""
    errors = []
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        if not rel.startswith(PENDING_DIRS) or rel in PENDING_EXCLUDE:
            continue
        for no, line in body_lines(path):
            if not PENDING.search(line):
                continue
            if "open_specs" in line or "balance_backlog" in line:
                continue
            errors.append(f"ERROR {rel}:{no}: 決定先送りが台帳へリンクしていない（open_specs.md へ登録し、行内でリンクする）")
    return errors


def check_ledger(files: list[Path]) -> list[str]:
    """open_specs.md の実在と、本文の存否の断定の整合。"""
    errors = []
    exists = LEDGER.exists()
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        if rel == "docs/backlog/open_specs.md":
            continue
        for no, line in body_lines(path):
            if "open_specs" not in line:
                continue
            if exists and LEDGER_ABSENT.search(line):
                errors.append(f"ERROR {rel}:{no}: open_specs.md は実在するのに不在と断定している")
            elif not exists and LEDGER_PRESENT.search(line):
                errors.append(f"ERROR {rel}:{no}: open_specs.md は不在なのに実在を前提にしている")
    return errors


def parse_ownership() -> list[tuple[str, str, set[str], re.Pattern]]:
    """spec_ownership.md から (トピック, 正, 許可, パターン) を読む。検出パターン列が空の行は対象外。"""
    if not OWNERSHIP.exists():
        return []
    rows = []
    for _, line in body_lines(OWNERSHIP):
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 4 or cells[0] in ("トピック", "") or set(cells[0]) <= {"-", " ", ":"}:
            continue
        topic, canonical, allowed, pattern = cells[0], cells[1], cells[2], cells[3]
        pattern = pattern.strip("`").strip()
        if not pattern or pattern in ("—", "-"):
            continue
        canonical = canonical.strip("`")
        allow = {canonical} | {a.strip().strip("`") for a in allowed.split(",") if a.strip().strip("`") not in ("—", "-", "")}
        rows.append((topic, canonical, allow, re.compile(pattern)))
    return rows


def check_ownership(files: list[Path]) -> list[str]:
    errors = []
    rules = parse_ownership()
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        if not rel.startswith(("docs/design/", "docs/tech/", "docs/data/", ".claude/")) and rel != "CLAUDE.md":
            continue
        text = path.read_text(encoding="utf-8")
        for topic, canonical, allow, rx in rules:
            if rel not in allow and rx.search(text):
                errors.append(f"ERROR {rel}: 「{topic}」の記載は {canonical} が正（重複させずリンクする。docs/process/spec_ownership.md）")
    return errors


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):  # pragma: no cover - 実行環境依存
        pass

    args = sys.argv[1:]
    files = targets()
    checks = {
        "--links": ("リンク", check_links),
        "--reach": ("索引到達性", check_reachability),
        "--words": ("曖昧語", check_ambiguous),
        "--owner": ("正の逸脱", check_ownership),
        "--pending": ("決定先送り", check_pending),
        "--ledger": ("台帳存否", check_ledger),
    }
    selected = [k for k in checks if k in args] or list(checks)

    total = 0
    for key in selected:
        label, fn = checks[key]
        errors = fn(files)
        total += len(errors)
        for e in errors:
            print(e)
        print(f"[{label}] {'OK' if not errors else f'{len(errors)} 件'}")

    print(f"\n{len(files)} files checked: {'違反なし' if total == 0 else f'{total} 件の違反'}")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
