#!/usr/bin/env python3
"""git worktree の作成・削除ヘルパー

並行作業用の worktree を「リポジトリの隣」（`<リポジトリ名>.worktrees/<名前>`）へ作成し、
git 管理外のローカル設定ファイルをコピーする。競合を避ける運用ルールは
docs/process/worktree_guide.md が正。

使い方:
    python scripts/worktree.py add <名前> [--base main]   # 作成（ブランチ wt/<名前>）
    python scripts/worktree.py merge <名前> [--keep]      # main へ統合し片付けまで一括
    python scripts/worktree.py remove <名前> [--delete-branch] [--force]
    python scripts/worktree.py list

add が行うこと:
    1. ブランチ wt/<名前> を --base（既定 main）から作成（既存なら再利用）
    2. worktree を <リポジトリ名>.worktrees/<名前> へ作成
    3. LOCAL_FILES のうち存在するものを worktree へコピー
    4. rerere（競合解消の記録・再適用）を有効化（リポジトリ共通設定・冪等）

merge が行うこと（main 側で実行する）:
    1. worktree に未コミットの変更がないことを確認
    2. worktree 側へ main を取り込む（競合したら停止 → worktree で解消・コミット後に再実行）
    3. main へ fast-forward で統合
    4. worktree とブランチを削除（--keep で残す）
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WT_ROOT = ROOT.parent / (ROOT.name + ".worktrees")
BRANCH_PREFIX = "wt/"

# git 管理外だが worktree 側にも必要なローカル設定（存在するものだけコピー）
LOCAL_FILES = [
    ".claude/settings.local.json",
    ".env",
    "frontend/.env",
    ".vscode/launch.json",   # README が参照。無いと worktree 内で check_docs が失敗する
    ".vscode/settings.json",
]


def run_git(*args: str, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git", "-C", str(ROOT), *args],
        check=check, capture_output=True, text=True, encoding="utf-8",
    )


def branch_exists(branch: str) -> bool:
    return run_git("rev-parse", "--verify", "--quiet", f"refs/heads/{branch}",
                   check=False).returncode == 0


def validate_name(name: str) -> None:
    if not name or any(c in name for c in "/\\ ..") or name.startswith("."):
        sys.exit(f"ERROR: 名前に使えない文字が含まれている: {name!r}"
                 "（英数・ハイフン・アンダースコアを推奨）")


def cmd_add(args: argparse.Namespace) -> None:
    validate_name(args.name)
    branch = BRANCH_PREFIX + args.name
    dest = WT_ROOT / args.name
    if dest.exists():
        sys.exit(f"ERROR: {dest} は既に存在する")

    WT_ROOT.mkdir(parents=True, exist_ok=True)
    if branch_exists(branch):
        print(f"ブランチ {branch} は既存のため再利用する")
        result = run_git("worktree", "add", str(dest), branch, check=False)
    else:
        result = run_git("worktree", "add", "-b", branch, str(dest), args.base,
                         check=False)
    if result.returncode != 0:
        sys.exit(f"ERROR: git worktree add に失敗:\n{result.stderr}")

    copied = []
    for rel in LOCAL_FILES:
        src = ROOT / rel
        if src.is_file():
            dst = dest / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dst)
            copied.append(rel)

    # 競合解消の記録・再適用。設定は共通 .git/config に入り全 worktree に効く
    run_git("config", "rerere.enabled", "true")

    print(f"作成: {dest}（ブランチ {branch}、起点 {args.base}）")
    if copied:
        print("コピー: " + ", ".join(copied))
    print("\n次の手順:")
    print(f"  cd {dest}")
    print("  # frontend を触るタスクなら: cd frontend; npm install")
    print("  # 運用ルール: docs/process/worktree_guide.md")


def run_wt(dest: Path, *args: str) -> subprocess.CompletedProcess:
    """worktree 側のディレクトリで git を実行する"""
    return subprocess.run(
        ["git", "-C", str(dest), *args],
        check=False, capture_output=True, text=True, encoding="utf-8",
    )


def cmd_merge(args: argparse.Namespace) -> None:
    validate_name(args.name)
    branch = BRANCH_PREFIX + args.name
    dest = WT_ROOT / args.name
    if not dest.exists():
        sys.exit(f"ERROR: {dest} が存在しない（worktree 削除済みなら"
                 f" git merge {branch} を直接使う）")
    try:
        Path.cwd().resolve().relative_to(dest.resolve())
        sys.exit("ERROR: worktree の中からは実行できない"
                 "（ExitWorktree で main へ戻ってから実行する）")
    except ValueError:
        pass

    if run_wt(dest, "status", "--porcelain").stdout.strip():
        sys.exit(f"ERROR: worktree に未コミットの変更がある。"
                 f"{dest} でコミットしてから再実行")
    if run_git("status", "--porcelain", "--untracked-files=no",
               check=False).stdout.strip():
        sys.exit("ERROR: main 側に未コミットの変更がある。"
                 "コミットしてから再実行")

    result = run_wt(dest, "merge", "main", "--no-edit")
    if result.returncode != 0:
        sys.exit("ERROR: main の取り込みで競合。worktree 側のファイルで解消し、"
                 "worktree でコミットしてから再実行:\n"
                 + result.stdout + result.stderr)

    result = run_git("merge", "--ff-only", branch, check=False)
    if result.returncode != 0:
        sys.exit(f"ERROR: main への fast-forward 統合に失敗:\n{result.stderr}")
    print(f"統合: {branch} → main")

    if args.keep:
        print(f"worktree は残した（片付けは"
              f" remove {args.name} --delete-branch）")
        return
    do_remove(args.name, delete_branch=True, force=False)


def do_remove(name: str, delete_branch: bool, force: bool) -> None:
    branch = BRANCH_PREFIX + name
    dest = WT_ROOT / name

    remove_args = ["worktree", "remove", str(dest)]
    if force:
        remove_args.append("--force")
    result = run_git(*remove_args, check=False)
    if result.returncode != 0:
        sys.exit(f"ERROR: git worktree remove に失敗（未コミットの変更が残っていれば"
                 f"コミットするか --force を付ける）:\n{result.stderr}")
    print(f"削除: {dest}")

    if delete_branch:
        result = run_git("branch", "-d", branch, check=False)
        if result.returncode != 0:
            sys.exit(f"ERROR: ブランチ {branch} の削除に失敗（main へ未マージ。"
                     f"統合してから削除するか、破棄なら git branch -D {branch}）:\n"
                     f"{result.stderr}")
        print(f"ブランチ削除: {branch}")
    else:
        print(f"ブランチ {branch} は残した（統合済みなら"
              f" --delete-branch で一緒に削除できる）")


def cmd_remove(args: argparse.Namespace) -> None:
    validate_name(args.name)
    do_remove(args.name, args.delete_branch, args.force)


def cmd_list(_args: argparse.Namespace) -> None:
    print(run_git("worktree", "list").stdout, end="")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    p_add = sub.add_parser("add", help="worktree とブランチ wt/<名前> を作成")
    p_add.add_argument("name", help="worktree 名（タスク名。例: step3a2-auth）")
    p_add.add_argument("--base", default="main", help="ブランチの起点（既定: main）")
    p_add.set_defaults(func=cmd_add)

    p_mg = sub.add_parser("merge", help="main を取り込み → main へ ff 統合 → "
                                        "worktree・ブランチ削除まで一括（main 側で実行）")
    p_mg.add_argument("name")
    p_mg.add_argument("--keep", action="store_true",
                      help="統合後も worktree とブランチを残す")
    p_mg.set_defaults(func=cmd_merge)

    p_rm = sub.add_parser("remove", help="worktree を削除")
    p_rm.add_argument("name")
    p_rm.add_argument("--delete-branch", action="store_true",
                      help="ブランチ wt/<名前> も削除（main へ統合済みのときのみ成功）")
    p_rm.add_argument("--force", action="store_true",
                      help="未コミットの変更があっても worktree を削除")
    p_rm.set_defaults(func=cmd_remove)

    p_ls = sub.add_parser("list", help="worktree の一覧")
    p_ls.set_defaults(func=cmd_list)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
        sys.stdout.reconfigure(encoding="utf-8")  # Windows コンソール対策
    main()
