"""review_prep.py の回帰テスト。

実行: `python -m pytest .claude/scripts/tests -q`（リポジトリルートから）

差分特定は git の実挙動に依存するため、`tmp_path` に使い捨てリポジトリを作り
`repo_root()` を差し替えて検証する（実リポジトリには一切触れない）。

review_prep.py は**プロジェクト非依存**が要件なので、本テストにも AFK GAME 固有の
パス・タイトル・カテゴリを持ち込まない。
"""

import subprocess
from datetime import datetime
from pathlib import Path

import pytest

import review_prep as mod


SAVE_DIR = "reviews/some-review"


def git(root: Path, *args: str) -> str:
    done = subprocess.run(["git", "-C", str(root), *args],
                          capture_output=True, text=True, check=True)
    return done.stdout.strip()


def commit(root: Path, rel: str, text: str, message: str = "c") -> str:
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    git(root, "add", "-A")
    git(root, "commit", "-q", "-m", message)
    return git(root, "rev-parse", "--short", "HEAD")


@pytest.fixture
def repo(tmp_path, monkeypatch):
    """初回コミット済みの使い捨てリポジトリを返す（`repo_root()` を差し替え済み）。"""
    root = tmp_path / "repo"
    root.mkdir()
    git(root, "init", "-q")
    git(root, "config", "user.email", "t@example.com")
    git(root, "config", "user.name", "tester")
    monkeypatch.setattr(mod, "repo_root", lambda: root)
    commit(root, "src/a.md", "初期\n", "init")
    return root


def write_report(root: Path, name: str, text: str = "> モード: 全量 / HEAD: 0000000\n") -> Path:
    path = root / SAVE_DIR / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return path


def run_main(monkeypatch, *args: str) -> int:
    monkeypatch.setattr(mod.sys, "argv", ["review_prep.py", "--dir", SAVE_DIR, *args])
    return mod.main()


def kv(out: str) -> dict[str, str]:
    """`KEY  値` 形式の出力を辞書にする（そのまま取り込める形であることの検証）。"""
    result = {}
    for line in out.splitlines():
        if line[:1].isupper() and "  " in line:
            key, _, value = line.partition("  ")
            result[key.strip()] = value.strip()
    return result


# ── git / repo_root ──────────────────────────────────────────

def test_git_returns_code_and_trimmed_stdout(repo):
    code, out = mod.git(repo, "rev-parse", "--abbrev-ref", "HEAD")
    assert code == 0 and out and not out.endswith("\n")


def test_git_returns_nonzero_for_unknown_revision(repo):
    code, _ = mod.git(repo, "cat-file", "-e", "deadbee^{commit}")
    assert code != 0


def test_git_keeps_leading_status_columns(repo):
    """`git status --porcelain` の先頭列は状態を表すため落とさない。"""
    (repo / "src" / "a.md").write_text("変更\n", encoding="utf-8")
    _, out = mod.git(repo, "status", "--porcelain")
    assert out.startswith(" M ")


def test_repo_root_points_at_the_repository_containing_the_script():
    assert (mod.repo_root() / ".claude" / "scripts" / "review_prep.py").is_file()


# ── reports ──────────────────────────────────────────────────

def test_reports_returns_newest_first(repo):
    write_report(repo, "2026-08-01_120000.md")
    write_report(repo, "2026-08-03_090000.md")
    assert [p.stem for p in mod.reports(repo / SAVE_DIR)] == ["2026-08-03_090000", "2026-08-01_120000"]


def test_reports_ignores_files_not_named_as_timestamp(repo):
    write_report(repo, "2026-08-01_120000.md")
    write_report(repo, "README.md")
    assert [p.stem for p in mod.reports(repo / SAVE_DIR)] == ["2026-08-01_120000"]


def test_reports_returns_empty_for_missing_directory(repo):
    assert mod.reports(repo / "none") == []


# ── issue_start ──────────────────────────────────────────────

def test_issue_start_is_one_without_any_report(repo):
    assert mod.issue_start(repo / SAVE_DIR) == 1


def test_issue_start_rounds_up_to_next_step(repo):
    write_report(repo, "2026-08-01_120000.md", "ISSUE-507 の指摘\n")
    assert mod.issue_start(repo / SAVE_DIR) == 601


def test_issue_start_counts_archived_reports_too(repo):
    """ローテーションで退避したレポートとの番号重複を防ぐ。"""
    write_report(repo, "2026-08-02_120000.md", "ISSUE-101\n")
    path = repo / SAVE_DIR / mod.ARCHIVE / "2026-08-01_120000.md"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("ISSUE-905\n", encoding="utf-8")
    assert mod.issue_start(repo / SAVE_DIR) == 1001


def test_issue_start_steps_over_exact_multiple(repo):
    write_report(repo, "2026-08-01_120000.md", "ISSUE-500\n")
    assert mod.issue_start(repo / SAVE_DIR) == 601


# ── recorded_head ────────────────────────────────────────────

def test_recorded_head_reads_sha_from_mode_line(repo):
    report = write_report(repo, "2026-08-01_120000.md", "# 見出し\n\n> モード: 差分 / HEAD: abc1234\n")
    assert mod.recorded_head(report) == "abc1234"


def test_recorded_head_returns_none_without_record(repo):
    report = write_report(repo, "2026-08-01_120000.md", "> モード: 全量\n")
    assert mod.recorded_head(report) is None


def test_recorded_head_ignores_sha_after_first_ten_lines(repo):
    report = write_report(repo, "2026-08-01_120000.md", "\n" * 12 + "HEAD: abc1234\n")
    assert mod.recorded_head(report) is None


# ── report_stamp ─────────────────────────────────────────────

def test_report_stamp_builds_git_readable_datetime(repo):
    report = write_report(repo, "2026-08-07_232135.md")
    assert mod.report_stamp(report) == "2026-08-07 23:21:35"


# ── resolve_base ─────────────────────────────────────────────

def test_resolve_base_prefers_recorded_sha(repo):
    sha = git(repo, "rev-parse", "--short", "HEAD")
    report = write_report(repo, "2099-01-01_000000.md", f"> モード: 差分 / HEAD: {sha}\n")
    assert mod.resolve_base(repo, report) == (sha, "レポート記録の HEAD SHA")


def test_resolve_base_falls_back_when_recorded_sha_is_gone(repo):
    report = write_report(repo, "2099-01-01_000000.md", "> モード: 差分 / HEAD: dead123\n")
    base, reason = mod.resolve_base(repo, report)
    assert base and "リポジトリに無い" in reason


def test_resolve_base_falls_back_when_no_sha_recorded(repo):
    report = write_report(repo, "2099-01-01_000000.md", "> モード: 差分\n")
    base, reason = mod.resolve_base(repo, report)
    assert base == git(repo, "rev-parse", "--short", "HEAD")
    assert "HEAD SHA の記録が無い" in reason


def test_resolve_base_returns_none_when_no_commit_precedes_report(repo):
    report = write_report(repo, "1999-01-01_000000.md", "> モード: 差分\n")
    base, reason = mod.resolve_base(repo, report)
    assert base is None and "全量へフォールバック" in reason


# ── changed_files ────────────────────────────────────────────

def test_changed_files_detects_committed_change(repo):
    base = git(repo, "rev-parse", "--short", "HEAD")
    commit(repo, "src/a.md", "更新\n", "update")
    assert mod.changed_files(repo, base, []) == {"src/a.md": "M"}


def test_changed_files_detects_added_and_deleted(repo):
    base = git(repo, "rev-parse", "--short", "HEAD")
    (repo / "src" / "a.md").unlink()
    commit(repo, "src/b.md", "新規\n", "add and delete")
    assert mod.changed_files(repo, base, []) == {"src/a.md": "D", "src/b.md": "A"}


def test_changed_files_includes_uncommitted_work(repo):
    base = git(repo, "rev-parse", "--short", "HEAD")
    (repo / "src" / "c.md").write_text("未コミット\n", encoding="utf-8")
    assert mod.changed_files(repo, base, [])["src/c.md"] == "??"


def test_changed_files_tracks_new_path_of_rename(repo):
    base = git(repo, "rev-parse", "--short", "HEAD")
    git(repo, "mv", "src/a.md", "src/renamed.md")
    git(repo, "commit", "-q", "-m", "rename")
    changed = mod.changed_files(repo, base, [])
    assert "src/renamed.md" in changed and "src/a.md" not in changed


def test_changed_files_limits_to_given_paths(repo):
    base = git(repo, "rev-parse", "--short", "HEAD")
    commit(repo, "other/x.md", "対象外\n", "outside")
    commit(repo, "src/a.md", "対象\n", "inside")
    assert list(mod.changed_files(repo, base, ["src"])) == ["src/a.md"]


def test_changed_files_warns_when_diff_fails(repo, capsys):
    mod.changed_files(repo, "deadbee", [])
    assert "WARN  git diff が失敗した" in capsys.readouterr().out


# ── mode_line ────────────────────────────────────────────────

def test_mode_line_for_full_mode_records_head():
    assert mod.mode_line("全量", "abc1234", None, []) == "> モード: 全量 / HEAD: abc1234"


def test_mode_line_for_diff_without_changes(tmp_path):
    prev = tmp_path / "2026-08-01_120000.md"
    line = mod.mode_line("差分", "abc1234", prev, [])
    assert "前回 2026-08-01_120000 以降の変更なし" in line


def test_mode_line_lists_changed_files(tmp_path):
    prev = tmp_path / "2026-08-01_120000.md"
    line = mod.mode_line("差分", "abc1234", prev, ["a.md", "b.md"])
    assert "以降の変更: a.md, b.md" in line


def test_mode_line_truncates_after_eight_files(tmp_path):
    prev = tmp_path / "2026-08-01_120000.md"
    files = [f"f{i}.md" for i in range(10)]
    line = mod.mode_line("差分", "abc1234", prev, files)
    assert "ほか計10件" in line and "f8.md" not in line


# ── skeleton ─────────────────────────────────────────────────

def test_skeleton_contains_title_mode_line_and_first_issue():
    text = mod.skeleton("レビュー結果", datetime(2026, 8, 8, 9, 30, 0), "> モード: 全量", ["整合性"], 601)
    assert text.startswith("# レビュー結果 — 2026-08-08 09:30:00")
    assert "> モード: 全量" in text and "### ISSUE-601: " in text
    assert "| 整合性 | 0件 |" in text


def test_skeleton_uses_placeholder_row_without_categories():
    text = mod.skeleton("t", datetime(2026, 8, 8), "> line", [], 1)
    assert "| | 0件 |" in text and "### ISSUE-001: " in text


# ── main ─────────────────────────────────────────────────────

def test_main_reports_full_mode_on_first_run(repo, monkeypatch, capsys):
    assert run_main(monkeypatch, "--paths", "src") == 0
    out = kv(capsys.readouterr().out)
    assert out["MODE"] == "全量" and out["PREV_REPORT"] == "-（初回）"
    assert out["ISSUE_START"] == "001" and out["MODE_LINE"].startswith("> モード: 全量")


def test_main_switches_to_diff_mode_with_previous_report(repo, monkeypatch, capsys):
    sha = git(repo, "rev-parse", "--short", "HEAD")
    write_report(repo, "2026-08-01_120000.md", f"> モード: 全量 / HEAD: {sha}\n")
    commit(repo, "src/a.md", "更新\n", "update")
    assert run_main(monkeypatch, "--paths", "src") == 0
    out = capsys.readouterr().out
    parsed = kv(out)
    assert parsed["MODE"] == "差分" and parsed["BASE"].startswith(sha)
    assert parsed["CHANGED"] == "1" and "  src/a.md" in out


def test_main_full_flag_overrides_diff_mode(repo, monkeypatch, capsys):
    sha = git(repo, "rev-parse", "--short", "HEAD")
    write_report(repo, "2026-08-01_120000.md", f"> モード: 全量 / HEAD: {sha}\n")
    assert run_main(monkeypatch, "--full") == 0
    parsed = kv(capsys.readouterr().out)
    assert parsed["MODE"] == "全量" and parsed["BASE"] == "-（--full 指定）"


def test_main_stops_when_nothing_changed(repo, monkeypatch, capsys):
    sha = git(repo, "rev-parse", "--short", "HEAD")
    write_report(repo, "2026-08-01_120000.md", f"> モード: 全量 / HEAD: {sha}\n")
    assert run_main(monkeypatch, "--paths", "src") == 0
    out = capsys.readouterr().out
    assert "変更なし。レビューを実行せず" in out and "INIT" not in out


def test_main_excludes_report_directory_from_changes(repo, monkeypatch, capsys):
    """レポート置き場の更新自体を「変更あり」と数えない。"""
    sha = git(repo, "rev-parse", "--short", "HEAD")
    write_report(repo, "2026-08-01_120000.md", f"> モード: 全量 / HEAD: {sha}\n")
    commit(repo, f"{SAVE_DIR}/2026-08-01_120000.md", f"> モード: 全量 / HEAD: {sha}\n", "report")
    assert run_main(monkeypatch) == 0
    parsed = kv(capsys.readouterr().out)
    assert parsed["EXCLUDE"] == "reviews" and parsed["MODE"] == "差分"


def test_main_honors_extra_exclude(repo, monkeypatch, capsys):
    sha = git(repo, "rev-parse", "--short", "HEAD")
    write_report(repo, "2026-08-01_120000.md", f"> モード: 全量 / HEAD: {sha}\n")
    commit(repo, "src/a.md", "更新\n", "update")
    assert run_main(monkeypatch, "--exclude", "src/") == 0
    out = capsys.readouterr().out
    assert "src/a.md" not in out and "変更なし" in out


def test_main_writes_skeleton_with_init(repo, monkeypatch, capsys):
    assert run_main(monkeypatch, "--init", "--title", "レビュー結果",
                    "--categories", "整合性 / 網羅性") == 0
    parsed = kv(capsys.readouterr().out)
    report = repo / parsed["REPORT_PATH"]
    text = report.read_text(encoding="utf-8")
    assert report.is_file() and "# レビュー結果 —" in text
    assert "| 整合性 | 0件 |" in text and "| 網羅性 | 0件 |" in text


def test_main_does_not_write_without_init(repo, monkeypatch, capsys):
    assert run_main(monkeypatch) == 0
    parsed = kv(capsys.readouterr().out)
    assert not (repo / parsed["REPORT_PATH"]).exists()


def test_main_fails_outside_git_repository(tmp_path, monkeypatch, capsys):
    monkeypatch.setattr(mod, "repo_root", lambda: tmp_path)
    assert run_main(monkeypatch) == 1
    assert "ERROR  git リポジトリではない" in capsys.readouterr().out


def test_main_falls_back_to_full_when_base_cannot_be_resolved(repo, monkeypatch, capsys):
    write_report(repo, "1999-01-01_000000.md", "> モード: 差分\n")
    assert run_main(monkeypatch) == 0
    parsed = kv(capsys.readouterr().out)
    assert parsed["MODE"] == "全量" and "全量へフォールバック" in parsed["BASE"]
