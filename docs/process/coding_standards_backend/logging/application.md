# バックエンドコーディング規約 — アプリケーションログ

> [logging.md](../logging.md)（ログ規約の索引）の分冊。**§3 AOP による境界ログ**と**§4 業務ログ**を担当する。
> どちらも `application.log` へ出る。「どこを通ったか」が §3、「なぜその結果になったか」が §4。
> 3種別の定義・出力先は [logging.md](../logging.md) §1、禁止事項とテストは同 §6・§7、通信ログは [communication.md](communication.md)。
> ログフォーマット・項目名・ロガー名体系・マスク規則・`reason` の値の正は [tech_logging.md](../../../tech/basic/tech_logging.md)（重複させない）。
> 例外の3分類と送出・変換は [exception.md](../exception.md)。

---

## 3. アプリケーションログ①：AOP による境界ログ

層をまたぐ呼び出しの START / END は **AOP が出す**。各メソッドの先頭・末尾に手書きすると、書き漏れ・書式のばらつき・業務コードの見通し低下がそのまま残るため。

| # | 境界 | 対象 |
|---|------|------|
| 1 | **Web ↔ Domain** | `com.afkgame.domain.service` **配下**（領域サブパッケージを含む）の Service（`ServiceImpl`）の public メソッド |
| 2 | **Domain ↔ Repository** | `com.afkgame.domain.repository` **配下**の Repository の public メソッド |

コントローラそのものは対象にしない（受信の START / END は [communication.md](communication.md) §2 の通信ログが持つ。二重に出さない）。

| # | 規約 |
|---|------|
| 1 | 境界ログは **`LayerLoggingInterceptor`（`afkgame-env` の `com.afkgame.env.logging`）だけが出す**。同じ内容を業務コードで手書きしない |
| 2 | 適用は `AspectJExpressionPointcutAdvisor` を境界ごとに1本ずつ Bean 定義して行い、**ポイントカット式は `META-INF/spring/afkgame.properties` が持つ**。`@Aspect` のアノテーションへ定数で書くと `afkgame-env` が `afkgame-domain` のパッケージ名を抱え、依存方向（`common.md` §2）に反するため |
| 3 | **START / END を対で出す**。END は例外で抜けた経路でも必ず出し、その場合は `reason=exception` を付ける（例外そのものの記録は [logging.md](../logging.md) §5 の担当で、ここでは ERROR にしない） |
| 4 | レベルは **INFO 固定**。引数・戻り値まで常時出力する（本番で事後追跡できることを優先する。ログ量は [logging.md](../logging.md) §1 のローテーションで受け止める） |
| 5 | ロガー名は `afkgame.layer`。出力するのは `signature`（`AuthServiceImpl#login` 形式）・`args`・`result`・`duration_ms` |
| 6 | **Spring AOP はプロキシ経由でのみ効く**。同一クラス内の自己呼び出しは記録されない（`@Transactional` と同じ制約）。境界をまたぐ呼び出しを内部呼び出しへ畳まない |
| 7 | Interceptor に**ログ以外の副作用を持たせない**。例外は必ずそのまま再スローする（握らない・差し替えない） |

```
[2026-08-09 14:38:30] INFO  afkgame.layer: START signature=AuthServiceImpl#login args=[ab***@example.com, ****] request_id=550e8400
[2026-08-09 14:38:30] INFO  afkgame.layer: START signature=UserRepository#findByEmail args=[ab***@example.com] request_id=550e8400
[2026-08-09 14:38:30] INFO  afkgame.layer: END   signature=UserRepository#findByEmail result=User(id=user_001) duration_ms=3 request_id=550e8400
[2026-08-09 14:38:30] INFO  afkgame.layer: END   signature=AuthServiceImpl#login result=AuthResult(userId=user_001) duration_ms=42 request_id=550e8400
```

### 3.1 引数・戻り値の出力規則（マスクは AOP 側で完結させる）

引数・戻り値を常時 INFO で出す以上、**マスク漏れは設計で塞ぐ**。`toString()` の実装任せにしない。整形は `com.afkgame.env.logging` の共通部品が担い、呼び出し側にマスクを書かせない（`tech_logging.md`「機密情報のマスク規則」と同じ方針）。

| # | 規約 |
|---|------|
| 1 | **パラメータ名が機密名に一致したら値を伏せる**。機密名は共通部品が持つ固定表（`password`・`rawPassword`・`newPassword`・`passwordHash`・`token`・`accessToken`・`refreshToken`・`googleAuthCode`・`secret`・`credential` → `****`／`email` → `LogKey.EMAIL` と同じマスク） |
| 2 | 名前で判定できないもの（戻り値・Entity・Resource のフィールド）は、**機密フィールドを `toString()` から外す**ことで担保する。Lombok は `@ToString.Exclude`、手書きは対象フィールドを含めない。**機密項目を持つクラスを新設・改修したら同時に対応する**（レビュー観点。[logging.md](../logging.md) §6） |
| 3 | 生のトークン・パスワードを返すメソッドは、**戻り値を出力対象から外す注釈**を付けて `****` にする（例: `JwtService#createAccessToken`） |
| 4 | **コレクション・配列・`Map` は要素を展開せず件数だけ**出す（`List(size=12)`）。ログ量とマスク漏れの両方を同時に抑える |
| 5 | 1値あたり **200文字で打ち切り**、末尾に `...` を付ける |
| 6 | `null` は `null`、`Optional` は中身へ #1〜#5 を適用する。`void` の戻り値は `result` を出さない |

ハッシュ値でも `passwordHash`（bcrypt）は辞書攻撃の入力になるため固定表に入れる。`tokenHash`（48バイト乱数の SHA-256）は総当たりが成立しないので `// 規約例外:` の抑止でよい。

## 4. アプリケーションログ②：業務ログ（AppLogger）

AOP が残すのは「**どこを通ったか**」だけ。「**なぜその結果になったか**」は業務コードが `AppLogger` で残す。共通部品は `afkgame-env` の `com.afkgame.env.logging` にあり、ロガー名・項目名・`reason` の値を各クラスの文字列リテラルから追い出すためのもの。

| 部品 | 役割 |
|------|------|
| `AppLogger` | 入口。`AppLogger.of(LoggerName.AUTH)` で得る |
| `LoggerName` | ロガー名（体系の正は `tech_logging.md`「ロガー名体系」） |
| `LogKey` | ログ項目名（正は「ログ項目」）。マスク規則も本 enum が持つ |
| `LogReason` | `reason` の値（正は「失敗理由（reason）の値」） |
| `LogEntry` | 項目を積み、`log()` で出力する |

```java
private static final AppLogger logger = AppLogger.of(LoggerName.AUTH);

logger.info("ログイン").with(LogKey.USER_ID, user.getId()).log();
logger.warn("ログイン失敗").reason(LogReason.PASSWORD_MISMATCH).log();
logger.error("未捕捉例外").cause(e).log();
```

| # | 規約 |
|---|------|
| 1 | `private static final AppLogger logger = AppLogger.of(LoggerName.<領域>)` の形で持つ。インスタンスフィールドにしない |
| 2 | **項目はメッセージへ埋め込まず `with()` / `reason()` で積む**（`"reason=" + r` や `user_id={}` を書かない）。JSON 形式で独立フィールドになる形を唯一の書き方にする |
| 3 | **境界の通過・引数・所要時間を書かない**（§3 の AOP が出す）。業務ログが持つのは分岐の理由と結果の要約だけ |
| 4 | 想定内の失敗は WARNING + `reason`。`reason` の値は `LogReason` へ足す（文字列リテラルで書かない）。レベルの使い分けの正は `tech_logging.md`「ログレベル方針」 |
| 5 | メッセージのプレースホルダは `{}`（文字列連結・`String.format` を使わない） |
| 6 | 横断項目（`request_id`・`player_id` ほか）は MDC（`RequestLogFilter`・`JwtAuthenticationFilter`）が載せる。各所で詰め直さない |
| 7 | パスワード・トークン生値・メールアドレスをそのまま出さない。トークン・メールは `LogKey.TOKEN` / `LogKey.EMAIL` へ渡せば自動でマスクされる |
