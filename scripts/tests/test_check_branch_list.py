"""check_branch_list.py の回帰テスト。

実行: `python -m pytest scripts/tests -q`（リポジトリルートから）

スクリプトは分岐一覧を `TECH_DIR` から、マーカーを `ROOT` 配下の JUnit テスト
（`JAVA_TEST_GLOB`）から読むため、各テストは両者を `tmp_path` へ差し替えて
実リポジトリの内容に依存させない。

緑パス（違反のない一覧を通す）と変異テスト（1項目ずつ壊して検出される）を
対にして置く。
"""

import pytest

import check_branch_list as mod


HEADER = "| # | 分岐点 | 条件 | 期待する振る舞い |\n|---|---|---|---|\n"


@pytest.fixture
def dirs(tmp_path, monkeypatch):
    """`TECH_DIR` と `ROOT`（JUnit テストの走査起点）を差し替えた空ツリーを返す。"""
    tech = tmp_path / "docs" / "tech"
    tests = tmp_path / "backend" / "afkgame-domain" / "src" / "test" / "java" / "com" / "afkgame"
    tech.mkdir(parents=True)
    tests.mkdir(parents=True)
    monkeypatch.setattr(mod, "TECH_DIR", tech)
    monkeypatch.setattr(mod, "ROOT", tmp_path)
    return tech, tests


def write_tech(dirs, name: str, text: str) -> None:
    """分岐一覧の文書を1つ置く。`name` に `basic/tech_a.md` のような層つきパスも渡せる。"""
    path = dirs[0] / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_test(dirs, cls: str, text: str) -> None:
    """JUnit テストを1つ置く（`JAVA_TEST_GLOB` に一致する `<cls>Test.java`）。"""
    (dirs[1] / f"{cls}Test.java").write_text(text, encoding="utf-8")


def standard_doc(rows: str, sec: str = "4. ", note: str = "") -> str:
    """標準形式（分岐点・条件列あり）の分岐一覧を1つ持つ文書。"""
    return f"## {sec}分岐一覧\n\n{HEADER}{rows}\n{note}"


def parse_sections() -> dict:
    """`parse_tables()` の第1要素（分岐一覧）。

    第2要素は文書名の衝突エラーで、それ自体を見るテスト
    （`test_parse_tables_reports_duplicate_doc_key` ほか）だけが `parse_tables()` を直接呼ぶ。
    """
    return mod.parse_tables()[0]


TWO_SIDED = "| 1 | HP判定 | HP>0 | 戦闘継続 |\n| 2 | HP判定 | HP<=0 | 戦闘終了 |\n"


# ── parse_tables ─────────────────────────────────────────────

def test_parse_tables_keys_by_filename_and_section_number(dirs):
    write_tech(dirs, "tech_a.md", standard_doc(TWO_SIDED))
    assert set(parse_sections()) == {("tech_a.md", "4")}


def test_parse_tables_uses_empty_section_when_heading_has_no_number(dirs):
    write_tech(dirs, "tech_a.md", standard_doc(TWO_SIDED, sec=""))
    assert set(parse_sections()) == {("tech_a.md", "")}


def test_parse_tables_ignores_heading_inside_code_fence(dirs):
    write_tech(dirs, "tech_a.md", "```\n## 4. 分岐一覧\n```\n" + standard_doc(TWO_SIDED, sec="5. "))
    assert set(parse_sections()) == {("tech_a.md", "5")}


def test_parse_tables_stops_collecting_at_next_heading(dirs):
    """次の見出し以降の表は分岐一覧に取り込まない。"""
    doc = standard_doc(TWO_SIDED) + "\n## 5. 別の節\n\n| a | b |\n|---|---|\n| 9 | x |\n"
    write_tech(dirs, "tech_a.md", doc)
    rows = parse_sections()[("tech_a.md", "4")]["rows"]
    assert [cells[0] for _, cells in rows] == ["1", "2"]


def test_parse_tables_marks_old_format_as_unstructured(dirs):
    write_tech(dirs, "tech_a.md", "## 4. 分岐一覧\n\n| # | 期待する振る舞い |\n|---|---|\n| 1 | 戦闘継続 |\n")
    assert parse_sections()[("tech_a.md", "4")]["structured"] is False


def test_parse_tables_skips_separator_row(dirs):
    write_tech(dirs, "tech_a.md", standard_doc(TWO_SIDED))
    assert len(parse_sections()[("tech_a.md", "4")]["rows"]) == 2


def test_parse_tables_collects_allow_warn_numbers(dirs):
    doc = standard_doc(TWO_SIDED, note="> WARN許容 #1・#2: 例外経路のため\n")
    write_tech(dirs, "tech_a.md", doc)
    assert parse_sections()[("tech_a.md", "4")]["allow_warn"] == {1, 2}


def test_parse_tables_ignores_quote_without_allow_warn_marker(dirs):
    doc = standard_doc(TWO_SIDED, note="> 単なる注記: #1 は重要\n")
    write_tech(dirs, "tech_a.md", doc)
    assert parse_sections()[("tech_a.md", "4")]["allow_warn"] == set()


def test_parse_tables_reports_duplicate_doc_key(dirs):
    """層ディレクトリ違いで同名になる文書は ERROR（一覧のキーとマーカーが一意にならない）。"""
    write_tech(dirs, "basic/tech_a.md", standard_doc(TWO_SIDED))
    write_tech(dirs, "detail/tech_a.md", standard_doc(TWO_SIDED))
    _, dup_errors = mod.parse_tables()
    assert len(dup_errors) == 1
    assert "文書名 tech_a.md が" in dup_errors[0]
    assert "docs/tech/basic/tech_a.md と衝突している" in dup_errors[0]


def test_parse_tables_reports_no_duplicate_for_distinct_doc_keys(dirs):
    """索引の子は親ディレクトリ名を残すため、基底名が同じでも衝突しない。"""
    write_tech(dirs, "detail/tech_a.md", standard_doc(TWO_SIDED))
    write_tech(dirs, "detail/tech_forge/tech_a.md", standard_doc(TWO_SIDED))
    _, dup_errors = mod.parse_tables()
    assert dup_errors == []


# ── check_structure: 緑パス ──────────────────────────────────

def test_check_structure_passes_two_sided_table(dirs):
    write_tech(dirs, "tech_a.md", standard_doc(TWO_SIDED))
    assert mod.check_structure(parse_sections()) == ([], [])


# ── check_structure: 変異（1項目ずつ壊す）────────────────────

def test_check_structure_detects_heading_without_table(dirs):
    write_tech(dirs, "tech_a.md", "## 4. 分岐一覧\n\n本文だけ\n")
    errors, _ = mod.check_structure(parse_sections())
    assert len(errors) == 1 and "表がない" in errors[0]


def test_check_structure_detects_empty_cell(dirs):
    rows = "| 1 | HP判定 | HP>0 |  |\n| 2 | HP判定 | HP<=0 | 戦闘終了 |\n"
    write_tech(dirs, "tech_a.md", standard_doc(rows))
    errors, _ = mod.check_structure(parse_sections())
    assert any("空セル" in e for e in errors)


def test_check_structure_detects_dash_placeholder_as_empty(dirs):
    rows = "| 1 | HP判定 | HP>0 | — |\n| 2 | HP判定 | HP<=0 | 戦闘終了 |\n"
    write_tech(dirs, "tech_a.md", standard_doc(rows))
    errors, _ = mod.check_structure(parse_sections())
    assert any("空セル" in e for e in errors)


def test_check_structure_detects_non_numeric_index(dirs):
    rows = "| a | HP判定 | HP>0 | 戦闘継続 |\n| 2 | HP判定 | HP<=0 | 戦闘終了 |\n"
    write_tech(dirs, "tech_a.md", standard_doc(rows))
    errors, _ = mod.check_structure(parse_sections())
    assert any("# 列が数値でない" in e for e in errors)


def test_check_structure_detects_non_sequential_index(dirs):
    rows = "| 1 | HP判定 | HP>0 | 戦闘継続 |\n| 3 | HP判定 | HP<=0 | 戦闘終了 |\n"
    write_tech(dirs, "tech_a.md", standard_doc(rows))
    errors, _ = mod.check_structure(parse_sections())
    assert any("連番でない" in e for e in errors)


def test_check_structure_warns_single_row_branch_point(dirs):
    """真偽の片側が欠けた分岐点は WARN（ERROR にはしない）。"""
    write_tech(dirs, "tech_a.md", standard_doc("| 1 | HP判定 | HP>0 | 戦闘継続 |\n"))
    errors, warns = mod.check_structure(parse_sections())
    assert errors == [] and len(warns) == 1 and "1行のみ" in warns[0]


def test_check_structure_suppresses_single_row_warn_by_allow_note(dirs):
    doc = standard_doc("| 1 | HP判定 | HP>0 | 戦闘継続 |\n", note="> WARN許容 #1: 例外経路のため\n")
    write_tech(dirs, "tech_a.md", doc)
    assert mod.check_structure(parse_sections()) == ([], [])


def test_check_structure_keeps_warn_when_allow_note_covers_only_part(dirs):
    """グループの一部しか許容されていなければ抑止しない。"""
    rows = "| 1 | ループA | 1周 | 継続 |\n"
    doc = standard_doc(rows, note="> WARN許容 #2: 別グループ\n")
    write_tech(dirs, "tech_a.md", doc)
    _, warns = mod.check_structure(parse_sections())
    assert len(warns) == 1


def test_check_structure_passes_loop_with_zero_one_two_laps(dirs):
    rows = ("| 1 | ループ判定 | 0周 | 即終了 |\n"
            "| 2 | ループ判定 | 1周 | 1回処理 |\n"
            "| 3 | ループ判定 | 2周 | 2回処理 |\n")
    write_tech(dirs, "tech_a.md", standard_doc(rows))
    assert mod.check_structure(parse_sections()) == ([], [])


def test_check_structure_warns_loop_missing_laps(dirs):
    rows = ("| 1 | ループ判定 | 0周 | 即終了 |\n"
            "| 2 | ループ判定 | 1周 | 1回処理 |\n")
    write_tech(dirs, "tech_a.md", standard_doc(rows))
    _, warns = mod.check_structure(parse_sections())
    assert len(warns) == 1 and "2周" in warns[0]


def test_check_structure_detects_lap_count_outside_loop_named_point(dirs):
    """分岐点名に「ループ」がなくても、周回数の記述があればループ扱いにする。"""
    write_tech(dirs, "tech_a.md", standard_doc("| 1 | リトライ | 1周 | 再試行 |\n"))
    _, warns = mod.check_structure(parse_sections())
    assert "0周・2周" in warns[0]


def test_check_structure_skips_branch_coverage_for_old_format(dirs):
    """旧形式（分岐点・条件列なし）は両側網羅を判定しない。"""
    write_tech(dirs, "tech_a.md", "## 4. 分岐一覧\n\n| # | 期待する振る舞い |\n|---|---|\n| 1 | 戦闘継続 |\n")
    assert mod.check_structure(parse_sections()) == ([], [])


# ── check_tests ──────────────────────────────────────────────

def test_check_tests_passes_when_every_row_has_marker(dirs):
    write_tech(dirs, "tech_a.md", standard_doc(TWO_SIDED))
    write_test(dirs, "A", '"""分岐: tech_a.md §4 #1,2"""\n')
    assert mod.check_tests(parse_sections()) == []


def test_check_tests_returns_empty_when_no_test_file_exists(dirs, monkeypatch, tmp_path):
    """テストが1件も無ければ照合対象ゼロ。マーカー未整備の文書を落とさない。"""
    write_tech(dirs, "tech_a.md", standard_doc(TWO_SIDED))
    monkeypatch.setattr(mod, "ROOT", tmp_path / "none")
    assert mod.check_tests(parse_sections()) == []


def test_check_tests_detects_row_without_test(dirs):
    write_tech(dirs, "tech_a.md", standard_doc(TWO_SIDED))
    write_test(dirs, "A", '"""分岐: tech_a.md §4 #1"""\n')
    errors = mod.check_tests(parse_sections())
    assert len(errors) == 1 and "行 #2 に対応するテストがない" in errors[0]


def test_check_tests_detects_marker_pointing_to_missing_row(dirs):
    write_tech(dirs, "tech_a.md", standard_doc(TWO_SIDED))
    write_test(dirs, "A", '"""分岐: tech_a.md §4 #1,2,3"""\n')
    errors = mod.check_tests(parse_sections())
    assert len(errors) == 1 and "存在しない行 #3" in errors[0]


def test_check_tests_detects_marker_to_document_without_branch_list(dirs):
    write_tech(dirs, "tech_a.md", standard_doc(TWO_SIDED))
    write_test(dirs, "A", '"""分岐: tech_zzz.md §4 #1"""\n')
    errors = mod.check_tests(parse_sections())
    assert any("参照先 tech_zzz.md に分岐一覧がない" in e for e in errors)


def test_check_tests_detects_marker_to_missing_section(dirs):
    write_tech(dirs, "tech_a.md", standard_doc(TWO_SIDED))
    write_test(dirs, "A", '"""分岐: tech_a.md §9 #1"""\n')
    errors = mod.check_tests(parse_sections())
    assert any("§9 の分岐一覧が見つからない" in e for e in errors)


def test_check_tests_allows_omitted_section_when_document_has_one_list(dirs):
    write_tech(dirs, "tech_a.md", standard_doc(TWO_SIDED))
    write_test(dirs, "A", '"""分岐: tech_a.md #1,2"""\n')
    assert mod.check_tests(parse_sections()) == []


def test_check_tests_requires_section_when_document_has_multiple_lists(dirs):
    doc = standard_doc(TWO_SIDED) + "\n" + standard_doc(TWO_SIDED, sec="5. ")
    write_tech(dirs, "tech_a.md", doc)
    write_test(dirs, "A", '"""分岐: tech_a.md #1,2"""\n')
    errors = mod.check_tests(parse_sections())
    assert any("§番号で特定する" in e for e in errors)


def test_check_tests_ignores_documents_no_marker_references(dirs):
    """マーカーが1件も指していない文書は照合対象外（レガシーテスト保護）。"""
    write_tech(dirs, "tech_a.md", standard_doc(TWO_SIDED))
    write_test(dirs, "A", '"""マーカーなし"""\n')
    assert mod.check_tests(parse_sections()) == []


# ── main ─────────────────────────────────────────────────────

def test_main_returns_zero_and_reports_counts(dirs, monkeypatch, capsys):
    write_tech(dirs, "tech_a.md", standard_doc(TWO_SIDED))
    monkeypatch.setattr(mod.sys, "argv", ["check_branch_list.py"])
    assert mod.main() == 0
    assert "分岐一覧 1 件（標準形式 1 / 旧形式 0）: 違反なし" in capsys.readouterr().out


def test_main_returns_one_on_error(dirs, monkeypatch, capsys):
    write_tech(dirs, "tech_a.md", "## 4. 分岐一覧\n\n本文だけ\n")
    monkeypatch.setattr(mod.sys, "argv", ["check_branch_list.py"])
    assert mod.main() == 1
    assert "1 件の違反" in capsys.readouterr().out


def test_main_skips_test_matching_without_flag(dirs, monkeypatch, capsys):
    write_tech(dirs, "tech_a.md", standard_doc(TWO_SIDED))
    write_test(dirs, "A", '"""分岐: tech_a.md §4 #1"""\n')
    monkeypatch.setattr(mod.sys, "argv", ["check_branch_list.py"])
    assert mod.main() == 0


def test_main_runs_test_matching_with_flag(dirs, monkeypatch, capsys):
    write_tech(dirs, "tech_a.md", standard_doc(TWO_SIDED))
    write_test(dirs, "A", '"""分岐: tech_a.md §4 #1"""\n')
    monkeypatch.setattr(mod.sys, "argv", ["check_branch_list.py", "--tests"])
    assert mod.main() == 1
    assert "行 #2 に対応するテストがない" in capsys.readouterr().out


def test_main_suggests_migration_when_old_format_exists(dirs, monkeypatch, capsys):
    write_tech(dirs, "tech_a.md", "## 4. 分岐一覧\n\n| # | 期待する振る舞い |\n|---|---|\n| 1 | 戦闘継続 |\n")
    monkeypatch.setattr(mod.sys, "argv", ["check_branch_list.py"])
    assert mod.main() == 0
    assert "旧形式（分岐点・条件列なし）は改稿時に標準形式へ移行する" in capsys.readouterr().out
