#!/usr/bin/env python3
"""DBスキーマ一致チェック（テーブル定義書 ↔ ER図 ↔ Flyway DDL）

正は docs/tech/basic/tech_db/*.md（テーブル定義書）。ER図・DDL を定義書へ照合する。
diagrams-review 2026-08-07 の「プロセスへの還元」に従い、セグメント1〜3 + レビューで
4本書かれた使い捨てスクリプトを常設化したもの（review-procedure.md §5）。

実装側の照合先は Flyway DDL のみである（Python/FastAPI の models は削除済み。
java_migration/steps.md §4 STEP 6）。

    DDL     backend/afkgame-initdb/.../db/migration/V*.sql（Flyway）

Java の Entity は列メタデータを持たない素の POJO のため列の照合対象にしない
（Java 側でスキーマの正を持つのは Flyway DDL）。定義書の「実装:」行が指す Entity は
**ファイルの実在だけ**を見る。未作成の Entity は「実装予定:」と書き分ける。

検証項目:
    1. 列        テーブル・列名・並び順の一致（`未実装` は DDL 側を除外）
                 「実装:」宣言の Entity が afkgame-domain に実在すること
    2. タグ      PK / FK / UK タグの一致（FK参照先の一致を含む）
    3. 一意制約  定義書の uq_* ↔ DDL の UNIQUE 制約
    4. FKなし    「FKなし（親 §4-6）」の列に ER図・DDL が FK を持たないこと
    5. nullable  定義書の NULL 欄 ↔ ER図注記 ↔ DDL の NOT NULL
    6. 備考      定義書の備考が示す記録時点・更新契機 ↔ ER図の注釈（ISSUE-708）
    7. 命名規約  一意制約名が uq_<テーブル名>_<列>_<列>（tech_db.md §2）に適合するか
    8. ER索引    er_diagram.md 索引の列挙エンティティ ↔ 各子ファイルの実在エンティティ

単一列 UNIQUE は定義書では備考欄に `UNIQUE` と書き（項目2 で照合）、複合 UNIQUE は
`一意制約:` 行で名前ごと宣言する（項目3 で照合）。DDL は両方を名前付き制約で書くため、
項目3 は複合のみを名前で突き合わせる。

使い方:
    python scripts/check_schema_triple.py            # 全検証（ERROR があれば exit 1）
    python scripts/check_schema_triple.py --columns  # 列のみ
        （--tags / --unique / --nofk / --nullable / --note / --naming / --index も同様）
    python scripts/check_schema_triple.py --summary  # 解析できた各ソースの規模だけを表示
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
ENTITY_DIR = (
    ROOT / "backend" / "afkgame-domain" / "src" / "main" / "java" / "com" / "afkgame" / "domain" / "model"
)
DDL_DIR = ROOT / "backend" / "afkgame-initdb" / "src" / "main" / "resources" / "db" / "migration"

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
    note: str = ""  # 備考欄の原文（記録時点・更新契機の照合に使う）


@dataclass
class DefTable:
    table: str
    src: str
    line: int
    cls: str = ""
    planned: bool = False  # 「実装予定:」（Entity 未作成）か
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
class DdlColumn:
    name: str
    nullable: bool  # NOT NULL が無い
    pk: bool  # 表レベルの PRIMARY KEY (...) に含まれる
    fk_target: str | None  # REFERENCES <表> (<列>)
    unique: bool  # 単一列の UNIQUE 制約に含まれる
    line: int


@dataclass
class DdlTable:
    table: str
    src: str
    line: int
    columns: list[DdlColumn] = field(default_factory=list)
    uniques: list[tuple[str, tuple[str, ...]]] = field(default_factory=list)


PARSE_ERRORS: list[str] = []


# --------------------------------------------------------------------------
# 1. テーブル定義書（正）
# --------------------------------------------------------------------------
SECTION = re.compile(r"^##\s+\d+\.\s+`(?P<table>[a-z0-9_]+)`(?:（(?P<phase>[^）]*)）)?\s*$")
IMPL = re.compile(r"^実装(?P<planned>予定)?:\s*`com\.afkgame\.domain\.model\.(?P<cls>\w+)`")
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
                current.cls = m.group("cls")
                current.planned = m.group("planned") is not None
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
                    note=note,
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
# 3. Java Entity（実在のみ）
# --------------------------------------------------------------------------
def entity_exists(cls: str) -> bool:
    """afkgame-domain に `com.afkgame.domain.model.<cls>` があるか。"""
    return (ENTITY_DIR / f"{cls}.java").is_file()


# --------------------------------------------------------------------------
# 4. Flyway DDL
# --------------------------------------------------------------------------
DDL_CREATE = re.compile(
    r'^CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?"?(?P<table>[a-z0-9_]+)"?\s*\(', re.IGNORECASE
)
DDL_CONSTRAINT = re.compile(
    r'^(?:CONSTRAINT\s+"?(?P<name>\w+)"?\s+)?'
    r"(?P<kind>PRIMARY\s+KEY|UNIQUE|FOREIGN\s+KEY|CHECK)\b",
    re.IGNORECASE,
)
DDL_COLUMN = re.compile(r'^"?(?P<name>[a-z0-9_]+)"?\s+(?P<rest>\S.*)$')
DDL_REFERENCES = re.compile(
    r'REFERENCES\s+"?(?P<table>[a-z0-9_]+)"?\s*\(\s*"?(?P<col>[a-z0-9_]+)"?\s*\)', re.IGNORECASE
)
DDL_PARENS = re.compile(r"\((?P<cols>[^)]*)\)")
DDL_NOT_NULL = re.compile(r"\bNOT\s+NULL\b", re.IGNORECASE)
DDL_INLINE_UNIQUE = re.compile(r"\bUNIQUE\b", re.IGNORECASE)


def _strip_sql_comment(line: str) -> str:
    """行末の `--` コメントを落とす（引用符の外のみ）。"""
    out, quote = [], ""
    i = 0
    while i < len(line):
        ch = line[i]
        if quote:
            out.append(ch)
            if ch == quote:
                quote = ""
            i += 1
            continue
        if ch in "\"'":
            quote = ch
            out.append(ch)
            i += 1
            continue
        if ch == "-" and line.startswith("--", i):
            break
        out.append(ch)
        i += 1
    return "".join(out)


def _ddl_cols(body: str) -> tuple[str, ...]:
    """`PRIMARY KEY (a, b)` の括弧内から列名を取り出す。"""
    m = DDL_PARENS.search(body)
    if not m:
        return ()
    return tuple(c.strip().strip('"') for c in m.group("cols").split(",") if c.strip())


def parse_ddl() -> dict[str, DdlTable]:
    """Flyway の V*.sql から CREATE TABLE を読む（1列1行の書式を前提にする）。"""
    tables: dict[str, DdlTable] = {}
    if not DDL_DIR.exists():
        return tables
    for path in sorted(DDL_DIR.glob("V*.sql")):
        rel = path.relative_to(ROOT).as_posix()
        current: DdlTable | None = None
        pk_cols: tuple[str, ...] = ()
        for no, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            line = _strip_sql_comment(raw).strip()
            if not line:
                continue

            if current is None:
                m = DDL_CREATE.match(line)
                if m:
                    current = DdlTable(table=m.group("table"), src=rel, line=no)
                    pk_cols = ()
                    if current.table in tables:
                        PARSE_ERRORS.append(
                            f"ERROR {rel}:{no}: CREATE TABLE `{current.table}` が重複している"
                            f"（既出: {tables[current.table].src}:{tables[current.table].line}）"
                        )
                    tables[current.table] = current
                continue

            if line.startswith(")"):  # テーブル定義の終端
                single = {u[0] for _, u in current.uniques if len(u) == 1}
                for c in current.columns:
                    c.pk = c.name in pk_cols
                    c.unique = c.unique or c.name in single
                    if c.pk:
                        c.nullable = False  # PRIMARY KEY は暗黙に NOT NULL
                current = None
                continue

            body = line.rstrip(",").strip()
            m = DDL_CONSTRAINT.match(body)
            if m:
                kind = re.sub(r"\s+", " ", m.group("kind")).upper()
                if kind == "PRIMARY KEY":
                    pk_cols = _ddl_cols(body)
                elif kind == "UNIQUE":
                    current.uniques.append((m.group("name") or "", _ddl_cols(body)))
                continue

            m = DDL_COLUMN.match(body)
            if not m:
                PARSE_ERRORS.append(f"ERROR {rel}:{no}: 列定義を解析できない → {body}")
                continue
            rest = m.group("rest")
            ref = DDL_REFERENCES.search(rest)
            current.columns.append(
                DdlColumn(
                    name=m.group("name"),
                    nullable=DDL_NOT_NULL.search(rest) is None,
                    pk=False,  # 終端で PRIMARY KEY (...) から確定する
                    fk_target=f"{ref.group('table')}.{ref.group('col')}" if ref else None,
                    unique=DDL_INLINE_UNIQUE.search(rest) is not None,
                    line=no,
                )
            )
        if current is not None:
            PARSE_ERRORS.append(
                f"ERROR {rel}:{current.line}: CREATE TABLE `{current.table}` が閉じていない"
            )
    return tables


# --------------------------------------------------------------------------
# 照合
# --------------------------------------------------------------------------
@dataclass
class Sources:
    defs: dict[str, DefTable]
    er: dict[str, ErEntity]
    er_files: dict[str, list[str]]
    ddl: dict[str, DdlTable] = field(default_factory=dict)

    def er_of(self, d: DefTable) -> ErEntity | None:
        return self.er.get(d.cls)

    def ddl_of(self, d: DefTable) -> DdlTable | None:
        """定義書に対応する DDL テーブル。DDL 未解析（`ddl` が空）なら常に None。"""
        return self.ddl.get(d.table)

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
            errors.append(
                f"ERROR {d.label}: 「実装:」行が無く Entity 名（= ER図のエンティティ名）を特定できない"
                f"（書式: 実装: `com.afkgame.domain.model.<クラス>`。未作成なら「実装予定:」）"
            )
            continue

        er = s.er_of(d)
        if er is None:
            errors.append(f"ERROR {d.label}: ER図にエンティティ {d.cls} が無い")
        else:
            for msg in _seq_diff("ER図の列", [c.name for c in d.columns], [a.name for a in er.attrs]):
                errors.append(f"ERROR {d.label} ↔ {er.src}:{er.line} {msg}")

        # DDL は models の有無に依らず見る（移行期は片方だけ存在しうる）
        if s.ddl:
            ddl = s.ddl_of(d)
            if ddl is None:
                if not d.unimplemented:
                    errors.append(
                        f"ERROR {d.label}: Flyway DDL に CREATE TABLE `{d.table}` が無い（未実装表記も無い）"
                    )
            else:
                if d.unimplemented:
                    errors.append(
                        f"ERROR {d.label}: 定義書は未実装だが Flyway DDL にテーブルがある"
                        f"（定義書の Phase 表記を更新する）"
                    )
                expected = [c.name for c in d.columns if not c.unimplemented]
                for msg in _seq_diff("DDL の列", expected, [c.name for c in ddl.columns]):
                    errors.append(f"ERROR {d.label} ↔ {ddl.src}:{ddl.line} {msg}")

        # Entity は列を持たない POJO のため、実在と「実装 / 実装予定」の書き分けだけを見る
        exists = entity_exists(d.cls)
        if d.planned and exists:
            errors.append(
                f"ERROR {d.label}: 「実装予定:」だが `{d.cls}.java` が実在する"
                f"（作成済みなら「実装:」へ変える）"
            )
        elif not d.planned and not exists:
            errors.append(
                f"ERROR {d.label}: 「実装:」が指す `com.afkgame.domain.model.{d.cls}` が無い"
                f"（未作成なら「実装予定:」と書く）"
            )

    known = {d.table for d in s.defs.values()}
    for table, t in s.ddl.items():
        if table not in known:
            errors.append(
                f"ERROR {t.src}:{t.line}: テーブル `{table}` が定義書に無い"
                f"（tech_db.md §7: 定義書に無いテーブルを作らない）"
            )

    return errors


def check_tags(s: Sources) -> list[str]:
    """PK / FK / UK タグの三者一致（ISSUE-601・602 の検出層）。"""
    errors = []
    for d, er in s.pairs():
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

        ddl = s.ddl_of(d)
        if ddl is not None and not d.unimplemented:
            d_cols = {c.name: c for c in ddl.columns}
            for c in d.columns:
                if c.unimplemented or c.name not in d_cols:
                    continue
                dc = d_cols[c.name]
                if c.pk != dc.pk:
                    errors.append(
                        f"ERROR {ddl.src}:{dc.line} `{ddl.table}`.{c.name}: PRIMARY KEY が定義書と不一致"
                        f"（定義書={'PK' if c.pk else 'PKでない'}）"
                    )
                if c.fk_target and dc.fk_target != c.fk_target:
                    errors.append(
                        f"ERROR {ddl.src}:{dc.line} `{ddl.table}`.{c.name}: REFERENCES の参照先が不一致"
                        f"（定義書={c.fk_target} / DDL={dc.fk_target}）"
                    )
                if c.unique != dc.unique:
                    errors.append(
                        f"ERROR {ddl.src}:{dc.line} `{ddl.table}`.{c.name}: UNIQUE が定義書と不一致"
                        f"（定義書={'UNIQUE' if c.unique else 'UNIQUEでない'}）"
                    )
    return errors


def check_unique(s: Sources) -> list[str]:
    """定義書の一意制約 ↔ DDL の UNIQUE 制約（名前・構成列）。

    定義書は単一列 UNIQUE を備考欄で宣言する（名前を持たない）ため、DDL 側からの
    「定義書に無い」報告は複合一意（2列以上）に限る。単一列は check_tags が見る。
    """
    errors = []
    for d in s.defs.values():
        ddl = s.ddl_of(d)
        if ddl is not None and not d.unimplemented:
            def_map = dict(d.uniques)
            ddl_map = dict(ddl.uniques)
            for name, cols in d.uniques:
                if name not in ddl_map:
                    errors.append(
                        f"ERROR {ddl.src}:{ddl.line} `{ddl.table}`: 一意制約 `{name}` が DDL に無い"
                        f"（定義書 {d.src}:{d.line} = {list(cols)}）"
                    )
                elif tuple(ddl_map[name]) != tuple(cols):
                    errors.append(
                        f"ERROR {ddl.src}:{ddl.line} `{ddl.table}`: 一意制約 `{name}` の構成列が不一致"
                        f"（定義書={list(cols)} / DDL={list(ddl_map[name])}）"
                    )
            for name, cols in ddl.uniques:
                if len(cols) >= 2 and name not in def_map:
                    errors.append(
                        f"ERROR {ddl.src}:{ddl.line} `{ddl.table}`: 一意制約 `{name}` が定義書 {d.src} に無い"
                        f"（DDL={list(cols)}）"
                    )
    return errors


def check_nofk(s: Sources) -> list[str]:
    """「FKなし（親 §4-6）」宣言の列に ER図・DDL が FK を持たないこと（ISSUE-602）。"""
    errors = []
    for d in s.defs.values():
        er = s.er_of(d)
        ddl = s.ddl_of(d)
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
            if ddl and not d.unimplemented:
                dc = next((x for x in ddl.columns if x.name == c.name), None)
                if dc and dc.fk_target:
                    errors.append(
                        f"ERROR {ddl.src}:{dc.line} `{ddl.table}`.{c.name}: 定義書は「FKなし」だが"
                        f" REFERENCES {dc.fk_target} がある（tech_db.md §4-6）"
                    )
            if c.fk_target:
                errors.append(
                    f"ERROR {d.src}:{c.line} `{d.table}`.{c.name}: 「FKなし」と「FK → {c.fk_target}」を同時に宣言している"
                )
    return errors


NULL_HINT = re.compile(r"null", re.IGNORECASE)
NULLABLE_HINT = re.compile(r"nullable", re.IGNORECASE)


def check_nullable(s: Sources) -> list[str]:
    """定義書の NULL 欄 ↔ ER図注記 ↔ DDL の NOT NULL（ISSUE-604）。"""
    errors = []
    for d in s.defs.values():
        er = s.er_of(d)
        ddl = s.ddl_of(d)
        for c in d.columns:
            if ddl and not d.unimplemented and not c.unimplemented:
                dc = next((x for x in ddl.columns if x.name == c.name), None)
                if dc is not None and dc.nullable != c.nullable:
                    errors.append(
                        f"ERROR {ddl.src}:{dc.line} `{ddl.table}`.{c.name}: NULL 可否が定義書と不一致"
                        f"（定義書={'可' if c.nullable else '不可'} /"
                        f" DDL={'NOT NULL なし' if dc.nullable else 'NOT NULL'}）"
                    )
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
    return errors


# 記録時点・更新契機（diagrams-review 2026-08-11 の還元案3 = ISSUE-708 の検出層）。
# 語の出現だけでは拾いすぎる（「限界突破の判定に使う」「全滅時も没収しない」等、
# 記録時点を述べていない文にも現れる）ため、**契機語 + 記録動詞**の句で判定する。
TIMING_EVENT = "突破|全滅|撃破|到達|クリア|即時|初回|復帰|失効|受取|リセット|毎tick"
RECORD_VERB = "更新|記録|加算|反映|確定|セット|保存|初期化"
TIMING_PHRASE = re.compile(
    rf"(?P<word>{TIMING_EVENT})(?:直後|時点|時|後|以降)?[^。]{{0,12}}?(?:{RECORD_VERB})"
)


def check_note(s: Sources) -> list[str]:
    """定義書の備考が示す記録時点・更新契機が ER図の注釈にもあるか（ISSUE-708）。

    列名・型タグが一致していても「ベスト時の」のような曖昧な注釈は残りうる。
    定義書（正）の備考が「<契機>に更新する」と書いているとき、ER図の注釈が
    その契機語を1つも持たなければ、図が別の時点を指している（か、時点を
    落としている）とみなす。注釈が空の列は「注釈を持たない」だけなので対象にしない。
    """
    errors = []
    for d, er in s.pairs():
        for c in d.columns:
            def_words = sorted({m.group("word") for m in TIMING_PHRASE.finditer(c.note)})
            if not def_words:
                continue
            a = er.by_name(c.name)
            if a is None or not a.comment.strip():
                continue
            if any(w in a.comment for w in def_words):
                continue
            errors.append(
                f"ERROR {er.src}:{a.line} {er.name}.{c.name}: ER図の注釈が定義書の記録時点"
                f"「{'・'.join(def_words)}」を示していない → \"{a.comment}\"（{d.src}:{c.line}）"
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
    ddl = parse_ddl()
    if not ddl:
        PARSE_ERRORS.append(
            f"ERROR {DDL_DIR.relative_to(ROOT).as_posix()}: Flyway の V*.sql から"
            f" CREATE TABLE を1件も読めない（DDL 照合が丸ごと無効になる）"
        )
    sources = Sources(defs=defs, er=er, er_files=er_files, ddl=ddl)

    n_def_cols = sum(len(d.columns) for d in defs.values())
    n_entity = sum(1 for d in defs.values() if d.cls and not d.planned)
    print(
        f"定義書 {len(defs)} テーブル / {n_def_cols} 列 ・ "
        f"ER図 {len(er)} エンティティ（{len(er_files)} ファイル） ・ "
        f"DDL {len(ddl)} テーブル ・ "
        f"Entity {n_entity} 件（実装宣言）"
    )
    # 定義書に対応の無い ER図エンティティ（マスターデータの論理設計。tech_db.md §4-6 で対象外）
    master_only = sorted(set(er) - {d.cls for d in defs.values() if d.cls})
    if master_only:
        print(f"  対象外（DBテーブルを持たないマスター系エンティティ） {len(master_only)} 件: {', '.join(master_only)}")
    unimpl = sorted(d.table for d in defs.values() if d.unimplemented)
    if unimpl:
        print(f"  DDL 照合を除外（定義書が未実装と明記） {len(unimpl)} 件: {', '.join(unimpl)}")
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
        "--note": ("備考の記録時点", check_note),
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
