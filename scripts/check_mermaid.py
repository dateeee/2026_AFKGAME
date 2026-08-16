#!/usr/bin/env python3
"""Mermaid 構造チェック（フェンス・ブロック対応・波括弧・ER図リレーション）

diagrams-review 2026-08-11 の「プロセスへの還元」2 に従い、レビューのたびに
書き直していた使い捨てチェッカーを常設化したもの（script-conventions.md §1）。
使い捨て版が2度誤検知した2点は除外規則として本体に持たせている。

    1. `participant` を `par` ブロックとして数える誤り
       → ブロック開始語は語境界つきで判定する（`par` は単独トークンのときだけ）
    2. ER図のカーディナリティ `||--o{` `}o--||` を波括弧として数える誤り
       → リレーション記法のトークンを取り除いてから波括弧を数える

検証項目:
    1. フェンス     ```mermaid の閉じ漏れ
    2. ブロック     subgraph / loop / alt / opt / par / critical / break / rect / box と end の対応
    3. 波括弧       エンティティ・クラス定義ブロックの { } の対応
    4. リレーション ER図のリレーション両端が、いずれかの ER図でエンティティとして定義済みか

使い方:
    python scripts/check_mermaid.py             # 全検証（ERROR があれば exit 1）
    python scripts/check_mermaid.py --fence     # フェンスのみ
        （--blocks / --braces / --relations も同様）
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# 検査対象外（check_docs.py と同基準。追記型アーカイブは当時の記述を保持する）
EXCLUDE = (
    "node_modules/",
    ".git/",
    ".claude/worktrees/",
    ".venv/",
    "venv/",
    "dist/",
    "docs/reviews/",
    "docs/changelog.md",
)

FENCE = re.compile(r"^\s*```")
MERMAID_FENCE = re.compile(r"^\s*```\s*mermaid\s*$", re.IGNORECASE)

# ブロック開始語。`participant` を `par` と誤認しないよう語境界を要求する
BLOCK_OPEN = re.compile(r"^(subgraph|loop|alt|opt|par|critical|break|rect|box)\b")
BLOCK_END = re.compile(r"^end\b")

# ER図のカーディナリティ（例: ||--o{ 、}o--|| 、||..o| ）。波括弧の計数前に取り除く
ER_CARDINALITY = re.compile(r"(\|\||\|o|\}\||\}o)(--|\.\.)(\|\||o\||\|\{|o\{)")
ER_RELATION = re.compile(
    r"^(?P<left>[A-Za-z][\w-]*)\s+"
    r"(?P<card>(\|\||\|o|\}\||\}o)(--|\.\.)(\|\||o\||\|\{|o\{))\s+"
    r"(?P<right>[A-Za-z][\w-]*)\s*:"
)
ER_ENTITY_OPEN = re.compile(r"^(?P<name>[A-Za-z][\w-]*)\s*\{$")


@dataclass
class Fence:
    """1つの ```mermaid ブロック。"""

    src: str
    start: int  # ```mermaid の行番号
    closed: bool = False
    kind: str = ""  # erDiagram / sequenceDiagram など宣言行の1語目
    lines: list[tuple[int, str]] = field(default_factory=list)  # (行番号, 本文)


def targets() -> list[Path]:
    result = []
    for path in sorted(ROOT.rglob("*.md")):
        rel = path.relative_to(ROOT).as_posix()
        if any(rel.startswith(x) or f"/{x}" in f"/{rel}" for x in EXCLUDE):
            continue
        result.append(path)
    return result


def parse_fences(files: list[Path]) -> list[Fence]:
    """全ファイルから ```mermaid ブロックを取り出す（閉じ漏れも Fence として返す）。"""
    fences: list[Fence] = []
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        current: Fence | None = None
        in_other = False  # mermaid 以外のコードフェンスの中か
        for no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if not FENCE.match(line):
                if current is not None:
                    body = line.strip()
                    if body:
                        current.lines.append((no, body))
                continue
            if current is not None:  # mermaid フェンスの閉じ
                current.closed = True
                fences.append(current)
                current = None
            elif in_other:
                in_other = False
            elif MERMAID_FENCE.match(line):
                current = Fence(src=rel, start=no)
            else:
                in_other = True
        if current is not None:  # 閉じ漏れ
            fences.append(current)
    for f in fences:
        # 図種は最初の非ディレクティブ行の1語目（`%%{init: ...}%%` は読み飛ばす）
        f.kind = next((b.split()[0] for _, b in f.lines if not b.startswith("%%")), "")
    return fences


def check_fence(fences: list[Fence]) -> list[str]:
    """```mermaid の閉じ漏れ。"""
    return [
        f"ERROR {f.src}:{f.start}: ```mermaid が閉じられていない（対応する ``` が無い）"
        for f in fences
        if not f.closed
    ]


def check_blocks(fences: list[Fence]) -> list[str]:
    """subgraph / loop / alt / opt / par などのブロックと end の対応。"""
    errors = []
    for f in fences:
        stack: list[tuple[int, str]] = []
        for no, body in f.lines:
            m = BLOCK_OPEN.match(body)
            if m:
                stack.append((no, m.group(1)))
                continue
            if BLOCK_END.match(body):
                if stack:
                    stack.pop()
                else:
                    errors.append(f"ERROR {f.src}:{no}: 対応する開始行の無い end")
        for no, word in stack:
            errors.append(f"ERROR {f.src}:{no}: {word} に対応する end が無い")
    return errors


def check_braces(fences: list[Fence]) -> list[str]:
    """エンティティ・クラス定義ブロックの波括弧の対応（カーディナリティは除外）。"""
    errors = []
    for f in fences:
        depth = 0
        opened: list[int] = []
        for no, body in f.lines:
            stripped = ER_CARDINALITY.sub("", body)
            for ch in stripped:
                if ch == "{":
                    depth += 1
                    opened.append(no)
                elif ch == "}":
                    if depth == 0:
                        errors.append(f"ERROR {f.src}:{no}: 対応する {{ の無い }}")
                    else:
                        depth -= 1
                        opened.pop()
        for no in opened:
            errors.append(f"ERROR {f.src}:{no}: {{ に対応する }} が無い")
    return errors


def er_entities(fences: list[Fence]) -> set[str]:
    """全 ER図で定義済みのエンティティ名（子ファイル分割のためファイル横断で集める）。"""
    names: set[str] = set()
    for f in fences:
        if f.kind != "erDiagram":
            continue
        for _, body in f.lines:
            m = ER_ENTITY_OPEN.match(ER_CARDINALITY.sub("", body).strip())
            if m:
                names.add(m.group("name"))
    return names


def check_relations(fences: list[Fence]) -> list[str]:
    """ER図のリレーション両端が、いずれかの ER図で定義済みか。"""
    defined = er_entities(fences)
    errors = []
    for f in fences:
        if f.kind != "erDiagram":
            continue
        for no, body in f.lines:
            m = ER_RELATION.match(body)
            if not m:
                continue
            for side in ("left", "right"):
                name = m.group(side)
                if name not in defined:
                    errors.append(
                        f"ERROR {f.src}:{no}: リレーションの {name} が"
                        f" どの ER図にもエンティティとして定義されていない"
                    )
    return errors


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):  # pragma: no cover - 実行環境依存
        pass

    args = sys.argv[1:]
    files = targets()
    fences = parse_fences(files)

    checks = {
        "--fence": ("フェンス", check_fence),
        "--blocks": ("ブロック", check_blocks),
        "--braces": ("波括弧", check_braces),
        "--relations": ("ERリレーション", check_relations),
    }
    selected = [k for k in checks if k in args] or list(checks)

    total = 0
    for key in selected:
        label, fn = checks[key]
        errors = fn(fences)
        total += len(errors)
        for e in errors:
            print(e)
        print(f"[{label}] {'OK' if not errors else f'{len(errors)} 件'}")

    n_files = len({f.src for f in fences})
    print(f"\n{len(fences)} 図（{n_files} ファイル）: {'違反なし' if total == 0 else f'{total} 件の違反'}")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
