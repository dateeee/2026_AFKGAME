"""check_schema_triple.py の回帰テスト。

実行: `python -m pytest scripts/tests -q`（リポジトリルートから）

定義書 / ER図 / models の三者を `tmp_path` に最小構成で作り、`ROOT` 系の定数を
差し替えて検証する。各検査は「三者が一致した状態を通す（緑パス）」と
「1か所だけ崩すと検出する（変異）」を対で置く。
"""

from types import SimpleNamespace

import pytest

import check_schema_triple as mod


# ── 最小の三者構成 ───────────────────────────────────────────

DEF_ROWS = ["| `id` | INTEGER | 不可 | — | PK |",
            "| `name` | TEXT | 不可 | — | UNIQUE |"]
ER_ATTRS = ["int id PK", "string name UK"]
MODEL_COLS = ['id: Mapped[int] = mapped_column(primary_key=True)',
              'name: Mapped[str] = mapped_column(unique=True)']


def def_doc(rows, table="players", phase="Phase 1", cls="Player",
            model_file="player.py", impl=True, uniques=()):
    parts = [f"## 1. `{table}`（{phase}）", ""]
    if impl:
        parts += [f"実装: `backend/app/models/{model_file}` `{cls}`", ""]
    for line in uniques:
        parts += [f"一意制約: {line}", ""]
    parts += ["| 列 | 型 | NULL | 既定 | 備考 |", "|---|---|---|---|---|"]
    parts += list(rows)
    return "\n".join(parts) + "\n"


def er_doc(attrs, entity="Player"):
    body = "\n".join(f"        {a}" for a in attrs)
    return f"```mermaid\nerDiagram\n    {entity} {{\n{body}\n    }}\n```\n"


def model_doc(cols, cls="Player", table="players", table_args=""):
    lines = [f"class {cls}(Base):", f'    __tablename__ = "{table}"', ""]
    if table_args:
        lines += [f"    __table_args__ = ({table_args},)", ""]
    lines += [f"    {c}" for c in cols]
    return "\n".join(lines) + "\n"


def index_doc(rows=(("`Player`", "player.md"),)):
    lines = ["| 領域 | エンティティ | ファイル |", "|---|---|---|"]
    lines += [f"| 領域 | {ents} | [図](er_diagram/{fname}) |" for ents, fname in rows]
    return "\n".join(lines) + "\n"


@pytest.fixture(autouse=True)
def clear_parse_errors():
    """`PARSE_ERRORS` はモジュール変数なのでテストごとに初期化する。"""
    mod.PARSE_ERRORS.clear()
    yield
    mod.PARSE_ERRORS.clear()


@pytest.fixture
def repo(tmp_path, monkeypatch):
    """三者一致した最小リポジトリを作り、書き換え用のパスを返す。"""
    defs = tmp_path / "docs" / "tech" / "basic" / "tech_db"
    er = tmp_path / "docs" / "diagrams" / "er_diagram"
    models = tmp_path / "backend" / "app" / "models"
    for d in (defs, er, models):
        d.mkdir(parents=True)
    index = tmp_path / "docs" / "diagrams" / "er_diagram.md"

    monkeypatch.setattr(mod, "ROOT", tmp_path)
    monkeypatch.setattr(mod, "DEF_DIR", defs)
    monkeypatch.setattr(mod, "ER_DIR", er)
    monkeypatch.setattr(mod, "ER_INDEX", index)
    monkeypatch.setattr(mod, "MODELS_DIR", models)

    r = SimpleNamespace(root=tmp_path, def_dir=defs, er_dir=er, models_dir=models, index=index)
    r.write_def = lambda text, name="player.md": (defs / name).write_text(text, encoding="utf-8")
    r.write_er = lambda text, name="player.md": (er / name).write_text(text, encoding="utf-8")
    r.write_model = lambda text, name="player.py": (models / name).write_text(text, encoding="utf-8")
    r.write_index = lambda text: index.write_text(text, encoding="utf-8")

    r.write_def(def_doc(DEF_ROWS))
    r.write_er(er_doc(ER_ATTRS))
    r.write_model(model_doc(MODEL_COLS))
    r.write_index(index_doc())
    return r


def sources() -> mod.Sources:
    er, er_files = mod.parse_er()
    return mod.Sources(defs=mod.parse_definitions(), er=er, er_files=er_files,
                       models=mod.parse_models())


ALL_CHECKS = (mod.check_columns, mod.check_tags, mod.check_unique, mod.check_nofk,
              mod.check_nullable, mod.check_naming, mod.check_index)


# ── 緑パス ───────────────────────────────────────────────────

def test_all_checks_pass_on_consistent_triple(repo):
    s = sources()
    assert [e for fn in ALL_CHECKS for e in fn(s)] == []
    assert mod.PARSE_ERRORS == []


# ── parse_definitions ────────────────────────────────────────

def test_parse_definitions_reads_table_columns_and_impl(repo):
    d = mod.parse_definitions()["players"]
    assert (d.cls, d.model_file) == ("Player", "player.py")
    assert [c.name for c in d.columns] == ["id", "name"]
    assert d.columns[0].pk is True and d.columns[1].unique is True


def test_parse_definitions_marks_nullable_from_null_column(repo):
    repo.write_def(def_doc(["| `id` | INTEGER | 不可 | — | PK |",
                            "| `memo` | TEXT | 可 | — | 備考 |"]))
    assert [c.nullable for c in mod.parse_definitions()["players"].columns] == [False, True]


def test_parse_definitions_reads_foreign_key_target(repo):
    repo.write_def(def_doc(["| `player_id` | INTEGER | 不可 | — | FK → `players.id` |"]))
    assert mod.parse_definitions()["players"].columns[0].fk_target == "players.id"


def test_parse_definitions_marks_unimplemented_table(repo):
    repo.write_def(def_doc(DEF_ROWS, phase="Phase 4・未実装"))
    assert mod.parse_definitions()["players"].unimplemented is True


def test_parse_definitions_reads_unique_constraint_line(repo):
    repo.write_def(def_doc(DEF_ROWS, uniques=["`uq_players_a_b` = (`a`, `b`)"]))
    assert mod.parse_definitions()["players"].uniques == [("uq_players_a_b", ("a", "b"))]


def test_parse_definitions_stops_columns_at_other_heading(repo):
    repo.write_def(def_doc(DEF_ROWS) + "\n## インデックス\n\n| `x` | y | 不可 | — | z |\n")
    assert [c.name for c in mod.parse_definitions()["players"].columns] == ["id", "name"]


def test_parse_definitions_reports_duplicate_table(repo):
    repo.write_def(def_doc(DEF_ROWS) + "\n" + def_doc(DEF_ROWS))
    mod.parse_definitions()
    assert any("定義が重複している" in e for e in mod.PARSE_ERRORS)


def test_parse_definitions_reports_unparsable_unique_line(repo):
    repo.write_def(def_doc(DEF_ROWS, uniques=["名前を書き忘れた"]))
    mod.parse_definitions()
    assert any("一意制約行を解析できない" in e for e in mod.PARSE_ERRORS)


def test_parse_definitions_reports_unparsable_column_name(repo):
    repo.write_def(def_doc(["| id | INTEGER | 不可 | — | PK |"]))
    mod.parse_definitions()
    assert any("列名を解析できない" in e for e in mod.PARSE_ERRORS)


# ── parse_er ─────────────────────────────────────────────────

def test_parse_er_reads_entity_attributes_and_tags(repo):
    entities, per_file = mod.parse_er()
    assert [a.name for a in entities["Player"].attrs] == ["id", "name"]
    assert entities["Player"].attrs[0].tags == {"PK"}
    assert per_file == {"player.md": ["Player"]}


def test_parse_er_keeps_attribute_comment(repo):
    repo.write_er(er_doc(['int id PK', 'string memo "nullable 備考"']))
    assert mod.parse_er()[0]["Player"].attrs[1].comment == "nullable 備考"


def test_parse_er_ignores_content_outside_er_diagram_fence(repo):
    repo.write_er("```mermaid\nflowchart TD\n    A --> B\n```\n" + er_doc(ER_ATTRS))
    assert set(mod.parse_er()[0]) == {"Player"}


def test_parse_er_reports_duplicate_entity(repo):
    repo.write_er(er_doc(ER_ATTRS) + er_doc(ER_ATTRS), name="dup.md")
    mod.parse_er()
    assert any("定義が重複している" in e for e in mod.PARSE_ERRORS)


def test_parse_er_reports_unparsable_attribute(repo):
    repo.write_er(er_doc(["int id PK", "壊れた行"]))
    mod.parse_er()
    assert any("属性行を解析できない" in e for e in mod.PARSE_ERRORS)


def test_parse_er_reports_unknown_tag(repo):
    repo.write_er(er_doc(["int id PK", "string name XX"]))
    mod.parse_er()
    assert any("未知のキータグ" in e for e in mod.PARSE_ERRORS)


# ── parse_models ─────────────────────────────────────────────

def test_parse_models_reads_columns_and_flags(repo):
    m = mod.parse_models()["players"]
    assert (m.cls, [c.name for c in m.columns]) == ("Player", ["id", "name"])
    assert m.columns[0].pk is True and m.columns[1].unique is True


def test_parse_models_skips_relationship_attributes(repo):
    repo.write_model(model_doc(MODEL_COLS + ['items: Mapped[list["Item"]] = relationship()']))
    assert [c.name for c in mod.parse_models()["players"].columns] == ["id", "name"]


def test_parse_models_reads_multiline_column_statement(repo):
    repo.write_model(model_doc([
        'id: Mapped[int] = mapped_column(primary_key=True)',
        'name: Mapped[str] = mapped_column(\n        String(50),\n        unique=True,\n    )',
    ]))
    assert mod.parse_models()["players"].columns[1].unique is True


def test_parse_models_reads_unique_constraint_from_table_args(repo):
    repo.write_model(model_doc(MODEL_COLS,
                               table_args='UniqueConstraint("a", "b", name="uq_players_a_b")'))
    assert mod.parse_models()["players"].uniques == [("uq_players_a_b", ("a", "b"))]


def test_parse_models_distinguishes_annotation_and_keyword_nullable(repo):
    repo.write_model(model_doc([
        'id: Mapped[int] = mapped_column(primary_key=True)',
        'memo: Mapped[str | None] = mapped_column(nullable=True)',
    ]))
    memo = mod.parse_models()["players"].columns[1]
    assert (memo.ann_nullable, memo.kw_nullable, memo.nullable) == (True, True, True)


def test_parse_models_reports_duplicate_tablename(repo):
    repo.write_model(model_doc(MODEL_COLS) + "\n" + model_doc(MODEL_COLS, cls="Player2"))
    mod.parse_models()
    assert any("__tablename__ `players` が重複" in e for e in mod.PARSE_ERRORS)


def test_strip_comment_keeps_hash_inside_string():
    assert mod._strip_comment('x = "a#b"  # 注釈').strip() == 'x = "a#b"'


def test_statement_joins_until_brackets_close():
    lines = ["    a: Mapped[int] = mapped_column(", "        primary_key=True,", "    )", "    b = 1"]
    stmt, end = mod._statement(lines, 0)
    assert end == 2 and "primary_key=True" in stmt and "b = 1" not in stmt


# ── _seq_diff ────────────────────────────────────────────────

def test_seq_diff_returns_empty_for_identical_sequences():
    assert mod._seq_diff("列", ["a", "b"], ["a", "b"]) == []


def test_seq_diff_reports_missing_and_extra():
    msgs = mod._seq_diff("列", ["a", "b"], ["a", "c"])
    assert msgs == ["列: 欠落 ['b']", "列: 余剰 ['c']"]


def test_seq_diff_reports_order_difference():
    msgs = mod._seq_diff("列", ["a", "b"], ["b", "a"])
    assert len(msgs) == 1 and "並び順が異なる" in msgs[0]


# ── check_columns ────────────────────────────────────────────

def test_check_columns_detects_missing_impl_line(repo):
    repo.write_def(def_doc(DEF_ROWS, impl=False))
    errors = mod.check_columns(sources())
    assert any("「実装:」行が無く" in e for e in errors)


def test_check_columns_detects_entity_missing_from_er(repo):
    repo.write_er(er_doc(ER_ATTRS, entity="Other"))
    errors = mod.check_columns(sources())
    assert any("ER図にエンティティ Player が無い" in e for e in errors)


def test_check_columns_detects_column_missing_in_er(repo):
    repo.write_er(er_doc(["int id PK"]))
    errors = mod.check_columns(sources())
    assert any("ER図の列: 欠落 ['name']" in e for e in errors)


def test_check_columns_detects_column_order_difference(repo):
    repo.write_er(er_doc(list(reversed(ER_ATTRS))))
    errors = mod.check_columns(sources())
    assert any("ER図の列: 並び順が異なる" in e for e in errors)


def test_check_columns_detects_missing_model(repo):
    (repo.models_dir / "player.py").unlink()
    errors = mod.check_columns(sources())
    assert any('models に __tablename__ = "players" が無い' in e for e in errors)


def test_check_columns_allows_missing_model_when_marked_unimplemented(repo):
    repo.write_def(def_doc(DEF_ROWS, phase="Phase 4・未実装"))
    (repo.models_dir / "player.py").unlink()
    assert mod.check_columns(sources()) == []


def test_check_columns_detects_implemented_table_marked_unimplemented(repo):
    repo.write_def(def_doc(DEF_ROWS, phase="Phase 4・未実装"))
    errors = mod.check_columns(sources())
    assert any("定義書は未実装だが models に実装がある" in e for e in errors)


def test_check_columns_detects_class_name_mismatch(repo):
    repo.write_model(model_doc(MODEL_COLS, cls="PlayerModel"))
    errors = mod.check_columns(sources())
    assert any("モデルクラス名が不一致" in e for e in errors)


def test_check_columns_detects_model_file_mismatch(repo):
    (repo.models_dir / "player.py").unlink()
    repo.write_model(model_doc(MODEL_COLS), name="players.py")
    errors = mod.check_columns(sources())
    assert any("実装ファイルが不一致" in e for e in errors)


def test_check_columns_detects_extra_column_in_model(repo):
    repo.write_model(model_doc(MODEL_COLS + ['memo: Mapped[str] = mapped_column()']))
    errors = mod.check_columns(sources())
    assert any("models の列: 余剰 ['memo']" in e for e in errors)


def test_check_columns_excludes_unimplemented_column_from_model_comparison(repo):
    repo.write_def(def_doc(DEF_ROWS + ["| `memo` | TEXT | 不可 | — | 未実装 |"]))
    repo.write_er(er_doc(ER_ATTRS + ["string memo"]))
    assert mod.check_columns(sources()) == []


def test_check_columns_detects_table_absent_from_definitions(repo):
    repo.write_model(model_doc(["id: Mapped[int] = mapped_column(primary_key=True)"],
                               cls="Ghost", table="ghosts"), name="ghost.py")
    errors = mod.check_columns(sources())
    assert any("`ghosts` が定義書に無い" in e for e in errors)


# ── check_tags ───────────────────────────────────────────────

def test_check_tags_detects_missing_pk_tag_in_er(repo):
    repo.write_er(er_doc(["int id", "string name UK"]))
    errors = mod.check_tags(sources())
    assert any("PK タグが不足" in e for e in errors)


def test_check_tags_detects_extra_uk_tag_in_er(repo):
    repo.write_er(er_doc(["int id PK", "string name UK", "string memo UK"]))
    repo.write_def(def_doc(DEF_ROWS + ["| `memo` | TEXT | 不可 | — | 備考 |"]))
    repo.write_model(model_doc(MODEL_COLS + ['memo: Mapped[str] = mapped_column()']))
    errors = mod.check_tags(sources())
    assert any("UK タグが余剰" in e for e in errors)


def test_check_tags_requires_uk_on_every_column_of_composite_unique(repo):
    repo.write_def(def_doc(["| `id` | INTEGER | 不可 | — | PK |",
                            "| `a` | TEXT | 不可 | — | 備考 |",
                            "| `b` | TEXT | 不可 | — | 備考 |"],
                           uniques=["`uq_players_a_b` = (`a`, `b`)"]))
    repo.write_er(er_doc(["int id PK", "string a UK", "string b"]))
    errors = mod.check_tags(sources())
    assert any("UK タグが不足" in e and "複合一意は構成列すべてに UK" in e for e in errors)


def test_check_tags_detects_missing_fk_tag_in_er(repo):
    repo.write_def(def_doc(["| `id` | INTEGER | 不可 | — | PK |",
                            "| `player_id` | INTEGER | 不可 | — | FK → `players.id` |"]))
    repo.write_er(er_doc(["int id PK", "int player_id"]))
    repo.write_model(model_doc([
        'id: Mapped[int] = mapped_column(primary_key=True)',
        'player_id: Mapped[int] = mapped_column(ForeignKey("players.id"))',
    ]))
    errors = mod.check_tags(sources())
    assert any("FK タグが不足" in e for e in errors)


def test_check_tags_detects_primary_key_mismatch_in_model(repo):
    repo.write_model(model_doc(['id: Mapped[int] = mapped_column()',
                                'name: Mapped[str] = mapped_column(unique=True)']))
    errors = mod.check_tags(sources())
    assert any("primary_key が定義書と不一致" in e for e in errors)


def test_check_tags_detects_foreign_key_target_mismatch_in_model(repo):
    repo.write_def(def_doc(["| `id` | INTEGER | 不可 | — | PK |",
                            "| `player_id` | INTEGER | 不可 | — | FK → `players.id` |"]))
    repo.write_er(er_doc(["int id PK", "int player_id FK"]))
    repo.write_model(model_doc([
        'id: Mapped[int] = mapped_column(primary_key=True)',
        'player_id: Mapped[int] = mapped_column(ForeignKey("users.id"))',
    ]))
    errors = mod.check_tags(sources())
    assert any("FK参照先が不一致" in e for e in errors)


def test_check_tags_detects_unique_flag_mismatch_in_model(repo):
    repo.write_model(model_doc(['id: Mapped[int] = mapped_column(primary_key=True)',
                                'name: Mapped[str] = mapped_column()']))
    errors = mod.check_tags(sources())
    assert any("unique が定義書と不一致" in e for e in errors)


# ── check_unique ─────────────────────────────────────────────

def composite_repo(repo, table_args='UniqueConstraint("a", "b", name="uq_players_a_b")'):
    repo.write_def(def_doc(["| `id` | INTEGER | 不可 | — | PK |",
                            "| `a` | TEXT | 不可 | — | 備考 |",
                            "| `b` | TEXT | 不可 | — | 備考 |"],
                           uniques=["`uq_players_a_b` = (`a`, `b`)"]))
    repo.write_er(er_doc(["int id PK", "string a UK", "string b UK"]))
    repo.write_model(model_doc(['id: Mapped[int] = mapped_column(primary_key=True)',
                                'a: Mapped[str] = mapped_column()',
                                'b: Mapped[str] = mapped_column()'],
                               table_args=table_args))


def test_check_unique_passes_when_constraint_matches(repo):
    composite_repo(repo)
    assert mod.check_unique(sources()) == []


def test_check_unique_detects_constraint_missing_from_model(repo):
    composite_repo(repo, table_args="")
    errors = mod.check_unique(sources())
    assert any("`uq_players_a_b` が models に無い" in e for e in errors)


def test_check_unique_detects_column_set_mismatch(repo):
    composite_repo(repo, table_args='UniqueConstraint("a", name="uq_players_a_b")')
    errors = mod.check_unique(sources())
    assert any("構成列が不一致" in e for e in errors)


def test_check_unique_detects_constraint_missing_from_definition(repo):
    repo.write_model(model_doc(MODEL_COLS,
                               table_args='UniqueConstraint("id", name="uq_players_id")'))
    errors = mod.check_unique(sources())
    assert any("`uq_players_id` が定義書" in e for e in errors)


# ── check_nofk ───────────────────────────────────────────────

def nofk_repo(repo, er_attr="int item_id", model_col='item_id: Mapped[int] = mapped_column()'):
    repo.write_def(def_doc(["| `id` | INTEGER | 不可 | — | PK |",
                            "| `item_id` | INTEGER | 不可 | — | FKなし（親 §4-6） |"]))
    repo.write_er(er_doc(["int id PK", er_attr]))
    repo.write_model(model_doc(['id: Mapped[int] = mapped_column(primary_key=True)', model_col]))


def test_check_nofk_passes_when_no_foreign_key_anywhere(repo):
    nofk_repo(repo)
    assert mod.check_nofk(sources()) == []


def test_check_nofk_detects_fk_tag_in_er(repo):
    nofk_repo(repo, er_attr="int item_id FK")
    errors = mod.check_nofk(sources())
    assert any("定義書は「FKなし」だが ER図が FK タグを持つ" in e for e in errors)


def test_check_nofk_detects_foreign_key_in_model(repo):
    nofk_repo(repo, model_col='item_id: Mapped[int] = mapped_column(ForeignKey("items.id"))')
    errors = mod.check_nofk(sources())
    assert any('ForeignKey("items.id") がある' in e for e in errors)


def test_check_nofk_detects_contradictory_declaration_in_definition(repo):
    repo.write_def(def_doc(["| `id` | INTEGER | 不可 | — | PK |",
                            "| `item_id` | INTEGER | 不可 | — | FKなし / FK → `items.id` |"]))
    repo.write_er(er_doc(["int id PK", "int item_id FK"]))
    repo.write_model(model_doc(['id: Mapped[int] = mapped_column(primary_key=True)',
                                'item_id: Mapped[int] = mapped_column(ForeignKey("items.id"))']))
    errors = mod.check_nofk(sources())
    assert any("同時に宣言している" in e for e in errors)


# ── check_nullable ───────────────────────────────────────────

def nullable_repo(repo, null="可", er_attr='string memo "nullable"',
                  model_col='memo: Mapped[str | None] = mapped_column()'):
    repo.write_def(def_doc(["| `id` | INTEGER | 不可 | — | PK |",
                            f"| `memo` | TEXT | {null} | — | 備考 |"]))
    repo.write_er(er_doc(["int id PK", er_attr]))
    repo.write_model(model_doc(['id: Mapped[int] = mapped_column(primary_key=True)', model_col]))


def test_check_nullable_passes_when_all_three_agree(repo):
    nullable_repo(repo)
    assert mod.check_nullable(sources()) == []


def test_check_nullable_detects_missing_note_in_er(repo):
    nullable_repo(repo, er_attr="string memo")
    errors = mod.check_nullable(sources())
    assert any("ER図の注記に nullable が無い" in e for e in errors)


def test_check_nullable_detects_er_note_contradicting_definition(repo):
    nullable_repo(repo, null="不可", model_col='memo: Mapped[str] = mapped_column()')
    errors = mod.check_nullable(sources())
    assert any("ER図は nullable と注記するが 定義書は NULL 不可" in e for e in errors)


def test_check_nullable_detects_model_disagreeing_with_definition(repo):
    nullable_repo(repo, model_col='memo: Mapped[str] = mapped_column()')
    errors = mod.check_nullable(sources())
    assert any("NULL 可否が定義書と不一致" in e for e in errors)


def test_check_nullable_detects_annotation_and_keyword_conflict_in_model(repo):
    nullable_repo(repo, model_col='memo: Mapped[str | None] = mapped_column(nullable=False)')
    errors = mod.check_nullable(sources())
    assert any("models 内で注釈と nullable= が矛盾" in e for e in errors)


# ── check_naming ─────────────────────────────────────────────

def test_check_naming_passes_for_conforming_name(repo):
    repo.write_def(def_doc(DEF_ROWS, uniques=["`uq_players_a_b` = (`a`, `b`)"]))
    assert mod.check_naming(sources()) == []


def test_check_naming_detects_wrong_prefix(repo):
    repo.write_def(def_doc(DEF_ROWS, uniques=["`uq_player_a_b` = (`a`, `b`)"]))
    errors = mod.check_naming(sources())
    assert any("命名規約に反する" in e for e in errors)


def test_check_naming_detects_word_count_mismatch(repo):
    repo.write_def(def_doc(DEF_ROWS, uniques=["`uq_players_a` = (`a`, `b`)"]))
    errors = mod.check_naming(sources())
    assert any("と対応していない" in e for e in errors)


# ── check_index ──────────────────────────────────────────────

def test_check_index_detects_missing_index_file(repo):
    repo.index.unlink()
    errors = mod.check_index(sources())
    assert len(errors) == 1 and "ER図の索引が見つからない" in errors[0]


def test_check_index_detects_entity_missing_from_index(repo):
    repo.write_er(er_doc(ER_ATTRS) + er_doc(["int id PK"], entity="Item"))
    errors = mod.check_index(sources())
    assert any("索引に無い → ['Item']" in e for e in errors)


def test_check_index_detects_entity_listed_but_absent(repo):
    repo.write_index(index_doc(rows=[("`Player` / `Ghost`", "player.md")]))
    errors = mod.check_index(sources())
    assert any("Ghost を挙げるが不在（どの図にも定義が無い）" in e for e in errors)


def test_check_index_points_to_actual_file_for_misplaced_entity(repo):
    repo.write_er(er_doc(["int id PK"], entity="Item"), name="item.md")
    repo.write_index(index_doc(rows=[("`Player` / `Item`", "player.md"), ("`Item`", "item.md")]))
    errors = mod.check_index(sources())
    assert any("実際の定義は docs/diagrams/er_diagram/item.md" in e for e in errors)


def test_check_index_detects_link_to_nonexistent_file(repo):
    repo.write_index(index_doc() + "| 領域 | `Ghost` | [図](er_diagram/ghost.md) |\n")
    errors = mod.check_index(sources())
    assert any("索引が指す ghost.md" in e for e in errors)


def test_check_index_detects_unregistered_file(repo):
    repo.write_er(er_doc(["int id PK"], entity="Item"), name="item.md")
    repo.write_index(index_doc())
    errors = mod.check_index(sources())
    assert any("item.md が索引に登録されていない" in e for e in errors)


# ── main ─────────────────────────────────────────────────────

def test_main_returns_zero_and_reports_scale(repo, monkeypatch, capsys):
    monkeypatch.setattr(mod.sys, "argv", ["check_schema_triple.py"])
    assert mod.main() == 0
    out = capsys.readouterr().out
    assert "定義書 1 テーブル / 2 列" in out and "差分なし" in out
    for label in ("列", "タグ", "一意制約", "FKなし", "nullable", "命名規約", "ER索引"):
        assert f"[{label}] OK" in out


def test_main_returns_one_and_counts_mismatches(repo, monkeypatch, capsys):
    repo.write_er(er_doc(["int id PK"]))
    monkeypatch.setattr(mod.sys, "argv", ["check_schema_triple.py"])
    assert mod.main() == 1
    assert "件の不一致" in capsys.readouterr().out


def test_main_runs_only_selected_check(repo, monkeypatch, capsys):
    monkeypatch.setattr(mod.sys, "argv", ["check_schema_triple.py", "--naming"])
    assert mod.main() == 0
    out = capsys.readouterr().out
    assert "[命名規約] OK" in out and "[タグ]" not in out


def test_main_summary_skips_checks_and_reports_parse_errors(repo, monkeypatch, capsys):
    repo.write_er(er_doc(["int id PK", "壊れた行"]))
    monkeypatch.setattr(mod.sys, "argv", ["check_schema_triple.py", "--summary"])
    assert mod.main() == 1
    out = capsys.readouterr().out
    assert "属性行を解析できない" in out and "[列]" not in out


def test_main_lists_master_only_entities(repo, monkeypatch, capsys):
    repo.write_er(er_doc(ER_ATTRS) + er_doc(["int id PK"], entity="ItemMaster"))
    monkeypatch.setattr(mod.sys, "argv", ["check_schema_triple.py", "--summary"])
    mod.main()
    assert "対象外（DBテーブルを持たないマスター系エンティティ） 1 件: ItemMaster" in capsys.readouterr().out


def test_main_lists_unimplemented_tables(repo, monkeypatch, capsys):
    repo.write_def(def_doc(DEF_ROWS, phase="Phase 4・未実装"))
    monkeypatch.setattr(mod.sys, "argv", ["check_schema_triple.py", "--summary"])
    mod.main()
    assert "models 照合を除外（定義書が未実装と明記） 1 件: players" in capsys.readouterr().out
