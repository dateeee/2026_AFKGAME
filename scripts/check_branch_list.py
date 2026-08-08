#!/usr/bin/env python3
"""分岐一覧の構造検証と、テストとの対応照合

分岐一覧（詳細設計の必須成果物・テストとC1網羅の唯一の導出元）を機械検証する。

検証項目（既定）:
    1. 行番号   # 列が 1 から連番で、重複がない
    2. 空セル   条件・期待する振る舞いが空でない
    3. 両側網羅 標準形式（分岐点・条件列あり）で、同一分岐点に行が1つしかない場合は WARN
                （真偽の片側欠落の可能性。例外経路なら許容されるため ERROR にはしない）
    4. ループ   分岐点に「ループ」を含む、または周回数（`0周` のような数字+周）を書いた
                グループに 0周・1周・2周 が揃っているか（WARN）

WARN の抑止（`WARN許容`）:
    条件分岐を持たない単一経路・例外経路・回数固定のループは、表の直後に理由つきの注記を置くと
    3・4 の WARN を出さない。書式: `> WARN許容 #21・#22: <理由>`（`#` 番号はコロンの前に列挙する）

テスト対応照合（--tests）:
    テストの docstring / Javadoc / コメントにあるマーカーを集計し、分岐一覧の行と突き合わせる。
    対象は backend/tests/unit/*.py（Python）と backend/*/src/test/java/**/*Test.java（Java）。
    マーカー形式: 「分岐: tech_shop.md §7 #3」（節番号は §8.3 のような枝番も可。
    一覧が1つだけの文書は §番号 を省略可。#3,4 と複数可）
    マーカーが1件も参照していない文書・セクションは照合対象外（レガシーテストに影響しない）。

使い方:
    python scripts/check_branch_list.py             # 構造検証（ERROR があれば exit 1）
    python scripts/check_branch_list.py --tests     # 構造検証 + テスト対応照合
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TECH_DIR = ROOT / "docs" / "tech"
PY_TEST_DIR = ROOT / "backend" / "tests" / "unit"
JAVA_TEST_GLOB = "backend/*/src/test/java/**/*Test.java"

HEADING = re.compile(r"^(#{2,4})\s*(?:(\d+(?:\.\d+)*)\.?\s*)?分岐一覧")
MARKER = re.compile(r"分岐[:：]\s*(\S+?\.md)(?:\s*§(\d+(?:\.\d+)*))?\s*#([0-9,\s#]+)")
ALLOW_WARN = re.compile(r"WARN許容\s*([#0-9,、・\s]+)[:：]")
LAP_COUNT = re.compile(r"\d+\s*周")


def parse_tables() -> dict[tuple[str, str], dict]:
    """{(ファイル名, セクション番号): {rows, structured, allow_warn, path, line}} を返す。"""
    sections = {}
    for path in sorted(TECH_DIR.rglob("tech_*.md")):
        lines = path.read_text(encoding="utf-8").splitlines()
        current, header, in_code = None, None, False
        for no, line in enumerate(lines, 1):
            if line.lstrip().startswith("```"):
                in_code = not in_code
                continue
            if in_code:
                continue
            m = HEADING.match(line)
            if m:
                key = (path.name, m.group(2) or "")
                current = {"rows": [], "structured": False, "allow_warn": set(), "path": path.name, "line": no}
                sections[key] = current
                header = None
                continue
            if line.startswith("#"):
                current = None
                continue
            if current is not None and line.startswith(">"):
                allow = ALLOW_WARN.search(line)
                if allow:
                    current["allow_warn"].update(int(n) for n in re.findall(r"\d+", allow.group(1)))
                continue
            if current is None or not line.startswith("|"):
                continue
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if header is None:
                header = cells
                current["structured"] = "分岐点" in cells and "条件" in cells
                continue
            if set("".join(cells)) <= {"-", ":", " "}:
                continue
            current["rows"].append((no, cells))
    return sections


def check_structure(sections) -> tuple[list[str], list[str]]:
    errors, warns = [], []
    for (fname, sec), data in sections.items():
        label = f"{fname} §{sec}" if sec else fname
        rows = data["rows"]
        if not rows:
            errors.append(f"ERROR {label}: 分岐一覧の見出しはあるが表がない（{fname}:{data['line']}）")
            continue
        numbers = []
        for no, cells in rows:
            if any(c in ("", "—", "-") for c in cells):
                errors.append(f"ERROR {label}: 空セルがある（{fname}:{no}）")
            try:
                numbers.append(int(cells[0]))
            except ValueError:
                errors.append(f"ERROR {label}: # 列が数値でない（{fname}:{no}: {cells[0]!r}）")
        if numbers and numbers != list(range(1, len(numbers) + 1)):
            errors.append(f"ERROR {label}: # 列が 1 からの連番でない（{numbers}）")
        if data["structured"]:
            groups: dict[str, list[list[str]]] = {}
            for _, cells in rows:
                groups.setdefault(cells[1], []).append(cells)
            allowed = data["allow_warn"]
            for point, grp in groups.items():
                # 注記（`> WARN許容 #N: 理由`）で全行が指定されたグループは網羅を求めない
                if allowed and all(cells[0].isdigit() and int(cells[0]) in allowed for cells in grp):
                    continue
                joined = " ".join(c for cells in grp for c in cells)
                if "ループ" in point or LAP_COUNT.search(joined):
                    missing = [w for w in ("0周", "1周", "2周") if w not in joined]
                    if missing:
                        warns.append(f"WARN  {label}: ループ「{point}」に {'・'.join(missing)} の行がない")
                elif len(grp) == 1:
                    warns.append(f"WARN  {label}: 分岐点「{point}」が1行のみ（真偽の片側欠落でないか確認。例外経路なら許容）")
    return errors, warns


def test_files() -> list[Path]:
    """マーカーを探すテストファイル（Python のレガシーテストと Java のテスト）。"""
    files = sorted(PY_TEST_DIR.glob("test_*.py")) if PY_TEST_DIR.exists() else []
    return files + sorted(ROOT.glob(JAVA_TEST_GLOB))


def check_tests(sections) -> list[str]:
    errors = []
    covered: dict[tuple[str, str], set[int]] = {}
    per_file_sections: dict[str, list[str]] = {}
    for fname, sec in sections:
        per_file_sections.setdefault(fname, []).append(sec)

    for test in test_files():
        for m in MARKER.finditer(test.read_text(encoding="utf-8")):
            fname, sec, nums = m.group(1), m.group(2) or "", m.group(3)
            if fname not in per_file_sections:
                errors.append(f"ERROR {test.name}: マーカーの参照先 {fname} に分岐一覧がない")
                continue
            if sec == "" and len(per_file_sections[fname]) > 1:
                errors.append(f"ERROR {test.name}: {fname} は分岐一覧が複数ある。§番号で特定する（{per_file_sections[fname]}）")
                continue
            if sec == "":
                sec = per_file_sections[fname][0]
            if (fname, sec) not in sections:
                errors.append(f"ERROR {test.name}: {fname} §{sec} の分岐一覧が見つからない")
                continue
            ids = {int(n) for n in re.findall(r"\d+", nums)}
            covered.setdefault((fname, sec), set()).update(ids)

    for key, ids in covered.items():
        fname, sec = key
        label = f"{fname} §{sec}" if sec else fname
        valid = {int(cells[0]) for _, cells in sections[key]["rows"] if cells[0].isdigit()}
        for n in sorted(ids - valid):
            errors.append(f"ERROR {label}: テストが存在しない行 #{n} を参照している")
        for n in sorted(valid - ids):
            errors.append(f"ERROR {label}: 行 #{n} に対応するテストがない（マーカー「分岐: {fname} §{sec} #{n}」を持つテストを書く）")
    return errors


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):  # pragma: no cover - 実行環境依存
        pass

    sections = parse_tables()
    errors, warns = check_structure(sections)
    if "--tests" in sys.argv[1:]:
        errors += check_tests(sections)

    for w in warns:
        print(w)
    for e in errors:
        print(e)

    structured = sum(1 for d in sections.values() if d["structured"])
    print(f"\n分岐一覧 {len(sections)} 件（標準形式 {structured} / 旧形式 {len(sections) - structured}）: "
          f"{'違反なし' if not errors else f'{len(errors)} 件の違反'}, WARN {len(warns)} 件")
    if len(sections) - structured:
        print("→ 旧形式（分岐点・条件列なし）は改稿時に標準形式へ移行する（.claude/project/detail-design.md §4）")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
