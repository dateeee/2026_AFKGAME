#!/usr/bin/env python3
"""ドキュメント文字数チェッカー

docs/documentation_rules.md で定めた文字数上限を検証する。

使い方:
    python scripts/check_doc_size.py             # 判定（新規の上限超過があれば exit 1）
    python scripts/check_doc_size.py --list      # 全ファイルの文字数一覧
    python scripts/check_doc_size.py --sections  # H2セクションの文字数超過も表示
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# ── 区分ごとの上限（documentation_rules.md §3）──
LIMITS = {
    "A 常時読込": 3000,
    "B 索引": 6000,
    "C 仕様・設計": 8000,
    "D エージェント定義": 5000,
}

# H2セクションの上限（documentation_rules.md §4）
SECTION_LIMIT = 2000

EXCLUDE = (
    "node_modules/",
    ".git/",
    ".claude/worktrees/",  # エージェント用の作業コピー（リポジトリの複製であり成果物ではない）
    ".venv/",
    "venv/",
    "dist/",
    "docs/reviews/",
    "docs/changelog.md",  # 追記型アーカイブ（documentation_rules.md §2・§5.1）
)

# 変更履歴セクションの見出し（documentation_rules.md §5.1 により各ファイルへの記載は禁止）
HISTORY_HEADING = re.compile(r"^#{1,6}\s*(?:\d+(?:\.\d+)*\.?\s*)?(?:変更|更新|改訂)履歴\s*$")

# ── 既知の上限超過（documentation_rules.md §8 の是正計画対象）──
# 是正計画に沿って分割が完了したファイルはこの集合から削除する。
# 2026-08-02 に全8ファイルの分割が完了したため現在は空。新規の超過はすべて ERROR となる。
KNOWN_OVERSIZED: set[str] = set()


def zone_of(rel: str) -> str:
    """ファイルパスから区分を判定する。"""
    if rel == "CLAUDE.md":
        return "A 常時読込"
    if rel == "README.md" or rel.endswith("_OVERVIEW.md"):
        return "B 索引"
    if rel.startswith(".claude/"):
        return "D エージェント定義"
    return "C 仕様・設計"


def collect() -> list[tuple[str, str, int, int]]:
    """(相対パス, 区分, 文字数, 上限) の一覧を返す。"""
    rows = []
    for path in sorted(ROOT.rglob("*.md")):
        rel = path.relative_to(ROOT).as_posix()
        if any(rel.startswith(x) or f"/{x}" in f"/{rel}" for x in EXCLUDE):
            continue
        text = path.read_text(encoding="utf-8")
        zone = zone_of(rel)
        rows.append((rel, zone, len(text), LIMITS[zone]))
    return rows


def history_sections(rel: str) -> list[tuple[int, str]]:
    """変更履歴セクションの (行番号, 見出し) を返す。コードブロック内は除く。"""
    result, in_code = [], False
    for no, line in enumerate((ROOT / rel).read_text(encoding="utf-8").splitlines(), 1):
        if line.lstrip().startswith("```"):
            in_code = not in_code
        elif not in_code and HISTORY_HEADING.match(line.strip()):
            result.append((no, line.strip()))
    return result


def oversized_sections(rel: str) -> list[tuple[str, int]]:
    """上限を超えた H2 セクションの (見出し, 文字数) を返す。"""
    text = (ROOT / rel).read_text(encoding="utf-8")
    result, heading, buf, in_code = [], None, [], False
    for line in text.splitlines(keepends=True):
        if line.lstrip().startswith("```"):
            in_code = not in_code
        if not in_code and line.startswith("## "):
            if heading is not None and len("".join(buf)) > SECTION_LIMIT:
                result.append((heading, len("".join(buf))))
            heading, buf = line.strip(), []
        elif heading is not None:
            buf.append(line)
    if heading is not None and len("".join(buf)) > SECTION_LIMIT:
        result.append((heading, len("".join(buf))))
    return result


def main() -> int:
    # Windows のコンソール（cp932）でも日本語を出力できるようにする
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):  # pragma: no cover - 実行環境依存
        pass

    args = sys.argv[1:]
    rows = collect()

    if "--list" in args:
        print(f'{"chars":>7} {"limit":>6}  {"区分":<16} path')
        for rel, zone, chars, limit in sorted(rows, key=lambda r: -r[2]):
            mark = " " if chars <= limit else ("~" if rel in KNOWN_OVERSIZED else "!")
            print(f"{chars:>7} {limit:>6} {mark} {zone:<16} {rel}")
        print(f"\n{len(rows)} files, {sum(r[2] for r in rows):,} chars")
        return 0

    errors = [r for r in rows if r[2] > r[3] and r[0] not in KNOWN_OVERSIZED]
    known = [r for r in rows if r[2] > r[3] and r[0] in KNOWN_OVERSIZED]

    for rel, zone, chars, limit in known:
        print(f"WARN  {rel}: {chars:,}字 > {limit:,}字（区分{zone[0]}）- 是正計画あり")

    for rel, zone, chars, limit in errors:
        over = chars - limit
        print(f"ERROR {rel}: {chars:,}字 > {limit:,}字（区分{zone[0]}）- {over:,}字 超過")

    # 変更履歴は docs/changelog.md へ集約する（§5.1）。個別ファイルへの復活を禁止する
    histories = [(rel, no, head) for rel, _, _, _ in rows for no, head in history_sections(rel)]
    for rel, no, head in histories:
        print(f"ERROR {rel}:{no}: 変更履歴セクション「{head}」は置けない（§5.1）")

    if "--sections" in args:
        for rel, _, _, _ in rows:
            for heading, chars in oversized_sections(rel):
                print(f"WARN  {rel}: {heading} が {chars:,}字 > {SECTION_LIMIT:,}字")

    ok = len(rows) - len(errors) - len(known)
    print(f"\n{len(rows)} files checked: {ok} OK, {len(known)} 既知超過, {len(errors)} 違反", end="")
    print(f", 変更履歴 {len(histories)} 件" if histories else "")

    if errors:
        print("→ docs/documentation_rules.md §6 の分割パターンに従って分割すること")
    if histories:
        print("→ 変更履歴は docs/changelog.md の先頭へ移すこと（documentation_rules.md §5.1）")
    return 1 if errors or histories else 0


if __name__ == "__main__":
    sys.exit(main())
