#!/usr/bin/env python3
"""Java コーディング規約の機械検証（`backend/` の *.java とマッピング XML）

backend-review で毎回使い捨てスクリプトを書いていた機械判定可能な規約を、
常設チェックとして固定する（`docs/backlog/carryover_notes.md` §3）。
規約の正は `docs/process/coding_standards_backend/` の分冊。

検証項目:
    --format   1. タブ       インデントは半角スペース4つ（common.md §5 #1）
               2. 行長       1行は120字まで（common.md §5 #1）
    --imports  3. ワイルドカード import 禁止（common.md §5 #2・§9）
               4. 未使用 import を残さない（common.md §5 #2）
    --log      5. System.out / printStackTrace 禁止（common.md §7 #6・§9）
               6. ログはプレースホルダ `{}` で組む（common.md §7 #3）
    --di       7. フィールド `@Autowired`・setter 注入の禁止（common.md §4 #1・§9）
    --sql      8. マッピング XML の `${}` 禁止（domain.md §3 #1・§9）
    --time     9. 現在時刻の直取得禁止（common.md §4 #2）
              10. java.util.Date / Calendar 禁止（common.md §5 #8・§9）
    --random  11. 静的な共有乱数の禁止（common.md §4 #2・§9）
    --mask    12. 境界ログでマスクされない機密名（logging/application.md §3.1 規約1）
    --unused  13. 用意したが読み手のいない部品（WARN のみ）
                  設定値・enum 値・pom の依存・Repository の戻り値

走査対象:
    7・9・11 は src/main のみ。テストは Spring から Bean を受け取り（test.md §1）、
    時刻・乱数を固定する側（test.md §3 #1）であり、同じ判定を当てられない。
    8 は `namespace` を持つ MyBatis マッピング XML のみ（logback.xml の `${}` は
    Logback の変数置換で正当）。
    12 は src/main のうち afkgame.properties のポイントカット2本
    （`com.afkgame.domain.{service,repository}..*` ＝サブパッケージを含む配下の
    public メソッド）に一致する範囲。
    13 は src/main 全体を参照コーパスとし、生成しているだけの config パッケージを除く。
    pom は各モジュールの `<dependencies>` 直下（compile / provided）のみを見て、
    同じモジュールの src/main の import と突き合わせる。Repository の戻り値は
    `com.afkgame.domain.repository` の非 void メソッドを src/main の呼び出しと照合する。

WARN の扱い:
    13 は「使う工程より先に投入した部品」を許容する運用（レビュー 2026-08-10 還元2）のため
    WARN で出し、exit code に算入しない。件数の増減だけを見る。

規約例外の抑止:
    ライブラリの API 制約などで避けられない箇所は、その行の行末か直前行へ
    `// 規約例外: <理由>` を書くと検出しない（理由が空なら抑止しない）。
    XML では `<!-- 規約例外: <理由> -->`。
    抑止した箇所は機械判定の外側に出るため、`--suppressed` で一覧できる
    （レビュー時に「今も妥当か」を再点検するためのもので、判定ではない）。

使い方:
    python scripts/check_java_conventions.py             # 全検証（ERROR があれば exit 1）
    python scripts/check_java_conventions.py --imports   # import 検査のみ（他フラグも同様）
    python scripts/check_java_conventions.py --suppressed # 規約例外の一覧（他フラグと併用しない）
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA_GLOB = "backend/*/src/*/java/**/*.java"
XML_GLOB = "backend/*/src/*/resources/**/*.xml"
POM_GLOB = "backend/*/pom.xml"

MAX_LINE = 120

# 抑止注記。理由（コロンの後ろの非空白）が無い注記では抑止しない
SUPPRESS = re.compile(r"(?://|<!--)\s*規約例外\s*[:：]\s*\S")
# 一覧出力（--suppressed）用。理由だけを取り出す。XML の `-->` は理由に含めない
SUPPRESS_REASON = re.compile(r"(?://|<!--)\s*規約例外\s*[:：]\s*(?P<reason>\S.*?)\s*(?:-->\s*)?$")

IMPORT = re.compile(r"^import\s+(?:static\s+)?([\w.*]+)\s*;")
LOG_CALL = re.compile(r"\b(?:logger|log|LOGGER)\.(?:trace|debug|info|warn|error)\s*\(")
CONCAT = re.compile(r'"\s*\+|\+\s*"|String\.format\s*\(')
ANNOTATION = re.compile(r"^\s*@(?:Autowired|Inject)\b")
SETTER = re.compile(r"\bvoid\s+set[A-Z]\w*\s*\(")
SYSOUT = re.compile(r"\bSystem\.(?:out|err)\b|\.printStackTrace\s*\(")
# 引数なしの `now()` だけが直取得。`Instant.now(clock)` は注入した Clock を使う正しい形
NOW = re.compile(
    r"\b(?:Instant|LocalDate|LocalDateTime|LocalTime|OffsetDateTime|ZonedDateTime)\.now\s*\(\s*\)"
    r"|\bSystem\.currentTimeMillis\s*\(\s*\)"
)
UTIL_DATE = re.compile(r"\bjava\.util\.(?:Date|Calendar)\b|\bnew\s+Date\s*\(|\bCalendar\.getInstance\s*\(")
# `SecureRandom` は左側の単語境界が立たないため、この式では拾わない（規約の明示例外）
STATIC_RANDOM = re.compile(r"\bstatic\b[^;()=]*\bRandom\b")
SHARED_RANDOM = re.compile(r"\bMath\.random\s*\(|\bThreadLocalRandom\b")

# 境界ログの対象範囲。afkgame.properties の
# `afkgame.logging.layer.pointcut.{service,repository}` と同じく「パッケージ配下の public」。
# 式は `..` なので領域サブパッケージ（`service.auth` 等）も対象に含む
POINTCUT_PKGS = ("com/afkgame/domain/service/", "com/afkgame/domain/repository/")

# logging/application.md §3.1 規約1 の固定表（LayerLoggingInterceptor#MASKED_PARAM_NAMES の写し）。
# `email` は LogKey.EMAIL と同じマスクが掛かるため、同じく伏せられる側として数える
MASKED_PARAMS = frozenset({
    "password", "rawPassword", "newPassword", "passwordHash", "token", "accessToken",
    "refreshToken", "googleAuthCode", "secret", "credential", "email",
})

# 機密を示す語。camelCase を語へ割ってから突き合わせる（`drawCount` の `raw` を拾わないため）
SECRET_WORDS = frozenset({"raw", "token", "password", "secret", "credential"})

# 単語では機密と判定できないが、並びで機密になる組（`authCode` は認可コード）。
# `code` 単独を SECRET_WORDS へ入れると `errorCode`・`statusCode` を巻き込むため並びで見る
SECRET_WORD_PAIRS = frozenset({("auth", "code")})

CAMEL_WORD = re.compile(r"[A-Z]+(?![a-z])|[A-Z][a-z]*|[a-z]+|\d+")

# 型の位置に立ったら宣言ではない語（`return hashToken(raw);` を宣言と読み違えないため）
NOT_A_TYPE = frozenset({
    "return", "new", "throw", "throws", "else", "case", "assert", "yield",
    "if", "while", "for", "switch", "catch", "do", "super", "this",
})

MODIFIER = r"public|protected|private|static|final|default|abstract|synchronized|native|strictfp"
METHOD = re.compile(
    rf"(?P<mods>(?:(?:{MODIFIER})\s+)*)"
    r"(?P<type>[\w.$]+(?:\s*<[^;{}]*?>)?(?:\s*\[\s*\])*)\s+"
    r"(?P<name>[a-z_]\w*)\s*\((?P<params>[^;{}]*?)\)\s*(?:\{|;|throws\b)"
)
PARAM_ANNOTATION = re.compile(r"@\w+(?:\s*\([^)]*\))?\s*")
TYPE_HEAD = re.compile(r"[\w.$]+")

# 判定13 の対象。config は record の全アクセサ、logging は enum の全定数
CONFIG_PKG = "com/afkgame/env/config/"
TRACKED_ENUMS = ("LogKey", "LogReason", "LoggerName")
RECORD = re.compile(r"\bpublic\s+record\s+(?P<name>\w+)\s*\((?P<comps>[^)]*)\)")

# 判定13（pom）。compile / provided の依存は「src/main のどこから参照されるか」を
# ここへ登録する（artifactId → import のプレフィクス）。表が「なぜこの依存が要るか」の
# 台帳を兼ねるので、未登録の依存も WARN にして先行投入を追加時点で見えるようにする
DEPENDENCY_IMPORTS = {
    "afkgame-domain": "com.afkgame.domain",
    "afkgame-env": "com.afkgame.env",
    # Jackson 3 系。パッケージは `com.fasterxml` ではなく `tools.jackson`
    "jackson-dataformat-yaml": "tools.jackson.dataformat.yaml",
    "jjwt-api": "io.jsonwebtoken",
    "angus-mail": "jakarta.mail",
    "jakarta.servlet-api": "jakarta.servlet",
}

# ソース参照を持たないのが正しい依存と、その理由。実行時にだけ読まれるもの、
# リソースだけを同梱するもの、注釈処理だけのものが該当する
DEPENDENCY_NO_SOURCE = {
    "jjwt-impl": "JJWT の実装。実行時に ServiceLoader から読まれる",
    "jjwt-jackson": "JJWT の JSON シリアライザ。実行時に読まれる",
    "afkgame-initdb": "Flyway が読むマイグレーション（SQL リソース）だけを同梱する",
    "flyway-core": "マイグレーションの実行本体。SQL のみで Java ソースから呼ばない",
    "flyway-database-postgresql": "Flyway の PostgreSQL 方言。実行時に読まれる",
    "lombok": "注釈処理のみ。生成されたコードに import は現れない",
    "postgresql": "JDBC ドライバ。実行時に読まれる",
}

# ソース参照を持たない依存の型・スコープ。`pom` は依存の束（集約 pom）でそれ自体は
# import されず、runtime / test は定義上 src/main のコンパイルに現れない
POM_SCOPES = ("compile", "provided")
XML_COMMENT = re.compile(r"<!--.*?-->", re.S)
POM_SECTION = re.compile(r"<(dependencyManagement|build)\b.*?</\1>", re.S)
POM_DEPENDENCY = re.compile(r"<dependency\b[^>]*>(?P<body>.*?)</dependency>", re.S)
IMPORT_LINE = re.compile(r"^import\s+(?:static\s+)?")

# 判定13（Repository の戻り値）。件数で経路を分けないなら void が正（レビュー ISSUE-907）。
# 更新系だけを見る。取得系の戻り値を捨てる呼び出しは別の意味を持つ（`findOne()` は
# DB へ到達できるかの確認で、MyBatis が SELECT を発行するために戻り値の型が要る）
REPOSITORY_PKG = "com/afkgame/domain/repository/"
MUTATING_PREFIXES = ("insert", "update", "delete", "save")
VOID_TYPES = ("void", "Void")
# 文の切れ目。呼び出しの手前がこれで終わっていれば戻り値を受け取っていない
STATEMENT_BREAK = ";{}"


def java_files() -> list[Path]:
    return sorted(ROOT.glob(JAVA_GLOB))


def mapper_files() -> list[Path]:
    """MyBatis マッピング XML（`namespace` を持つもの）だけを返す。"""
    result = []
    for path in sorted(ROOT.glob(XML_GLOB)):
        head = path.read_text(encoding="utf-8")[:500]
        if "<mapper" in head and "namespace" in head:
            result.append(path)
    return result


def is_main(path: Path) -> bool:
    return "/src/main/" in path.as_posix()


def code_lines(text: str) -> list[str]:
    """行コメント・ブロックコメントを空白へ潰した行（行番号は保つ）。

    文字列リテラルは残す（ログ引数の連結判定に要るため）。コメント中の
    `System.out` や `{@link Instant}` を違反と誤認しないための前処理。
    """
    out: list[str] = []
    in_block = False
    for line in text.splitlines():
        buf: list[str] = []
        i = 0
        while i < len(line):
            two = line[i:i + 2]
            if in_block:
                if two == "*/":
                    in_block = False
                    buf.append("  ")
                    i += 2
                    continue
                buf.append(" ")
                i += 1
                continue
            if two == "//":
                buf.append(" " * (len(line) - i))
                break
            if two == "/*":
                in_block = True
                buf.append("  ")
                i += 2
                continue
            if line[i] in "\"'":
                j = skip_literal(line, i)
                buf.append(line[i:j])
                i = j
                continue
            buf.append(line[i])
            i += 1
        out.append("".join(buf))
    return out


def skip_literal(line: str, start: int) -> int:
    """`start` の引用符から始まるリテラルの終端（次の位置）を返す。"""
    quote = line[start]
    i = start + 1
    while i < len(line):
        if line[i] == "\\":
            i += 2
            continue
        if line[i] == quote:
            return i + 1
        i += 1
    return len(line)  # 行内で閉じないリテラルは行末まで（Java では通常起きない）


def read(path: Path) -> tuple[str, list[str], list[str]]:
    """(リポジトリ相対パス, 生の行, コメントを潰した行) を返す。"""
    text = path.read_text(encoding="utf-8")
    return path.relative_to(ROOT).as_posix(), text.splitlines(), code_lines(text)


def suppressed(raw: list[str], no: int) -> bool:
    """その行の行末か直前行に `規約例外: <理由>` があるか（`no` は1始まり）。"""
    return any(SUPPRESS.search(raw[i]) for i in (no - 1, no - 2) if 0 <= i < len(raw))


def check_format(files: list[Path]) -> list[str]:
    """タブと行長（common.md §5 #1）。"""
    errors = []
    for path in files:
        rel, raw, _ = read(path)
        for no, line in enumerate(raw, 1):
            if suppressed(raw, no):
                continue
            if "\t" in line:
                errors.append(f"ERROR {rel}:{no}: タブ文字（インデントは半角スペース4つ。common.md §5 #1）")
            if len(line) > MAX_LINE:
                errors.append(f"ERROR {rel}:{no}: 1行が {len(line)} 字（上限 {MAX_LINE} 字。common.md §5 #1）")
    return errors


def check_imports(files: list[Path]) -> list[str]:
    """ワイルドカード import と未使用 import（common.md §5 #2・§9）。"""
    errors = []
    for path in files:
        rel, raw, _ = read(path)
        # 使用判定は import 行以外の全文（Javadoc の {@link Xxx} も使用とみなす）
        body = "\n".join(line for line in raw if not line.startswith("import "))
        for no, line in enumerate(raw, 1):
            m = IMPORT.match(line)
            if not m or suppressed(raw, no):
                continue
            fqcn = m.group(1)
            name = fqcn.rsplit(".", 1)[-1]
            if name == "*":
                errors.append(f"ERROR {rel}:{no}: ワイルドカード import（個別に import する。common.md §5 #2・§9）")
            elif not re.search(rf"\b{re.escape(name)}\b", body):
                errors.append(f"ERROR {rel}:{no}: 未使用 import `{fqcn}`（common.md §5 #2）")
    return errors


def log_calls(code: str):
    """(呼び出し開始の行番号, 引数の文字列) を返す。文字列リテラル内の括弧は数えない。"""
    for m in LOG_CALL.finditer(code):
        depth, i = 1, m.end()
        while i < len(code) and depth:
            ch = code[i]
            if ch in "\"'":
                i = skip_literal(code, i)
                continue
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            i += 1
        yield code[:m.start()].count("\n") + 1, code[m.end():i - 1]


def check_log(files: list[Path]) -> list[str]:
    """System.out / printStackTrace（§7 #6・§9）とログの文字列連結（§7 #3）。"""
    errors = []
    for path in files:
        rel, raw, code = read(path)
        for no, line in enumerate(code, 1):
            if SYSOUT.search(line) and not suppressed(raw, no):
                errors.append(f"ERROR {rel}:{no}: System.out / printStackTrace（SLF4J のロガーを使う。common.md §7 #6・§9）")
        for no, arg in log_calls("\n".join(code)):
            if CONCAT.search(arg) and not suppressed(raw, no):
                errors.append(f"ERROR {rel}:{no}: ログの文字列連結・String.format（プレースホルダ {{}} で組む。common.md §7 #3）")
    return errors


def next_declaration(code: list[str], no: int) -> tuple[str, int]:
    """`no` 行のアノテーションが掛かる宣言（行の内容, 行番号）。空行・他の注釈は飛ばす。"""
    for i in range(no, len(code)):
        s = code[i].strip()
        if s and not s.startswith("@"):
            return s, i + 1
    return "", 0


def check_di(files: list[Path]) -> list[str]:
    """フィールド `@Autowired`・setter 注入（common.md §4 #1・§9）。src/main のみ。"""
    errors = []
    for path in [p for p in files if is_main(p)]:
        rel, raw, code = read(path)
        for no, line in enumerate(code, 1):
            if not ANNOTATION.match(line) or suppressed(raw, no):
                continue
            target, target_no = next_declaration(code, no)
            if not target:
                continue
            if SETTER.search(target):
                errors.append(f"ERROR {rel}:{target_no}: setter 注入（コンストラクタ注入にする。common.md §4 #1・§9）")
            elif "(" not in target:
                # コンストラクタへの `@Autowired` 明示は common.md §4 #4 が求めるもので違反ではない
                errors.append(f"ERROR {rel}:{target_no}: フィールド @Autowired（コンストラクタ注入にする。common.md §4 #1・§9）")
    return errors


def check_sql(files: list[Path]) -> list[str]:
    """マッピング XML の `${}`（domain.md §3 #1・§9）。"""
    errors = []
    for path in files:
        rel, raw, _ = read(path)
        for no, line in enumerate(raw, 1):
            if "${" in line and not suppressed(raw, no):
                errors.append(f"ERROR {rel}:{no}: SQL の ${{}} 展開（#{{}} でバインドする。domain.md §3 #1・§9）")
    return errors


def check_time(files: list[Path]) -> list[str]:
    """現在時刻の直取得（§4 #2。src/main のみ）と java.util.Date / Calendar（§5 #8・§9）。"""
    errors = []
    for path in files:
        rel, raw, code = read(path)
        for no, line in enumerate(code, 1):
            if suppressed(raw, no):
                continue
            if is_main(path) and NOW.search(line):
                errors.append(f"ERROR {rel}:{no}: 現在時刻の直取得（外から受け取る。common.md §4 #2）")
            if UTIL_DATE.search(line):
                errors.append(f"ERROR {rel}:{no}: java.util.Date / Calendar（java.time を使う。common.md §5 #8・§9）")
    return errors


def check_random(files: list[Path]) -> list[str]:
    """静的な共有乱数（common.md §4 #2・§9）。src/main のみ。"""
    errors = []
    for path in [p for p in files if is_main(p)]:
        rel, raw, code = read(path)
        for no, line in enumerate(code, 1):
            if suppressed(raw, no):
                continue
            if STATIC_RANDOM.search(line) or SHARED_RANDOM.search(line):
                errors.append(
                    f"ERROR {rel}:{no}: 静的な共有乱数（RandomFactory から受け取り引数で引き回す。common.md §4 #2・§9）")
    return errors


def in_pointcut(rel: str) -> bool:
    """ポイントカット `com.afkgame.domain.{service,repository}..*` の配下か。"""
    return any(pkg in rel for pkg in POINTCUT_PKGS)


def split_params(params: str) -> list[str]:
    """引数リストをカンマで割る。`<>`・`()` の入れ子は跨がない。"""
    out: list[str] = []
    buf: list[str] = []
    depth = 0
    for ch in params:
        if ch in "<(":
            depth += 1
        elif ch in ">)":
            depth -= 1
        elif ch == "," and depth == 0:
            out.append("".join(buf))
            buf = []
            continue
        buf.append(ch)
    if "".join(buf).strip():
        out.append("".join(buf))
    return out


def public_methods(text: str, iface: bool):
    """(宣言開始の行番号, [(型, 引数名)]) を返す。

    `static` はプロキシが張れず境界ログに出ないため除く。インタフェースのメソッドは
    修飾子が無くても暗黙 public で、JDK 動的プロキシが渡すのはこちら側の名前になる。
    """
    for m in METHOD.finditer(text):
        mods = m.group("mods")
        if "static" in mods or "private" in mods:
            continue
        if not iface and "public" not in mods:
            continue
        head = TYPE_HEAD.match(m.group("type"))
        if not head or head.group(0) in NOT_A_TYPE:
            continue
        params = []
        for part in split_params(m.group("params")):
            tokens = re.sub(r"\bfinal\b", " ", PARAM_ANNOTATION.sub("", part)).split()
            if len(tokens) >= 2:
                params.append((" ".join(tokens[:-1]), tokens[-1]))
        if params:
            yield text[:m.start()].count("\n") + 1, params


def is_secret_name(name: str) -> bool:
    """camelCase を語へ割り、機密を示す語（単独 or 並び）を含むか。"""
    words = [w.lower() for w in CAMEL_WORD.findall(name)]
    if any(w in SECRET_WORDS for w in words):
        return True
    return any(pair in SECRET_WORD_PAIRS for pair in zip(words, words[1:]))


def check_mask(files: list[Path]) -> list[str]:
    """境界ログでマスクされない機密名（logging/application.md §3.1 規約1）。

    `String` 引数だけを見る。Entity・Resource は同 §3.1 規約1 #2（`toString()` から
    機密フィールドを外す）の担当で、名前一致マスクの対象外。
    """
    errors = []
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        if not is_main(path) or not in_pointcut(rel):
            continue
        _, raw, code = read(path)
        text = "\n".join(code)
        iface = re.search(rf"\binterface\s+{re.escape(path.stem)}\b", text) is not None
        for no, params in public_methods(text, iface):
            if suppressed(raw, no):
                continue
            for type_, name in params:
                if type_.split()[-1] not in ("String", "String...", "java.lang.String"):
                    continue
                if name in MASKED_PARAMS or not is_secret_name(name):
                    continue
                errors.append(
                    f"ERROR {rel}:{no}: 引数 `{name}` が固定表に無く境界ログへ生値が出る"
                    f"（logging/application.md §3.1 規約1 の名前へ揃える）")
    return errors


def record_components(text: str):
    """(行番号, レコード名, アクセサ名) を返す。"""
    m = RECORD.search(text)
    if not m:
        return
    at = m.start("comps")
    for part in split_params(m.group("comps")):
        tokens = part.split()
        if len(tokens) >= 2:
            pos = text.index(tokens[-1], at)
            yield text[:pos].count("\n") + 1, m.group("name"), tokens[-1]
        at += len(part) + 1


def enum_constants(text: str, name: str):
    """(行番号, 定数名) を返す。定数リスト（本体先頭から最初の `;` まで）だけを見る。"""
    m = re.search(rf"\benum\s+{re.escape(name)}\b[^{{]*\{{", text)
    if not m:
        return
    depth = 0
    i = m.end()
    while i < len(text):
        ch = text[i]
        if ch in "({":
            depth += 1
        elif ch in ")}":
            if depth == 0:
                break
            depth -= 1
        elif ch == ";" and depth == 0:
            break
        i += 1
    for c in re.finditer(r"\b[A-Z][A-Z0-9_]*\b", text[m.end():i]):
        # 定数の引数（`REQUEST_ID("request_id")`）や定数本体の中は数えない
        if not nested(text, m.end(), m.end() + c.start()):
            yield text[:m.end() + c.start()].count("\n") + 1, c.group(0)


def nested(text: str, start: int, pos: int) -> bool:
    """`start`〜`pos` の間で括弧が開いたままか。"""
    depth = 0
    for ch in text[start:pos]:
        if ch in "({":
            depth += 1
        elif ch in ")}":
            depth -= 1
    return depth > 0


def check_unused(files: list[Path]) -> list[str]:
    """用意したが読み手のいない設定値・enum 値（レビュー 2026-08-10 還元2）。WARN のみ。"""
    mains = [p for p in files if is_main(p)]
    targets = []  # (rel, 行番号, ラベル, 参照の式)
    bodies = []  # (rel, コメントを潰した本文)
    for path in mains:
        rel = path.relative_to(ROOT).as_posix()
        text = "\n".join(read(path)[2])
        if CONFIG_PKG in rel:
            for no, rec, comp in record_components(text):
                targets.append((rel, no, f"{rec}#{comp}()",
                                re.compile(rf"\.{re.escape(comp)}\s*\(|::{re.escape(comp)}\b")))
        else:
            # 生成しているだけの config パッケージは読み手に数えない
            bodies.append((rel, text))
        if path.stem in TRACKED_ENUMS:
            for no, const in enum_constants(text, path.stem):
                targets.append((rel, no, f"{path.stem}.{const}", re.compile(rf"\b{re.escape(const)}\b")))

    warns = []
    for rel, no, label, pattern in targets:
        if any(r != rel and pattern.search(body) for r, body in bodies):
            continue
        warns.append(f"WARN {rel}:{no}: {label} は src/main から参照されていない（先行投入なら件数の増減を見る）")
    return warns


def pom_files() -> list[Path]:
    """モジュールの pom（集約 pom `backend/pom.xml` は dependencyManagement のみで対象外）。"""
    return sorted(ROOT.glob(POM_GLOB))


def blank(text: str, pattern: re.Pattern) -> str:
    """一致した範囲を空白へ潰す（改行は残すので行番号が動かない）。"""
    return pattern.sub(lambda m: re.sub(r"[^\n]", " ", m.group(0)), text)


def pom_dependencies(path: Path):
    """(行番号, artifactId, scope, type) を返す。

    `<dependencies>` 直下だけを見る。`<dependencyManagement>` は版の宣言、`<build>` は
    プラグインの依存でモジュールが引くものではない。コメントアウトされたブロック
    （復活待ちの JDBC ドライバなど）も対象外。
    """
    text = blank(blank(path.read_text(encoding="utf-8"), XML_COMMENT), POM_SECTION)
    for m in POM_DEPENDENCY.finditer(text):
        body = m.group("body")
        artifact = re.search(r"<artifactId>\s*([^<]*?)\s*</artifactId>", body)
        if not artifact:
            continue
        scope = re.search(r"<scope>\s*([^<]*?)\s*</scope>", body)
        type_ = re.search(r"<type>\s*([^<]*?)\s*</type>", body)
        yield (text[:m.start()].count("\n") + 1, artifact.group(1),
               scope.group(1) if scope else "compile", type_.group(1) if type_ else "jar")


def check_pom_dependencies(files: list[Path]) -> list[str]:
    """src/main から参照されない compile 依存（レビュー 2026-08-10 還元1）。WARN のみ。

    参照の見分け方は artifactId ごとに `DEPENDENCY_IMPORTS` へ持つ（`jjwt-api` が
    `io.jsonwebtoken` を配るように、名前から import を導けないため）。表に無い依存は
    「登録されていない」として WARN にし、追加した時点で必ず理由が残るようにする。
    """
    warns = []
    for path in pom_files():
        rel = path.relative_to(ROOT).as_posix()
        module = path.parent
        imports = [line for p in files if is_main(p) and p.is_relative_to(module)
                   for line in read(p)[1] if IMPORT_LINE.match(line)]
        for no, artifact, scope, type_ in pom_dependencies(path):
            if scope not in POM_SCOPES or type_ == "pom" or artifact in DEPENDENCY_NO_SOURCE:
                continue
            prefix = DEPENDENCY_IMPORTS.get(artifact)
            if prefix is None:
                warns.append(f"WARN {rel}:{no}: 依存 `{artifact}` が判定13 の表に無い"
                             f"（DEPENDENCY_IMPORTS か DEPENDENCY_NO_SOURCE へ理由つきで登録する）")
            elif not any(re.match(rf"{IMPORT_LINE.pattern}{re.escape(prefix)}\.", line) for line in imports):
                warns.append(f"WARN {rel}:{no}: 依存 `{artifact}` は src/main から参照されていない"
                             f"（`{prefix}` の import が無い。先行投入なら件数の増減を見る）")
    return warns


def repository_methods(text: str, iface: bool):
    """(宣言開始の行番号, メソッド名, 戻り値の型) を返す。static / private は除く。"""
    for m in METHOD.finditer(text):
        mods = m.group("mods")
        if "static" in mods or "private" in mods:
            continue
        if not iface and "public" not in mods:
            continue
        head = TYPE_HEAD.match(m.group("type"))
        if not head or head.group(0) in NOT_A_TYPE:
            continue
        yield text[:m.start()].count("\n") + 1, m.group("name"), head.group(0)


def call_sites(body: str, name: str):
    """`.<name>(` の呼び出しごとに「戻り値を捨てているか」を返す。

    呼び出し式の手前が文の切れ目なら、値を受け取る先が無い＝捨てている。代入・`return`・
    `if (`・比較はいずれも手前に語が残るため、使っている側と区別できる。
    """
    for m in re.finditer(rf"(?P<recv>[\w.$]*)\.{re.escape(name)}\s*\(", body):
        at = m.start("recv")
        cut = max(body.rfind(ch, 0, at) for ch in STATEMENT_BREAK)
        yield body[cut + 1:at].strip() == ""


def check_discarded_results(files: list[Path]) -> list[str]:
    """更新系 Repository メソッドの戻り値が全呼び出し元で捨てられている（ISSUE-907）。WARN のみ。

    件数で経路を分けないなら宣言も `void` にする（使わない値を返すと、意味がずれても
    読み手がいないので気づけない）。src/main に呼び出しが1つも無いものは「未参照」で
    あって本判定の対象ではないため数えない。
    """
    mains = [p for p in files if is_main(p)]
    bodies = [(p.relative_to(ROOT).as_posix(), "\n".join(read(p)[2])) for p in mains]
    warns = []
    for path in mains:
        rel = path.relative_to(ROOT).as_posix()
        if REPOSITORY_PKG not in rel:
            continue
        text = "\n".join(read(path)[2])
        iface = re.search(rf"\binterface\s+{re.escape(path.stem)}\b", text) is not None
        for no, name, type_ in repository_methods(text, iface):
            if type_ in VOID_TYPES or not name.startswith(MUTATING_PREFIXES):
                continue
            discarded = [d for r, b in bodies if r != rel for d in call_sites(b, name)]
            if discarded and all(discarded):
                warns.append(f"WARN {rel}:{no}: {path.stem}#{name} の戻り値が全呼び出し元で捨てられている"
                             f"（件数で経路を分けないなら void にする）")
    return warns


def list_suppressed(files: list[Path]) -> list[str]:
    """`規約例外` の抑止注記を並べる（レビュー時の再点検用。判定ではない）。

    src/main の Java とマッピング XML だけを見る。テストは記述規約の対象外
    （review-checklist.md 末尾）で、Javadoc からこの注記に言及した行まで拾ってしまうため。
    """
    found = []
    for path in [p for p in files if is_main(p)]:
        rel, raw, _ = read(path)
        for no, line in enumerate(raw, 1):
            m = SUPPRESS_REASON.search(line)
            if m:
                found.append(f"{rel}:{no}: {m.group('reason')}")
    return found


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):  # pragma: no cover - 実行環境依存
        pass

    args = sys.argv[1:]
    files = java_files()
    if "--suppressed" in args:
        found = list_suppressed(files + mapper_files())
        for line in found:
            print(f"SUPPRESSED {line}")
        print(f"\n規約例外の抑止 {len(found)} 件（レビュー時に「今も妥当か」を再点検する）")
        return 0

    checks = {
        "--format": ("記述", lambda: check_format(files)),
        "--imports": ("import", lambda: check_imports(files)),
        "--log": ("ログ", lambda: check_log(files)),
        "--di": ("DI", lambda: check_di(files)),
        "--sql": ("SQL", lambda: check_sql(mapper_files())),
        "--time": ("日時", lambda: check_time(files)),
        "--random": ("乱数", lambda: check_random(files)),
        "--mask": ("マスク", lambda: check_mask(files)),
        "--unused": ("未参照", lambda: check_unused(files) + check_pom_dependencies(files)
                     + check_discarded_results(files)),
    }
    selected = [k for k in checks if k in args] or list(checks)

    total = 0
    warned = 0
    for key in selected:
        label, fn = checks[key]
        found = fn()
        errors = [m for m in found if not m.startswith("WARN")]
        warns = [m for m in found if m.startswith("WARN")]
        total += len(errors)
        warned += len(warns)
        for m in found:
            print(m)
        status = f"{len(errors)} 件" if errors else "OK"
        if warns:
            status += f"（WARN {len(warns)} 件）"
        print(f"[{label}] {status}")

    summary = "違反なし" if total == 0 else f"{total} 件の違反"
    if warned:
        summary += f" / WARN {warned} 件"
    print(f"\n{len(files)} files checked: {summary}")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
