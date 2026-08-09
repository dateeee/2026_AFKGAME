#!/usr/bin/env python3
"""Java テスト結果の集計出力（surefire・failsafe・JaCoCo・mvn ログ）

`mvn` の生出力は長く CP932 で、会話へ持ち込むと文脈を食い潰す
（`.claude/project/commands.md` §2）。本スクリプトは XML レポートから
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
    python scripts/report_java_tests.py --run           # mvn verify -DskipITs → 集計
    python scripts/report_java_tests.py --run --it      # mvn verify（結合テスト込み）→ 集計
    python scripts/report_java_tests.py --run --test BattleServiceTest   # 絞って mvn test
    python scripts/report_java_tests.py --run --module afkgame-domain    # -pl + -am
    python scripts/report_java_tests.py --log backend/target/mvn.log     # ログのみ解析
    python scripts/report_java_tests.py --coverage --uncovered           # 節を絞る

`--module` は親 POM の `mvn -N install -q` を先に流し、`--test` は
`-Dsurefire.failIfNoSpecifiedTests=false` を自動で足す（commands.md §3 の
「セットで使う4点」を取りこぼさないため）。
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
JACOCO_XML = "target/site/jacoco/jacoco.xml"
DEFAULT_LOG = "backend/target/mvn.log"

MSG_MAX = 300  # 失敗メッセージの表示上限（字）
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

    def add(self, other: "Suite") -> None:
        self.tests += other.tests
        self.failures += other.failures
        self.errors += other.errors
        self.skipped += other.skipped
        self.time += other.time


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
    # (ソースパス, 行番号, 通った分岐数, 全分岐数)
    uncovered: list[tuple[str, int, int, int]] = field(default_factory=list)


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


def parse_tests(kind: str) -> tuple[dict[str, Suite], list[Case], list[Case], float | None]:
    """`(モジュール別集計, 失敗一覧, 再実行一覧, 最新レポートの更新時刻)` を返す。

    件数は `<testsuite>` の属性ではなく **`<testcase>` の実数**から数える。
    `@Nested` を持つクラスでは surefire が属性へ 0 を書く（テストは内側の
    クラスに属し、外側の test set は 0 件と数えられる）ため、属性を信じると
    黙って過少集計になる。所要時間だけは実測値である属性を採る。
    """
    suites: dict[str, Suite] = {}
    failures: list[Case] = []
    reruns: list[Case] = []
    newest: float | None = None

    for module, directory in report_dirs(kind):
        suite = suites.setdefault(module, Suite(module))
        for path in sorted(directory.glob("TEST-*.xml")):
            mtime = path.stat().st_mtime
            newest = mtime if newest is None else max(newest, mtime)
            try:
                root = ET.parse(path).getroot()
            except ET.ParseError as exc:
                # 書きかけ・破損のレポートを黙って0件と読まない（誤って OK と判定するのを防ぐ）
                failures.append(Case(module, path.name, "ERROR", "ParseError", collapse(str(exc)), ""))
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
                            failures.append(make_case(module, label, outcome, child))
                        elif child.tag in RERUN_ELEMENTS:
                            reruns.append(make_case(module, label, RERUN_ELEMENTS[child.tag], child))
                        elif child.tag == "skipped" and not outcome:
                            outcome = "SKIP"
                    if outcome == "FAIL":
                        suite.failures += 1
                    elif outcome == "ERROR":
                        suite.errors += 1
                    elif outcome == "SKIP":
                        suite.skipped += 1

    return {m: s for m, s in suites.items() if s.tests or s.errors}, failures, reruns, newest


# ── JaCoCo ────────────────────────────────────────────────────

def parse_coverage() -> tuple[list[Coverage], float | None]:
    """`(モジュール別カバレッジ, 最新レポートの更新時刻)` を返す。"""
    result: list[Coverage] = []
    newest: float | None = None

    for path in sorted((ROOT / BACKEND).glob(f"*/{JACOCO_XML}")):
        mtime = path.stat().st_mtime
        newest = mtime if newest is None else max(newest, mtime)
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        coverage = Coverage(path.parents[3].name)
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

    return result, newest


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
    if args.module:
        command += ["-pl", args.module, "-am"]  # `-am` が無いと ~/.m2 の変更前成果物を掴む
    return command


def run_maven(args: argparse.Namespace, log_path: Path) -> tuple[list[str], int]:
    """mvn を実行してログをファイルへ落とし、`(コマンド, 終了コード)` を返す。"""
    mvn = shutil.which("mvn")
    if mvn is None:
        raise SystemExit("ERROR mvn が PATH に見つからない（commands.md §5 を参照）")

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

    log_path.write_bytes(bytes(captured))
    return command, code


# ── 出力 ──────────────────────────────────────────────────────

def render_suites(title: str, suites: dict[str, Suite]) -> tuple[list[str], Suite]:
    total = Suite("TOTAL")
    out = [f"== {title} =="]
    if not suites:
        return out + ["（レポートなし）"], total

    rows = []
    for module in sorted(suites):
        suite = suites[module]
        total.add(suite)
        rows.append([suite.module, suite.tests, suite.failures, suite.errors, suite.skipped, f"{suite.time:.2f}"])
    rows.append(["TOTAL", total.tests, total.failures, total.errors, total.skipped, f"{total.time:.2f}"])
    return out + table(["MODULE", "TESTS", "FAIL", "ERROR", "SKIP", "TIME"], rows, "<>>>>>"), total


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
    out = ["== C1 分岐カバレッジ（JaCoCo） =="]
    if not coverages:
        return out + ["（レポートなし。`--run` か `mvn verify -DskipITs` で生成する）"], 0, 0, 0

    rows, covered, missed, lines_missed = [], 0, 0, 0
    for coverage in sorted(coverages, key=lambda c: c.module):
        covered += coverage.branch_covered
        missed += coverage.branch_missed
        lines_missed += coverage.line_missed
        rows.append([
            coverage.module,
            pct(coverage.branch_covered, coverage.branch_missed),
            f"{coverage.branch_covered}/{coverage.branch_covered + coverage.branch_missed}",
            coverage.branch_missed,
            pct(coverage.line_covered, coverage.line_missed),
            "OK" if coverage.branch_missed == 0 else "NG",
        ])
    rows.append(["TOTAL", pct(covered, missed), f"{covered}/{covered + missed}", missed,
                 "", "OK" if missed == 0 else "NG"])
    header = ["MODULE", "BRANCH", "COVERED", "MISSED", "LINE", "STATUS"]
    return out + table(header, rows, "<>>>><"), covered, missed, lines_missed


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
                   has_coverage: bool, newest: float | None, build_failed: bool) -> tuple[list[str], str]:
    if not has_reports:
        result = "NO_REPORT"
    elif unit.failures or unit.errors or it.failures or it.errors:
        result = "FAIL"
    elif build_failed:
        # コンパイルで落ちるとレポートが更新されない。前回の成功分を読んで OK と答えないための分岐
        result = "BUILD_FAILURE"
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
    if newest is not None:
        out.append(f"{'REPORT_TIME':<17}{datetime.fromtimestamp(newest):%Y-%m-%d %H:%M:%S}")
    out.append(f"{'RESULT':<17}{result}")
    return out, result


# ── エントリポイント ──────────────────────────────────────────

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Java テスト結果（surefire・failsafe・JaCoCo）を要約して出力する")
    parser.add_argument("--run", action="store_true", help="mvn を実行してから集計する")
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

    unit_suites, unit_failures, unit_reruns, unit_time = parse_tests(SUREFIRE)
    it_suites, it_failures, it_reruns, it_time = parse_tests(FAILSAFE)
    coverages, coverage_time = parse_coverage()

    unit_lines, unit_total = render_suites("単体テスト（surefire）", unit_suites)
    it_lines, it_total = render_suites("結合テスト（failsafe）", it_suites)
    coverage_lines, covered, missed, _ = render_coverage(coverages)

    if "unit" in selected:
        lines += unit_lines + [""]
    if "failures" in selected:
        lines += render_failures(unit_failures + it_failures, unit_reruns + it_reruns, args.max_failures) + [""]
    if "integration" in selected:
        lines += it_lines + [""]
    if "coverage" in selected:
        lines += coverage_lines + [""]
    if "uncovered" in selected:
        lines += render_uncovered(coverages, args.max_uncovered) + [""]

    newest = max((t for t in (unit_time, it_time, coverage_time) if t is not None), default=None)
    verdict, result = render_verdict(unit_total, it_total, covered, missed, bool(unit_suites or it_suites),
                                     bool(coverages), newest, build_failed)
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
