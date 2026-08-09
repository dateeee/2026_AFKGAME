# バックエンドコーディング規約 — ドメイン層のデータ（`afkgame-domain`）

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。全層共通の規約は [common.md](common.md) が先、層の位置づけは [layering.md](layering.md)。
> **Service は [domain_service.md](domain_service.md) が正**。本書は Entity・Repository・マスターデータを扱う。
> ベースはガイドライン `ImplementationAtEachLayer/DomainLayer`（3.2.1〜3.2.4）と `InfrastructureLayer`（MyBatis3）。本書はそこからの差分だけを持つ（準拠元は [basis.md](basis.md) §1）。

---

## 1. 対象と責務

ドメイン層は**アプリケーション層に業務ロジックを提供する**層で、実装は「Entity」「業務データを操作するデータアクセス」「Service」の3つに分かれる（ガイドライン 3.2.1）。本書は前2者を扱う。

| パッケージ | 置くもの | ガイドライン上の分類 |
|-----------|---------|------------------|
| `.model` | Entity（永続化の器） | Domain Object |
| `.repository` | Repository インタフェース + 同名・同パッケージのマッピング XML | Repository（インタフェースはドメイン層、XML はインフラストラクチャ層。[layering.md](layering.md) §3） |
| `.service` | ビジネスロジック | Service（[domain_service.md](domain_service.md)） |
| `.masterdata` | マスターデータの `record` と YAML ローダ | （対応なし） |
| `.rng` | 乱数（`RandomFactory`） | （対応なし） |
| `.exception` | ビジネス例外（`AppException`）・システム例外（`SystemException`） | ビジネス例外・システム例外（[exception.md](exception.md) §2） |

- **Web 層の型を持ち込まない**（Spring MVC・`jakarta.servlet`・`HttpStatus`）。依存してよいのは `afkgame-env` だけ（[common.md](common.md) §2）
- ドメイン層は HTTP・画面の都合を知らない。API の形に合わせた戻り値の整形は Web 層（[web.md](web.md) §3）で行う

## 2. Entity（`model`）

| # | 規約 |
|---|------|
| 1 | Entity は**永続化の器**。ロジック・導出プロパティを持たない。ガイドラインが扱う「状態のみを持つモデル」に従い、振る舞いは Service 側へ置く |
| 2 | **テーブル1つに Entity 1つ**、テーブル名の単数形で作る（§5）。列は [tech_db.md](../../tech/basic/tech_db.md) の定義どおりに作る |
| 3 | 可変フィールド + getter/setter を許すのは Entity だけ（[common.md](common.md) §5 #4・#5）。MyBatis がマッピングするため |
| 4 | 集計・結合の結果やテーブルに対応しない検索条件は、Entity を歪めずに**専用の `record`** を `.repository` へ置く（ガイドライン 3.2.4.3 #3「Repository で使う DTO は Repository インタフェースと同じパッケージに配置する」） |
| 5 | テーブル定義書に無い列・テーブルが必要になったら、実装で先行させず**基本設計へ差し戻す**（[phases.md](../phases.md) §3.2.1） |

## 3. Repository（`repository`・MyBatis3）

Repository は Service へ Entity のライフサイクル操作を提供するドメイン層のインタフェースで、実装は MyBatis3 が Mapper インタフェースの仕組みで生成する。書くのは**インタフェースとマッピング XML の2つだけ**（RepositoryImpl は書かない。[layering.md](layering.md) §3）。

| # | 規約 |
|---|------|
| 1 | SQL は XML に置き、値は `#{}` でバインドする。`${}` による文字列組み立てをしない |
| 2 | マッピング XML は **インタフェースと同名・同パッケージのリソース配下**に置き、`namespace` にインタフェースの FQCN を書く |
| 3 | **Repository は主体となる Entity に対して作る**（ガイドライン 3.2.4.3 #1）。従の Entity 専用の Repository は作らず、主体の Repository のメソッドとして持たせる（`player_settings`・`inventory_items` は `PlayerRepository`、`character_equip_slots` は `CharacterRepository`）。**テーブル単位では作らない** |
| 4 | **Service へ永続化技術を漏らさない**。`SqlSession` や MyBatis の型を引数・戻り値に出さず、Entity・`record`・プリミティブだけでやり取りする |
| 5 | **DB 列は snake_case、Java フィールドは lowerCamelCase**。変換は MyBatis の `map-underscore-to-camel-case: true` に任せ、`<result>` の手書きマッピングを増やさない。列名の正は [tech_db.md](../../tech/basic/tech_db.md) |
| 6 | 取得は N+1 を作らない（JOIN・一括取得） |
| 7 | Repository に業務分岐・計算を持ち込まない（SQL の条件式として自然に書けるもの以外は Service へ） |
| 8 | Repository から Service を呼ばない（依存の逆流。[common.md](common.md) §2） |
| 9 | Entity を持たない DB アクセス（疎通確認など）は #3 の対象外。`<用途>Repository` として作ってよい（`HealthRepository`） |

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
| Repository | `<主体Entity>Repository`。XML は**同名・同パッケージのリソース配下** | `UserRepository.java` ↔ `com/afkgame/domain/repository/UserRepository.xml` |
| Repository メソッド | ガイドライン 3.2.4.5.2 に従う。1件取得 `findBy<条件>` / 複数件取得 `findAllBy<条件>` / 存在確認 `existsBy<条件>` / 登録 `save` / 更新 `updateBy<条件>` / 削除 `deleteBy<条件>` | `findById`・`findAllByPlayerId`・`updateRevokedByUserId` |
| Service | `<領域>Service` | `AuthService` |

- **発行される SQL 種別を名前に出さない**（`selectById` ではなく `findById`）。Service に SQL を意識させないことが Repository を挟む目的（§3・[layering.md](layering.md) §3）
- 主体以外の Entity を扱うメソッドは、動詞と `By` の間に対象の Entity 名を入れて区別する（`findSettingsByPlayerId`・`findAllItemsByPlayerId`・`saveEquipSlot`）
- 共通の命名（クラス・メソッド・定数・例外・パッケージ）は [common.md](common.md) §3
