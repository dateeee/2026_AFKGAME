# バックエンドコーディング規約 — ドメイン層のデータ（`afkgame-domain`）

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。全層共通の規約は [common.md](common.md) が先、層の位置づけは [layering.md](layering.md)。
> **Service は [domain_service.md](domain_service.md) が正**。本書は Entity・Mapper・マスターデータを扱う。
> ベースはガイドライン `ImplementationAtEachLayer/DomainLayer`（3.2.1〜3.2.4）と `InfrastructureLayer`（MyBatis3）。本書はそこからの差分だけを持つ（準拠元は [basis.md](basis.md) §1）。

---

## 1. 対象と責務

ドメイン層は**アプリケーション層に業務ロジックを提供する**層で、実装は「Entity」「業務データを操作するデータアクセス」「Service」の3つに分かれる（ガイドライン 3.2.1）。本書は前2者を扱う。

| パッケージ | 置くもの | ガイドライン上の分類 |
|-----------|---------|------------------|
| `.model` | Entity（永続化の器） | Domain Object |
| `.repository` | MyBatis3 Mapper インタフェース + 同名・同パッケージの Mapper XML | O/R Mapper（Repository は作らない。[layering.md](layering.md) §3） |
| `.service` | ビジネスロジック | Service（[domain_service.md](domain_service.md)） |
| `.masterdata` | マスターデータの `record` と YAML ローダ | （対応なし） |
| `.rng` | 乱数（`RandomFactory`） | （対応なし） |
| `.exception` | 業務例外（`AppException`） | 業務例外・システム例外（[domain_service.md](domain_service.md) §6） |

- **Web 層の型を持ち込まない**（Spring MVC・`jakarta.servlet`・`HttpStatus`）。依存してよいのは `afkgame-env` だけ（[common.md](common.md) §2）
- ドメイン層は HTTP・画面の都合を知らない。API の形に合わせた戻り値の整形は Web 層（[web.md](web.md) §3）で行う

## 2. Entity（`model`）

| # | 規約 |
|---|------|
| 1 | Entity は**永続化の器**。ロジック・導出プロパティを持たない。ガイドラインが扱う「状態のみを持つモデル」に従い、振る舞いは Service 側へ置く |
| 2 | **テーブル1つに Entity 1つ**、テーブル名の単数形で作る（§5）。列は [tech_db.md](../../tech/basic/tech_db.md) の定義どおりに作る |
| 3 | 可変フィールド + getter/setter を許すのは Entity だけ（[common.md](common.md) §5 #4・#5）。MyBatis がマッピングするため |
| 4 | 集計・結合の結果やテーブルに対応しない検索条件は、Entity を歪めずに**専用の `record`** を `.repository` へ置く（ガイドライン 3.2.4.3 #3「Repository で使う DTO は同じパッケージに配置する」に倣う） |
| 5 | テーブル定義書に無い列・テーブルが必要になったら、実装で先行させず**基本設計へ差し戻す**（[phases.md](../phases.md) §3.2.1） |

## 3. Mapper（`repository`・MyBatis3）

Mapper はガイドラインの O/R Mapper に当たり、Repository インタフェースを介さず Service から直接呼ぶ（[layering.md](layering.md) §3）。

| # | 規約 |
|---|------|
| 1 | SQL は XML に置き、値は `#{}` でバインドする。`${}` による文字列組み立てをしない |
| 2 | Mapper XML は **インタフェースと同名・同パッケージのリソース配下**に置く |
| 3 | **テーブル1つに Mapper 1つ。** ガイドライン 3.2.4.3 #1 は「Repository は主体となる Entity に対して作る（従の Entity 専用は作らない）」としているが、本プロジェクトは Mapper XML をテーブル単位で書き、SQL の所在をテーブル名から一意に引けるようにする |
| 4 | **DB 列は snake_case、Java フィールドは lowerCamelCase**。変換は MyBatis の `map-underscore-to-camel-case: true` に任せ、`<result>` の手書きマッピングを増やさない。列名の正は [tech_db.md](../../tech/basic/tech_db.md) |
| 5 | 取得は N+1 を作らない（JOIN・一括取得） |
| 6 | Mapper に業務分岐・計算を持ち込まない（SQL の条件式として自然に書けるもの以外は Service へ） |
| 7 | Mapper から Service を呼ばない（依存の逆流。[common.md](common.md) §2） |

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

- Mapper メソッド名は、ガイドライン 3.2.4.5 の Repository 命名（`findById`・`findAll`・`save`・`delete`・`findBy<フィールド>` など）とは**別体系**（逸脱 #6）。Mapper は永続化を隠す抽象ではなく SQL の窓口であり、**発行される SQL 種別が名前から分かる**ほうがレビューしやすいため
- 共通の命名（クラス・メソッド・定数・例外・パッケージ）は [common.md](common.md) §3
