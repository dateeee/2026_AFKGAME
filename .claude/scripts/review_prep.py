#!/usr/bin/env python3
"""レビュー実行前の準備（モード判定・差分特定・ISSUE採番・雛形生成）

レビュー系スキルが共通で使う。決定的に決まる手順を LLM の手作業から外すのが目的。
**プロジェクト固有の値は一切持たない**（保存先・対象パス・タイトル・カテゴリはすべて引数）。

やること:
    (a) 保存先直下の最新レポートを特定し、差分 / 全量モードを判定する
    (b) 最新レポートに記録された HEAD SHA からの `git diff` で変更ファイルを特定する
        （タイムスタンプ方式の TZ差・rebase 取りこぼしを避けるため SHA 基準を優先する）
    (c) 保存先の全レポート（archive/ を含む）を走査し、次の ISSUE 開始番号を算出する
    (d) レポート雛形（HEAD SHA 記録つき）を生成する

使い方:
    python .claude/scripts/review_prep.py --dir docs/reviews/doc-review \\
        --paths docs --title 仕様レビュー結果 --categories "整合性 / 網羅性 / 規約"
    python .claude/scripts/review_prep.py --dir docs/reviews/doc-review --paths docs --full
    python .claude/scripts/review_prep.py --dir docs/reviews/doc-review --paths docs --init

オプション:
    --dir DIR          保存先ディレクトリ（必須。プロファイル §0 のレビューパラメータ）
    --paths P [P ...]  レビュー対象パス（省略時はリポジトリ全体）
    --exclude P [P ...] 変更ファイルから除外するパス接頭辞（既定: 保存先の親 = レポート置き場）
    --full             全量モードを強制する
    --title T          レポートタイトル（雛形の見出し）
    --categories C     カテゴリ（`/` または `,` 区切り。雛形のサマリー表）
    --init             雛形ファイルを実際に書き出す（省略時は生成予定パスの表示のみ）

出力は `KEY  値` 形式。そのまま取り込んで使う。
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path

# レポートのモード行に埋め込む HEAD SHA の記法（.claude/references/review-format.md）
HEAD_MARK = re.compile(r"HEAD:\s*([0-9a-f]{7,40})", re.IGNORECASE)

# レポートファイル名 `YYYY-MM-DD_HHMMSS.md`（辞書順の降順 = 新しい順）
REPORT_NAME = re.compile(r"^\d{4}-\d{2}-\d{2}_\d{6}\.md$")

ISSUE_NUM = re.compile(r"ISSUE-(\d+)")

ARCHIVE = "archive"

# 採番の粒度（前回の最大番号をこの単位で切り上げた次から採番する）
ISSUE_STEP = 100


def git(root: Path, *args: str) -> tuple[int, str]:
    """git を実行し `(終了コード, 標準出力)` を返す。

    `core.quotepath=false` は非ASCIIのパスが `\\346\\227\\245` 形式へ退化するのを防ぐため。
    """
    done = subprocess.run(
        ["git", "-C", str(root), "-c", "core.quotepath=false", *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    # 末尾だけを落とす。`git status --porcelain` の先頭列（` M path` の空白）は状態を表すため
    return done.returncode, (done.stdout or "").rstrip()


def repo_root() -> Path:
    """リポジトリのルート。git が使えない場合はスクリプト位置から推定する。"""
    here = Path(__file__).resolve()
    done = subprocess.run(
        ["git", "-C", str(here.parent), "rev-parse", "--show-toplevel"],
        capture_output=True,
        text=True,
    )
    if done.returncode == 0 and done.stdout.strip():
        return Path(done.stdout.strip()).resolve()
    return here.parents[2]  # <root>/.claude/scripts/review_prep.py


def reports(directory: Path) -> list[Path]:
    """レポートを新しい順に返す（ファイル名が日時のため辞書順の降順）。"""
    if not directory.is_dir():
        return []
    found = [p for p in directory.glob("*.md") if REPORT_NAME.match(p.name)]
    return sorted(found, key=lambda p: p.name, reverse=True)


def issue_start(directory: Path) -> int:
    """次の ISSUE 開始番号。

    保存先の全レポート（`archive/` を含む）で使われた最大番号を ISSUE_STEP 単位で
    切り上げ、その次から採番する（例: 最大 507 → 601）。番号が無ければ 1。
    archive/ まで見るのは、ローテーションで退避したレポートとの重複を防ぐため。
    """
    largest = 0
    for target in (directory, directory / ARCHIVE):
        for path in reports(target):
            for found in ISSUE_NUM.findall(path.read_text(encoding="utf-8")):
                largest = max(largest, int(found))
    if largest == 0:
        return 1
    return (largest // ISSUE_STEP + 1) * ISSUE_STEP + 1


def recorded_head(report: Path) -> str | None:
    """レポートのモード行に記録された HEAD SHA。無ければ None。"""
    for line in report.read_text(encoding="utf-8").splitlines()[:10]:
        found = HEAD_MARK.search(line)
        if found:
            return found.group(1)
    return None


def report_stamp(report: Path) -> str:
    """ファイル名 `YYYY-MM-DD_HHMMSS.md` から git が解釈できる日時文字列を作る。"""
    date, _, time = report.stem.partition("_")
    return f"{date} {time[0:2]}:{time[2:4]}:{time[4:6]}"


def resolve_base(root: Path, report: Path) -> tuple[str | None, str]:
    """差分の基点コミットを決め、`(SHA, 根拠)` を返す。

    記録された HEAD SHA を優先し、記録が無い / そのコミットが失われている場合のみ
    ファイル名のタイムスタンプから引き当てる（移行期と rebase 後の保険）。
    """
    sha = recorded_head(report)
    if sha:
        code, _ = git(root, "cat-file", "-e", f"{sha}^{{commit}}")
        if code == 0:
            return sha, "レポート記録の HEAD SHA"
        note = f"記録された SHA {sha} がリポジトリに無い（rebase 等）ためタイムスタンプで代替"
    else:
        note = "レポートに HEAD SHA の記録が無いためタイムスタンプで代替"

    code, out = git(
        root, "rev-list", "-1", "--abbrev-commit", f"--before={report_stamp(report)}", "HEAD"
    )
    if code == 0 and out:
        return out.split()[0], f"{note}（TZ差で前後する可能性あり）"
    return None, f"{note}（該当コミットも見つからず全量へフォールバック）"


def changed_files(root: Path, base: str, paths: list[str]) -> dict[str, str]:
    """基点コミット以降に変更されたファイル（未コミット分を含む）を `パス: 状態` で返す。"""
    changed: dict[str, str] = {}
    limit = ["--", *paths] if paths else []

    code, out = git(root, "diff", "--name-status", base, "HEAD", *limit)
    if code != 0:
        print(f"WARN  git diff が失敗した（--paths の指定を確認する）: {base}..HEAD")
    else:
        for line in out.splitlines():
            columns = line.split("\t")
            if len(columns) >= 2:
                # リネームは `R100 旧 新` の3列。追跡すべきは新しいパス
                changed[columns[-1]] = columns[0][0]

    code, out = git(root, "status", "--porcelain", *limit)
    if code == 0:
        for line in out.splitlines():
            state, _, path = line[:2].strip(), None, line[3:].strip()
            if " -> " in path:  # リネーム
                path = path.split(" -> ", 1)[1]
            changed[path.strip('"')] = state or "?"

    return changed


def mode_line(mode: str, head: str, prev: Path | None, files: list[str]) -> str:
    """レポート冒頭へ入れるモード行（review-format.md の記法）。"""
    if mode == "全量":
        return f"> モード: 全量 / HEAD: {head}"
    if not files:
        return f"> モード: 差分 / HEAD: {head}（前回 {prev.stem if prev else '-'} 以降の変更なし）"
    shown = ", ".join(files[:8])
    if len(files) > 8:
        shown += f" ほか計{len(files)}件"
    stamp = prev.stem if prev else "-"
    return f"> モード: 差分 / HEAD: {head}（前回 {stamp} 以降の変更: {shown}）"


def skeleton(title: str, stamp: datetime, line: str, categories: list[str], first: int) -> str:
    """review-format.md 準拠のレポート雛形。"""
    rows = "\n".join(f"| {name} | 0件 |" for name in categories) if categories else "| | 0件 |"
    return (
        f"# {title} — {stamp:%Y-%m-%d %H:%M:%S}\n\n"
        f"{line}\n\n"
        "## サマリー\n\n"
        "| カテゴリ | 件数 |\n"
        "|---------|------|\n"
        f"{rows}\n"
        "| 合計 | 0件 |\n\n"
        "---\n\n"
        "## 指摘一覧\n\n"
        f"### ISSUE-{first:03d}: \n\n"
        "| 項目 | 値 |\n"
        "|------|-----|\n"
        "| カテゴリ |  |\n"
        "| 重要度 |  |\n"
        "| 関連ファイル |  |\n"
        "| 該当箇所 |  |\n"
        "| 検出可能工程 |  |\n\n"
        "**問題の説明:**\n\n\n"
        "**修正案:**\n\n\n"
        "---\n"
    )


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):  # pragma: no cover - 実行環境依存
        pass

    parser = argparse.ArgumentParser(add_help=True, description="レビュー実行前の準備")
    parser.add_argument("--dir", required=True, help="保存先ディレクトリ（リポジトリ相対）")
    parser.add_argument("--paths", nargs="*", default=[], help="レビュー対象パス")
    parser.add_argument("--exclude", nargs="*", default=[], help="除外するパス接頭辞")
    parser.add_argument("--full", action="store_true", help="全量モードを強制する")
    parser.add_argument("--title", default="レビュー結果", help="レポートタイトル")
    parser.add_argument("--categories", default="", help="カテゴリ（`/` か `,` 区切り）")
    parser.add_argument("--init", action="store_true", help="雛形を書き出す")
    args = parser.parse_args()

    root = repo_root()
    save_dir = (root / args.dir).resolve()

    code, head = git(root, "rev-parse", "--short", "HEAD")
    if code != 0:
        print("ERROR  git リポジトリではない（または HEAD が無い）")
        return 1

    # レポート置き場自体は対象に含めない（既定は保存先の親。ルート直下なら保存先のみ）
    parent = save_dir.parent
    default_exclude = save_dir if parent == root else parent
    excludes = [default_exclude.relative_to(root).as_posix()]
    excludes += [Path(item).as_posix().rstrip("/") for item in args.exclude]

    found = reports(save_dir)
    prev = found[0] if found else None
    mode = "全量" if (args.full or prev is None) else "差分"

    base, reason, files = None, "", []
    if mode == "差分":
        base, reason = resolve_base(root, prev)
        if base is None:
            mode = "全量"
        else:
            changed = changed_files(root, base, args.paths)
            files = sorted(
                path
                for path, state in changed.items()
                if state != "D" and not any(path.startswith(f"{item}/") or path == item for item in excludes)
            )

    stamp = datetime.now()
    report = save_dir / f"{stamp:%Y-%m-%d_%H%M%S}.md"
    first = issue_start(save_dir)
    line = mode_line(mode, head, prev, files)

    print(f"MODE         {mode}")
    print(f"PREV_REPORT  {prev.relative_to(root).as_posix() if prev else '-（初回）'}")
    print(f"HEAD         {head}")
    if mode == "差分":
        print(f"BASE         {base}（{reason}）")
    elif prev is not None:
        print(f"BASE         -（{'--full 指定' if args.full else reason}）")
    print(f"ISSUE_START  {first:03d}")
    print(f"REPORT_PATH  {report.relative_to(root).as_posix()}")
    print(f"MODE_LINE    {line}")
    print(f"EXCLUDE      {', '.join(excludes)}")

    if mode == "差分":
        print(f"CHANGED      {len(files)}")
        for path in files:
            print(f"  {path}")
        if not files:
            print("→ 変更なし。レビューを実行せず「前回レビュー以降、変更なし」と報告して終了する")
            return 0

    if args.init:
        categories = [c.strip() for c in re.split(r"[/,]", args.categories) if c.strip()]
        save_dir.mkdir(parents=True, exist_ok=True)
        report.write_text(skeleton(args.title, stamp, line, categories, first), encoding="utf-8")
        print(f"INIT         {report.relative_to(root).as_posix()} を作成した")

    return 0


if __name__ == "__main__":
    sys.exit(main())
