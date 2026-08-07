"""check_docs.py の回帰テスト。

実行: `python -m pytest scripts/tests -q`（リポジトリルートから）

`ROOT` / `OWNERSHIP` / `LEDGER` を `tmp_path` へ差し替え、実リポジトリの内容に
依存させない。各検査は「違反のない文書を通す（緑パス）」と「1項目だけ壊すと
検出する（変異）」を対で置く。
"""

import pytest

import check_docs as mod


@pytest.fixture
def root(tmp_path, monkeypatch):
    """`ROOT` 系を差し替えた空リポジトリを返す。"""
    monkeypatch.setattr(mod, "ROOT", tmp_path)
    monkeypatch.setattr(mod, "OWNERSHIP", tmp_path / "docs" / "spec_ownership.md")
    monkeypatch.setattr(mod, "LEDGER", tmp_path / "docs" / "open_specs.md")
    (tmp_path / "docs").mkdir()
    return tmp_path


def write(root, rel: str, text: str = "x") -> None:
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def rels(paths) -> list[str]:
    return [p.name for p in paths]


# ── targets ──────────────────────────────────────────────────

def test_targets_collects_markdown_recursively(root):
    write(root, "README.md")
    write(root, "docs/design/a.md")
    assert sorted(rels(mod.targets())) == ["README.md", "a.md"]


@pytest.mark.parametrize("rel", [
    "docs/reviews/doc-review/2026-08-01_120000.md",
    "docs/changelog.md",
    "node_modules/a.md",
    "frontend/node_modules/a.md",
    ".claude/worktrees/w/a.md",
])
def test_targets_excludes_generated_and_archive_paths(root, rel):
    write(root, "README.md")
    write(root, rel)
    assert rels(mod.targets()) == ["README.md"]


# ── body_lines / extract_links ───────────────────────────────

def test_body_lines_skips_code_fence(root):
    write(root, "docs/a.md", "本文\n```\nコード\n```\n末尾\n")
    assert [line for _, line in mod.body_lines(root / "docs" / "a.md")] == ["本文", "末尾"]


def test_extract_links_returns_relative_targets_with_line_numbers(root):
    write(root, "docs/a.md", "前文\n[b](b.md)\n")
    assert mod.extract_links(root / "docs" / "a.md") == [(2, "b.md")]


@pytest.mark.parametrize("link", ["[x](https://e.com)", "[x](http://e.com)",
                                  "[x](mailto:a@e.com)", "[x](#anchor)",
                                  "[x](docs/{name}.md)"])
def test_extract_links_ignores_external_anchor_and_template(root, link):
    write(root, "docs/a.md", link + "\n")
    assert mod.extract_links(root / "docs" / "a.md") == []


def test_extract_links_strips_anchor_from_target(root):
    write(root, "docs/a.md", "[x](b.md#section-3)\n")
    assert mod.extract_links(root / "docs" / "a.md") == [(1, "b.md")]


def test_extract_links_accepts_image_and_titled_links(root):
    write(root, "docs/a.md", '![図](img.png)\n[x](b.md "題名")\n')
    assert mod.extract_links(root / "docs" / "a.md") == [(1, "img.png"), (2, "b.md")]


def test_extract_links_ignores_links_inside_code_fence(root):
    write(root, "docs/a.md", "```\n[x](none.md)\n```\n")
    assert mod.extract_links(root / "docs" / "a.md") == []


# ── check_links ──────────────────────────────────────────────

def test_check_links_passes_when_target_exists(root):
    write(root, "docs/a.md", "[b](b.md)\n")
    write(root, "docs/b.md")
    assert mod.check_links(mod.targets()) == []


def test_check_links_detects_broken_link(root):
    write(root, "docs/a.md", "[b](none.md)\n")
    errors = mod.check_links(mod.targets())
    assert errors == ["ERROR docs/a.md:1: リンク切れ → none.md"]


# ── check_reachability ───────────────────────────────────────

def test_check_reachability_passes_for_transitively_linked_docs(root):
    write(root, "README.md", "[a](docs/a.md)\n")
    write(root, "docs/a.md", "[b](b.md)\n")
    write(root, "docs/b.md")
    assert mod.check_reachability(mod.targets()) == []


def test_check_reachability_detects_orphan_doc(root):
    write(root, "README.md", "起点\n")
    write(root, "docs/a.md")
    errors = mod.check_reachability(mod.targets())
    assert len(errors) == 1 and "docs/a.md" in errors[0] and "到達できない" in errors[0]


def test_check_reachability_reaches_from_claude_md_too(root):
    write(root, "CLAUDE.md", "[a](docs/a.md)\n")
    write(root, "docs/a.md")
    assert mod.check_reachability(mod.targets()) == []


def test_check_reachability_ignores_orphans_outside_docs_and_diagrams(root):
    write(root, "README.md", "起点\n")
    write(root, ".claude/project/a.md")
    assert mod.check_reachability(mod.targets()) == []


def test_check_reachability_checks_diagrams_too(root):
    write(root, "README.md", "起点\n")
    write(root, "diagrams/er_diagram.md")
    errors = mod.check_reachability(mod.targets())
    assert len(errors) == 1 and "diagrams/er_diagram.md" in errors[0]


# ── check_ambiguous ──────────────────────────────────────────

def test_check_ambiguous_passes_when_no_vague_word(root):
    write(root, "docs/tech/tech_a.md", "上限は 100 とする\n")
    assert mod.check_ambiguous(mod.targets()) == []


@pytest.mark.parametrize("word", ["適宜", "おおよそ", "TBD", "後日検討", "未定"])
def test_check_ambiguous_detects_each_vague_word(root, word):
    write(root, "docs/tech/tech_a.md", f"値は{word}とする\n")
    errors = mod.check_ambiguous(mod.targets())
    assert len(errors) == 1 and f"曖昧語「{word}」" in errors[0]


def test_check_ambiguous_does_not_flag_undefined_term(root):
    """「未定義」は曖昧語ではない（否定先読み）。"""
    write(root, "docs/tech/tech_a.md", "この値は未定義の状態を持たない\n")
    assert mod.check_ambiguous(mod.targets()) == []


def test_check_ambiguous_allows_line_pointing_to_ledger(root):
    write(root, "docs/tech/tech_a.md", "未定の項目は open_specs.md で管理する\n")
    assert mod.check_ambiguous(mod.targets()) == []


def test_check_ambiguous_skips_non_spec_directories(root):
    """管理台帳・プロセス文書は対象外（「未定」を扱う場でありうる）。"""
    write(root, "docs/development_process.md", "値は未定\n")
    assert mod.check_ambiguous(mod.targets()) == []


def test_check_ambiguous_ignores_code_fence(root):
    write(root, "docs/tech/tech_a.md", "```\nTBD\n```\n")
    assert mod.check_ambiguous(mod.targets()) == []


# ── check_pending ────────────────────────────────────────────

def test_check_pending_passes_without_deferral(root):
    write(root, "docs/tech/tech_a.md", "Phase 3 の基本設計で API を定義する\n")
    assert mod.check_pending(mod.targets()) == []


def test_check_pending_detects_deferral_without_ledger_link(root):
    write(root, "docs/tech/tech_a.md", "詳細は Phase 3 の基本設計で確定する\n")
    errors = mod.check_pending(mod.targets())
    assert len(errors) == 1 and "台帳へリンクしていない" in errors[0]


def test_check_pending_allows_deferral_linked_to_ledger(root):
    write(root, "docs/tech/tech_a.md",
          "詳細は Phase 3 の基本設計で確定する（[台帳](../open_specs.md)）\n")
    write(root, "docs/open_specs.md")
    assert mod.check_pending(mod.targets()) == []


def test_check_pending_skips_the_ledger_itself(root):
    write(root, "docs/open_specs.md", "Phase 3 の基本設計で確定する\n")
    assert mod.check_pending(mod.targets()) == []


def test_check_pending_skips_files_outside_docs_and_diagrams(root):
    write(root, ".claude/project/a.md", "Phase 3 の基本設計で確定する\n")
    assert mod.check_pending(mod.targets()) == []


# ── check_ledger ─────────────────────────────────────────────

def test_check_ledger_passes_when_assertion_matches_reality(root):
    write(root, "docs/open_specs.md", "台帳\n")
    write(root, "docs/a.md", "open_specs.md は現在 3 件\n")
    assert mod.check_ledger(mod.targets()) == []


def test_check_ledger_detects_absent_claim_while_ledger_exists(root):
    write(root, "docs/open_specs.md", "台帳\n")
    write(root, "docs/a.md", "open_specs.md は全解消済み\n")
    errors = mod.check_ledger(mod.targets())
    assert len(errors) == 1 and "実在するのに不在と断定" in errors[0]


def test_check_ledger_detects_present_claim_while_ledger_missing(root):
    write(root, "docs/a.md", "open_specs.md は現在 3 件\n")
    errors = mod.check_ledger(mod.targets())
    assert len(errors) == 1 and "不在なのに実在を前提" in errors[0]


def test_check_ledger_allows_absent_claim_while_ledger_missing(root):
    write(root, "docs/a.md", "open_specs.md は全解消済み\n")
    assert mod.check_ledger(mod.targets()) == []


def test_check_ledger_ignores_lines_without_ledger_mention(root):
    write(root, "docs/open_specs.md", "台帳\n")
    write(root, "docs/a.md", "現在は不在\n")
    assert mod.check_ledger(mod.targets()) == []


# ── parse_ownership / check_ownership ────────────────────────

OWNERSHIP_TABLE = (
    "| トピック | 正 | 許可 | 検出パターン |\n"
    "|---|---|---|---|\n"
    "| tick間隔 | `docs/tech/tick.md` | `CLAUDE.md` | `60秒間隔` |\n"
)


def test_parse_ownership_returns_empty_when_file_missing(root):
    assert mod.parse_ownership() == []


def test_parse_ownership_skips_header_separator_and_patternless_rows(root):
    table = OWNERSHIP_TABLE + "| 空欄 | `docs/a.md` | — | — |\n"
    write(root, "docs/spec_ownership.md", table)
    assert [topic for topic, *_ in mod.parse_ownership()] == ["tick間隔"]


def test_parse_ownership_builds_allow_set_from_canonical_and_allowed(root):
    write(root, "docs/spec_ownership.md", OWNERSHIP_TABLE)
    _, canonical, allow, _ = mod.parse_ownership()[0]
    assert canonical == "docs/tech/tick.md" and allow == {"docs/tech/tick.md", "CLAUDE.md"}


def test_check_ownership_passes_for_canonical_file(root):
    write(root, "docs/spec_ownership.md", OWNERSHIP_TABLE)
    write(root, "docs/tech/tick.md", "tick は 60秒間隔\n")
    assert mod.check_ownership(mod.targets()) == []


def test_check_ownership_passes_for_allowed_file(root):
    write(root, "docs/spec_ownership.md", OWNERSHIP_TABLE)
    write(root, "CLAUDE.md", "tick は 60秒間隔\n")
    assert mod.check_ownership(mod.targets()) == []


def test_check_ownership_detects_duplication_outside_allow_set(root):
    write(root, "docs/spec_ownership.md", OWNERSHIP_TABLE)
    write(root, "docs/design/systems/battle.md", "tick は 60秒間隔\n")
    errors = mod.check_ownership(mod.targets())
    assert len(errors) == 1 and "docs/tech/tick.md が正" in errors[0]


def test_check_ownership_skips_files_outside_scanned_areas(root):
    write(root, "docs/spec_ownership.md", OWNERSHIP_TABLE)
    write(root, "README.md", "tick は 60秒間隔\n")
    assert mod.check_ownership(mod.targets()) == []


# ── main ─────────────────────────────────────────────────────

def test_main_runs_all_checks_and_returns_zero(root, monkeypatch, capsys):
    write(root, "README.md", "[a](docs/a.md)\n")
    write(root, "docs/a.md", "本文\n")
    monkeypatch.setattr(mod.sys, "argv", ["check_docs.py"])
    assert mod.main() == 0
    out = capsys.readouterr().out
    for label in ("リンク", "索引到達性", "曖昧語", "正の逸脱", "決定先送り", "台帳存否"):
        assert f"[{label}] OK" in out
    assert "2 files checked: 違反なし" in out


def test_main_returns_one_and_counts_violations(root, monkeypatch, capsys):
    write(root, "README.md", "[a](none.md)\n")
    monkeypatch.setattr(mod.sys, "argv", ["check_docs.py"])
    assert mod.main() == 1
    assert "1 件の違反" in capsys.readouterr().out


def test_main_runs_only_selected_check(root, monkeypatch, capsys):
    write(root, "README.md", "起点\n")
    write(root, "docs/a.md", "本文\n")
    monkeypatch.setattr(mod.sys, "argv", ["check_docs.py", "--links"])
    assert mod.main() == 0
    out = capsys.readouterr().out
    assert "[リンク] OK" in out and "索引到達性" not in out
