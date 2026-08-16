"""check_java_conventions.py の回帰テスト。

実行: `python -m pytest scripts/tests -q`（リポジトリルートから）

`ROOT` を `tmp_path` へ差し替え、実リポジトリの内容に依存させない。各検査は
「違反のない実装を通す（緑パス）」と「1項目だけ壊すと検出する（変異）」を対で置く。
誤検知の回避（コンストラクタへの `@Autowired` 明示・`SecureRandom`・テスト側の
`Instant.now()` など）は、実コードで実際に踏んだものを個別に固定している。
"""

import pytest

import check_java_conventions as mod


@pytest.fixture
def root(tmp_path, monkeypatch):
    """`ROOT` を差し替えた空リポジトリを返す。"""
    monkeypatch.setattr(mod, "ROOT", tmp_path)
    return tmp_path


def write(root, rel: str, text: str) -> None:
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def java(root, name: str, body: str, area: str = "main") -> None:
    write(root, f"backend/afkgame-domain/src/{area}/java/com/afkgame/{name}.java", body)


def xml(root, rel: str, text: str) -> None:
    write(root, f"backend/afkgame-domain/src/main/resources/{rel}", text)


MAPPER = """<?xml version="1.0" encoding="UTF-8"?>
<mapper namespace="com.afkgame.domain.repository.UserRepository">
  <select id="findById">
{sql}
  </select>
</mapper>
"""


# ── 収集 ──────────────────────────────────────────────────────

def test_java_files_collects_main_and_test_sources(root):
    java(root, "A", "class A {}")
    java(root, "ATest", "class ATest {}", area="test")
    write(root, "backend/afkgame-domain/src/main/resources/x.yml", "a: 1")
    assert [p.name for p in mod.java_files()] == ["A.java", "ATest.java"]


def test_mapper_files_excludes_xml_without_namespace(root):
    xml(root, "com/afkgame/domain/repository/UserRepository.xml", MAPPER.format(sql="SELECT 1"))
    xml(root, "logback.xml", '<configuration><logger name="afkgame" level="${LOG_LEVEL:-INFO}" /></configuration>')
    assert [p.name for p in mod.mapper_files()] == ["UserRepository.xml"]


def test_is_main_distinguishes_test_sources(root):
    java(root, "A", "class A {}")
    java(root, "ATest", "class ATest {}", area="test")
    main, test = sorted(mod.java_files(), key=lambda p: p.name)
    assert mod.is_main(main) and not mod.is_main(test)


# ── code_lines / skip_literal ─────────────────────────────────

def test_code_lines_blanks_comments_but_keeps_strings():
    src = 'a; // System.out\n/* b\n c */ d;\nString s = "// not a comment";\n'
    assert [line.strip() for line in mod.code_lines(src)] == ["a;", "", "d;", 'String s = "// not a comment";']


def test_code_lines_keeps_line_numbers_across_block_comment():
    assert len(mod.code_lines("a;\n/*\n\n*/\nb;\n")) == 5


def test_code_lines_ignores_quote_inside_char_literal():
    # char リテラル内の `"` で文字列モードに入ると、後続の行コメントを消せなくなる
    assert mod.code_lines("char q = '\"'; // 注釈")[0].strip() == "char q = '\"';"


@pytest.mark.parametrize("src,expected", [
    ('"a\\"b" x', 6),      # エスケープされた引用符ではリテラルが終わらない
    ("'\\''  y", 4),        # char リテラルのエスケープ
    ('"unterminated', 13),  # 行内で閉じないリテラルは行末まで
])
def test_skip_literal_returns_end_position(src, expected):
    assert mod.skip_literal(src, 0) == expected


# ── 抑止注記 ──────────────────────────────────────────────────

def test_suppressed_accepts_same_line_and_previous_line():
    raw = ["// 規約例外: ライブラリ制約", "import java.util.Date;", "import java.util.Date; // 規約例外: 制約"]
    assert mod.suppressed(raw, 2) and mod.suppressed(raw, 3)


def test_suppressed_requires_a_reason():
    assert not mod.suppressed(["import java.util.Date; // 規約例外:"], 1)


def test_suppressed_ignores_unrelated_comments():
    assert not mod.suppressed(["// 例外の説明", "import java.util.Date;"], 2)


# ── 1. タブ / 2. 行長（--format） ──────────────────────────────

def test_check_format_passes_spaces_and_short_lines(root):
    java(root, "A", "class A {\n    void m() {}\n}\n")
    assert mod.check_format(mod.java_files()) == []


def test_check_format_detects_tab(root):
    java(root, "A", "class A {\n\tvoid m() {}\n}\n")
    assert "A.java:2" in mod.check_format(mod.java_files())[0]


@pytest.mark.parametrize("length,detected", [(120, False), (121, True)])
def test_check_format_line_length_boundary(root, length, detected):
    java(root, "A", "// " + "x" * (length - 3) + "\n")
    assert bool(mod.check_format(mod.java_files())) is detected


def test_check_format_respects_suppression(root):
    java(root, "A", "\t// 規約例外: 生成コードのため\n")
    assert mod.check_format(mod.java_files()) == []


# ── 3. ワイルドカード / 4. 未使用 import（--imports） ──────────

def test_check_imports_passes_used_individual_imports(root):
    java(root, "A", "import java.time.Instant;\n\nclass A {\n    Instant t;\n}\n")
    assert mod.check_imports(mod.java_files()) == []


def test_check_imports_detects_wildcard(root):
    java(root, "A", "import java.util.*;\n\nclass A {}\n")
    assert "ワイルドカード" in mod.check_imports(mod.java_files())[0]


def test_check_imports_detects_unused(root):
    java(root, "A", "import java.time.Instant;\n\nclass A {}\n")
    assert "未使用 import `java.time.Instant`" in mod.check_imports(mod.java_files())[0]


def test_check_imports_counts_javadoc_reference_as_used(root):
    java(root, "A", "import java.time.Instant;\n\n/** {@link Instant} を返す。 */\nclass A {}\n")
    assert mod.check_imports(mod.java_files()) == []


def test_check_imports_handles_static_import_by_member_name(root):
    java(root, "ATest", "import static org.assertj.core.api.Assertions.assertThat;\n\nclass ATest {}\n", area="test")
    assert "assertThat" in mod.check_imports(mod.java_files())[0]


def test_check_imports_does_not_match_partial_names(root):
    java(root, "A", "import com.afkgame.Item;\n\nclass A {\n    ItemStack s;\n}\n")
    assert len(mod.check_imports(mod.java_files())) == 1


# ── 5. System.out / 6. ログ連結（--log） ──────────────────────

def test_check_log_passes_placeholder_style(root):
    java(root, "A", 'class A {\n    void m() {\n        logger.info("作成 id={}", id);\n    }\n}\n')
    assert mod.check_log(mod.java_files()) == []


@pytest.mark.parametrize("stmt", ["System.out.println(x);", "System.err.print(x);", "e.printStackTrace();"])
def test_check_log_detects_console_output(root, stmt):
    java(root, "A", "class A {\n    void m() {\n        %s\n    }\n}\n" % stmt)
    assert "System.out / printStackTrace" in mod.check_log(mod.java_files())[0]


def test_check_log_ignores_console_output_inside_comment(root):
    java(root, "A", "class A {\n    // System.out.println は使わない\n}\n")
    assert mod.check_log(mod.java_files()) == []


@pytest.mark.parametrize("call", [
    'logger.info("作成 id=" + id);',
    'logger.info(String.format("作成 id=%s", id));',
])
def test_check_log_detects_concatenation(root, call):
    java(root, "A", "class A {\n    void m() {\n        %s\n    }\n}\n" % call)
    assert "文字列連結" in mod.check_log(mod.java_files())[0]


def test_check_log_handles_parenthesis_inside_message(root):
    java(root, "A", 'class A {\n    void m() {\n        logger.info("完了 (件数={})", n);\n        String s = "a" + "b";\n    }\n}\n')
    assert mod.check_log(mod.java_files()) == []


def test_check_log_detects_concatenation_across_lines(root):
    java(root, "A", 'class A {\n    void m() {\n        logger.warn("失敗 "\n                + reason);\n    }\n}\n')
    assert mod.check_log(mod.java_files())[0].endswith("common.md §7 #3）")


# ── 7. DI（--di） ─────────────────────────────────────────────

CTOR = """class A {
    private final B b;

    /** コンストラクタが2つあるため、DI に使う側を明示する。 */
    @Autowired
    public A(B b) {
        this.b = b;
    }
}
"""


def test_check_di_passes_constructor_injection(root):
    java(root, "A", CTOR)
    assert mod.check_di(mod.java_files()) == []


def test_check_di_detects_field_injection(root):
    java(root, "A", "class A {\n    @Autowired\n    private B b;\n}\n")
    assert "フィールド @Autowired" in mod.check_di(mod.java_files())[0]


def test_check_di_detects_setter_injection(root):
    java(root, "A", "class A {\n    @Autowired\n    public void setB(B b) {\n        this.b = b;\n    }\n}\n")
    assert "setter 注入" in mod.check_di(mod.java_files())[0]


def test_check_di_skips_annotations_between_marker_and_declaration(root):
    java(root, "A", "class A {\n    @Autowired\n    @Qualifier(\"x\")\n    private B b;\n}\n")
    assert "A.java:4" in mod.check_di(mod.java_files())[0]


def test_check_di_ignores_test_sources(root):
    java(root, "ATest", "class ATest {\n    @Autowired\n    private B b;\n}\n", area="test")
    assert mod.check_di(mod.java_files()) == []


# ── 8. SQL の ${}（--sql） ────────────────────────────────────

def test_check_sql_passes_bind_variables(root):
    xml(root, "com/afkgame/domain/repository/UserRepository.xml", MAPPER.format(sql="SELECT * FROM users WHERE id = #{id}"))
    assert mod.check_sql(mod.mapper_files()) == []


def test_check_sql_detects_substitution(root):
    xml(root, "com/afkgame/domain/repository/UserRepository.xml", MAPPER.format(sql="SELECT * FROM ${table}"))
    assert "SQL の ${} 展開" in mod.check_sql(mod.mapper_files())[0]


def test_check_sql_ignores_logback_variables(root):
    xml(root, "logback.xml", '<configuration><logger level="${LOG_LEVEL:-INFO}" /></configuration>')
    assert mod.check_sql(mod.mapper_files()) == []


# ── 9. 時刻の直取得 / 10. java.util.Date（--time） ────────────

def test_check_time_passes_injected_clock(root):
    java(root, "A", "class A {\n    Instant now() {\n        return Instant.now(clock);\n    }\n}\n")
    assert mod.check_time(mod.java_files()) == []


@pytest.mark.parametrize("expr", ["Instant.now()", "LocalDateTime.now()", "System.currentTimeMillis()"])
def test_check_time_detects_direct_clock_access(root, expr):
    java(root, "A", "class A {\n    void m() {\n        var t = %s;\n    }\n}\n" % expr)
    assert "現在時刻の直取得" in mod.check_time(mod.java_files())[0]


def test_check_time_allows_direct_clock_access_in_tests(root):
    java(root, "ATest", "class ATest {\n    void m() {\n        var t = Instant.now();\n    }\n}\n", area="test")
    assert mod.check_time(mod.java_files()) == []


@pytest.mark.parametrize("area", ["main", "test"])
def test_check_time_detects_util_date_in_both_areas(root, area):
    java(root, "A" if area == "main" else "ATest", "import java.util.Date;\n\nclass A {}\n", area=area)
    assert "java.util.Date / Calendar" in mod.check_time(mod.java_files())[0]


def test_check_time_respects_suppression_for_library_constraint(root):
    java(root, "A", "import java.util.Date; // 規約例外: JJWT が Date を要求する\n\nclass A {}\n")
    assert mod.check_time(mod.java_files()) == []


# ── 11. 静的な共有乱数（--random） ────────────────────────────

def test_check_random_passes_factory_instance(root):
    java(root, "RandomFactory", "class RandomFactory {\n    Random create() {\n        return new Random();\n    }\n}\n")
    assert mod.check_random(mod.java_files()) == []


def test_check_random_detects_static_field(root):
    java(root, "A", "class A {\n    private static final Random RANDOM = new Random();\n}\n")
    assert "静的な共有乱数" in mod.check_random(mod.java_files())[0]


def test_check_random_allows_secure_random_for_tokens(root):
    java(root, "A", "class A {\n    private static final SecureRandom RANDOM = new SecureRandom();\n}\n")
    assert mod.check_random(mod.java_files()) == []


@pytest.mark.parametrize("expr", ["Math.random()", "ThreadLocalRandom.current().nextInt()"])
def test_check_random_detects_shared_generators(root, expr):
    java(root, "A", "class A {\n    void m() {\n        var v = %s;\n    }\n}\n" % expr)
    assert mod.check_random(mod.java_files())


def test_check_random_ignores_test_sources(root):
    java(root, "ATest", "class ATest {\n    private static final Random R = new Random(42L);\n}\n", area="test")
    assert mod.check_random(mod.java_files()) == []


# ── 12. 機密名の突合（--mask） ────────────────────────────────

def service(root, name: str, body: str, area: str = "main") -> None:
    write(root, f"backend/afkgame-domain/src/{area}/java/com/afkgame/domain/service/{name}.java", body)


def repository(root, name: str, body: str) -> None:
    write(root, f"backend/afkgame-domain/src/main/java/com/afkgame/domain/repository/{name}.java", body)


def test_check_mask_passes_names_in_the_fixed_table(root):
    service(root, "AuthServiceImpl", "class AuthServiceImpl {\n    public void refresh(String refreshToken) {}\n}\n")
    assert mod.check_mask(mod.java_files()) == []


def test_check_mask_detects_name_outside_the_fixed_table(root):
    service(root, "AuthServiceImpl", "class AuthServiceImpl {\n    public void verifyEmail(String rawToken) {}\n}\n")
    assert "rawToken" in mod.check_mask(mod.java_files())[0]


def test_check_mask_covers_interface_methods_without_the_public_modifier(root):
    # インタフェースのメソッドは暗黙 public。JDK 動的プロキシが渡すのはこちらの名前
    repository(root, "RefreshTokenRepository",
               "interface RefreshTokenRepository {\n    RefreshToken findByTokenHash(String tokenHash);\n}\n")
    assert "tokenHash" in mod.check_mask(mod.java_files())[0]


def test_check_mask_ignores_non_string_parameters(root):
    # Entity・Resource は規約1 #2（toString から機密フィールドを外す）の担当で、名前一致の対象外
    repository(root, "EmailVerificationTokenRepository",
               "interface EmailVerificationTokenRepository {\n"
               "    void save(EmailVerificationToken emailVerificationToken);\n}\n")
    assert mod.check_mask(mod.java_files()) == []


def test_check_mask_detects_an_authorization_code_by_the_word_pair(root):
    # `auth` + `code` の並びは認可コード（アクセストークンと交換できる）。語単独では拾えない
    service(root, "AuthServiceImpl", "class AuthServiceImpl {\n    public void link(String authCode) {}\n}\n")
    assert "authCode" in mod.check_mask(mod.java_files())[0]


def test_check_mask_passes_an_authorization_code_name_in_the_fixed_table(root):
    service(root, "AuthServiceImpl",
            "class AuthServiceImpl {\n    public void link(String googleAuthCode) {}\n}\n")
    assert mod.check_mask(mod.java_files()) == []


def test_check_mask_does_not_treat_a_trailing_code_word_as_secret(root):
    # `code` 単独を機密語にすると `errorCode`・`statusCode` を巻き込む
    service(root, "BattleServiceImpl", "class BattleServiceImpl {\n    public void fail(String errorCode) {}\n}\n")
    assert mod.check_mask(mod.java_files()) == []


def test_check_mask_does_not_match_words_that_merely_contain_a_secret_word(root):
    # `drawCount` の `raw` を拾わないよう、camelCase を語へ割ってから突き合わせる
    service(root, "GachaServiceImpl", "class GachaServiceImpl {\n    public void draw(String drawCount) {}\n}\n")
    assert mod.check_mask(mod.java_files()) == []


def test_check_mask_ignores_classes_outside_the_pointcut(root):
    java(root, "AuthController", "class AuthController {\n    public void verify(String rawToken) {}\n}\n")
    assert mod.check_mask(mod.java_files()) == []


def test_check_mask_covers_subpackages_of_the_pointcut(root):
    # ポイントカットは `service..*` であり、領域サブパッケージ（`service.auth.*`）にも一致する
    write(root, "backend/afkgame-domain/src/main/java/com/afkgame/domain/service/auth/X.java",
          "class X {\n    public void m(String rawToken) {}\n}\n")
    assert "rawToken" in mod.check_mask(mod.java_files())[0]


def test_check_mask_ignores_non_public_methods(root):
    service(root, "AuthServiceImpl",
            "class AuthServiceImpl {\n    private String hashToken(String rawToken) {\n        return rawToken;\n    }\n}\n")
    assert mod.check_mask(mod.java_files()) == []


def test_check_mask_ignores_static_methods(root):
    # static はプロキシが張れないため境界ログに出ない
    service(root, "AuthServiceImpl", "class AuthServiceImpl {\n    public static void m(String rawToken) {}\n}\n")
    assert mod.check_mask(mod.java_files()) == []


def test_check_mask_ignores_test_sources(root):
    service(root, "AuthServiceImplTest",
            "class AuthServiceImplTest {\n    public void m(String rawToken) {}\n}\n", area="test")
    assert mod.check_mask(mod.java_files()) == []


def test_check_mask_respects_suppression(root):
    repository(root, "RefreshTokenRepository",
               "interface RefreshTokenRepository {\n"
               "    RefreshToken findByTokenHash(String tokenHash); // 規約例外: ハッシュ値であり生値ではない\n}\n")
    assert mod.check_mask(mod.java_files()) == []


# ── 13. 未参照の設定・enum 値（--unused） ─────────────────────

def config(root, name: str, body: str) -> None:
    write(root, f"backend/afkgame-env/src/main/java/com/afkgame/env/config/{name}.java", body)


def logging_enum(root, name: str, body: str) -> None:
    write(root, f"backend/afkgame-env/src/main/java/com/afkgame/env/logging/{name}.java", body)


SETTINGS = """package com.afkgame.env.config;

public record AuthSettings(
        String secret,
        Duration guestExpire) {
}
"""

LOG_KEY = """package com.afkgame.env.logging;

public enum LogKey {

    /** リクエストID。 */
    REQUEST_ID("request_id"),

    /** トークン。 */
    TOKEN("token");

    private final String key;
}
"""


def test_check_unused_passes_accessor_with_a_reader(root):
    config(root, "AuthSettings", SETTINGS)
    java(root, "JwtService",
         "class JwtService {\n    void m() {\n        var s = settings.secret();\n"
         "        var g = settings.guestExpire();\n    }\n}\n")
    assert mod.check_unused(mod.java_files()) == []


def test_check_unused_detects_accessor_without_a_reader(root):
    config(root, "AuthSettings", SETTINGS)
    java(root, "JwtService", "class JwtService {\n    void m() {\n        var s = settings.secret();\n    }\n}\n")
    found = mod.check_unused(mod.java_files())
    assert len(found) == 1 and "AuthSettings#guestExpire()" in found[0]


def test_check_unused_does_not_count_the_config_package_as_a_reader(root):
    # 生成しているだけの `AfkgameSettingsConfig` は読み手に数えない
    config(root, "AuthSettings", SETTINGS)
    write(root, "backend/afkgame-env/src/main/java/com/afkgame/env/config/app/AfkgameSettingsConfig.java",
          "class AfkgameSettingsConfig {\n    void m(AuthSettings s) {\n        var a = s.secret();\n"
          "        var b = s.guestExpire();\n    }\n}\n")
    assert len(mod.check_unused(mod.java_files())) == 2


def test_check_unused_counts_a_method_reference_as_a_reader(root):
    config(root, "AuthSettings", SETTINGS)
    java(root, "JwtService",
         "class JwtService {\n    void m() {\n        f(AuthSettings::secret);\n"
         "        f(AuthSettings::guestExpire);\n    }\n}\n")
    assert mod.check_unused(mod.java_files()) == []


def test_check_unused_passes_enum_constant_with_a_reader(root):
    logging_enum(root, "LogKey", LOG_KEY)
    java(root, "RequestLogFilter",
         "class RequestLogFilter {\n    void m() {\n        entry.with(LogKey.REQUEST_ID, id);\n"
         "        entry.with(LogKey.TOKEN, t);\n    }\n}\n")
    assert mod.check_unused(mod.java_files()) == []


def test_check_unused_detects_enum_constant_without_a_reader(root):
    logging_enum(root, "LogKey", LOG_KEY)
    java(root, "RequestLogFilter",
         "class RequestLogFilter {\n    void m() {\n        entry.with(LogKey.REQUEST_ID, id);\n    }\n}\n")
    found = mod.check_unused(mod.java_files())
    assert len(found) == 1 and "LogKey.TOKEN" in found[0]


def test_check_unused_does_not_count_members_after_the_constant_list(root):
    logging_enum(root, "LogKey", LOG_KEY)
    java(root, "RequestLogFilter",
         "class RequestLogFilter {\n    void m() {\n        entry.with(LogKey.REQUEST_ID, id);\n"
         "        entry.with(LogKey.TOKEN, t);\n    }\n}\n")
    # `key` フィールドは定数リストの外なので対象に入らない（対象が2件だけであることを保証する）
    assert mod.check_unused(mod.java_files()) == []


def test_check_unused_does_not_count_test_sources_as_readers(root):
    logging_enum(root, "LogKey", LOG_KEY)
    java(root, "RequestLogFilter",
         "class RequestLogFilter {\n    void m() {\n        entry.with(LogKey.REQUEST_ID, id);\n    }\n}\n")
    java(root, "LogKeyTest", "class LogKeyTest {\n    void m() {\n        assertThat(LogKey.TOKEN);\n    }\n}\n",
         area="test")
    assert "LogKey.TOKEN" in mod.check_unused(mod.java_files())[0]


def test_check_unused_reports_at_warn_level(root):
    config(root, "AuthSettings", SETTINGS)
    assert all(m.startswith("WARN ") for m in mod.check_unused(mod.java_files()))


# ── 13. 未参照の pom 依存（--unused） ────────────────────────

def pom(root, text: str, module: str = "afkgame-domain") -> None:
    write(root, f"backend/{module}/pom.xml", text)


POM = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <artifactId>afkgame-domain</artifactId>
    <dependencies>
{deps}
    </dependencies>
</project>
"""


def dep(artifact: str, extra: str = "") -> str:
    return ("        <dependency>\n"
            "            <groupId>example</groupId>\n"
            f"            <artifactId>{artifact}</artifactId>\n"
            f"{extra}"
            "        </dependency>")


def test_check_pom_passes_a_registered_dependency_with_an_import(root):
    pom(root, POM.format(deps=dep("jjwt-api")))
    java(root, "JwtServiceImpl", "import io.jsonwebtoken.Jwts;\n\nclass JwtServiceImpl {}\n")
    assert mod.check_pom_dependencies(mod.java_files()) == []


def test_check_pom_detects_a_dependency_without_an_import(root):
    # ISSUE-905（Phase 2 でしか使わない依存の先行投入）と同じ形
    pom(root, POM.format(deps=dep("jjwt-api")))
    java(root, "A", "class A {}\n")
    found = mod.check_pom_dependencies(mod.java_files())
    assert len(found) == 1 and "jjwt-api" in found[0] and "参照されていない" in found[0]


def test_check_pom_detects_a_dependency_missing_from_the_table(root):
    pom(root, POM.format(deps=dep("httpclient5")))
    found = mod.check_pom_dependencies(mod.java_files())
    assert len(found) == 1 and "判定13 の表に無い" in found[0]


def test_check_pom_passes_a_dependency_registered_as_having_no_source_reference(root):
    pom(root, POM.format(deps=dep("jjwt-impl")))
    assert mod.check_pom_dependencies(mod.java_files()) == []


def test_check_pom_ignores_test_and_runtime_scopes(root):
    deps = "\n".join([dep("junit-jupiter", "            <scope>test</scope>\n"),
                      dep("httpclient5", "            <scope>runtime</scope>\n")])
    pom(root, POM.format(deps=deps))
    assert mod.check_pom_dependencies(mod.java_files()) == []


def test_check_pom_ignores_an_aggregate_pom(root):
    # 依存の束はそれ自体 import されない
    pom(root, POM.format(deps=dep("terasoluna-gfw-common-dependencies", "            <type>pom</type>\n")))
    assert mod.check_pom_dependencies(mod.java_files()) == []


def test_check_pom_ignores_dependency_management_and_plugin_dependencies(root):
    pom(root, "<project>\n    <dependencyManagement>\n        <dependencies>\n"
        + dep("httpclient5") + "\n        </dependencies>\n    </dependencyManagement>\n"
        "    <build>\n        <plugins>\n            <plugin>\n                <dependencies>\n"
        + dep("mapstruct-processor") + "\n                </dependencies>\n"
        "            </plugin>\n        </plugins>\n    </build>\n</project>\n")
    assert mod.check_pom_dependencies(mod.java_files()) == []


def test_check_pom_ignores_a_commented_out_dependency(root):
    pom(root, POM.format(deps="        <!-- 復活待ち\n" + dep("httpclient5") + "\n        -->"))
    assert mod.check_pom_dependencies(mod.java_files()) == []


def test_check_pom_reports_the_line_of_the_dependency_tag(root):
    text = POM.format(deps="        <!-- 復活待ち\n" + dep("ojdbc17") + "\n        -->\n" + dep("httpclient5"))
    pom(root, text)
    # コメントを潰しても行番号は動かない（`<dependency>` の開始行 ＝ artifactId の2行前）
    expected = next(i for i, line in enumerate(text.splitlines(), 1) if "httpclient5" in line) - 2
    found = mod.check_pom_dependencies(mod.java_files())
    assert len(found) == 1 and found[0].startswith(f"WARN backend/afkgame-domain/pom.xml:{expected}:")


def test_check_pom_does_not_count_an_import_from_another_module(root):
    pom(root, POM.format(deps=dep("jjwt-api")))
    write(root, "backend/afkgame-web/src/main/java/com/afkgame/web/A.java",
          "import io.jsonwebtoken.Jwts;\n\nclass A {}\n")
    found = mod.check_pom_dependencies(mod.java_files())
    assert len(found) == 1 and "jjwt-api" in found[0]


def test_check_pom_does_not_count_a_test_source_as_a_reader(root):
    pom(root, POM.format(deps=dep("jjwt-api")))
    java(root, "JwtServiceImplTest", "import io.jsonwebtoken.Jwts;\n\nclass JwtServiceImplTest {}\n", area="test")
    assert len(mod.check_pom_dependencies(mod.java_files())) == 1


def test_check_pom_reports_at_warn_level(root):
    pom(root, POM.format(deps=dep("httpclient5")))
    assert all(m.startswith("WARN ") for m in mod.check_pom_dependencies(mod.java_files()))


# ── 13. 捨てられている Repository の戻り値（--unused） ────────

def repository(root, name: str, body: str) -> None:
    write(root, f"backend/afkgame-domain/src/main/java/com/afkgame/domain/repository/{name}.java", body)


REPOSITORY = """package com.afkgame.domain.repository;

public interface RefreshTokenRepository {

    int updateRevokedByUserId(String userId);
}
"""


def caller(root, body: str) -> None:
    java(root, "AuthServiceImpl", "class AuthServiceImpl {\n    void m() {\n" + body + "    }\n}\n")


def test_check_discarded_results_passes_a_result_used_in_a_condition(root):
    repository(root, "RefreshTokenRepository", REPOSITORY)
    caller(root, "        if (refreshTokenRepository.updateRevokedByUserId(id) == 0) {\n"
                  "            throw detectReuse(id);\n        }\n")
    assert mod.check_discarded_results(mod.java_files()) == []


def test_check_discarded_results_detects_a_result_discarded_at_every_call_site(root):
    # ISSUE-907（件数で経路を分けないのに int を返している）と同じ形
    repository(root, "RefreshTokenRepository", REPOSITORY)
    caller(root, "        refreshTokenRepository.updateRevokedByUserId(id);\n")
    found = mod.check_discarded_results(mod.java_files())
    assert len(found) == 1 and "RefreshTokenRepository#updateRevokedByUserId" in found[0]


def test_check_discarded_results_detects_a_call_through_a_field_qualifier(root):
    repository(root, "RefreshTokenRepository", REPOSITORY)
    caller(root, "        this.refreshTokenRepository.updateRevokedByUserId(id);\n")
    assert len(mod.check_discarded_results(mod.java_files())) == 1


def test_check_discarded_results_passes_when_one_of_the_call_sites_uses_it(root):
    repository(root, "RefreshTokenRepository", REPOSITORY)
    caller(root, "        refreshTokenRepository.updateRevokedByUserId(id);\n")
    java(root, "UserServiceImpl",
         "class UserServiceImpl {\n    void m() {\n"
         "        int n = refreshTokenRepository.updateRevokedByUserId(id);\n    }\n}\n")
    assert mod.check_discarded_results(mod.java_files()) == []


def test_check_discarded_results_counts_a_return_as_a_reader(root):
    repository(root, "RefreshTokenRepository", REPOSITORY)
    caller(root, "        return refreshTokenRepository.updateRevokedByUserId(id);\n")
    assert mod.check_discarded_results(mod.java_files()) == []


def test_check_discarded_results_ignores_void_methods(root):
    repository(root, "RefreshTokenRepository", REPOSITORY.replace("int update", "void update"))
    caller(root, "        refreshTokenRepository.updateRevokedByUserId(id);\n")
    assert mod.check_discarded_results(mod.java_files()) == []


def test_check_discarded_results_ignores_query_methods(root):
    # `findOne()` は DB へ到達できるかの確認で、MyBatis が SELECT を出すために戻り値の型が要る
    repository(root, "HealthRepository",
               "public interface HealthRepository {\n\n    Integer findOne();\n}\n")
    java(root, "HealthServiceImpl", "class HealthServiceImpl {\n    void m() {\n"
         "        healthRepository.findOne();\n    }\n}\n")
    assert mod.check_discarded_results(mod.java_files()) == []


def test_check_discarded_results_ignores_a_method_without_call_sites(root):
    # 呼び出しが1つも無いのは「未参照」であって本判定の対象ではない
    repository(root, "RefreshTokenRepository", REPOSITORY)
    assert mod.check_discarded_results(mod.java_files()) == []


def test_check_discarded_results_ignores_classes_outside_the_repository_package(root):
    java(root, "Counter", "public interface Counter {\n\n    int updateAll(String id);\n}\n")
    caller(root, "        counter.updateAll(id);\n")
    assert mod.check_discarded_results(mod.java_files()) == []


def test_check_discarded_results_does_not_count_a_test_source_as_a_reader(root):
    repository(root, "RefreshTokenRepository", REPOSITORY)
    caller(root, "        refreshTokenRepository.updateRevokedByUserId(id);\n")
    java(root, "AuthServiceImplTest", "class AuthServiceImplTest {\n    void m() {\n"
         "        int n = refreshTokenRepository.updateRevokedByUserId(id);\n    }\n}\n", area="test")
    assert len(mod.check_discarded_results(mod.java_files())) == 1


def test_check_discarded_results_reports_at_warn_level(root):
    repository(root, "RefreshTokenRepository", REPOSITORY)
    caller(root, "        refreshTokenRepository.updateRevokedByUserId(id);\n")
    assert all(m.startswith("WARN ") for m in mod.check_discarded_results(mod.java_files()))


# ── 規約例外の一覧（--suppressed） ───────────────────────────

def test_list_suppressed_lists_the_path_line_and_reason(root):
    java(root, "JwtServiceImpl",
         "import java.util.Date; // 規約例外: JJWT の expiration(Date) が Date を要求する\n\nclass JwtServiceImpl {}\n")
    assert mod.list_suppressed(mod.java_files()) == [
        "backend/afkgame-domain/src/main/java/com/afkgame/JwtServiceImpl.java:1:"
        " JJWT の expiration(Date) が Date を要求する"]


def test_list_suppressed_covers_mapping_xml_and_strips_the_close_tag(root):
    xml(root, "com/afkgame/domain/repository/UserRepository.xml",
        '<mapper namespace="x">\n  <!-- 規約例外: 表名は固定で外部入力を含まない -->\n</mapper>\n')
    assert mod.list_suppressed(mod.mapper_files()) == [
        "backend/afkgame-domain/src/main/resources/com/afkgame/domain/repository/UserRepository.xml:2:"
        " 表名は固定で外部入力を含まない"]


def test_list_suppressed_excludes_test_sources(root):
    # テストは記述規約の対象外。Javadoc からこの注記に言及した行を拾わない側でもある
    java(root, "AuthServiceImplTest",
         " * {@code // 規約例外: ハッシュ}と同じ扱いにはしない\n", area="test")
    assert mod.list_suppressed(mod.java_files()) == []


def test_list_suppressed_ignores_a_note_without_a_reason(root):
    java(root, "A", "class A {} // 規約例外:\n")
    assert mod.list_suppressed(mod.java_files()) == []


# ── main ─────────────────────────────────────────────────────

def test_main_returns_zero_when_clean(root, capsys, monkeypatch):
    java(root, "A", "class A {\n    void m() {}\n}\n")
    monkeypatch.setattr(mod.sys, "argv", ["check_java_conventions.py"])
    assert mod.main() == 0
    assert "違反なし" in capsys.readouterr().out


def test_main_returns_one_and_prints_violations(root, capsys, monkeypatch):
    java(root, "A", "import java.util.*;\n\nclass A {}\n")
    monkeypatch.setattr(mod.sys, "argv", ["check_java_conventions.py"])
    assert mod.main() == 1
    assert "1 件の違反" in capsys.readouterr().out


def test_main_runs_only_the_selected_check(root, capsys, monkeypatch):
    java(root, "A", "import java.util.*;\n\nclass A {}\n")
    monkeypatch.setattr(mod.sys, "argv", ["check_java_conventions.py", "--di"])
    assert mod.main() == 0
    out = capsys.readouterr().out
    assert "[DI]" in out and "[import]" not in out


def test_main_does_not_fail_on_warnings_alone(root, capsys, monkeypatch):
    # 判定13 は先行投入を許容する運用のため、WARN は exit code に算入しない
    config(root, "AuthSettings", SETTINGS)
    monkeypatch.setattr(mod.sys, "argv", ["check_java_conventions.py", "--unused"])
    assert mod.main() == 0
    out = capsys.readouterr().out
    assert "WARN" in out and "違反なし" in out and "WARN 2 件" in out


def test_main_counts_all_three_targets_of_the_unused_check(root, capsys, monkeypatch):
    # 設定値・pom の依存・Repository の戻り値をまとめて1つの WARN 件数で見る
    config(root, "AuthSettings", SETTINGS)
    pom(root, POM.format(deps=dep("httpclient5")))
    repository(root, "RefreshTokenRepository", REPOSITORY)
    caller(root, "        refreshTokenRepository.updateRevokedByUserId(id);\n")
    monkeypatch.setattr(mod.sys, "argv", ["check_java_conventions.py", "--unused"])
    assert mod.main() == 0
    assert "WARN 4 件" in capsys.readouterr().out


def test_main_lists_suppressions_without_running_the_checks(root, capsys, monkeypatch):
    java(root, "A", "import java.util.*; // 規約例外: 事情がある\n\nclass A {}\n")
    monkeypatch.setattr(mod.sys, "argv", ["check_java_conventions.py", "--suppressed"])
    assert mod.main() == 0
    out = capsys.readouterr().out
    # ワイルドカード import の違反があっても判定は走らない（一覧だけを出す）
    assert "SUPPRESSED" in out and "規約例外の抑止 1 件" in out and "[import]" not in out
