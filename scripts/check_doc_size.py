#!/usr/bin/env python3
"""ドキュメント文字数チェッカー

docs/process/documentation_rules.md で定めた文字数上限を検証する。

使い方:
    python scripts/check_doc_size.py             # 判定（未登録の上限超過・変更履歴があれば exit 1）
    python scripts/check_doc_size.py --list      # 全ファイルの文字数一覧
    python scripts/check_doc_size.py --sections  # H2セクションの文字数超過も表示
    python scripts/check_doc_size.py --sections <path>...
                                                 # 指定ファイルの全H2の文字数（測定モード。判定なし・常に exit 0）
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

# ── 一括是正の台帳（documentation_rules.md §7）──
# 新規の上限超過（区分B・C）は即時是正せず、是正方針を1行添えてここへ登録する。
# 登録済みは WARN（exit 0）となり、一括是正タスクで解消したら行を削除する。
# 区分A・Dは登録不可（毎ターン・スキル起動ごとに読み込まれ放置コストが複利のため、セッション内に移管優先で是正）。
# Phase完了ゲートでは本台帳が空であること。
# 例: "docs/tech/basic/tech_data.md": "分割（レイヤー分割: スキーマ/JSON構造）",
KNOWN_OVERSIZED: dict[str, str] = {
    # 索引が満杯（登録前で 5,995字）。コーディング規約の登録で 77字 超過した。
    "README.md": "圧縮（ドキュメント索引の説明文を削るか、索引テーブルを docs/INDEX.md へ分離）",
}

# 台帳登録を許す区分（documentation_rules.md §7。区分A・Dはセッション内是正）
DEFERRABLE_ZONES = frozenset({"B 索引", "C 仕様・設計"})


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


def sections_of(rel: str) -> list[tuple[str, int]]:
    """H2 セクションの (見出し, 文字数) を出現順に返す。コードブロック内の `## ` は見出しにしない。

    文字数は見出し行を含まない本文のみ（`## ` 行の長さは「前文・見出し行」へ回る）。
    """
    text = (ROOT / rel).read_text(encoding="utf-8")
    result, heading, buf, in_code = [], None, [], False
    for line in text.splitlines(keepends=True):
        if line.lstrip().startswith("```"):
            in_code = not in_code
        if not in_code and line.startswith("## "):
            if heading is not None:
                result.append((heading, len("".join(buf))))
            heading, buf = line.strip(), []
        elif heading is not None:
            buf.append(line)
    if heading is not None:
        result.append((heading, len("".join(buf))))
    return result


def oversized_sections(rel: str) -> list[tuple[str, int]]:
    """上限を超えた H2 セクションの (見出し, 文字数) を返す。"""
    return [(h, c) for h, c in sections_of(rel) if c > SECTION_LIMIT]


def resolve_rel(target: str) -> str | None:
    """引数のパスをリポジトリ相対（POSIX）へ正規化する。範囲外・不在なら None。

    カレントディレクトリ相対とリポジトリルート相対の両方を受ける
    （`backend/` 等から呼んでも同じ引数で通るようにするため）。
    """
    for base in (Path.cwd(), ROOT):
        path = Path(target) if Path(target).is_absolute() else base / target
        try:
            rel = path.resolve().relative_to(ROOT).as_posix()
        except (ValueError, OSError):
            continue
        if (ROOT / rel).is_file():
            return rel
    return None


def report_sections(targets: list[str]) -> int:
    """指定ファイルの H2 別文字数を降順で出す測定モード（判定はせず常に 0 を返す）。

    痩身作業で「どのセクションを削れば上限に収まるか」を1回で決めるための出力。
    上限以下のセクションも省かずに出す（削減候補の比較に必要なため）。
    """
    for target in targets:
        rel = resolve_rel(target)
        if rel is None:
            print(f"ERROR {target}: リポジトリ内の実在ファイルとして解決できない")
            continue
        total = len((ROOT / rel).read_text(encoding="utf-8"))
        zone = zone_of(rel)
        limit = LIMITS[zone]
        rest = f"残り {limit - total:,}字" if total <= limit else f"{total - limit:,}字 超過"
        print(f"\n{rel}  {total:,}字 / 上限 {limit:,}字（区分{zone[0]}・{rest}）")
        sections = sections_of(rel)
        for heading, chars in sorted(sections, key=lambda s: -s[1]):
            mark = "!" if chars > SECTION_LIMIT else " "
            print(f"{chars:>7} {mark} {heading}")
        print(f"{total - sum(c for _, c in sections):>7}   （前文・H2見出し行）")
    return 0


def main() -> int:
    # Windows のコンソール（cp932）でも日本語を出力できるようにする
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):  # pragma: no cover - 実行環境依存
        pass

    args = sys.argv[1:]

    # `--sections <path>` は測定モード（指定ファイルのH2内訳のみ。判定は行わない）
    targets = [a for a in args if not a.startswith("--")]
    if "--sections" in args and targets:
        return report_sections(targets)

    rows = collect()

    if "--list" in args:
        print(f'{"chars":>7} {"limit":>6}  {"区分":<16} path')
        for rel, zone, chars, limit in sorted(rows, key=lambda r: -r[2]):
            mark = " " if chars <= limit else ("~" if rel in KNOWN_OVERSIZED else "!")
            print(f"{chars:>7} {limit:>6} {mark} {zone:<16} {rel}")
        print(f"\n{len(rows)} files, {sum(r[2] for r in rows):,} chars")
        return 0

    over = [r for r in rows if r[2] > r[3]]
    known = [r for r in over if r[0] in KNOWN_OVERSIZED and r[1] in DEFERRABLE_ZONES]
    errors = [r for r in over if r not in known]

    for rel, zone, chars, limit in known:
        print(f"WARN  {rel}: {chars:,}字 > {limit:,}字（区分{zone[0]}）- 台帳登録済み: {KNOWN_OVERSIZED[rel]}")

    for rel, zone, chars, limit in errors:
        over_by = chars - limit
        note = "（区分A・Dは台帳登録不可）" if rel in KNOWN_OVERSIZED else ""
        print(f"ERROR {rel}: {chars:,}字 > {limit:,}字（区分{zone[0]}）- {over_by:,}字 超過{note}")

    # 台帳に残っているが上限内に収まった（または削除された）ファイルは行を消させる
    stales = sorted(set(KNOWN_OVERSIZED) - {r[0] for r in over})
    for rel in stales:
        print(f"WARN  {rel}: 上限内 - 台帳（KNOWN_OVERSIZED）から行を削除する")

    # 上限の90%超は残量WARN（次の追記で超過し得る早期警戒。一括是正時の分割候補）
    nears = [r for r in rows if r[3] * 0.9 < r[2] <= r[3] and r[0] not in KNOWN_OVERSIZED]
    for rel, zone, chars, limit in nears:
        print(f"WARN  {rel}: {chars:,}字（上限{limit:,}字・残り{limit - chars:,}字）- 次の追記で超過し得る（一括是正の分割候補。§6・§7）")

    # 変更履歴は docs/changelog.md へ集約する（§5.1）。個別ファイルへの復活を禁止する
    histories = [(rel, no, head) for rel, _, _, _ in rows for no, head in history_sections(rel)]
    for rel, no, head in histories:
        print(f"ERROR {rel}:{no}: 変更履歴セクション「{head}」は置けない（§5.1）")

    if "--sections" in args:
        for rel, _, _, _ in rows:
            for heading, chars in oversized_sections(rel):
                print(f"WARN  {rel}: {heading} が {chars:,}字 > {SECTION_LIMIT:,}字")

    ok = len(rows) - len(errors) - len(known)
    print(f"\n{len(rows)} files checked: {ok} OK, {len(known)} 台帳登録, {len(errors)} 違反, 残量WARN {len(nears)}", end="")
    print(f", 変更履歴 {len(histories)} 件" if histories else "")

    if errors:
        print("→ 区分B・Cは KNOWN_OVERSIZED（台帳）へ是正方針つきで登録して一括是正へ、区分A・Dはセッション内に是正（documentation_rules.md §7）")
    if histories:
        print("→ 変更履歴は docs/changelog.md の先頭へ移すこと（documentation_rules.md §5.1）")
    return 1 if errors or histories else 0


if __name__ == "__main__":
    sys.exit(main())
