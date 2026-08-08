# バックエンドコーディング規約 — Web層（`afkgame-web`）

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。全層共通の規約は [common.md](common.md) が先、層の位置づけは [layering.md](layering.md)。
> ベースは TERASOLUNA 開発ガイドライン 5.11.0.RELEASE 日本語版（[basis.md](basis.md) §1）の `ImplementationAtEachLayer/ApplicationLayer` と `Security`。本書はそこからの差分だけを持つ。
> `afkgame-web` はガイドラインの**アプリケーション層**に当たる。**実装はできるだけ薄く保ち、ビジネスルールを含めない**（[layering.md](layering.md) §1）。

---

## 1. 対象と責務

| パッケージ | 置くもの | ガイドライン上の分類 |
|-----------|---------|------------------|
| `.api` | `@RestController` | Controller |
| `.resource` | Resource(DTO) + Bean Validation | Form（2.4.1.1.3 Tip: REST では `Resource` が Form の役割を担う） |
| `.config` | Security・Jackson・`@ConfigurationProperties` | （対応なし） |
| `.filter` | リクエストIDログ・共通例外ハンドラ | （対応なし） |

- Web層は「受け取る・検証する・ドメインへ渡す・返す」だけを担う。業務判断は [domain_service.md](domain_service.md) §1 の Service が持つ
- **View と Helper は持たない**（逸脱 #8・#7）。描画は SPA（Vue 3）、変換は Resource の `static from(...)`（§3 #3）
- Controller から Mapper を直接呼ばない（[layering.md](layering.md) §3）。参照系でも Service を通す
- API契約（パス・HTTPメソッド・ステータス・JSON構造）の正は [tech_api.md](../../tech/basic/tech_api.md)・[tech_api_common.md](../../tech/basic/tech_api_common.md)。本書で再掲しない

## 2. コントローラ（`api`）

| # | 規約 |
|---|------|
| 1 | マッピング・入力検証・Resource 変換**だけ**を書く。業務分岐・計算・DB アクセスを置かない |
| 2 | ボディは `@Valid @RequestBody` で受ける |
| 3 | 戻り値は Resource（`ResponseEntity` はステータスやヘッダを変える場合のみ） |
| 4 | `try-catch` しない。応答への変換は `ApiExceptionHandler`（`@RestControllerAdvice`）へ集約する |
| 5 | **単項目チェック・相関項目チェックは本層**（Bean Validation）で行う。ビジネスルールのチェックは Service（責任分界点の全体像は [domain_service.md](domain_service.md) §1） |

## 3. Resource（`resource`）

| # | 規約 |
|---|------|
| 1 | `record` + Bean Validation（Jakarta）で定義する |
| 2 | リクエスト用とレスポンス用を**兼用しない** |
| 3 | ドメイン型 ↔ Resource の変換は `public static from(...)` に集約する。ガイドラインは Helper クラスか MapStruct への委譲を推奨するが、本プロジェクトは**Helper を作らない**（逸脱 #7）。変換対象が Resource ごとに閉じており、Controller の見通しは `from(...)` への集約で足りるため |
| 4 | **JSON のフィールド名は lowerCamelCase**。Jackson の既定でそのまま出るため `@JsonProperty` での改名をしない（[tech_api_common.md](../../tech/basic/tech_api_common.md) §5.0） |
| 5 | 業務ロジックを持たせない（判定・計算は Service 側） |

## 4. 設定・フィルタ（`config`・`filter`）

| # | 規約 |
|---|------|
| 1 | 設定値は `@ConfigurationProperties`（`afkgame.*`）で受ける。`@Value` の直書きをしない |
| 2 | 個別機能の業務ロジックをフィルタ・インターセプタに置かない（横断処理だけ） |
| 3 | `request_id` などの横断項目は MDC（`RequestLogFilter`）が載せる（[common.md](common.md) §7 #5） |

## 5. エラー応答

| # | 規約 |
|---|------|
| 1 | 応答メッセージに内部情報（SQL・スタックトレース・テーブル構造・ライブラリ名）を載せない |
| 2 | 認証・認可の失敗理由を出し分けない（探索の手がかりになるため）。詳細はログにだけ残す |
| 3 | エラーコード体系の正は [tech_logging.md](../../tech/basic/tech_logging.md)、レスポンス形式の正は [tech_api_common.md](../../tech/basic/tech_api_common.md) |

## 6. 命名（Web層）

| 対象 | 規約 | 例 |
|------|------|-----|
| コントローラ | `<リソース>Api`。`Controller` 接尾辞は使わない | `AuthApi`・`HealthApi` |
| Resource（DTO） | `<用途>Resource` | `AuthResource`・`ErrorResource` |

共通の命名（クラス・メソッド・定数・例外・パッケージ）は [common.md](common.md) §3。
