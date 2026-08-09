"""check_doc_size.py の回帰テスト。

実行: `python -m pytest scripts/tests -q`（リポジトリルートから）

スクリプトはリポジトリルート（`ROOT`）を起点に走査するため、各テストは
`ROOT` を `tmp_path` へ差し替えて、実リポジトリの内容に依存させない。
"""

import pytest

import check_doc_size as mod


@pytest.fixture
def root(tmp_path, monkeypatch):
    """`ROOT` を差し替えた空のリポジトリを返す。"""
    monkeypatch.setattr(mod, "ROOT", tmp_path)
    (tmp_path / "docs").mkdir()
    return tmp_path


def write(root, rel: str, text: str) -> str:
    (root / rel).write_text(text, encoding="utf-8")
    return rel


# ── sections_of ──────────────────────────────────────────────

def test_sections_of_splits_by_h2_in_order(root):
    rel = write(root, "docs/a.md", "前文\n\n## 一\nAAA\n\n## 二\nBB\n")
    assert [h for h, _ in mod.sections_of(rel)] == ["## 一", "## 二"]


def test_sections_of_counts_body_without_heading_line(root):
    rel = write(root, "docs/a.md", "## 一\nAAA\n")
    assert mod.sections_of(rel) == [("## 一", len("AAA\n"))]


def test_sections_of_ignores_h2_inside_code_fence(root):
    rel = write(root, "docs/a.md", "## 一\n```\n## 偽物\n```\n終\n")
    headings = [h for h, _ in mod.sections_of(rel)]
    assert headings == ["## 一"]


def test_sections_of_returns_empty_when_no_h2(root):
    rel = write(root, "docs/a.md", "# 見出し1\n本文だけ\n")
    assert mod.sections_of(rel) == []


# ── oversized_sections（既存挙動の回帰）──────────────────────

def test_oversized_sections_returns_only_over_limit(root):
    big, small = "あ" * (mod.SECTION_LIMIT + 1), "い" * 10
    rel = write(root, "docs/a.md", f"## 大\n{big}\n\n## 小\n{small}\n")
    assert [h for h, _ in mod.oversized_sections(rel)] == ["## 大"]


# ── 上限の緩和例外（documentation_rules.md §3.1）────────────

RELAXED_DIR = "docs/process/coding_standards_backend"


@pytest.fixture
def relaxed_root(root):
    """緩和対象のディレクトリを用意した root。"""
    (root / RELAXED_DIR).mkdir(parents=True)
    return root


@pytest.mark.parametrize(
    "rel, relaxed",
    [
        (f"{RELAXED_DIR}.md", True),
        (f"{RELAXED_DIR}/basis.md", True),
        ("docs/process/coding_standards_backend_old.md", False),
        ("docs/process/documentation_rules.md", False),
    ],
)
def test_is_relaxed_matches_index_and_children_only(rel, relaxed):
    assert mod.is_relaxed(rel) is relaxed


def test_limit_of_scales_relaxed_file_and_leaves_others():
    base = mod.LIMITS["C 仕様・設計"]
    assert mod.limit_of(f"{RELAXED_DIR}/basis.md") == int(base * mod.RELAXED_FACTOR)
    assert mod.limit_of("docs/a.md") == base


def test_section_limit_of_scales_relaxed_file_and_leaves_others():
    assert mod.section_limit_of(f"{RELAXED_DIR}/basis.md") == int(
        mod.SECTION_LIMIT * mod.RELAXED_FACTOR
    )
    assert mod.section_limit_of("docs/a.md") == mod.SECTION_LIMIT


def test_oversized_sections_allows_relaxed_h2_up_to_factor(relaxed_root):
    """緩和対象では通常上限超えの H2 も、緩和後の上限までは違反にしない。"""
    body = "あ" * (mod.SECTION_LIMIT + 1)
    rel = write(relaxed_root, f"{RELAXED_DIR}/basis.md", f"## 一\n{body}\n")
    assert mod.oversized_sections(rel) == []

    over = "あ" * (mod.section_limit_of(rel) + 1)
    rel2 = write(relaxed_root, f"{RELAXED_DIR}/web.md", f"## 一\n{over}\n")
    assert [h for h, _ in mod.oversized_sections(rel2)] == ["## 一"]


def test_collect_reports_relaxed_limit_for_exception_files(relaxed_root):
    write(relaxed_root, f"{RELAXED_DIR}/basis.md", "x")
    write(relaxed_root, "docs/a.md", "x")
    limits = {rel: limit for rel, _, _, limit in mod.collect()}
    assert limits[f"{RELAXED_DIR}/basis.md"] == int(
        mod.LIMITS["C 仕様・設計"] * mod.RELAXED_FACTOR
    )
    assert limits["docs/a.md"] == mod.LIMITS["C 仕様・設計"]


def test_main_accepts_relaxed_file_over_base_limit(relaxed_root, monkeypatch, capsys):
    """区分Cの上限は超えるが緩和後の上限内なら違反にしない。"""
    write(relaxed_root, f"{RELAXED_DIR}/basis.md", "あ" * (mod.LIMITS["C 仕様・設計"] + 100))
    monkeypatch.setattr(mod.sys, "argv", ["check_doc_size.py"])
    assert mod.main() == 0
    assert "ERROR" not in capsys.readouterr().out


def test_main_rejects_relaxed_file_over_relaxed_limit(relaxed_root, monkeypatch, capsys):
    rel = f"{RELAXED_DIR}/basis.md"
    write(relaxed_root, rel, "あ" * (mod.limit_of(rel) + 1))
    monkeypatch.setattr(mod.sys, "argv", ["check_doc_size.py"])
    assert mod.main() == 1
    assert f"ERROR {rel}" in capsys.readouterr().out


def test_report_sections_marks_relaxed_limit_in_header(relaxed_root, capsys):
    rel = write(relaxed_root, f"{RELAXED_DIR}/basis.md", "## 一\nAAA\n")
    mod.report_sections([rel])
    out = capsys.readouterr().out
    assert f"上限 {mod.limit_of(rel):,}字" in out and "緩和" in out


# ── resolve_rel ──────────────────────────────────────────────

def test_resolve_rel_accepts_root_relative_path(root, monkeypatch, tmp_path):
    write(root, "docs/a.md", "x")
    monkeypatch.chdir(tmp_path)
    assert mod.resolve_rel("docs/a.md") == "docs/a.md"


def test_resolve_rel_accepts_cwd_relative_path(root, monkeypatch):
    write(root, "docs/a.md", "x")
    monkeypatch.chdir(root / "docs")
    assert mod.resolve_rel("a.md") == "docs/a.md"


def test_resolve_rel_accepts_absolute_path(root):
    write(root, "docs/a.md", "x")
    assert mod.resolve_rel(str(root / "docs" / "a.md")) == "docs/a.md"


def test_resolve_rel_rejects_path_outside_root(root, tmp_path_factory):
    outside = tmp_path_factory.mktemp("outside") / "b.md"
    outside.write_text("x", encoding="utf-8")
    assert mod.resolve_rel(str(outside)) is None


def test_resolve_rel_rejects_missing_file(root):
    assert mod.resolve_rel("docs/none.md") is None


def test_resolve_rel_rejects_directory(root):
    assert mod.resolve_rel("docs") is None


# ── report_sections（測定モード）─────────────────────────────

def test_report_sections_lists_sections_under_limit_too(root, capsys):
    """上限以下のセクションも出す（削減候補の比較に必要なため）。"""
    write(root, "docs/a.md", "## 一\nAAA\n\n## 二\nBB\n")
    assert mod.report_sections(["docs/a.md"]) == 0
    out = capsys.readouterr().out
    assert "## 一" in out and "## 二" in out


def test_report_sections_sorts_desc_and_marks_oversized(root, capsys):
    big = "あ" * (mod.SECTION_LIMIT + 1)
    write(root, "docs/a.md", f"## 小\nx\n\n## 大\n{big}\n")
    mod.report_sections(["docs/a.md"])
    lines = [ln for ln in capsys.readouterr().out.splitlines() if "## " in ln]
    assert lines[0].endswith("## 大") and lines[0].lstrip().startswith(str(len(big) + 1))
    assert "!" in lines[0] and "!" not in lines[1]


def test_report_sections_remainder_makes_total_add_up(root, capsys):
    text = "前文\n\n## 一\nAAA\n\n## 二\nBB\n"
    write(root, "docs/a.md", text)
    mod.report_sections(["docs/a.md"])
    out = capsys.readouterr().out
    counted = sum(c for _, c in mod.sections_of("docs/a.md"))
    assert f"{len(text) - counted:>7}   （前文・H2見出し行）" in out


def test_report_sections_shows_zone_limit_and_remaining(root, capsys):
    text = "## 一\nAAA\n"
    write(root, "docs/a.md", text)
    mod.report_sections(["docs/a.md"])
    out = capsys.readouterr().out
    limit = mod.LIMITS["C 仕様・設計"]
    assert f"上限 {limit:,}字" in out and f"残り {limit - len(text):,}字" in out


def test_report_sections_shows_overage_when_over_limit(root, capsys):
    text = "## 一\n" + "あ" * (mod.LIMITS["C 仕様・設計"] + 5)
    write(root, "docs/a.md", text)
    mod.report_sections(["docs/a.md"])
    assert "字 超過）" in capsys.readouterr().out


def test_report_sections_reports_unresolvable_and_continues(root, capsys):
    write(root, "docs/a.md", "## 一\nAAA\n")
    assert mod.report_sections(["docs/none.md", "docs/a.md"]) == 0
    out = capsys.readouterr().out
    assert "ERROR docs/none.md" in out and "## 一" in out


# ── main のディスパッチ ──────────────────────────────────────

def test_main_sections_with_path_skips_judgment(root, monkeypatch, capsys):
    """測定モードは違反があっても exit 0（判定を走らせない）。"""
    write(root, "CLAUDE.md", "## 一\n" + "あ" * (mod.LIMITS["A 常時読込"] + 1))
    monkeypatch.setattr(mod.sys, "argv", ["check_doc_size.py", "--sections", "CLAUDE.md"])
    assert mod.main() == 0
    assert "files checked" not in capsys.readouterr().out


def test_main_sections_without_path_keeps_judgment(root, monkeypatch, capsys):
    """引数なしの `--sections` は従来どおり全量判定つき。"""
    write(root, "CLAUDE.md", "## 一\n" + "あ" * (mod.LIMITS["A 常時読込"] + 1))
    monkeypatch.setattr(mod.sys, "argv", ["check_doc_size.py", "--sections"])
    assert mod.main() == 1
    out = capsys.readouterr().out
    assert "files checked" in out and "ERROR CLAUDE.md" in out
