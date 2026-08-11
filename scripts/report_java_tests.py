#!/usr/bin/env python3
"""Java テスト結果の集計出力（surefire・failsafe・JaCoCo・mvn ログ）

`mvn` の生出力は長く CP932 で、会話へ持ち込むと文脈を食い潰す
（`.claude/project/commands/backend.md` §2）。本スクリプトは XML レポートから
**そのまま取り込める要約**だけを標準出力へ出す。

出力する節:
    == 実行 ==       `--run` で起動した mvn のコマンド・終了コード・ログの所在
    == ビルド ==     ログから抽出したコンパイルエラー（レポートが出ない失敗の原因）
    == 単体テスト == surefire レポートのモジュール別集計
    == 失敗の詳細 == 失敗・エラーになったテストの型・メッセージ・発生位置
    == 結合テスト == failsafe レポートのモジュール別集計
    == カバレッジ == JaCoCo の C1（分岐）カバレッジ。判定基準は branch 100%
    == 未達分岐 ==   分岐が残っている `<ソース>:<行>`（C1 補完の作業リスト）
    == 判定 ==       `KEY  値` 形式の要約。RESULT が OK 以外なら exit 1

使い方:
    python scripts/report_java_tests.py                 # 既存レポートを集計するだけ
    python scripts/report_java_tests.py --run           # mvn clean verify -DskipITs → 集計
    python scripts/report_java_tests.py --run --it      # mvn clean verify（結合テスト込み）→ 集計
    python scripts/report_java_tests.py --run --test BattleServiceImplTest  # 絞って mvn clean test
    python scripts/report_java_tests.py --run --module afkgame-domain    # -pl + -am
    python scripts/report_java_tests.py --run --no-clean                 # clean を省いて再実行を速める
    python scripts/report_java_tests.py --log backend/target/mvn.log     # ログのみ解析
    python scripts/report_java_tests.py --coverage --uncovered           # 節を絞る

`--module` は親 POM の `mvn -N install -q` を先に流し、`--test` は
`-Dsurefire.failIfNoSpecifiedTests=false` を自動で足す（commands/backend.md §3 の
「セットで使う4点」を取りこぼさないため）。

**`--run` は既定で `clean` を先に流す**。surefire はレポートディレクトリを掃除しないため、
clean しないとパッケージを移動・改名したテストの旧レポートが残って二重計上になる
（`service.AuthServiceImplTest` と `service.auth.AuthServiceImplTest` が併存した実例がある）。
`--no-clean` で省けるが、集計の正しさは落ちる。

clean が及ばない範囲（`--module` の対象外モジュール、`--no-clean`、手動実行の残骸）に備え、
集計側でも陳腐化を検出する:

* `src/test/java` に対応するクラスが無いレポートは **集計から除外**し、除外分を表示する
* JaCoCo が同モジュールの surefire より古ければ STALE と表示し、RESULT を COVERAGE_STALE にする
  （現行コードを測っていない古い 100% を OK と答えないため）
* レポートの最古と最新が離れていれば REPORT_TIME に範囲を出す（別々の実行の混在）
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BACKEND = "backend"
SUREFIRE = "surefire-reports"
FAILSAFE = "failsafe-reports"
TEST_SRC = "src/test/java"
JACOCO_XML = "target/site/jacoco/jacoco.xml"
DEFAULT_LOG = "backend/target/mvn.log"

MSG_MAX = 300  # 失敗メッセージの表示上限（字）
STALE_MAX = 10  # 陳腐化レポートの表示上限（件）
# JaCoCo の report は同モジュールの test フェーズ直後（verify）に書かれるので surefire より必ず新しい。
# 秒単位の揺れだけを許し、それを超えて古ければ別の実行の残骸と見なす
STALE_GRACE = 60.0
# レポート群の最古と最新がこれ以上離れていれば、別々の実行が混ざっている
MIXED_RUN_GAP = 900.0
SECTIONS = ("build", "unit", "failures", "integration", "coverage", "uncovered")

# 失敗の発生位置は自プロジェクトの最初のフレームを採る（フレームワーク内部は読み飛ばす）
AT_FRAME = re.compile(r"^\s*at\s+(com\.afkgame\.[\w.$]+\([^)]*\))", re.M)
# javac のエラー行。`[ERROR] /path/Foo.java:[10,20] シンボルを見つけられません`
JAVAC_ERROR = re.compile(r"^\[ERROR\]\s+(?P<path>.+?\.java):\[(?P<line>\d+),(?P<col>\d+)\]\s*(?P<msg>.*)$")
BUILDING = re.compile(r"^\[INFO\]\s+Building\s+(\S+)")
# スタックフレームは字下げ行でも取り込まない（1件で数十行になり要約の意味が消える）
STACK_FRAME = re.compile(r"^(?:at\s|\.{3}\s\d+\s+more)")
MAX_EXTRA = 6  # 1エラーあたりに拾う字下げ行の上限
# 原因ではなく案内文の `[ERROR]`。列挙しても失敗の特定に寄与しない
BOILERPLATE = re.compile(
    r"^(?:$|-> \[Help|\[Help \d|Re-?run Maven|For more information|After correcting"
    r"|To see the full stack trace|Please read the following|Failed to execute goal.*-> \[Help)"
)

TEST_ELEMENTS = {"failure": "FAIL", "error": "ERROR"}
RERUN_ELEMENTS = {"flakyFailure": "FLAKY", "flakyError": "FLAKY", "rerunFailure": "RERUN", "rerunError": "RERUN"}


# ── データ ────────────────────────────────────────────────────

@dataclass
class Suite:
    """モジュール1つ分のテスト実行結果。"""

    module: str
    tests: int = 0
    failures: int = 0
    errors: int = 0
    skipped: int = 0
    time: float = 0.0
    mtime: float = 0.0  # 採用したレポートの最終更新時刻（JaCoCo の鮮度比較に使う）

    def add(self, other: "Suite") -> None:
        self.tests += other.tests
        self.failures += other.failures
        self.errors += other.errors
        self.skipped += other.skipped
        self.time += other.time
        self.mtime = max(self.mtime, other.mtime)


@dataclass
class Case:
    """失敗したテスト1件。"""

    module: str
    label: str
    kind: str
    type: str
    message: str
    at: str


@dataclass
class Coverage:
    """モジュール1つ分の JaCoCo カバレッジ。"""

    module: str
    branch_missed: int = 0
    branch_covered: int = 0
    line_missed: int = 0
    line_covered: int = 0
    mtime: float = 0.0
    stale: bool = False  # surefire より古い＝現行コードを測っていない
    # (ソースパス, 行番号, 通った分岐数, 全分岐数)
    uncovered: list[tuple[str, int, int, int]] = field(default_factory=list)


@dataclass
class Tests:
    """レポート1種別（surefire か failsafe）の集計結果。"""

    suites: dict[str, Suite] = field(default_factory=dict)
    failures: list[Case] = field(default_factory=list)
    reruns: list[Case] = field(default_factory=list)
    stale: list[tuple[str, str]] = field(default_factory=list)  # (モジュール, 除外したクラスFQN)
    oldest: float | None = None
    newest: float | None = None

    def stamp(self, mtime: float) -> None:
        self.oldest = mtime if self.oldest is None else min(self.oldest, mtime)
        self.newest = mtime if self.newest is None else max(self.newest, mtime)


# ── 共通ユーティリティ ────────────────────────────────────────

def num(value: str | None) -> int:
    """XML 属性を整数へ。桁区切りの `,` は落とす（surefire はロケール依存で入れる）。"""
    try:
        return int((value or "0").replace(",", "").strip())
    except ValueError:
        return 0


def sec(value: str | None) -> float:
    try:
        return float((value or "0").replace(",", "").strip())
    except ValueError:
        return 0.0


def collapse(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


def clip(text: str, limit: int = MSG_MAX) -> str:
    return text if len(text) <= limit else f"{text[:limit]}…（+{len(text) - limit}字）"


def display_path(path: Path) -> str:
    """リポジトリ相対で表示する（外を指していれば絶対パスのまま）。"""
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return path.as_posix()


def pct(covered: int, missed: int) -> str:
    total = covered + missed
    return "n/a" if total == 0 else f"{covered / total * 100:.1f}%"


def table(headers: list[str], rows: list[list[str]], align: str) -> list[str]:
    """桁を揃えた表を返す。**セルは ASCII 前提**（全角が入ると桁が崩れる）。"""
    cells = [[str(v) for v in row] for row in [headers, *rows]]
    widths = [max(len(row[i]) for row in cells) for i in range(len(headers))]
    return [
        "  ".join(format(v, f"{align[i]}{widths[i]}") for i, v in enumerate(row)).rstrip()
        for row in cells
    ]


# ── surefire / failsafe ───────────────────────────────────────

def report_dirs(kind: str) -> list[tuple[str, Path]]:
    """`(モジュール名, レポートディレクトリ)` を返す。"""
    return [
        (path.parents[1].name, path)
        for path in sorted((ROOT / BACKEND).glob(f"*/target/{kind}"))
        if path.is_dir()
    ]


def test_class_names() -> set[str]:
    """`src/test/java` にあるテストクラスのFQN一式。レポートの陳腐化判定に使う。

    モジュールを問わず一括で集める。レポート側の所属モジュールと突き合わせないのは、
    テストを別モジュールへ移した場合に移動先を「存在しない」と誤判定しないため。
    """
    names: set[str] = set()
    for source_root in sorted((ROOT / BACKEND).glob(f"*/{TEST_SRC}")):
        for path in source_root.rglob("*.java"):
            names.add(".".join(path.relative_to(source_root).with_suffix("").parts))
    return names


def make_case(module: str, label: str, kind: str, element: ET.Element) -> Case:
    body = element.text or ""
    message = element.get("message") or ""
    if not message:
        lines = [line for line in body.splitlines() if line.strip()]
        message = lines[0] if lines else ""
    frame = AT_FRAME.search(body)
    return Case(module, label, kind, element.get("type") or "",
                clip(collapse(message)), frame.group(1) if frame else "")


def case_label(owner: str, testcase: ET.Element) -> str:
    """`<クラスFQN>[ > <@Nested の表示名>]#<テスト名>`。"""
    classname = testcase.get("classname") or ""
    name = testcase.get("name") or ""
    nested = f" > {classname}" if classname and classname != owner else ""
    return f"{owner or classname}{nested}#{name}"


def parse_tests(kind: str, sources: set[str]) -> Tests:
    """`kind` のレポートを集計する。

    件数は `<testsuite>` の属性ではなく **`<testcase>` の実数**から数える。
    `@Nested` を持つクラスでは surefire が属性へ 0 を書く（テストは内側の
    クラスに属し、外側の test set は 0 件と数えられる）ため、属性を信じると
    黙って過少集計になる。所要時間だけは実測値である属性を採る。

    `sources` に無いクラスのレポートは陳腐化として除外する。surefire は
    レポートディレクトリを掃除しないので、パッケージを移した・消したテストの
    旧レポートが残り、clean しない限り新旧の両方を数えてしまう。
    """
    result = Tests()

    for module, directory in report_dirs(kind):
        suite = result.suites.setdefault(module, Suite(module))
        for path in sorted(directory.glob("TEST-*.xml")):
            fqn = path.stem[len("TEST-"):]
            # sources が空（走査に失敗）なら判定しない。全件除外して「テスト0件」と答えないため
            if sources and fqn not in sources:
                result.stale.append((module, fqn))
                continue
            mtime = path.stat().st_mtime
            result.stamp(mtime)
            suite.mtime = max(suite.mtime, mtime)
            try:
                root = ET.parse(path).getroot()
            except ET.ParseError as exc:
                # 書きかけ・破損のレポートを黙って0件と読まない（誤って OK と判定するのを防ぐ）
                result.failures.append(Case(module, path.name, "ERROR", "ParseError", collapse(str(exc)), ""))
                suite.errors += 1
                continue
            for testsuite in root.iter("testsuite"):
                suite.time += sec(testsuite.get("time"))
                owner = testsuite.get("name") or ""
                for testcase in testsuite.iter("testcase"):
                    suite.tests += 1
                    label = case_label(owner, testcase)
                    outcome = ""
                    for child in testcase:
                        if child.tag in TEST_ELEMENTS and not outcome:
                            outcome = TEST_ELEMENTS[child.tag]
                            result.failures.append(make_case(module, label, outcome, child))
                        elif child.tag in RERUN_ELEMENTS:
                            result.reruns.append(make_case(module, label, RERUN_ELEMENTS[child.tag], child))
                        elif child.tag == "skipped" and not outcome:
                            outcome = "SKIP"
                    if outcome == "FAIL":
                        suite.failures += 1
                    elif outcome == "ERROR":
                        suite.errors += 1
                    elif outcome == "SKIP":
                        suite.skipped += 1

    result.suites = {m: s for m, s in result.suites.items() if s.tests or s.errors}
    return result


# ── JaCoCo ────────────────────────────────────────────────────

def parse_coverage() -> list[Coverage]:
    """モジュール別カバレッジを返す。"""
    result: list[Coverage] = []

    for path in sorted((ROOT / BACKEND).glob(f"*/{JACOCO_XML}")):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        coverage = Coverage(path.parents[3].name, mtime=path.stat().st_mtime)
        for counter in root.findall("counter"):  # 直下のみ＝レポート全体の合計
            if counter.get("type") == "BRANCH":
                coverage.branch_missed = num(counter.get("missed"))
                coverage.branch_covered = num(counter.get("covered"))
            elif counter.get("type") == "LINE":
                coverage.line_missed = num(counter.get("missed"))
                coverage.line_covered = num(counter.get("covered"))
        for package in root.findall("package"):
            prefix = package.get("name") or ""
            for sourcefile in package.findall("sourcefile"):
                source = f"{prefix}/{sourcefile.get('name')}" if prefix else str(sourcefile.get("name"))
                for line in sourcefile.findall("line"):
                    missed, covered = num(line.get("mb")), num(line.get("cb"))
                    if missed:
                        coverage.uncovered.append((source, num(line.get("nr")), covered, covered + missed))
        coverage.uncovered.sort()
        result.append(coverage)

    return result


def mark_stale_coverage(coverages: list[Coverage], suites: dict[str, Suite]) -> None:
    """surefire より古い JaCoCo レポートへ印を付ける。

    report ゴールは同モジュールの test フェーズ直後に走るため、正常な実行では
    JaCoCo のほうが必ず新しい。古ければ前回の実行の残骸であり、その 100% は
    現行コードを測った値ではない。
    """
    for coverage in coverages:
        suite = suites.get(coverage.module)
        if suite and suite.mtime and coverage.mtime < suite.mtime - STALE_GRACE:
            coverage.stale = True


# ── mvn ログ ──────────────────────────────────────────────────

def decode(raw: bytes) -> str:
    """UTF-8 で読めなければ CP932 で読む（日本語 Windows の mvn 出力）。"""
    try:
        return raw.decode("utf-8")
    except UnicodeDecodeError:
        return raw.decode("cp932", errors="replace")


def continuation(line: str) -> str | None:
    """直前のエラーへぶら下がる字下げ行（`シンボル: クラス X` 等）なら中身を返す。"""
    if line.startswith("[ERROR]"):
        rest = line[len("[ERROR]"):]
        return rest.strip() if rest.startswith("  ") and rest.strip() else None
    return line.strip() if line[:1].isspace() and line.strip() else None


def parse_log(text: str) -> tuple[str, list[tuple[str, str, list[str]]]]:
    """`(BUILD 結果, [(モジュール, 見出し, 字下げ行)])` を返す。"""
    build = "UNKNOWN"
    entries: list[tuple[str, str, list[str]]] = []
    module = "-"

    for line in text.splitlines():
        line = line.rstrip()
        if "BUILD SUCCESS" in line:
            build = "SUCCESS"
        elif "BUILD FAILURE" in line:
            build = "FAILURE"
        matched = BUILDING.match(line)
        if matched:
            module = matched.group(1)
            continue
        extra = continuation(line)
        if extra is not None and entries:
            if not STACK_FRAME.match(extra) and len(entries[-1][2]) < MAX_EXTRA:
                entries[-1][2].append(extra)
            continue
        if not line.startswith("[ERROR]"):
            continue
        body = line[len("[ERROR]"):].strip()
        if BOILERPLATE.match(body):
            continue
        javac = JAVAC_ERROR.match(line)
        if javac:
            source = Path(javac.group("path")).name
            body = f"{source}:{javac.group('line')},{javac.group('col')} {javac.group('msg')}".strip()
        entries.append((module, body, []))

    return build, entries


# ── mvn 実行 ──────────────────────────────────────────────────

def maven_command(args: argparse.Namespace) -> list[str]:
    """`--it` `--test` `--module` から mvn の引数列を組む。"""
    if args.test:
        command = ["test", f"-Dtest={args.test}", "-Dsurefire.failIfNoSpecifiedTests=false"]
    elif args.it:
        command = ["verify"]
    else:
        command = ["verify", "-DskipITs"]
    # clean を先に置く。surefire はレポートディレクトリを掃除しないので、これが無いと
    # 移動・改名したテストの旧レポートが残って二重計上になる
    if not args.no_clean:
        command = ["clean", *command]
    if args.module:
        command += ["-pl", args.module, "-am"]  # `-am` が無いと ~/.m2 の変更前成果物を掴む
    return command


def run_maven(args: argparse.Namespace, log_path: Path) -> tuple[list[str], int]:
    """mvn を実行してログをファイルへ落とし、`(コマンド, 終了コード)` を返す。"""
    mvn = shutil.which("mvn")
    if mvn is None:
        raise SystemExit("ERROR mvn が PATH に見つからない（commands/backend.md §5 を参照）")

    log_path.parent.mkdir(parents=True, exist_ok=True)
    captured = bytearray()
    command = maven_command(args)

    # 単一モジュールを回す前に親 POM を入れておかないと依存解決に失敗する
    steps = [["-N", "install", "-q"], command] if args.module else [command]
    code = 0
    for step in steps:
        proc = subprocess.run([mvn, *step], cwd=ROOT / BACKEND,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
        captured += b"$ mvn " + " ".join(step).encode("utf-8") + b"\n" + proc.stdout
        code = proc.returncode
        if code != 0:
            break

    # 実行後にもう一度掘る。`clean` が backend/target ごと消すため、実行前に作った
    # ディレクトリは残っていない（集約 POM は成果物を作らないので mvn も作り直さない）
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_path.write_bytes(bytes(captured))
    return command, code


# ── 出力 ──────────────────────────────────────────────────────

def render_stale(stale: list[tuple[str, str]]) -> list[str]:
    """除外した陳腐化レポートを並べる（黙って捨てると件数の変化が説明できない）。"""
    if not stale:
        return []
    out = [f"陳腐化レポートを除外  {len(stale)} 件（対応するテストクラスが無い。clean で消える）:"]
    out += [f"  {module}  {fqn}" for module, fqn in stale[:STALE_MAX]]
    if len(stale) > STALE_MAX:
        out.append(f"  …ほか {len(stale) - STALE_MAX} 件")
    return out


def render_suites(title: str, tests: Tests) -> tuple[list[str], Suite]:
    total = Suite("TOTAL")
    out = [f"== {title} =="]
    if not tests.suites:
        return out + ["（レポートなし）"] + render_stale(tests.stale), total

    rows = []
    for module in sorted(tests.suites):
        suite = tests.suites[module]
        total.add(suite)
        rows.append([suite.module, suite.tests, suite.failures, suite.errors, suite.skipped, f"{suite.time:.2f}"])
    rows.append(["TOTAL", total.tests, total.failures, total.errors, total.skipped, f"{total.time:.2f}"])
    out += table(["MODULE", "TESTS", "FAIL", "ERROR", "SKIP", "TIME"], rows, "<>>>>>")
    return out + render_stale(tests.stale), total


def render_failures(cases: list[Case], reruns: list[Case], limit: int) -> list[str]:
    out = [f"== 失敗の詳細 ({len(cases)}) =="]
    if not cases:
        out.append("（なし）")
    for case in cases[:limit]:
        out.append(f"{case.kind}  {case.label}")
        out.append(f"  module   {case.module}")
        if case.type:
            out.append(f"  type     {case.type}")
        if case.message:
            out.append(f"  message  {case.message}")
        if case.at:
            out.append(f"  at       {case.at}")
    if len(cases) > limit:
        out.append(f"…ほか {len(cases) - limit} 件（--max-failures で増やす）")
    if reruns:
        out.append(f"RERUN/FLAKY  {len(reruns)} 件: " + ", ".join(c.label for c in reruns[:10]))
    return out


def render_coverage(coverages: list[Coverage]) -> tuple[list[str], int, int, int]:
    """`(出力行, 通った分岐, 未達分岐, 陳腐化モジュール数)` を返す。"""
    out = ["== C1 分岐カバレッジ（JaCoCo） =="]
    if not coverages:
        return out + ["（レポートなし。`--run` か `mvn clean verify -DskipITs` で生成する）"], 0, 0, 0

    rows, covered, missed, stale = [], 0, 0, 0
    for coverage in sorted(coverages, key=lambda c: c.module):
        covered += coverage.branch_covered
        missed += coverage.branch_missed
        stale += coverage.stale
        rows.append([
            coverage.module,
            pct(coverage.branch_covered, coverage.branch_missed),
            f"{coverage.branch_covered}/{coverage.branch_covered + coverage.branch_missed}",
            coverage.branch_missed,
            pct(coverage.line_covered, coverage.line_missed),
            "STALE" if coverage.stale else ("OK" if coverage.branch_missed == 0 else "NG"),
        ])
    rows.append(["TOTAL", pct(covered, missed), f"{covered}/{covered + missed}", missed,
                 "", "STALE" if stale else ("OK" if missed == 0 else "NG")])
    header = ["MODULE", "BRANCH", "COVERED", "MISSED", "LINE", "STATUS"]
    out += table(header, rows, "<>>>><")
    if stale:
        out.append("STALE  テストより古いレポート＝現行コードの測定値ではない。`--run` で取り直す")
    return out, covered, missed, stale


def render_uncovered(coverages: list[Coverage], limit: int) -> list[str]:
    rows = [[c.module, f"{source}:{line}", f"{covered}/{total}"]
            for c in sorted(coverages, key=lambda c: c.module)
            for source, line, covered, total in c.uncovered]
    out = [f"== 未達分岐 ({len(rows)}) =="]
    if not rows:
        return out + ["（なし）"]
    out += table(["MODULE", "SOURCE:LINE", "BRANCH"], rows[:limit], "<<>")
    if len(rows) > limit:
        out.append(f"…ほか {len(rows) - limit} 件（--max-uncovered で増やす）")
    return out


def render_build(build: str, entries: list[tuple[str, str, list[str]]], limit: int) -> list[str]:
    out = ["== ビルド ==", f"BUILD  {build}", f"ERROR_LINES  {len(entries)}"]
    for module, head, extra in entries[:limit]:
        out.append(f"  [{module}] {head}")
        out += [f"      {line}" for line in extra]
    if len(entries) > limit:
        out.append(f"…ほか {len(entries) - limit} 件（--max-errors で増やす）")
    return out


def render_verdict(unit: Suite, it: Suite, covered: int, missed: int, has_reports: bool,
                   has_coverage: bool, stamps: list[float], build_failed: bool,
                   coverage_stale: int, stale_reports: int) -> tuple[list[str], str]:
    if not has_reports:
        result = "NO_REPORT"
    elif unit.failures or unit.errors or it.failures or it.errors:
        result = "FAIL"
    elif build_failed:
        # コンパイルで落ちるとレポートが更新されない。前回の成功分を読んで OK と答えないための分岐
        result = "BUILD_FAILURE"
    elif has_coverage and coverage_stale:
        # 古いレポートの 100% を根拠に OK と答えない
        result = "COVERAGE_STALE"
    elif has_coverage and missed:
        result = "COVERAGE_NG"
    else:
        result = "OK"

    out = ["== 判定 =="]
    for key, value in (
        ("UNIT_TESTS", unit.tests), ("UNIT_FAILED", unit.failures),
        ("UNIT_ERRORS", unit.errors), ("UNIT_SKIPPED", unit.skipped),
        ("IT_TESTS", it.tests), ("IT_FAILED", it.failures + it.errors),
    ):
        out.append(f"{key:<17}{value}")
    if has_coverage:
        out.append(f"{'BRANCH_COVERAGE':<17}{pct(covered, missed)}  ({covered}/{covered + missed})")
        out.append(f"{'MISSED_BRANCHES':<17}{missed}")
    else:
        out.append(f"{'BRANCH_COVERAGE':<17}未計測")
    if stale_reports:
        out.append(f"{'STALE_REPORTS':<17}{stale_reports}  (集計から除外)")
    if stamps:
        oldest, newest = min(stamps), max(stamps)
        shown = f"{datetime.fromtimestamp(newest):%Y-%m-%d %H:%M:%S}"
        if newest - oldest > MIXED_RUN_GAP:
            shown = f"{datetime.fromtimestamp(oldest):%Y-%m-%d %H:%M:%S} 〜 {shown}  (別々の実行が混在)"
        out.append(f"{'REPORT_TIME':<17}{shown}")
    out.append(f"{'RESULT':<17}{result}")
    return out, result


# ── エントリポイント ──────────────────────────────────────────

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Java テスト結果（surefire・failsafe・JaCoCo）を要約して出力する")
    parser.add_argument("--run", action="store_true", help="mvn を実行してから集計する（既定で clean 込み）")
    parser.add_argument("--no-clean", action="store_true",
                        help="--run と併用。clean を省いて速くする（旧レポートが残るぶん集計の精度は落ちる）")
    parser.add_argument("--it", action="store_true", help="--run と併用。結合テストも回す（mvn verify）")
    parser.add_argument("--test", metavar="CLASS", help="--run と併用。クラスを絞って mvn test")
    parser.add_argument("--module", metavar="NAME", help="--run と併用。-pl NAME -am で絞る")
    parser.add_argument("--log", metavar="PATH", help="解析する mvn ログ（--run 時の出力先も兼ねる）")
    parser.add_argument("--max-failures", type=int, default=20, metavar="N")
    parser.add_argument("--max-uncovered", type=int, default=50, metavar="N")
    parser.add_argument("--max-errors", type=int, default=20, metavar="N")
    for name in SECTIONS:
        parser.add_argument(f"--{name}", action="store_true", help=f"{name} 節のみ出力する")
    return parser


def report(args: argparse.Namespace) -> tuple[str, str]:
    """`(出力文字列, RESULT)` を返す。"""
    selected = {name for name in SECTIONS if getattr(args, name)} or set(SECTIONS)
    log_path = ROOT / (args.log or DEFAULT_LOG)
    lines: list[str] = []
    build_failed = False

    if args.run:
        command, code = run_maven(args, log_path)
        build_failed = code != 0
        lines += ["== 実行 ==", f"CMD    mvn {' '.join(command)}", f"EXIT   {code}",
                  f"LOG    {display_path(log_path)}", ""]

    if args.run or args.log:
        # 節を出さない場合も解析する（BUILD FAILURE を判定へ反映するため）
        if log_path.exists():
            build, entries = parse_log(decode(log_path.read_bytes()))
            build_failed = build_failed or build == "FAILURE"
            if "build" in selected:
                lines += render_build(build, entries, args.max_errors) + [""]
        elif "build" in selected:
            lines += ["== ビルド ==", f"（ログがない: {display_path(log_path)}）", ""]

    sources = test_class_names()
    unit = parse_tests(SUREFIRE, sources)
    it = parse_tests(FAILSAFE, sources)
    coverages = parse_coverage()
    mark_stale_coverage(coverages, unit.suites)

    unit_lines, unit_total = render_suites("単体テスト（surefire）", unit)
    it_lines, it_total = render_suites("結合テスト（failsafe）", it)
    coverage_lines, covered, missed, coverage_stale = render_coverage(coverages)

    if "unit" in selected:
        lines += unit_lines + [""]
    if "failures" in selected:
        lines += render_failures(unit.failures + it.failures, unit.reruns + it.reruns, args.max_failures) + [""]
    if "integration" in selected:
        lines += it_lines + [""]
    if "coverage" in selected:
        lines += coverage_lines + [""]
    if "uncovered" in selected:
        lines += render_uncovered(coverages, args.max_uncovered) + [""]

    stamps = [t for t in (unit.oldest, unit.newest, it.oldest, it.newest) if t is not None]
    stamps += [c.mtime for c in coverages]
    verdict, result = render_verdict(unit_total, it_total, covered, missed, bool(unit.suites or it.suites),
                                     bool(coverages), stamps, build_failed,
                                     coverage_stale, len(unit.stale) + len(it.stale))
    return "\n".join(lines + verdict), result


def main(argv: list[str] | None = None) -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):  # pragma: no cover - 実行環境依存
        pass

    args = build_parser().parse_args(argv)
    text, result = report(args)
    print(text)
    return 0 if result == "OK" else 1


if __name__ == "__main__":
    sys.exit(main())
