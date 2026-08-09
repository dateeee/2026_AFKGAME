# バックエンドコーディング規約 — Web層（`afkgame-web`）

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。全層共通の規約は [common.md](common.md) が先、層の位置づけは [layering.md](layering.md)。
> ベースは TERASOLUNA 開発ガイドライン 5.11.0.RELEASE 日本語版（[basis.md](basis.md) §1）の `ImplementationAtEachLayer/ApplicationLayer` と `Security`。本書はそこからの差分だけを持つ。
> `afkgame-web` はガイドラインの**アプリケーション層**に当たる。**実装はできるだけ薄く保ち、ビジネスルールを含めない**（`layering.md` §1）。

---

## 1. 対象と責務

| パッケージ | 置くもの | ガイドライン上の分類 |
|-----------|---------|------------------|
| `.api` | `@RestController` | Controller |
| `.resource` | Resource(DTO) + Bean Validation | Form（2.4.1.1.3 Tip: REST では `Resource` が Form の役割を担う） |
| `.config.app` / `.config.web` | Security・Jackson（`ApplicationContextConfig`・`SpringSecurityConfig`）、Spring MVC 設定（`SpringMvcConfig`） | （対応なし） |
| `.filter` | リクエストIDログ・共通例外ハンドラ | （対応なし） |

- Web層は「受け取る・検証する・ドメインへ渡す・返す」だけを担う。業務判断は [domain_service.md](domain_service.md) §1 の Service が持つ
- **View と Helper は持たない**（ガイドライン 2.4.1.1.2・2.4.1.1.3 との差分。`layering.md` §2）。描画は SPA（Vue 3）、変換は Resource の `static from(...)`（§3 #3）
- Controller から Repository を直接呼ばない（`layering.md` §3）。参照系でも Service を通す
- API契約（パス・HTTPメソッド・ステータス・JSON構造）の正は [tech_api.md](../../tech/basic/tech_api.md)・[tech_api_common.md](../../tech/basic/tech_api_common.md)。本書で再掲しない

## 2. コントローラ（`api`）

| # | 規約 |
|---|------|
| 1 | マッピング・入力検証・Resource 変換**だけ**を書く。業務分岐・計算・DB アクセスを置かない |
| 2 | ボディは `@Valid @RequestBody` で受ける |
| 3 | 戻り値は Resource（`ResponseEntity` はステータスやヘッダを変える場合のみ） |
| 4 | `try-catch` しない。応答への変換は `ApiExceptionHandler`（`@RestControllerAdvice`）へ集約する |
| 5 | **単項目チェック・相関項目チェックは本層**（Bean Validation）で行う。ビジネスルールのチェックは Service（責任分界点の全体像は `domain_service.md` §1） |

## 3. Resource（`resource`）

| # | 規約 |
|---|------|
| 1 | `record` + Bean Validation（Jakarta）で定義する |
| 2 | リクエスト用とレスポンス用を**兼用しない** |
| 3 | ドメイン型 ↔ Resource の変換は `public static from(...)` に集約する。ガイドラインは Helper クラス（2.4.1.1.3・2.4.1.1.4）か MapStruct への委譲を推奨するが、本プロジェクトは**Helper を作らない**。変換対象が Resource ごとに閉じており、Controller の見通しは `from(...)` への集約で足りるため |
| 4 | **JSON のフィールド名は lowerCamelCase**。Jackson の既定でそのまま出るため `@JsonProperty` での改名をしない（`tech_api_common.md` §5.0） |
| 5 | 業務ロジックを持たせない（判定・計算は Service 側） |

## 4. 設定・フィルタ（`config`・`filter`）

| # | 規約 |
|---|------|
| 1 | 設定値は `afkgame-env` の設定保持 Bean を注入して読む。Web層で `@Value` を直書きしない（`@ConfigurationProperties` は Boot 機能のため使わない。組み立て方は [tech_structure_backend.md](../../tech/basic/tech_structure_backend.md) §4.2） |
| 2 | 個別機能の業務ロジックをフィルタ・インターセプタに置かない（横断処理だけ） |
| 3 | `request_id` などの横断項目は MDC（`RequestLogFilter`）が載せる（`common.md` §7 #5） |

**フィルタ・インターセプタの正は [filter.md](filter.md)（使い分け・作り方・登録順・例外）と [interceptor.md](interceptor.md)（フック・登録・拒否の書き方）**。本書では再掲しない。

## 5. エラー応答

**正は [exception.md](exception.md) §4**（`ApiExceptionHandler` が受ける例外と応答の対応、内部情報を出さない規約、ガイドライン 5.1.4.6.1・4.3.3.1.3 との差分）。本書では再掲しない。

## 6. 命名（Web層）

| 対象 | 規約 | 例 |
|------|------|-----|
| コントローラ | `<リソース>Api`。`Controller` 接尾辞は使わない | `AuthApi`・`HealthApi` |
| Resource（DTO） | `<用途>Resource` | `AuthResource`・`ErrorResource` |

共通の命名（クラス・メソッド・定数・例外・パッケージ）は `common.md` §3。

ガイドライン 5.1.4.5.1 は、ルートパッケージ `api` の下に**リソース毎のパッケージ**を切り、そこへ `[リソース名]RestController`・`[リソース名]Resource`・`[リソース名]Validator`・`[リソース名]Helper` をまとめる構成を推奨する。本プロジェクトは**採らない**。

| # | 理由 |
|---|------|
| 1 | Helper を作らず（§3 #3）、相関チェックも Bean Validation で書く（§2 #5）ため、リソース毎のパッケージに入るクラスが Controller と Resource の2つだけになり、束ねる意味が薄い |
| 2 | `.config`・`.filter` は特定リソースに属さない。種類別（`.api` / `.resource` / `.config` / `.filter`）で切るほうが全体の並びがそろう |
| 3 | 接尾辞 `Api` は Vue 側の呼び出し単位（`api/auth.ts` 等）と名前が一致する。`RestController` は本プロジェクトでは唯一の Controller 種別のため、種別を名前で区別する必要がない |

## 7. セキュリティ

方式の正は [tech_auth.md](../../tech/detail/tech_auth.md)（認証）と [tech_security.md](../../tech/nonfunctional/tech_security.md)（対策一覧）。本節はガイドライン `Security`（9章）との差分だけを持ち、ハッシュ方式・レート制限・トークンのローテーションは再掲しない。

| # | 規約 |
|---|------|
| 1 | 認証は**ステートレス**（`Authorization: Bearer` の JWT）。ガイドラインが前提とするフォーム認証 + セッション（9.2.2.1）と `UserDetailsService` による DB 認証（9.2.2.4）は採らない。クライアントが SPA だけで画面遷移を伴うログインが無く、サーバーにセッションを持たせない構成のため |
| 2 | CSRF トークン（9.5）を使わない。Cookie を認証に使わないため、偽造リクエストに資格情報が乗らない（`tech_security.md` §11.2）。**Cookie を使う認証へ変えるときは本行ごと見直す** |
