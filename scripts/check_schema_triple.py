#!/usr/bin/env python3
"""DBスキーマ三者一致チェック（テーブル定義書 ↔ ER図 ↔ SQLAlchemy models）

正は docs/tech/basic/tech_db/*.md（テーブル定義書）。ER図と models を定義書へ照合する。
diagrams-review 2026-08-07 の「プロセスへの還元」に従い、セグメント1〜3 + レビューで
4本書かれた使い捨てスクリプトを常設化したもの（review-procedure.md §5）。

検証項目:
    1. 列        テーブル・列名・並び順の三者一致（`未実装` は models 側を除外）
    2. タグ      PK / FK / UK タグの三者一致（FK参照先の一致を含む）
    3. 一意制約  定義書の uq_* ↔ models の UniqueConstraint（名前・構成列）
    4. FKなし    「FKなし（親 §4-6）」の列に ER図・models が FK を持たないこと
    5. nullable  定義書の NULL 欄 ↔ ER図注記の nullable ↔ models の Mapped[T | None]
    6. 命名規約  一意制約名が uq_<テーブル名>_<列>_<列>（tech_db.md §2）に適合するか
    7. ER索引    er_diagram.md 索引の列挙エンティティ ↔ 各子ファイルの実在エンティティ

使い方:
    python scripts/check_schema_triple.py            # 全検証（ERROR があれば exit 1）
    python scripts/check_schema_triple.py --columns  # 列のみ
        （--tags / --unique / --nofk / --nullable / --naming / --index も同様）
    python scripts/check_schema_triple.py --summary  # 解析できた三者の規模だけを表示
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEF_DIR = ROOT / "docs" / "tech" / "basic" / "tech_db"
ER_INDEX = ROOT / "docs" / "diagrams" / "er_diagram.md"
ER_DIR = ROOT / "docs" / "diagrams" / "er_diagram"
MODELS_DIR = ROOT / "backend" / "app" / "models"

ER_TAGS = {"PK", "FK", "UK"}


# --------------------------------------------------------------------------
# データ構造
# --------------------------------------------------------------------------
@dataclass
class DefColumn:
    name: str
    nullable: bool
    pk: bool
    fk_target: str | None  # 「FK → `players.id`」の参照先
    no_fk: bool  # 「FKなし」宣言
    unique: bool  # 単一列 UNIQUE
    unimplemented: bool
    line: int


@dataclass
class DefTable:
    table: str
    src: str
    line: int
    cls: str = ""
    model_file: str = ""
    unimplemented: bool = False
    columns: list[DefColumn] = field(default_factory=list)
    uniques: list[tuple[str, tuple[str, ...]]] = field(default_factory=list)

    @property
    def label(self) -> str:
        return f"{self.src}:{self.line} `{self.table}`"


@dataclass
class ErAttr:
    name: str
    tags: set[str]
    comment: str
    line: int


@dataclass
class ErEntity:
    name: str
    src: str
    line: int
    attrs: list[ErAttr] = field(default_factory=list)

    def by_name(self, name: str) -> ErAttr | None:
        return next((a for a in self.attrs if a.name == name), None)


@dataclass
class ModelColumn:
    name: str
    nullable: bool  # 実効値（nullable= の明示があればそれ、無ければ注釈）
    ann_nullable: bool  # Mapped[T | None] か
    kw_nullable: bool | None  # mapped_column(nullable=...) の明示。無指定は None
    pk: bool
    fk_target: str | None
    unique: bool
    line: int


@dataclass
class ModelTable:
    table: str
    cls: str
    src: str
    line: int
    columns: list[ModelColumn] = field(default_factory=list)
    uniques: list[tuple[str, tuple[str, ...]]] = field(default_factory=list)


PARSE_ERRORS: list[str] = []


# --------------------------------------------------------------------------
# 1. テーブル定義書（正）
# --------------------------------------------------------------------------
SECTION = re.compile(r"^##\s+\d+\.\s+`(?P<table>[a-z0-9_]+)`(?:（(?P<phase>[^）]*)）)?\s*$")
IMPL = re.compile(r"^実装(?:予定)?:\s*`backend/app/models/(?P<file>[\w./]+)`\s*`(?P<cls>\w+)`")
UNIQ = re.compile(r"`(?P<name>uq_[a-z0-9_]+)`\s*=\s*\((?P<cols>[^)]*)\)")
BACKTICKED = re.compile(r"`([a-z0-9_]+)`")
FK_REF = re.compile(r"FK\s*(?:→|->)\s*`(?P<target>[a-z0-9_]+\.[a-z0-9_]+)`")


def _cells(line: str) -> list[str]:
    return [c.strip() for c in line.strip().strip("|").split("|")]


def parse_definitions() -> dict[str, DefTable]:
    """tech_db/*.md からテーブル定義を読む（キー = 物理テーブル名）。"""
    tables: dict[str, DefTable] = {}
    for path in sorted(DEF_DIR.glob("*.md")):
        rel = path.relative_to(ROOT).as_posix()
        current: DefTable | None = None
        in_columns = False
        for no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            m = SECTION.match(line)
            if m:
                current = DefTable(table=m.group("table"), src=rel, line=no)
                current.unimplemented = "未実装" in (m.group("phase") or "")
                if current.table in tables:
                    PARSE_ERRORS.append(
                        f"ERROR {rel}:{no}: テーブル `{current.table}` の定義が重複している"
                        f"（既出: {tables[current.table].label}）"
                    )
                tables[current.table] = current
                in_columns = False
                continue
            if line.startswith("## "):  # 索引・インデックス節などテーブル定義以外の見出し
                current = None
                in_columns = False
                continue
            if current is None:
                continue

            m = IMPL.match(line)
            if m:
                current.model_file = m.group("file")
                current.cls = m.group("cls")
                continue

            if line.startswith("一意制約"):
                found = list(UNIQ.finditer(line))
                if not found:
                    PARSE_ERRORS.append(f"ERROR {rel}:{no}: 一意制約行を解析できない → {line.strip()}")
                for u in found:
                    cols = tuple(BACKTICKED.findall(u.group("cols")))
                    current.uniques.append((u.group("name"), cols))
                continue

            if not line.startswith("|"):
                in_columns = False
                continue

            cells = _cells(line)
            if len(cells) != 5:
                in_columns = False
                continue
            if cells[0] == "列":  # ヘッダ行
                in_columns = True
                continue
            if set("".join(cells)) <= {"-", ":", " "}:  # 区切り行
                continue
            if not in_columns:
                continue

            name_m = BACKTICKED.fullmatch(cells[0])
            if not name_m:
                PARSE_ERRORS.append(f"ERROR {rel}:{no}: 列名を解析できない → {cells[0]}")
                continue
            note = cells[4]
            fk = FK_REF.search(note)
            current.columns.append(
                DefColumn(
                    name=name_m.group(1),
                    nullable=cells[2] == "可",
                    pk=re.search(r"(?<![A-Za-z])PK(?![A-Za-z])", note) is not None,
                    fk_target=fk.group("target") if fk else None,
                    no_fk="FKなし" in note,
                    unique="UNIQUE" in note,
                    unimplemented="未実装" in note,
                    line=no,
                )
            )
    return tables


# --------------------------------------------------------------------------
# 2. ER図
# --------------------------------------------------------------------------
ENTITY_OPEN = re.compile(r"^\s{4,}(?P<name>[A-Za-z][A-Za-z0-9_]*)\s*\{\s*$")


def parse_er() -> tuple[dict[str, ErEntity], dict[str, list[str]]]:
    """er_diagram/*.md から (エンティティ, ファイル別エンティティ名一覧) を読む。"""
    entities: dict[str, ErEntity] = {}
    per_file: dict[str, list[str]] = {}
    for path in sorted(ER_DIR.glob("*.md")):
        rel = path.relative_to(ROOT).as_posix()
        per_file.setdefault(path.name, [])
        in_fence = False
        is_er = False
        current: ErEntity | None = None
        for no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if line.lstrip().startswith("```"):
                in_fence = not in_fence
                is_er = False
                current = None
                continue
            if not in_fence:
                continue
            if line.strip().startswith("erDiagram"):
                is_er = True
                continue
            if not is_er:
                continue

            if current is None:
                m = ENTITY_OPEN.match(line)
                if m:
                    current = ErEntity(name=m.group("name"), src=rel, line=no)
                    if current.name in entities:
                        PARSE_ERRORS.append(
                            f"ERROR {rel}:{no}: エンティティ {current.name} の定義が重複している"
                            f"（既出: {entities[current.name].src}:{entities[current.name].line}）"
                        )
                    entities[current.name] = current
                    per_file[path.name].append(current.name)
                continue

            if line.strip() == "}":
                current = None
                continue

            attr = _parse_er_attr(line, no, rel, current.name)
            if attr:
                current.attrs.append(attr)
    return entities, per_file


def _parse_er_attr(raw: str, no: int, rel: str, entity: str) -> ErAttr | None:
    body = raw.strip()
    if not body:
        return None
    comment = ""
    if '"' in body:
        head, _, rest = body.partition('"')
        comment = rest.rsplit('"', 1)[0] if '"' in rest else rest
        body = head
    tokens = body.replace(",", " ").split()
    if len(tokens) < 2:
        PARSE_ERRORS.append(f"ERROR {rel}:{no}: {entity} の属性行を解析できない → {raw.strip()}")
        return None
    tags = set(tokens[2:])
    unknown = tags - ER_TAGS
    if unknown:
        PARSE_ERRORS.append(
            f"ERROR {rel}:{no}: {entity}.{tokens[1]} に未知のキータグ {sorted(unknown)}"
            f"（使えるのは {sorted(ER_TAGS)}）"
        )
    return ErAttr(name=tokens[1], tags=tags & ER_TAGS, comment=comment, line=no)


# --------------------------------------------------------------------------
# 3. SQLAlchemy models
# --------------------------------------------------------------------------
CLASS_DEF = re.compile(r"^class\s+(?P<cls>\w+)\(Base\):")
TABLENAME = re.compile(r'^\s+__tablename__\s*=\s*"(?P<table>\w+)"')
COLUMN_DEF = re.compile(r"^\s{4}(?P<name>\w+)\s*:\s*Mapped\[")
UNIQUE_CONSTRAINT = re.compile(r"UniqueConstraint\((?P<args>[^)]*)\)")
FOREIGN_KEY = re.compile(r'ForeignKey\(\s*"(?P<target>[^"]+)"')
NAME_KW = re.compile(r'name\s*=\s*"(?P<name>[^"]+)"')


def _strip_comment(line: str) -> str:
    """行末コメントを落とす（引用符の外の # のみ）。"""
    out, quote = [], ""
    for ch in line:
        if quote:
            out.append(ch)
            if ch == quote:
                quote = ""
            continue
        if ch in "\"'":
            quote = ch
            out.append(ch)
            continue
        if ch == "#":
            break
        out.append(ch)
    return "".join(out)


def _statement(lines: list[str], i: int) -> tuple[str, int]:
    """括弧が閉じるまで行を連結して1つの文にする。"""
    buf = _strip_comment(lines[i])
    depth = buf.count("(") - buf.count(")") + buf.count("[") - buf.count("]")
    while depth > 0 and i + 1 < len(lines):
        i += 1
        piece = _strip_comment(lines[i]).strip()
        buf += " " + piece
        depth += piece.count("(") - piece.count(")") + piece.count("[") - piece.count("]")
    return buf, i


def parse_models() -> dict[str, ModelTable]:
    """backend/app/models/*.py からテーブルを読む（正規表現ベース。import しない）。"""
    tables: dict[str, ModelTable] = {}
    for path in sorted(MODELS_DIR.glob("*.py")):
        if path.name == "__init__.py":
            continue
        rel = path.relative_to(ROOT).as_posix()
        lines = path.read_text(encoding="utf-8").splitlines()
        cls = ""
        cls_line = 0
        current: ModelTable | None = None
        i = 0
        while i < len(lines):
            line = lines[i]
            m = CLASS_DEF.match(line)
            if m:
                cls, cls_line, current = m.group("cls"), i + 1, None
                i += 1
                continue
            if line and not line[0].isspace() and not line.startswith(")"):
                cls, current = "", None  # クラス外へ出た
            m = TABLENAME.match(line)
            if m and cls:
                current = ModelTable(table=m.group("table"), cls=cls, src=rel, line=cls_line)
                if current.table in tables:
                    PARSE_ERRORS.append(
                        f"ERROR {rel}:{i + 1}: __tablename__ `{current.table}` が重複している"
                    )
                tables[current.table] = current
                i += 1
                continue
            if current is None:
                i += 1
                continue

            if re.match(r"\s+__table_args__\s*=", line):
                stmt, i = _statement(lines, i)
                for u in UNIQUE_CONSTRAINT.finditer(stmt):
                    args = u.group("args")
                    nm = NAME_KW.search(args)
                    cols = tuple(re.findall(r'"([^"]+)"', NAME_KW.sub("", args)))
                    current.uniques.append((nm.group("name") if nm else "", cols))
                i += 1
                continue

            m = COLUMN_DEF.match(line)
            if m:
                stmt, i = _statement(lines, i)
                if "relationship(" in stmt or "mapped_column(" not in stmt:
                    i += 1
                    continue
                ann = re.search(r"Mapped\[(?P<t>.+?)\]\s*=", stmt)
                annotation = ann.group("t") if ann else ""
                ann_nullable = "None" in annotation
                kw = re.search(r"nullable\s*=\s*(?P<v>True|False)", stmt)
                kw_nullable = (kw.group("v") == "True") if kw else None
                fk = FOREIGN_KEY.search(stmt)
                current.columns.append(
                    ModelColumn(
                        name=m.group("name"),
                        nullable=kw_nullable if kw_nullable is not None else ann_nullable,
                        ann_nullable=ann_nullable,
                        kw_nullable=kw_nullable,
                        pk="primary_key=True" in stmt,
                        fk_target=fk.group("target") if fk else None,
                        unique="unique=True" in stmt,
                        line=i + 1,
                    )
                )
            i += 1
    return tables


# --------------------------------------------------------------------------
# 照合
# --------------------------------------------------------------------------
@dataclass
class Sources:
    defs: dict[str, DefTable]
    er: dict[str, ErEntity]
    er_files: dict[str, list[str]]
    models: dict[str, ModelTable]

    def er_of(self, d: DefTable) -> ErEntity | None:
        return self.er.get(d.cls)

    def model_of(self, d: DefTable) -> ModelTable | None:
        return self.models.get(d.table)

    def pairs(self):
        """(定義, ER図エンティティ) の組。ER図に無いものは check_columns が報告する。"""
        for d in self.defs.values():
            er = self.er_of(d)
            if er is not None:
                yield d, er


def _seq_diff(label: str, expected: list[str], actual: list[str]) -> list[str]:
    """並び順まで含めた差分を1〜3行で説明する。"""
    if expected == actual:
        return []
    missing = [c for c in expected if c not in actual]
    extra = [c for c in actual if c not in expected]
    msgs = []
    if missing:
        msgs.append(f"{label}: 欠落 {missing}")
    if extra:
        msgs.append(f"{label}: 余剰 {extra}")
    if not missing and not extra:
        msgs.append(f"{label}: 並び順が異なる 定義書={expected} / 対象={actual}")
    return msgs


def check_columns(s: Sources) -> list[str]:
    """テーブルの実在・列名・並び順の三者一致。"""
    errors = []
    for d in s.defs.values():
        if not d.cls:
            errors.append(f"ERROR {d.label}: 「実装:」行が無く models クラス名を特定できない")
            continue

        er = s.er_of(d)
        if er is None:
            errors.append(f"ERROR {d.label}: ER図にエンティティ {d.cls} が無い")
        else:
            for msg in _seq_diff("ER図の列", [c.name for c in d.columns], [a.name for a in er.attrs]):
                errors.append(f"ERROR {d.label} ↔ {er.src}:{er.line} {msg}")

        model = s.model_of(d)
        if model is None:
            if not d.unimplemented:
                errors.append(f"ERROR {d.label}: models に __tablename__ = \"{d.table}\" が無い（未実装表記も無い）")
            continue
        if d.unimplemented:
            errors.append(f"ERROR {d.label}: 定義書は未実装だが models に実装がある（定義書の Phase 表記を更新する）")
        if model.cls != d.cls:
            errors.append(f"ERROR {d.label}: モデルクラス名が不一致 定義書={d.cls} / {model.src}={model.cls}")
        expected_src = f"backend/app/models/{d.model_file}"
        if d.model_file and model.src != expected_src:
            errors.append(
                f"ERROR {d.label}: 実装ファイルが不一致 定義書={expected_src} / 実体={model.src}"
            )
        expected = [c.name for c in d.columns if not c.unimplemented]
        for msg in _seq_diff("models の列", expected, [c.name for c in model.columns]):
            errors.append(f"ERROR {d.label} ↔ {model.src}:{model.line} {msg}")

    known = {d.table for d in s.defs.values()}
    for table, m in s.models.items():
        if table not in known:
            errors.append(f"ERROR {m.src}:{m.line}: テーブル `{table}` が定義書に無い（tech_db.md §7: 定義書に無いテーブルを実装しない）")

    return errors


def check_tags(s: Sources) -> list[str]:
    """PK / FK / UK タグの三者一致（ISSUE-601・602 の検出層）。"""
    errors = []
    for d, er in s.pairs():
        model = s.model_of(d)
        er_pk = {a.name for a in er.attrs if "PK" in a.tags}
        er_fk = {a.name for a in er.attrs if "FK" in a.tags}
        er_uk = {a.name for a in er.attrs if "UK" in a.tags}
        er_names = {a.name for a in er.attrs}

        def_pk = {c.name for c in d.columns if c.pk}
        # FKなし宣言の列は check_nofk の担当。ここでは明示FKのみを比較する
        def_fk = {c.name for c in d.columns if c.fk_target}
        def_uk = {c.name for c in d.columns if c.unique}
        for _, cols in d.uniques:
            def_uk |= set(cols)

        for label, expected, actual in (
            ("PK", def_pk, er_pk),
            ("FK", def_fk, er_fk & (def_fk | {c.name for c in d.columns if not c.no_fk})),
            ("UK", def_uk, er_uk),
        ):
            expected = expected & er_names  # 列そのものの欠落は check_columns が報告済み
            if expected == actual:
                continue

            def at(names: set[str]) -> str:
                return ", ".join(f"{n}@{er.by_name(n).line}" for n in sorted(names))

            missing = expected - actual
            extra = actual - expected
            if missing:
                hint = "（複合一意は構成列すべてに UK を付ける）" if label == "UK" else ""
                errors.append(
                    f"ERROR {er.src} {er.name}: {label} タグが不足 [{at(missing)}]"
                    f" — 定義書 {d.src}:{d.line} の宣言と不一致{hint}"
                )
            if extra:
                errors.append(
                    f"ERROR {er.src} {er.name}: {label} タグが余剰 [{at(extra)}]"
                    f" — 定義書 {d.src}:{d.line} は宣言していない"
                )

        if model is None or d.unimplemented:
            continue
        m_cols = {c.name: c for c in model.columns}
        for c in d.columns:
            if c.unimplemented or c.name not in m_cols:
                continue
            mc = m_cols[c.name]
            if c.pk != mc.pk:
                errors.append(
                    f"ERROR {model.src}:{mc.line} {model.cls}.{c.name}: primary_key が定義書と不一致"
                    f"（定義書={'PK' if c.pk else 'PKでない'}）"
                )
            if c.fk_target and mc.fk_target != c.fk_target:
                errors.append(
                    f"ERROR {model.src}:{mc.line} {model.cls}.{c.name}: FK参照先が不一致"
                    f"（定義書={c.fk_target} / models={mc.fk_target}）"
                )
            if c.unique != mc.unique:
                errors.append(
                    f"ERROR {model.src}:{mc.line} {model.cls}.{c.name}: unique が定義書と不一致"
                    f"（定義書={'UNIQUE' if c.unique else 'UNIQUEでない'}）"
                )
    return errors


def check_unique(s: Sources) -> list[str]:
    """定義書の一意制約 ↔ models の UniqueConstraint（名前・構成列）。"""
    errors = []
    for d in s.defs.values():
        model = s.model_of(d)
        if model is None or d.unimplemented:
            continue
        def_map = dict(d.uniques)
        model_map = dict(model.uniques)
        for name, cols in d.uniques:
            if name not in model_map:
                errors.append(
                    f"ERROR {model.src}:{model.line} {model.cls}: 一意制約 `{name}` が models に無い"
                    f"（定義書 {d.src}:{d.line} = {list(cols)}）"
                )
            elif tuple(model_map[name]) != tuple(cols):
                errors.append(
                    f"ERROR {model.src}:{model.line} {model.cls}: 一意制約 `{name}` の構成列が不一致"
                    f"（定義書={list(cols)} / models={list(model_map[name])}）"
                )
        for name, cols in model.uniques:
            if name not in def_map:
                errors.append(
                    f"ERROR {model.src}:{model.line} {model.cls}: 一意制約 `{name}` が定義書 {d.src} に無い"
                    f"（models={list(cols)}）"
                )
    return errors


def check_nofk(s: Sources) -> list[str]:
    """「FKなし（親 §4-6）」宣言の列に ER図・models が FK を持たないこと（ISSUE-602）。"""
    errors = []
    for d in s.defs.values():
        er = s.er_of(d)
        model = s.model_of(d)
        for c in d.columns:
            if not c.no_fk:
                continue
            if er:
                a = er.by_name(c.name)
                if a and "FK" in a.tags:
                    errors.append(
                        f"ERROR {er.src}:{a.line} {er.name}.{c.name}: 定義書は「FKなし」だが ER図が FK タグを持つ"
                        f"（マスター参照は FK を外し「（DB外部キーなし）」と注記する。tech_db.md §4-6）"
                    )
            if model and not d.unimplemented:
                mc = next((x for x in model.columns if x.name == c.name), None)
                if mc and mc.fk_target:
                    errors.append(
                        f"ERROR {model.src}:{mc.line} {model.cls}.{c.name}: 定義書は「FKなし」だが"
                        f" ForeignKey(\"{mc.fk_target}\") がある（tech_db.md §4-6）"
                    )
            if c.fk_target:
                errors.append(
                    f"ERROR {d.src}:{c.line} `{d.table}`.{c.name}: 「FKなし」と「FK → {c.fk_target}」を同時に宣言している"
                )
    return errors


NULL_HINT = re.compile(r"null", re.IGNORECASE)
NULLABLE_HINT = re.compile(r"nullable", re.IGNORECASE)


def check_nullable(s: Sources) -> list[str]:
    """定義書の NULL 欄 ↔ ER図注記 ↔ models の Mapped[T | None]（ISSUE-604）。"""
    errors = []
    for d in s.defs.values():
        er = s.er_of(d)
        model = s.model_of(d)
        for c in d.columns:
            if er:
                a = er.by_name(c.name)
                if a is not None:
                    has_null = NULL_HINT.search(a.comment) is not None
                    if c.nullable and not has_null:
                        errors.append(
                            f"ERROR {er.src}:{a.line} {er.name}.{c.name}: 定義書は NULL 可だが"
                            f" ER図の注記に nullable が無い（{d.src}:{c.line}）"
                        )
                    elif not c.nullable and NULLABLE_HINT.search(a.comment):
                        errors.append(
                            f"ERROR {er.src}:{a.line} {er.name}.{c.name}: ER図は nullable と注記するが"
                            f" 定義書は NULL 不可（{d.src}:{c.line}）"
                        )
            if model and not d.unimplemented and not c.unimplemented:
                mc = next((x for x in model.columns if x.name == c.name), None)
                if mc is None:
                    continue
                if mc.kw_nullable is not None and mc.kw_nullable != mc.ann_nullable:
                    errors.append(
                        f"ERROR {model.src}:{mc.line} {model.cls}.{c.name}: models 内で注釈と nullable= が矛盾"
                        f"（Mapped[...{'| None' if mc.ann_nullable else ''}] / nullable={mc.kw_nullable}）"
                        f" — tech_db.md §4-3 は Mapped[T | None] と一対一に対応させる"
                    )
                elif mc.nullable != c.nullable:
                    errors.append(
                        f"ERROR {model.src}:{mc.line} {model.cls}.{c.name}: NULL 可否が定義書と不一致"
                        f"（定義書={'可' if c.nullable else '不可'} / models={'可' if mc.nullable else '不可'}）"
                        f" — tech_db.md §4-3 は Mapped[T | None] と一対一に対応させる"
                    )
    return errors


def check_naming(s: Sources) -> list[str]:
    """一意制約名が uq_<テーブル名>_<列>_<列>（tech_db.md §2）に適合するか。"""
    errors = []
    for d in s.defs.values():
        for name, cols in d.uniques:
            prefix = f"uq_{d.table}_"
            if not name.startswith(prefix):
                errors.append(
                    f"ERROR {d.src}:{d.line} `{d.table}`: 一意制約名 `{name}` が命名規約に反する"
                    f"（tech_db.md §2: uq_<テーブル名>_<列>_<列> → `{prefix}...` で始まる必要がある）"
                )
                continue
            parts = [p for p in name[len(prefix):].split("_") if p]
            if len(parts) != len(cols):
                errors.append(
                    f"ERROR {d.src}:{d.line} `{d.table}`: 一意制約名 `{name}` の列部が {len(parts)} 語で、"
                    f"構成列 {len(cols)} 件（{list(cols)}）と対応していない（tech_db.md §2）"
                )
    return errors


INDEX_LINK = re.compile(r"\[[^\]]*\]\(er_diagram/(?P<file>[\w.]+\.md)\)")


def check_index(s: Sources) -> list[str]:
    """er_diagram.md 索引の列挙エンティティ ↔ 各子ファイルの実在エンティティ（ISSUE-603）。"""
    errors = []
    if not ER_INDEX.exists():
        return [f"ERROR {ER_INDEX.relative_to(ROOT).as_posix()}: ER図の索引が見つからない"]
    rel = ER_INDEX.relative_to(ROOT).as_posix()
    listed_files: set[str] = set()
    for no, line in enumerate(ER_INDEX.read_text(encoding="utf-8").splitlines(), 1):
        if not line.startswith("|"):
            continue
        cells = _cells(line)
        if len(cells) < 3:
            continue
        link = INDEX_LINK.search(cells[2])
        if not link:
            continue
        fname = link.group("file")
        listed_files.add(fname)
        actual = s.er_files.get(fname)
        if actual is None:
            errors.append(f"ERROR {rel}:{no}: 索引が指す {fname} が {ER_DIR.name}/ に無い")
            continue
        listed = [x.strip().strip("`") for x in re.split(r"[/、,]", cells[1]) if x.strip()]
        missing = [e for e in actual if e not in listed]
        extra = [e for e in listed if e not in actual]
        if missing:
            errors.append(
                f"ERROR {rel}:{no}: {fname} に定義済みのエンティティが索引に無い → {missing}"
            )
        for e in extra:
            where = s.er.get(e)
            loc = f"（実際の定義は {where.src}）" if where else "（どの図にも定義が無い）"
            errors.append(f"ERROR {rel}:{no}: 索引は {fname} に {e} を挙げるが不在{loc}")
    for fname in sorted(set(s.er_files) - listed_files):
        errors.append(f"ERROR {rel}: {fname} が索引に登録されていない")
    return errors


# --------------------------------------------------------------------------
def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):  # pragma: no cover - 実行環境依存
        pass

    args = sys.argv[1:]
    defs = parse_definitions()
    er, er_files = parse_er()
    models = parse_models()
    sources = Sources(defs=defs, er=er, er_files=er_files, models=models)

    n_def_cols = sum(len(d.columns) for d in defs.values())
    print(
        f"定義書 {len(defs)} テーブル / {n_def_cols} 列 ・ "
        f"ER図 {len(er)} エンティティ（{len(er_files)} ファイル） ・ "
        f"models {len(models)} テーブル"
    )
    # 定義書に対応の無い ER図エンティティ（マスターデータの論理設計。tech_db.md §4-6 で対象外）
    master_only = sorted(set(er) - {d.cls for d in defs.values() if d.cls})
    if master_only:
        print(f"  対象外（DBテーブルを持たないマスター系エンティティ） {len(master_only)} 件: {', '.join(master_only)}")
    unimpl = sorted(d.table for d in defs.values() if d.unimplemented)
    if unimpl:
        print(f"  models 照合を除外（定義書が未実装と明記） {len(unimpl)} 件: {', '.join(unimpl)}")
    if "--summary" in args:
        for e in PARSE_ERRORS:
            print(e)
        return 1 if PARSE_ERRORS else 0

    checks = {
        "--columns": ("列", check_columns),
        "--tags": ("タグ", check_tags),
        "--unique": ("一意制約", check_unique),
        "--nofk": ("FKなし", check_nofk),
        "--nullable": ("nullable", check_nullable),
        "--naming": ("命名規約", check_naming),
        "--index": ("ER索引", check_index),
    }
    selected = [k for k in checks if k in args] or list(checks)

    total = len(PARSE_ERRORS)
    for e in PARSE_ERRORS:
        print(e)
    if PARSE_ERRORS:
        print(f"[解析] {len(PARSE_ERRORS)} 件")

    for key in selected:
        label, fn = checks[key]
        errors = fn(sources)
        total += len(errors)
        for e in errors:
            print(e)
        print(f"[{label}] {'OK' if not errors else f'{len(errors)} 件'}")

    print(f"\n{'差分なし' if total == 0 else f'{total} 件の不一致'}")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
