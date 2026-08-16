"""check_mermaid.py の回帰テスト。

実行: `python -m pytest scripts/tests -q`（リポジトリルートから）

`ROOT` を `tmp_path` へ差し替え、実リポジトリの内容に依存させない。
各検査は「違反のない図を通す（緑パス）」と「1項目だけ壊すと検出する（変異）」を対で置く。
使い捨て版が誤検知した2点（`participant` の `par` 誤一致 / ER図カーディナリティの
波括弧誤計上）は、誤検知しないことを明示的なテストで固定する。
"""

import pytest

import check_mermaid as mod


@pytest.fixture
def root(tmp_path, monkeypatch):
    monkeypatch.setattr(mod, "ROOT", tmp_path)
    (tmp_path / "docs").mkdir()
    return tmp_path


def write(root, rel: str, text: str) -> None:
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def fences(root):
    return mod.parse_fences(mod.targets())


def diagram(body: str) -> str:
    return "```mermaid\n" + body + "```\n"


ER_OK = diagram(
    "%%{init: {'theme': 'default'} }%%\n"
    "erDiagram\n"
    "    Player ||--o{ BattleLog : \"has\"\n"
    "    Player ||--o| BossRushState : \"has\"\n"
    "    Player {\n"
    "        uuid id PK\n"
    "    }\n"
    "    BattleLog {\n"
    "        uuid id PK\n"
    "    }\n"
    "    BossRushState {\n"
    "        uuid id PK\n"
    "    }\n"
)

SEQ_OK = diagram(
    "sequenceDiagram\n"
    "    participant F as Frontend\n"
    "    participant B as Backend\n"
    "    loop 60秒ごと\n"
    "        F->>B: GET /tick\n"
    "        alt 進行あり\n"
    "            B-->>F: 200\n"
    "        else 進行なし\n"
    "            B-->>F: 204\n"
    "        end\n"
    "    end\n"
    "    opt 復帰時\n"
    "        F->>B: POST /offline\n"
    "    end\n"
)


# ── targets / parse_fences ───────────────────────────────────

@pytest.mark.parametrize("rel", [
    "docs/reviews/diagrams-review/2026-08-01_120000.md",
    "docs/changelog.md",
    "node_modules/a.md",
    ".claude/worktrees/w/a.md",
])
def test_targets_excludes_generated_and_archive_paths(root, rel):
    write(root, "docs/a.md", "x")
    write(root, rel, "x")
    assert [p.name for p in mod.targets()] == ["a.md"]


def test_parse_fences_collects_only_mermaid_blocks(root):
    write(root, "docs/a.md", "前文\n```bash\necho x\n```\n" + ER_OK)
    fs = fences(root)
    assert len(fs) == 1
    assert fs[0].kind == "erDiagram"
    assert fs[0].closed is True


def test_parse_fences_reads_kind_after_init_directive(root):
    write(root, "docs/a.md", ER_OK)
    assert fences(root)[0].kind == "erDiagram"


# ── フェンス ─────────────────────────────────────────────────

def test_fence_passes_closed_diagram(root):
    write(root, "docs/a.md", ER_OK)
    assert mod.check_fence(fences(root)) == []


def test_fence_detects_unclosed_diagram(root):
    write(root, "docs/a.md", "```mermaid\nerDiagram\n")
    errors = mod.check_fence(fences(root))
    assert len(errors) == 1 and "閉じられていない" in errors[0]


# ── ブロック ─────────────────────────────────────────────────

def test_blocks_passes_balanced_diagram(root):
    write(root, "docs/a.md", SEQ_OK)
    assert mod.check_blocks(fences(root)) == []


def test_blocks_does_not_count_participant_as_par(root):
    """使い捨て版の誤検知1: `participant` が `par` ブロックに一致していた。"""
    write(root, "docs/a.md", diagram("sequenceDiagram\n    participant F as Frontend\n"))
    assert mod.check_blocks(fences(root)) == []


def test_blocks_counts_par_when_standalone(root):
    write(root, "docs/a.md", diagram("sequenceDiagram\n    par 並行\n        F->>B: a\n"))
    errors = mod.check_blocks(fences(root))
    assert len(errors) == 1 and "par に対応する end が無い" in errors[0]


def test_blocks_detects_missing_end(root):
    write(root, "docs/a.md", diagram("graph TD\n    subgraph 塔\n        A --> B\n"))
    errors = mod.check_blocks(fences(root))
    assert len(errors) == 1 and "subgraph に対応する end が無い" in errors[0]


def test_blocks_detects_extra_end(root):
    write(root, "docs/a.md", diagram("graph TD\n    A --> B\n    end\n"))
    errors = mod.check_blocks(fences(root))
    assert len(errors) == 1 and "対応する開始行の無い end" in errors[0]


# ── 波括弧 ───────────────────────────────────────────────────

def test_braces_passes_balanced_entities(root):
    write(root, "docs/a.md", ER_OK)
    assert mod.check_braces(fences(root)) == []


def test_braces_ignores_er_cardinality(root):
    """使い捨て版の誤検知2: `||--o{` `}o--||` を波括弧として数えていた。"""
    write(root, "docs/a.md", diagram(
        "erDiagram\n"
        "    Player ||--o{ BattleLog : \"has\"\n"
        "    BattleLog }o--|| Player : \"belongs\"\n"
        "    Tower ||..o| Dungeon : \"in\"\n"
    ))
    assert mod.check_braces(fences(root)) == []


def test_braces_detects_missing_close(root):
    write(root, "docs/a.md", diagram("erDiagram\n    Player {\n        uuid id PK\n"))
    errors = mod.check_braces(fences(root))
    assert len(errors) == 1 and "{ に対応する } が無い" in errors[0]


def test_braces_detects_extra_close(root):
    write(root, "docs/a.md", diagram("erDiagram\n    Player {\n        uuid id PK\n    }\n    }\n"))
    errors = mod.check_braces(fences(root))
    assert len(errors) == 1 and "対応する { の無い }" in errors[0]


# ── ERリレーション ───────────────────────────────────────────

def test_relations_passes_defined_endpoints(root):
    write(root, "docs/a.md", ER_OK)
    assert mod.check_relations(fences(root)) == []


def test_relations_accepts_entity_defined_in_another_file(root):
    """ER図は索引 + 子ファイル構成のため、定義は別ファイルにありうる。"""
    write(root, "docs/er/a.md", diagram(
        "erDiagram\n"
        "    Player ||--o{ BattleLog : \"has\"\n"
        "    BattleLog {\n        uuid id PK\n    }\n"
    ))
    write(root, "docs/er/b.md", diagram("erDiagram\n    Player {\n        uuid id PK\n    }\n"))
    assert mod.check_relations(fences(root)) == []


def test_relations_detects_undefined_endpoint(root):
    write(root, "docs/a.md", diagram(
        "erDiagram\n"
        "    Player ||--o{ BattleLog : \"has\"\n"
        "    Player {\n        uuid id PK\n    }\n"
    ))
    errors = mod.check_relations(fences(root))
    assert len(errors) == 1 and "BattleLog が" in errors[0]


def test_relations_ignores_non_er_diagrams(root):
    write(root, "docs/a.md", diagram("graph TD\n    A --> B\n"))
    assert mod.check_relations(fences(root)) == []


# ── main ─────────────────────────────────────────────────────

def test_main_returns_zero_when_clean(root, monkeypatch, capsys):
    write(root, "docs/a.md", ER_OK + SEQ_OK)
    monkeypatch.setattr(mod.sys, "argv", ["check_mermaid.py"])
    assert mod.main() == 0
    assert "違反なし" in capsys.readouterr().out


def test_main_returns_one_and_reports_count(root, monkeypatch, capsys):
    write(root, "docs/a.md", diagram("graph TD\n    subgraph 塔\n        A --> B\n"))
    monkeypatch.setattr(mod.sys, "argv", ["check_mermaid.py"])
    assert mod.main() == 1
    assert "1 件の違反" in capsys.readouterr().out


def test_main_runs_only_selected_check(root, monkeypatch, capsys):
    write(root, "docs/a.md", diagram("graph TD\n    subgraph 塔\n        A --> B\n"))
    monkeypatch.setattr(mod.sys, "argv", ["check_mermaid.py", "--braces"])
    assert mod.main() == 0
    out = capsys.readouterr().out
    assert "[波括弧] OK" in out and "[ブロック]" not in out
