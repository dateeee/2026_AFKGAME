"""rotate_reviews.py の回帰テスト。

実行: `python -m pytest scripts/tests -q`（リポジトリルートから）

`ROOT` / `REVIEWS` を `tmp_path` へ差し替え、実リポジトリの `docs/reviews/` を
一切触らずに検証する（`--apply` は実ファイルを動かすため特に重要）。
"""

import subprocess

import pytest

import rotate_reviews as mod


SLOT = "doc-review"


@pytest.fixture
def reviews(tmp_path, monkeypatch):
    """`ROOT` / `REVIEWS` を差し替えた空ツリーの `docs/reviews/` を返す。"""
    root = tmp_path
    reviews = root / "docs" / "reviews"
    reviews.mkdir(parents=True)
    monkeypatch.setattr(mod, "ROOT", root)
    monkeypatch.setattr(mod, "REVIEWS", reviews)
    return reviews


def make_reports(directory, count: int, body: str = "x") -> list[str]:
    """`YYYY-MM-DD_HHMMSS.md` 形式のレポートを count 件作り、新しい順の名前を返す。"""
    directory.mkdir(parents=True, exist_ok=True)
    names = [f"2026-08-{day:02d}_120000.md" for day in range(1, count + 1)]
    for name in names:
        (directory / name).write_text(body, encoding="utf-8")
    return sorted(names, reverse=True)


# ── 定数（規約と同期していること）────────────────────────────

def test_recent_matches_documentation_rule():
    """直下に残す件数は documentation_rules.md §9 の 10 件。"""
    assert mod.RECENT == 10


# ── reports ──────────────────────────────────────────────────

def test_reports_returns_newest_first(reviews):
    names = make_reports(reviews / SLOT, 3)
    assert [p.name for p in mod.reports(reviews / SLOT)] == names


def test_reports_returns_empty_for_missing_directory(reviews):
    assert mod.reports(reviews / "none") == []


def test_reports_ignores_non_markdown(reviews):
    make_reports(reviews / SLOT, 1)
    (reviews / SLOT / "note.txt").write_text("x", encoding="utf-8")
    assert [p.suffix for p in mod.reports(reviews / SLOT)] == [".md"]


# ── rel ──────────────────────────────────────────────────────

def test_rel_returns_posix_path_from_root(reviews):
    path = reviews / SLOT / "a.md"
    assert mod.rel(path) == f"docs/reviews/{SLOT}/a.md"


# ── move ─────────────────────────────────────────────────────

def test_move_falls_back_to_rename_when_git_mv_fails(reviews, monkeypatch):
    """git mv が失敗しても移動は成立する（内容を失わない）。"""
    monkeypatch.setattr(
        mod.subprocess, "run",
        lambda *a, **kw: subprocess.CompletedProcess(a[0], 1, b"", b"not a git repository"),
    )
    src = reviews / SLOT / "a.md"
    src.parent.mkdir(parents=True)
    src.write_text("body", encoding="utf-8")
    dest = reviews / SLOT / mod.ARCHIVE / "a.md"
    mod.move(src, dest)
    assert not src.exists() and dest.read_text(encoding="utf-8") == "body"


def test_move_creates_archive_directory(reviews):
    src = reviews / SLOT / "a.md"
    src.parent.mkdir(parents=True)
    src.write_text("body", encoding="utf-8")
    dest = reviews / SLOT / mod.ARCHIVE / "a.md"
    assert not dest.parent.exists()
    mod.move(src, dest)
    assert dest.parent.is_dir()


def test_move_uses_git_mv_inside_repository(reviews, monkeypatch):
    """git 管理下では git mv が使われ、移動後も追跡が続く。"""
    root = mod.ROOT
    subprocess.run(["git", "init", "-q", str(root)], check=True, capture_output=True)
    src = reviews / SLOT / "a.md"
    src.parent.mkdir(parents=True)
    src.write_text("body", encoding="utf-8")
    subprocess.run(["git", "-C", str(root), "add", "-A"], check=True, capture_output=True)

    dest = reviews / SLOT / mod.ARCHIVE / "a.md"
    mod.move(src, dest)

    staged = subprocess.run(
        ["git", "-C", str(root), "diff", "--cached", "--name-only"],
        capture_output=True, text=True, check=True,
    ).stdout
    assert f"docs/reviews/{SLOT}/{mod.ARCHIVE}/a.md" in staged
    assert dest.exists() and not src.exists()


# ── main: 既定（表示のみ）────────────────────────────────────

def test_main_reports_nothing_to_move_when_within_limit(reviews, monkeypatch, capsys):
    make_reports(reviews / SLOT, mod.RECENT)
    monkeypatch.setattr(mod.sys, "argv", ["rotate_reviews.py"])
    assert mod.main() == 0
    out = capsys.readouterr().out
    assert "TO ARCHIVE" not in out and f"直下 {mod.RECENT} 件" in out


def test_main_lists_overflow_without_moving(reviews, monkeypatch, capsys):
    """既定は表示のみ。ファイルは1件も動かさない。"""
    names = make_reports(reviews / SLOT, mod.RECENT + 2)
    monkeypatch.setattr(mod.sys, "argv", ["rotate_reviews.py"])
    assert mod.main() == 0
    out = capsys.readouterr().out
    assert out.count("TO ARCHIVE") == 2
    assert all((reviews / SLOT / n).exists() for n in names)
    assert not (reviews / SLOT / mod.ARCHIVE).exists()


def test_main_selects_oldest_reports_for_archiving(reviews, monkeypatch, capsys):
    names = make_reports(reviews / SLOT, mod.RECENT + 2)
    monkeypatch.setattr(mod.sys, "argv", ["rotate_reviews.py"])
    mod.main()
    out = capsys.readouterr().out
    assert names[-1] in out and names[-2] in out and names[0] not in out


def test_main_suggests_apply_when_overflow_exists(reviews, monkeypatch, capsys):
    make_reports(reviews / SLOT, mod.RECENT + 1)
    monkeypatch.setattr(mod.sys, "argv", ["rotate_reviews.py"])
    mod.main()
    assert "--apply を付けて再実行する" in capsys.readouterr().out


# ── main: --apply ────────────────────────────────────────────

def test_main_apply_moves_overflow_into_archive(reviews, monkeypatch, capsys):
    names = make_reports(reviews / SLOT, mod.RECENT + 2)
    monkeypatch.setattr(mod.sys, "argv", ["rotate_reviews.py", "--apply"])
    assert mod.main() == 0
    archive = reviews / SLOT / mod.ARCHIVE
    assert sorted(p.name for p in archive.glob("*.md")) == sorted(names[-2:])
    assert len(list((reviews / SLOT).glob("*.md"))) == mod.RECENT


def test_main_apply_reports_counts_after_move(reviews, monkeypatch, capsys):
    make_reports(reviews / SLOT, mod.RECENT + 2)
    monkeypatch.setattr(mod.sys, "argv", ["rotate_reviews.py", "--apply"])
    mod.main()
    out = capsys.readouterr().out
    assert "ARCHIVE " in out and f"直下 {mod.RECENT} 件" in out and "archive/ 2 件" in out


# ── main: 置き忘れの検出 ─────────────────────────────────────

def test_main_warns_report_directly_under_reviews(reviews, monkeypatch, capsys):
    (reviews / "2026-08-01_120000.md").write_text("x", encoding="utf-8")
    monkeypatch.setattr(mod.sys, "argv", ["rotate_reviews.py"])
    assert mod.main() == 0
    assert "WARN  docs/reviews/2026-08-01_120000.md" in capsys.readouterr().out


def test_main_tolerates_missing_reviews_directory(reviews, monkeypatch, capsys):
    monkeypatch.setattr(mod, "REVIEWS", mod.ROOT / "docs" / "none")
    monkeypatch.setattr(mod.sys, "argv", ["rotate_reviews.py"])
    assert mod.main() == 0
    assert "直下 0 件" in capsys.readouterr().out


# ── main: --list ─────────────────────────────────────────────

def test_main_list_includes_archive_and_totals_chars(reviews, monkeypatch, capsys):
    make_reports(reviews / SLOT, 1, body="あいう")
    make_reports(reviews / SLOT / mod.ARCHIVE, 1, body="かきくけこ")
    monkeypatch.setattr(mod.sys, "argv", ["rotate_reviews.py", "--list"])
    assert mod.main() == 0
    out = capsys.readouterr().out
    assert f"docs/reviews/{SLOT}/{mod.ARCHIVE}/" in out and "8 chars" in out


def test_main_list_does_not_move_anything(reviews, monkeypatch, capsys):
    make_reports(reviews / SLOT, mod.RECENT + 2)
    monkeypatch.setattr(mod.sys, "argv", ["rotate_reviews.py", "--list", "--apply"])
    mod.main()
    assert len(list((reviews / SLOT).glob("*.md"))) == mod.RECENT + 2
