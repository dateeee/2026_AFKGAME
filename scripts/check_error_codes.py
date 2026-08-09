#!/usr/bin/env python3
"""エラーコード一致チェック（仕様 ↔ Web層のコード→ステータス対応表）

正は docs/tech/basic/tech_error_handling.md（コード体系と HTTP ステータス）。
実装側の `ErrorCatalog` はその写しであり、規約
docs/process/coding_standards_backend/exception.md §4 #5 が「手で同期させず機械照合する」
ことを求めている。本スクリプトがその照合を担う。

    仕様  docs/tech/basic/tech_error_handling.md
    実装  backend/afkgame-web/src/main/java/com/afkgame/web/filter/ErrorCatalog.java

検証項目:
    1. 欠落      仕様の表にあって対応表に無いコード（＝実装すると 422 へ倒れてしまう）
    2. 余剰      対応表にあって仕様のどこにも書かれていないコード
    3. ステータス 仕様の HTTP 列と対応表の Entry のステータスの不一致

仕様が「コードごとの HTTP ステータス」を表で持つのは現時点で「AUTH_ コード一覧」だけである。
ほかのプレフィックス（BATTLE_・SHOP_ 等）は例示にとどまるため、項目1・3 は
ステータス列を持つ表のみを対象とし、それ以外のコードは項目2（仕様への出現）だけを見る。
新しい領域の一覧表が仕様へ追加されれば、その表も自動的に項目1・3 の対象になる。

使い方:
    python scripts/check_error_codes.py           # 全検証（ERROR があれば exit 1）
    python scripts/check_error_codes.py --summary # 解析できた件数だけを表示
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "docs" / "tech" / "basic" / "tech_error_handling.md"
CATALOG = (
    ROOT / "backend" / "afkgame-web" / "src" / "main" / "java" / "com" / "afkgame" / "web"
    / "filter" / "ErrorCatalog.java"
)

# 仕様の表: | `AUTH_TOKEN_EXPIRED` | 401 | 発生条件 |
SPEC_ROW = re.compile(r"^\|\s*`([A-Z][A-Z0-9_]*)`\s*\|\s*(\d{3})\s*\|")
# 仕様の本文に現れるコード（項目2 用。表・箇条書き・コード片のどこでもよい）
SPEC_CODE = re.compile(r"`([A-Z][A-Z0-9]*_[A-Z0-9_]+)`")

# 実装の定数: private static final String INVALID_TOKEN = "AUTH_INVALID_TOKEN";
JAVA_CONST = re.compile(r"(?:static\s+final\s+String)\s+([A-Za-z_]\w*)\s*=\s*\"([^\"]+)\"\s*;")
# 実装の表: Map.entry("AUTH_TOKEN_EXPIRED", new Entry(401, ...)) / Map.entry(INTERNAL_ERROR, new Entry(500, ...))
JAVA_ENTRY = re.compile(
    r"Map\.entry\(\s*(?:\"([A-Z][A-Z0-9_]*)\"|([A-Za-z_]\w*))\s*,\s*new\s+Entry\(\s*(\d{3})\s*,"
)


def read(path: Path) -> str:
    if not path.exists():
        sys.exit(f"ERROR: 対象が見つからない: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def parse_spec(text: str) -> tuple[dict[str, int], set[str]]:
    """ステータス付きの表から {コード: ステータス} を、本文全体から出現コードの集合を作る。"""
    table: dict[str, int] = {}
    for line in text.splitlines():
        matched = SPEC_ROW.match(line)
        if matched:
            table[matched.group(1)] = int(matched.group(2))
    mentioned = set(SPEC_CODE.findall(text))
    return table, mentioned


def parse_catalog(text: str) -> tuple[dict[str, int], list[str]]:
    """ErrorCatalog の対応表から {コード: ステータス} を作る（定数参照は解決する）。

    解決できないキーは黙って落とさず、呼び出し元へ返して検証を失敗させる
    （落とすと「照合対象に無い＝一致」に見えてしまうため）。
    """
    constants = dict(JAVA_CONST.findall(text))
    entries: dict[str, int] = {}
    problems: list[str] = []
    for literal, name, status in JAVA_ENTRY.findall(text):
        code = literal or constants.get(name)
        if code is None:
            problems.append(f"対応表のキー {name} を解決できない（同じファイル内の定数にする）")
            continue
        entries[code] = int(status)
    return entries, problems


def main() -> int:
    summary_only = "--summary" in sys.argv

    spec_table, spec_mentioned = parse_spec(read(SPEC))
    catalog, unresolved = parse_catalog(read(CATALOG))

    if not spec_table or not catalog:
        print("ERROR: 仕様または対応表を解析できなかった（書式が変わっていないか確認する）")
        return 1

    if summary_only:
        print(f"仕様（ステータス付き）: {len(spec_table)}件 / 仕様の出現コード: {len(spec_mentioned)}件")
        print(f"対応表: {len(catalog)}件 / 解決できないキー: {len(unresolved)}件")
        return 0

    errors: list[str] = list(unresolved)

    for code, status in sorted(spec_table.items()):
        if code not in catalog:
            errors.append(f"欠落: {code}（仕様は {status}）が ErrorCatalog に無い")
        elif catalog[code] != status:
            errors.append(f"ステータス不一致: {code} 仕様={status} 対応表={catalog[code]}")

    for code in sorted(catalog):
        if code not in spec_table and code not in spec_mentioned:
            errors.append(f"余剰: {code} が仕様（{SPEC.name}）に無い")

    for message in errors:
        print(f"ERROR: {message}")

    if errors:
        print(f"\n{len(errors)}件の不一致（正は {SPEC.relative_to(ROOT)}）")
        return 1

    print(f"OK: エラーコード {len(catalog)}件が仕様と一致（うちステータス照合 {len(spec_table)}件）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
