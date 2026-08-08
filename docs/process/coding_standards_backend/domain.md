# バックエンドコーディング規約 — ドメイン層（`afkgame-domain`）

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。全層共通の規約は [common.md](common.md) が先。
> ベースは TERASOLUNA 開発ガイドライン 5.11.0.RELEASE 日本語版（索引 §1）の `ImplementationAtEachLayer/DomainLayer`（Service）と `ImplementationAtEachLayer/InfrastructureLayer`（MyBatis3 のデータアクセス）。本書はそこからの差分だけを持つ。

---

## 1. 対象と責務

| パッケージ | 置くもの |
|-----------|---------|
| `.model` | Entity（永続化の器） |
| `.repository` | MyBatis3 Mapper インタフェース + 同名・同パッケージの Mapper XML |
| `.service` | ビジネスロジック（唯一の置き場） |
| `.masterdata` | マスターデータの `record` と YAML ローダ |
| `.rng` | 乱数（`RandomFactory`） |
| `.exception` | 業務例外（`AppException`） |

- **Web 層の型を持ち込まない**（Spring MVC・`jakarta.servlet`・`HttpStatus`）。依存してよいのは `afkgame-env` だけ（[common.md](common.md) §2）
- ドメイン層は HTTP・画面の都合を知らない。API の形に合わせた戻り値の整形は Web 層（[web.md](web.md) §3）で行う

## 2. サービス（`service`）

| # | 規約 |
|---|------|
| 1 | ビジネスロジックの唯一の置き場。コントローラ・Mapper に業務分岐や計算を置かない |
| 2 | トランザクション境界は **Service の public メソッドに `@Transactional`**（Mapper・Controller には付けない） |
| 3 | 複数 Mapper をまたぐ更新は1メソッドに閉じる |
| 4 | 失敗時にロールバックさせたくない副作用（不正検知による失効など）は `@Transactional(noRollbackFor = ...)` を明示し、理由を Javadoc に書く |
| 5 | クライアントへ返す業務エラーは `AppException` のみを投げる（[common.md](common.md) §6）。HTTP ステータスは `int` で保持する |
| 6 | 計算式・判定のしきい値は詳細設計が正。Service に数値を直書きしない（[common.md](common.md) §5 #10） |

## 3. Mapper・Entity（MyBatis3）

| # | 規約 |
|---|------|
| 1 | Entity は永続化の器。ロジック・導出プロパティを持たない |
| 2 | SQL は XML に置き、値は `#{}` でバインドする。`${}` による文字列組み立てをしない |
| 3 | 取得は N+1 を作らない（JOIN・一括取得） |
| 4 | Mapper XML は **インタフェースと同名・同パッケージのリソース配下**に置く |
| 5 | **DB 列は snake_case、Java フィールドは lowerCamelCase**。変換は MyBatis の `map-underscore-to-camel-case: true` に任せ、`<result>` の手書きマッピングを増やさない。列名の正は [tech_db.md](../../tech/basic/tech_db.md) |
| 6 | テーブル定義書に無い列・テーブルが必要になったら、実装で先行させず**基本設計へ差し戻す**（[phases.md](../phases.md) §3.2.1） |

## 4. マスターデータ・乱数

| # | 規約 |
|---|------|
| 1 | マスターデータは `src/main/resources/masterdata/` の YAML を起動時に読み込み、不変の `record` として公開する。値の正は [docs/data/](../../data/master_data.md) |
| 2 | 読み込み・検証の失敗は起動失敗にする（不正なデータのまま動かさない） |
| 3 | 乱数は `RandomFactory` から受け取り、引数で引き回す（[common.md](common.md) §4 #2） |

## 5. 命名（ドメイン層）

| 対象 | 規約 | 例 |
|------|------|-----|
| Entity | テーブル名の単数形 | `User`（`users`）・`RefreshToken` |
| Mapper | `<Entity>Mapper`。XML は**同名・同パッケージのリソース配下** | `UserMapper.java` ↔ `com/afkgame/domain/repository/UserMapper.xml` |
| Mapper メソッド | `select` / `insert` / `update` / `delete` + `By<条件>` | `selectById`・`revokeAllByUserId` |
| Service | `<領域>Service` | `AuthService` |

共通の命名（クラス・メソッド・定数・例外・パッケージ）は [common.md](common.md) §3。
