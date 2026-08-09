# バックエンドコーディング規約 — 例外とエラー

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。**例外を投げる・捕まえる・応答へ変換する**ときは層を問わず本書に従う。
> ベースは TERASOLUNA 開発ガイドライン 5.11.0.RELEASE 日本語版（[basis.md](basis.md) §1）の `ArchitectureInDetail/WebApplicationDetail/ExceptionHandling`（4.3）。本書はそこからの差分だけを持つ。
> 全層共通の規約は [common.md](common.md)、層の位置づけは [layering.md](layering.md)。

---

## 1. 例外の3分類

**すべての例外を「誰が対処するのか」で3つに分類する**。分類が決まれば、投げるクラス・応答・ログレベルが一意に決まる。

| # | 分類 | 何が起きたか | 対処する人 | 送出するクラス | HTTP | ログ |
|---|------|------------|-----------|--------------|------|------|
| 1 | **ビジネス例外** | ビジネスルールの違反を検知した。アプリケーションとして想定される状態 | 利用者（再操作で解消できる） | `BusinessException(ResultMessages)` | 対応表が決める 4xx（§4 #4） | WARN |
| 2 | **システム例外** | 正常稼働時には発生してはいけない状態を検知した | 運用者（データ・設定の修復が要る） | `SystemException(code, message[, cause])` | 500 | ERROR |
| 3 | **予期しないエラー** | バグ・基盤障害・`java.lang.Error`。検知ではなく発生してしまったもの | 開発者（解析が要る） | **投げない・捕まえない** | 500 | ERROR + スタックトレース |

**送出するクラスはすべて `terasoluna-gfw` の提供物**とし、例外クラスを自作しない（完全修飾名と採否は §2.1）。

- 分類1はアプリケーション開発者が意識する例外、分類2・3はアーキテクトが意識する例外（ガイドライン 4.3.1.1 Note）。**新しい分岐を書くときは、まずこの表のどの行かを決める**
- **分類2・3はクライアントが対処できない**。応答はどちらも `500` + `INTERNAL_UNEXPECTED_ERROR` にそろえ、内部情報を載せない（§4 #1）。両者を区別するのは**ログ**で、分類2には原因を特定できるコードとメッセージが残る
- 3分類とも `RuntimeException` 派生にする。**検査例外を新設しない**（`@Transactional` の既定ロールバック対象に載せるため。ガイドライン 3.2.5.6.4 Note）
- **例外を処理フローの制御に使わない**（ガイドライン 4.3.3.2.1 Note）。ビジネスルールの違反は条件式で検知して投げる。捕捉して投げ直すのは、ライブラリが例外でしか違反を通知しない場合に限る

## 2. ガイドラインの例外の種類との対応

ガイドラインは例外を6種類に分ける（4.3.2.1）。本プロジェクトの3分類との対応は次のとおり。

| ガイドラインの種類 | 分類 | 扱い |
|------------------|------|------|
| ビジネス例外（4.3.2.1.1） | 1 | `BusinessException` を投げる |
| 正常稼働時に発生するライブラリ例外（4.3.2.1.2） | 1 へ変換 | 捕捉して `BusinessException` にする（例: `JwtService` の `ExpiredJwtException` → `AUTH_TOKEN_EXPIRED`）。継続できるならログを残して継続してよい（§3 #4） |
| システム例外（4.3.2.1.3） | 2 | `SystemException` を投げる |
| 予期しないシステム例外（4.3.2.1.4） | 3 | 捕まえない |
| `java.lang.Error` を継承しているエラー（4.3.2.1.5） | 3 | 捕まえない |
| リクエスト不正時のフレームワーク例外（4.3.2.1.6） | 1 と同じ応答 | アプリからは投げない。Spring MVC が投げるものを `ApiExceptionHandler` が 422 / `HTTP_<status>` へ変換する（§4） |

### 2.1 例外クラスの対応

`terasoluna-gfw` 5.11.0.RELEASE は**導入済み**（`basis.md` §1）。4.3.2.1.1・4.3.2.1.3 の Note は独自クラスの新設を認めているが、**本プロジェクトは gfw の提供物だけを使う**。下表は完全修飾名で書く（`o.t.gfw` ＝ `org.terasoluna.gfw`）。

| ガイドライン | 本プロジェクト | 決定の理由 |
|------------|--------------|-----------|
| `o.t.gfw.common.exception.BusinessException` | **そのまま使う** | 標準クラスに寄せる。エラーコードは `ResultMessages` に載せ、HTTP ステータスは Web 層の対応表が決める（§4 #4） |
| `o.t.gfw.common.exception.SystemException` | **そのまま使う** | `(code, message[, cause])` が §3 #2 の要求と一致し `getCode()` も持つ |
| `o.t.gfw.common.exception.ResourceNotFoundException` | **そのまま使う**（404 専用） | `BusinessException` と同じ扱いだが、404 を投げる側で表現できる。ステータスは対応表を引かず常に 404 |
| `ResultMessages`（警告メッセージ） | 戻り値の `record` に載せる | 処理は成功する通知を例外にしない（ガイドライン 3.2.5.6.3 と同じ「戻り値で返す」方式） |

**リクエスト処理の例外クラスを自作しない**（旧 `AppException` は廃止。自作してよいのは応答を持たない起動時例外だけ ＝ `MasterDataException`・§3 #3）。ステータスを例外に持たせる自作クラスは gfw の階層から外れ、「例外クラスごとの応答」を増やして `ApiExceptionHandler` を太らせる。

**`getMessage()` を応答へ写さない。** `BusinessException` の親 `ResultMessagesNotificationException` は `getMessage()` を `ResultMessages#toString()`（`ResultMessages [type=error, list=[ResultMessage [code=…]]]`）へ上書きしている。そのまま返すと**内部のクラス名が応答へ漏れる**（§4 #1 違反）ので、Web 層は `getResultMessages()` からコードを取り出す（§4 #4）。

`SystemException` の送出箇所は現時点で無い。必要になった実装で gfw のクラスを `import` する。

## 3. 送出（ドメイン層）

| # | 規約 |
|---|------|
| 1 | ビジネスルールの違反は**ロジックで検知して** `BusinessException`（404 は `ResourceNotFoundException`）を投げる。`ResultMessages.error().add(code)` に**コードだけ**を載せ、コードは仕様の値をそのまま使う。`@throws` にコードを書く（`common.md` §8 #3） |
| 2 | あるはずのマスターデータ・設定・資源が無いなどの異常は `SystemException`（gfw。§2.1）を投げる。コードは `INTERNAL_` 接頭辞（体系の正は [tech_logging.md](../../tech/basic/tech_logging.md)）、メッセージには**原因を特定できる識別子**（ID・パス）を入れる。どちらも応答には出ずログにだけ残る |
| 3 | 起動時（Bean 生成中）に検知するマスターデータの不正は `MasterDataException` で**起動を止める**。分類2だが、リクエスト外で起きるため応答を持たない（[domain.md](domain.md) §4 #2） |
| 4 | ライブラリの例外は**捕まえて分類し直す**。利用者の再操作で解消するなら `BusinessException`、システム異常なら `SystemException`、到達しないはずなら理由コメントを添えて `IllegalStateException`（＝分類3・バグとして扱う） |
| 5 | Service は**メッセージ文言を解決しない**。`ResultMessage` に持たせるのはコードと埋め込み値（`args`）だけで、`fromText` を使わない（ガイドライン 3.2.5.6.2）。文言はフロントエンドが持つ |
| 6 | 分類1・2はいずれも `@Transactional` の既定でロールバックされる。ロールバックさせたくない副作用は `noRollbackFor` を明示する（[domain/service.md](domain/service.md) §4 #6） |
| 7 | **例外を握りつぶさない**。空の `catch` を書かない。継続させる場合も理由をコメントに書き、ログを残す |

## 4. 応答への変換（Web層）

例外から応答への変換は `ApiExceptionHandler`（`@RestControllerAdvice`）へ集約する。コントローラで `try-catch` しない（[web.md](web.md) §2 #4）。

| 受ける例外 | 分類 | 応答 | ログ |
|-----------|------|------|------|
| `BusinessException` | 1 | 対応表が決める 4xx（既定 422） + `code` | WARN（送出元で出す） |
| `ResourceNotFoundException` | 1 | 404 + `code` | WARN（送出元で出す） |
| Bean Validation 違反・本文が読めない | 1 | 422 + `VALIDATION_ERROR` | WARN |
| ほかの Spring MVC 標準例外（4xx） | 1 | そのまま + `HTTP_<status>` | WARN |
| `SystemException` | 2 | 500 + `INTERNAL_UNEXPECTED_ERROR` | ERROR（例外のコードとメッセージ + スタックトレース） |
| 上記以外の `Exception`・5xx の標準例外 | 3 | 500 + `INTERNAL_UNEXPECTED_ERROR` | ERROR + スタックトレース |

| # | 規約 |
|---|------|
| 1 | 応答に内部情報（SQL・スタックトレース・テーブル構造・ライブラリ名・クラス名）を載せない。5xx の標準例外はメッセージに内部の型名が入るため定型文へそろえる |
| 2 | 認証・認可の失敗理由を出し分けない（探索の手がかりになるため）。詳細はログにだけ残す |
| 3 | エラーコード体系と統一エラーレスポンス形式の正は `tech_logging.md`、ステータスの使い分けの正は [tech_api_common.md](../../tech/basic/tech_api_common.md)。本書で再掲しない |
| 4 | `getResultMessages()` の**先頭** `ResultMessage#getCode()` を `ErrorResource.code` へ写し、HTTP ステータスは Web 層が持つ**コード→ステータス対応表**で引く（未登録は 422）。応答の `message` は同表の定型文とし、**例外の `getMessage()` は使わない**（§2.1）。ガイドラインの `ApiError` + `ExceptionCodeResolver`（5.1.4.6.1）は**採らない** — 解決したいのは「例外クラス→コード」ではなく「コード→ステータス」で向きが違うため |
| 5 | 対応表は `tech_logging.md`（コード体系の正）の写しなので、**一致を機械照合する**（`scripts/check_error_codes.py`。仕様に無いコード・ステータス違い・仕様にあって表に無いコードを落とす）。手で同期させない |
| 6 | `ResponseEntityExceptionHandler` の継承は**採る**（5.1.4.6.1）。Spring MVC が投げる標準例外（404・405・415 など）も統一形式へ寄せるため |
| 7 | ガイドラインの `SystemExceptionResolver` + 遷移先ビューのマッピング（4.3.3.1.3）は**採らない**。REST 専用で遷移先の画面が無く、応答は #4 の対応表で決まるため。サーブレットコンテナのエラーページ設定（4.3.3.1.4）も同じ理由で使わない |
| 8 | フィルタ内で起きた認証エラーはハンドラを通らない。Spring Security の `ApiAuthenticationEntryPoint` が**同じ形式で**応答する（形式を変えない） |

## 5. ログ

| # | 規約 |
|---|------|
| 1 | 分類1は**送出元**で WARN、分類2・3は**ハンドラ**で ERROR。同じ例外を両方で出さない |
| 2 | 分類2・3はスタックトレースを必ず残す。分類1は残さない（想定内のため） |
| 3 | gfw の `ExceptionLogger`・`ExceptionLevelResolver`（4.3.5.1）は**使わない**（導入済みだが依存しない）。出力先とレベルを機能単位で切り替えるため、ロガー名体系（`common.md` §7 #1）で出す方針のため。例外コードの先頭1文字でレベルを決める仕組み（`DefaultExceptionLevelResolver`）も採らず、レベルは本節の分類で決める |
| 4 | レベルの使い分け・出力フォーマット・マスク規則の正は `tech_logging.md` |

## 6. 本書が持たないもの（分担）

| 内容 | 正 |
|------|-----|
| エラーコード体系・統一エラーレスポンス形式・ロガー名体系 | `tech_logging.md` |
| HTTP ステータスコードの使い分け | `tech_api_common.md` |
| コントローラ・Resource の書き方、セキュリティ | `web.md` |
| フィルタ内・インターセプタ内で起きた例外の扱い | [filter.md](filter.md) §4・[interceptor.md](interceptor.md) §3 #4 |
| Service の責務・トランザクションと伝播属性 | `domain/service.md` |
| 例外のテスト（送出の検証・ハンドラ経由の応答検証） | [test.md](test.md)・[.claude/project/test-patterns.md](../../../.claude/project/test-patterns.md) |
