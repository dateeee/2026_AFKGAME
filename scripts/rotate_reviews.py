#!/usr/bin/env python3
"""レビューレポートのローテーション

`docs/reviews/<スキル名>/` 直下に残す件数を最新 RECENT 件に保ち、
それより古いレポートを `<スキル名>/archive/` へ移す。**削除はしない**。

一覧を開いたときに視界へ入るファイル数を一定に保つのが目的で、
総量を減らすことは目的ではない（docs/process/documentation_rules.md §9）。

使い方:
    python scripts/rotate_reviews.py            # 退避対象の表示のみ（何も動かさない）
    python scripts/rotate_reviews.py --apply    # 実際に archive/ へ移す
    python scripts/rotate_reviews.py --list     # 全レポートの一覧と文字数
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REVIEWS = ROOT / "docs" / "reviews"

# 各スキルディレクトリの直下に残す件数（docs/process/documentation_rules.md §9）
RECENT = 10

ARCHIVE = "archive"

# レポートを持つディレクトリ（レビュー系スキル名と一致させる）
SLOTS = (
    "doc-review",
    "diagrams-review",
    "backend-review",
    "frontend-review",
    "full-review",
)


def reports(directory: Path) -> list[Path]:
    """レポートを新しい順に返す。

    ファイル名は `YYYY-MM-DD_HHMMSS.md` のため、辞書順の降順 = 新しい順。
    """
    if not directory.is_dir():
        return []
    return sorted(directory.glob("*.md"), key=lambda p: p.name, reverse=True)


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def move(src: Path, dest: Path) -> None:
    """Git の履歴を追えるよう `git mv` で移す（失敗したら通常の移動）。"""
    dest.parent.mkdir(parents=True, exist_ok=True)
    done = subprocess.run(
        ["git", "-C", str(ROOT), "mv", "--", rel(src), rel(dest)],
        capture_output=True,
    )
    if done.returncode != 0:
        src.rename(dest)


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):  # pragma: no cover - 実行環境依存
        pass

    args = sys.argv[1:]
    apply_ = "--apply" in args

    if "--list" in args:
        total = 0
        for slot in SLOTS:
            for directory in (REVIEWS / slot, REVIEWS / slot / ARCHIVE):
                for path in reports(directory):
                    chars = len(path.read_text(encoding="utf-8"))
                    total += chars
                    print(f"{chars:>7}  {rel(path)}")
        print(f"\n{total:,} chars")
        return 0

    # `docs/reviews/` 直下の置き忘れ（スキル名ディレクトリへ入っていないもの）
    for path in sorted(REVIEWS.glob("*.md")) if REVIEWS.is_dir() else []:
        print(f"WARN  {rel(path)}: スキル名のディレクトリへ移すこと")

    moved = 0
    for slot in SLOTS:
        for path in reports(REVIEWS / slot)[RECENT:]:
            dest = REVIEWS / slot / ARCHIVE / path.name
            print(f"{'ARCHIVE' if apply_ else 'TO ARCHIVE'} {rel(path)} → {ARCHIVE}/")
            if apply_:
                move(path, dest)
            moved += 1

    visible = sum(len(reports(REVIEWS / slot)) for slot in SLOTS)
    archived = sum(len(reports(REVIEWS / slot / ARCHIVE)) for slot in SLOTS)
    print(f"\n直下 {visible} 件（各ディレクトリ最新{RECENT}件まで）, archive/ {archived} 件")
    if moved and not apply_:
        print("→ 退避するには --apply を付けて再実行する")
    return 0


if __name__ == "__main__":
    sys.exit(main())
