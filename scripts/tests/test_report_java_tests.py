"""report_java_tests.py の回帰テスト。

実行: `python -m pytest scripts/tests -q`（リポジトリルートから）

`ROOT` を `tmp_path` へ差し替え、実リポジトリの `backend/target/` に依存させない。
集計・検出の各項目は「正常なレポートを正しく読む（緑パス）」と「1項目だけ
壊す/足すと結果へ現れる（変異）」を対で置く。**黙って 0 件と読む退行**
（`@Nested` での属性 0・レポート破損・ディレクトリ空・カバレッジ未生成）は
実データで踏んだものとして個別に固定している。
"""

import pytest

import report_java_tests as mod


@pytest.fixture
def root(tmp_path, monkeypatch):
    """`ROOT` を差し替えた空リポジトリを返す。"""
    monkeypatch.setattr(mod, "ROOT", tmp_path)
    return tmp_path


# ── フィクスチャ生成 ──────────────────────────────────────────

SUITE = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="{name}" time="{time}" tests="{tests}" errors="{errors}" skipped="{skipped}" failures="{failures}">
{cases}
</testsuite>
"""

JACOCO = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
<report name="{module}">
  <package name="com/afkgame/domain/service">
    <sourcefile name="BattleService.java">
{lines}
      <counter type="LINE" missed="{lm}" covered="{lc}"/>
    </sourcefile>
  </package>
  <counter type="LINE" missed="{lm}" covered="{lc}"/>
  <counter type="BRANCH" missed="{bm}" covered="{bc}"/>
</report>
"""

CLS = "com.afkgame.domain.BattleServiceTest"


def suite(root, module, cls=CLS, *, cases="", tests=0, failures=0, errors=0, skipped=0,
          time="0.50", kind="surefire-reports"):
    """surefire/failsafe のレポート1ファイルを置く（属性値は宣言だけで集計に使われない）。"""
    path = root / "backend" / module / "target" / kind / f"TEST-{cls}.xml"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(SUITE.format(name=cls, time=time, tests=tests, errors=errors,
                                 skipped=skipped, failures=failures, cases=cases), encoding="utf-8")
    return path


def jacoco(root, module, *, bm=0, bc=10, lm=0, lc=20, lines=""):
    path = root / "backend" / module / "target" / "site" / "jacoco" / "jacoco.xml"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(JACOCO.format(module=module, bm=bm, bc=bc, lm=lm, lc=lc, lines=lines), encoding="utf-8")
    return path


def passing(count, cls=CLS):
    return "\n".join(f'<testcase name="t{i}" classname="{cls}" time="0.01"/>' for i in range(count))


SKIPPED = f'<testcase name="wip" classname="{CLS}" time="0.0"><skipped/></testcase>'

FAILING = f"""<testcase name="ダメージが下限でクランプされる" classname="{CLS}" time="0.5">
  <failure message="expected: &lt;1&gt; but was: &lt;0&gt;" type="org.opentest4j.AssertionFailedError">
org.opentest4j.AssertionFailedError: expected: &lt;1&gt; but was: &lt;0&gt;
\tat org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:38)
\tat com.afkgame.domain.BattleServiceTest.clampsMinimumDamage(BattleServiceTest.java:123)
  </failure>
</testcase>"""

ERRORING = f"""<testcase name="boom" classname="{CLS}" time="0.1">
  <error message="NPE" type="java.lang.NullPointerException">
java.lang.NullPointerException
\tat com.afkgame.domain.BattleServiceTest.boom(BattleServiceTest.java:9)
  </error>
</testcase>"""

FLAKY = f"""<testcase name="unstable" classname="{CLS}" time="0.1">
  <flakyFailure message="1回目は落ちた" type="org.opentest4j.AssertionFailedError">detail</flakyFailure>
</testcase>"""

# `@Nested` + `@DisplayName`。classname に表示名が入り、testsuite の属性は 0 になる
NESTED = """<testcase name="test_境界の3文字でも先頭2文字を残す" classname="メールアドレス" time="0.0"/>
<testcase name="test_空文字も全体を伏せる" classname="トークン" time="0.001"/>"""


# ── 属性の読み取り ────────────────────────────────────────────

def test_num_strips_locale_thousands_separator():
    assert mod.num("1,234") == 1234
    assert mod.sec("1,234.5") == pytest.approx(1234.5)
    assert mod.num(None) == 0 and mod.num("-") == 0


def test_clip_marks_truncation():
    assert mod.clip("あ" * 10, limit=4) == "ああああ…（+6字）"
    assert mod.clip("あ" * 4, limit=4) == "ああああ"


# ── surefire / failsafe ───────────────────────────────────────

def test_parse_tests_aggregates_modules(root):
    suite(root, "afkgame-env", "com.afkgame.env.TokenTest", cases=passing(4), time="1.25")
    suite(root, "afkgame-domain", cases=passing(5) + "\n" + SKIPPED, time="2.75")
    suites, failures, reruns, newest = mod.parse_tests(mod.SUREFIRE)

    assert sorted(suites) == ["afkgame-domain", "afkgame-env"]
    assert suites["afkgame-domain"].tests == 6
    assert suites["afkgame-domain"].skipped == 1
    assert suites["afkgame-domain"].time == pytest.approx(2.75)
    assert (failures, reruns) == ([], [])
    assert newest is not None


def test_parse_tests_counts_testcases_not_suite_attributes(root):
    """`@Nested` を持つクラスは surefire が `tests="0"` を書く（実データで確認済み）。"""
    suite(root, "afkgame-env", "com.afkgame.env.logging.LogMaskerTest", cases=NESTED, tests=0)
    assert mod.parse_tests(mod.SUREFIRE)[0]["afkgame-env"].tests == 2


def test_parse_tests_sums_multiple_files_of_same_module(root):
    suite(root, "afkgame-web", "com.afkgame.web.ATest", cases=passing(3))
    suite(root, "afkgame-web", "com.afkgame.web.BTest", cases=passing(5))
    assert mod.parse_tests(mod.SUREFIRE)[0]["afkgame-web"].tests == 8


def test_parse_tests_detects_failure_with_type_message_and_frame(root):
    suite(root, "afkgame-domain", cases=FAILING)
    suites, failures, _, _ = mod.parse_tests(mod.SUREFIRE)

    assert suites["afkgame-domain"].failures == 1
    assert len(failures) == 1
    case = failures[0]
    assert case.kind == "FAIL"
    assert case.module == "afkgame-domain"
    assert case.label == f"{CLS}#ダメージが下限でクランプされる"
    assert case.type == "org.opentest4j.AssertionFailedError"
    assert case.message == "expected: <1> but was: <0>"
    # 自プロジェクトの最初のフレームを採る（JUnit 内部の AssertionUtils は飛ばす）
    assert case.at == "com.afkgame.domain.BattleServiceTest.clampsMinimumDamage(BattleServiceTest.java:123)"


def test_parse_tests_labels_nested_display_name_with_owning_class(root):
    nested_failure = NESTED.replace(
        'classname="トークン" time="0.001"/>',
        'classname="トークン" time="0.001"><failure message="ng" type="X">boom</failure></testcase>')
    suite(root, "afkgame-env", "com.afkgame.env.logging.LogMaskerTest", cases=nested_failure)
    case = mod.parse_tests(mod.SUREFIRE)[1][0]
    assert case.label == "com.afkgame.env.logging.LogMaskerTest > トークン#test_空文字も全体を伏せる"


def test_parse_tests_detects_error_element(root):
    suite(root, "afkgame-domain", cases=ERRORING)
    suites, failures, _, _ = mod.parse_tests(mod.SUREFIRE)
    assert suites["afkgame-domain"].errors == 1
    assert [(c.kind, c.type) for c in failures] == [("ERROR", "java.lang.NullPointerException")]


def test_parse_tests_separates_rerun_from_failure(root):
    suite(root, "afkgame-domain", cases=FLAKY)
    suites, failures, reruns, _ = mod.parse_tests(mod.SUREFIRE)
    assert suites["afkgame-domain"].failures == 0
    assert failures == []
    assert [c.kind for c in reruns] == ["FLAKY"]


def test_parse_tests_reports_broken_xml_as_error(root):
    """壊れたレポートを 0 件と読んで OK 判定にしない。"""
    path = suite(root, "afkgame-domain", cases=passing(1))
    path.write_text("<testsuite tests=", encoding="utf-8")
    suites, failures, _, _ = mod.parse_tests(mod.SUREFIRE)
    assert suites["afkgame-domain"].errors == 1
    assert failures[0].type == "ParseError"


def test_parse_tests_drops_module_without_any_report(root):
    (root / "backend" / "afkgame-initdb" / "target" / "surefire-reports").mkdir(parents=True)
    assert mod.parse_tests(mod.SUREFIRE)[0] == {}


def test_parse_tests_reads_failsafe_separately(root):
    suite(root, "afkgame-web", "com.afkgame.web.AuthIntegrationTest",
          cases=passing(2), kind="failsafe-reports")
    assert mod.parse_tests(mod.SUREFIRE)[0] == {}
    assert mod.parse_tests(mod.FAILSAFE)[0]["afkgame-web"].tests == 2


# ── JaCoCo ────────────────────────────────────────────────────

def test_parse_coverage_reads_report_level_counters(root):
    jacoco(root, "afkgame-domain", bm=0, bc=42, lm=0, lc=100)
    coverages, newest = mod.parse_coverage()
    assert [c.module for c in coverages] == ["afkgame-domain"]
    assert (coverages[0].branch_covered, coverages[0].branch_missed) == (42, 0)
    assert coverages[0].uncovered == []
    assert newest is not None


def test_parse_coverage_lists_uncovered_branch_lines(root):
    lines = ('      <line nr="88" mi="0" ci="3" mb="1" cb="1"/>\n'
             '      <line nr="12" mi="0" ci="3" mb="0" cb="2"/>\n'
             '      <line nr="90" mi="4" ci="0" mb="2" cb="0"/>')
    jacoco(root, "afkgame-domain", bm=3, bc=39, lines=lines)
    coverage = mod.parse_coverage()[0][0]

    assert coverage.branch_missed == 3
    # 分岐が残る行だけを行番号順で挙げる（mb=0 の 12 行目は出さない）
    assert coverage.uncovered == [
        ("com/afkgame/domain/service/BattleService.java", 88, 1, 2),
        ("com/afkgame/domain/service/BattleService.java", 90, 0, 2),
    ]


def test_parse_coverage_ignores_broken_report(root):
    path = jacoco(root, "afkgame-domain")
    path.write_text("<report", encoding="utf-8")
    assert mod.parse_coverage()[0] == []


def test_pct_handles_zero_denominator():
    assert mod.pct(0, 0) == "n/a"
    assert mod.pct(3, 1) == "75.0%"


# ── mvn ログ ──────────────────────────────────────────────────

LOG = """[INFO] Building afkgame-domain 0.1.0
[INFO] --- compiler:3.13.0:compile ---
[ERROR] /c/GIT/backend/afkgame-domain/src/main/java/Foo.java:[88,17] シンボルを見つけられません
  シンボル:   メソッド calc(int)
  場所: クラス com.afkgame.domain.service.BattleService
[ERROR]   補足: 型を確認すること
[INFO] BUILD FAILURE
[ERROR] Failed to execute goal compiler:compile on project afkgame-domain
[ERROR]
[ERROR] -> [Help 1]
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
"""


def test_parse_log_extracts_javac_error_with_indented_lines():
    build, entries = mod.parse_log(LOG)
    assert build == "FAILURE"

    module, head, extra = entries[0]
    assert module == "afkgame-domain"
    assert head == "Foo.java:88,17 シンボルを見つけられません"
    # 素の字下げ行と `[ERROR]` 付き字下げ行の両方を拾う（grep では前者が落ちる）
    assert extra == ["シンボル:   メソッド calc(int)",
                     "場所: クラス com.afkgame.domain.service.BattleService",
                     "補足: 型を確認すること"]


def test_parse_log_drops_boilerplate_error_lines():
    _, entries = mod.parse_log(LOG)
    heads = [head for _, head, _ in entries]
    assert heads == ["Foo.java:88,17 シンボルを見つけられません",
                     "Failed to execute goal compiler:compile on project afkgame-domain"]


def test_parse_log_detects_success():
    assert mod.parse_log("[INFO] BUILD SUCCESS")[0] == "SUCCESS"
    assert mod.parse_log("[INFO] nothing")[0] == "UNKNOWN"


def test_parse_log_skips_stack_frames_and_caps_extra_lines():
    text = "[ERROR] boom\n\tat com.afkgame.Foo.bar(Foo.java:1)\n" + "".join(f"  note {i}\n" for i in range(10))
    _, entries = mod.parse_log(text)
    assert entries[0][2] == [f"note {i}" for i in range(mod.MAX_EXTRA)]


def test_decode_falls_back_to_cp932():
    assert mod.decode("シンボル".encode("utf-8")) == "シンボル"
    assert mod.decode("シンボル".encode("cp932")) == "シンボル"


# ── mvn の組み立て ────────────────────────────────────────────

def parse_args(*argv):
    return mod.build_parser().parse_args(list(argv))


@pytest.mark.parametrize("argv, expected", [
    (["--run"], ["verify", "-DskipITs"]),
    (["--run", "--it"], ["verify"]),
    (["--run", "--test", "BattleServiceTest"],
     ["test", "-Dtest=BattleServiceTest", "-Dsurefire.failIfNoSpecifiedTests=false"]),
    (["--run", "--module", "afkgame-domain"], ["verify", "-DskipITs", "-pl", "afkgame-domain", "-am"]),
])
def test_maven_command(argv, expected):
    assert mod.maven_command(parse_args(*argv)) == expected


def test_run_maven_prepends_parent_install_for_single_module(root, monkeypatch):
    calls = []

    class Result:
        returncode = 0
        stdout = b"[INFO] BUILD SUCCESS\n"

    monkeypatch.setattr(mod.shutil, "which", lambda _: "mvn")
    monkeypatch.setattr(mod.subprocess, "run", lambda cmd, **kw: calls.append(cmd) or Result())

    command, code = mod.run_maven(parse_args("--run", "--module", "afkgame-domain"), root / "mvn.log")
    assert code == 0
    assert calls[0] == ["mvn", "-N", "install", "-q"]  # 欠けると依存解決に失敗する
    assert calls[1] == ["mvn", *command]
    assert (root / "mvn.log").exists()


def test_run_maven_fails_clearly_without_mvn(root, monkeypatch):
    monkeypatch.setattr(mod.shutil, "which", lambda _: None)
    with pytest.raises(SystemExit):
        mod.run_maven(parse_args("--run"), root / "mvn.log")


# ── 判定と出力 ────────────────────────────────────────────────

def test_report_ok_when_all_pass_and_branch_full(root):
    suite(root, "afkgame-domain", cases=passing(6))
    jacoco(root, "afkgame-domain", bm=0, bc=42)
    text, result = mod.report(parse_args())

    assert result == "OK"
    assert "UNIT_TESTS       6" in text
    assert "BRANCH_COVERAGE  100.0%  (42/42)" in text
    assert "MISSED_BRANCHES  0" in text


def test_report_fails_on_test_failure(root):
    suite(root, "afkgame-domain", cases=FAILING)
    jacoco(root, "afkgame-domain", bm=0, bc=42)
    text, result = mod.report(parse_args())
    assert result == "FAIL"
    assert f"FAIL  {CLS}#ダメージが下限でクランプされる" in text


def test_report_fails_on_missed_branch(root):
    suite(root, "afkgame-domain", cases=passing(6))
    jacoco(root, "afkgame-domain", bm=2, bc=40, lines='      <line nr="88" mi="0" ci="3" mb="2" cb="0"/>')
    text, result = mod.report(parse_args())

    assert result == "COVERAGE_NG"
    assert "com/afkgame/domain/service/BattleService.java:88" in text
    assert "== 未達分岐 (1) ==" in text


def test_report_flags_integration_failure(root):
    suite(root, "afkgame-web", "com.afkgame.web.AuthIntegrationTest",
          cases=passing(1) + "\n" + ERRORING, kind="failsafe-reports")
    text, result = mod.report(parse_args())
    assert result == "FAIL"
    assert "IT_TESTS         2" in text
    assert "IT_FAILED        1" in text


def test_report_marks_missing_reports(root):
    text, result = mod.report(parse_args())
    assert result == "NO_REPORT"
    assert "BRANCH_COVERAGE  未計測" in text


def test_report_is_ok_without_coverage_when_tests_pass(root):
    """`mvn test` だけ回した状態。カバレッジ未計測でもテストの成否は返す。"""
    suite(root, "afkgame-domain", cases=passing(6))
    text, result = mod.report(parse_args())
    assert result == "OK"
    assert "未計測" in text


def test_report_sections_can_be_narrowed(root):
    suite(root, "afkgame-domain", cases=passing(6))
    jacoco(root, "afkgame-domain", bm=0, bc=42)
    text, _ = mod.report(parse_args("--coverage"))
    assert "== C1 分岐カバレッジ（JaCoCo） ==" in text
    assert "== 単体テスト（surefire）" not in text
    assert "== 判定 ==" in text  # 判定は常に出す


def test_report_reads_log_for_build_section(root):
    (root / "backend" / "target").mkdir(parents=True)
    (root / "backend" / "target" / "mvn.log").write_bytes(LOG.encode("cp932"))
    text, result = mod.report(parse_args("--build", "--log", "backend/target/mvn.log"))
    assert "BUILD  FAILURE" in text
    assert "Foo.java:88,17 シンボルを見つけられません" in text
    assert result == "NO_REPORT"


def test_report_flags_build_failure_over_stale_passing_reports(root):
    """コンパイルで落ちた回はレポートが更新されない。前回の成功分で OK と答えない。"""
    suite(root, "afkgame-domain", cases=passing(6))
    jacoco(root, "afkgame-domain", bm=0, bc=42)
    (root / "backend" / "target").mkdir(parents=True, exist_ok=True)
    (root / "backend" / "target" / "mvn.log").write_bytes(LOG.encode("cp932"))

    # 節を絞ってビルド節を出さない場合もログは解析し、判定へ反映する
    text, result = mod.report(parse_args("--coverage", "--log", "backend/target/mvn.log"))
    assert result == "BUILD_FAILURE"
    assert "== ビルド ==" not in text


def test_report_ignores_log_unless_requested(root):
    """`--run` も `--log` も無いときは古いログを読まない（誤って BUILD_FAILURE にしない）。"""
    suite(root, "afkgame-domain", cases=passing(6))
    (root / "backend" / "target").mkdir(parents=True, exist_ok=True)
    (root / "backend" / "target" / "mvn.log").write_bytes(LOG.encode("cp932"))
    assert mod.report(parse_args())[1] == "OK"


def test_display_path_falls_back_to_absolute(root):
    assert mod.display_path(root / "backend" / "target" / "mvn.log") == "backend/target/mvn.log"
    assert mod.display_path(root.parent / "other.log").endswith("other.log")


def test_report_notes_missing_log(root):
    text, _ = mod.report(parse_args("--build", "--log", "backend/target/mvn.log"))
    assert "（ログがない:" in text


def test_max_failures_truncates(root):
    suite(root, "afkgame-domain", cases="\n".join(FAILING for _ in range(3)))
    text, _ = mod.report(parse_args("--failures", "--max-failures", "1"))
    assert "== 失敗の詳細 (3) ==" in text
    assert "…ほか 2 件" in text


def test_main_returns_exit_code(root, capsys):
    suite(root, "afkgame-domain", cases=FAILING)
    assert mod.main([]) == 1
    assert "RESULT           FAIL" in capsys.readouterr().out


def test_main_returns_zero_when_ok(root, capsys):
    suite(root, "afkgame-domain", cases=passing(1))
    jacoco(root, "afkgame-domain", bm=0, bc=1)
    assert mod.main([]) == 0
    assert "RESULT           OK" in capsys.readouterr().out
