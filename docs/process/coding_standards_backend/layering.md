# バックエンドコーディング規約 — アプリケーションのレイヤ化

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。**新しいクラスの置き場・呼び出し方向に迷ったとき**に読む。
> ベースはガイドライン `Overview/ApplicationLayering`（2.4）。本書はその要点と、本プロジェクトへの対応づけ・差分を持つ（準拠元は [basis.md](basis.md) §1）。
> モジュール名・パッケージ・依存方向の具体は [common.md](common.md) §2 が正。

---

## 1. 3レイヤの定義

ガイドラインはアプリケーションを3レイヤに分ける。**アプリケーション層とインフラストラクチャ層はドメイン層に依存してよいが、ドメイン層は他の層に依存してはいけない。**

| レイヤ | 責務 | 本プロジェクトの実体 |
|--------|------|-------------------|
| アプリケーション層 | クライアントとのデータ入出力の制御（リクエストハンドリング、入力データの妥当性チェック、ドメイン層の呼び出し）。**実装はできるだけ薄く保ち、ビジネスルールを含めない** | `afkgame-web` |
| ドメイン層 | 業務ロジックと、その対象となる業務データ。トランザクション境界を宣言する | `afkgame-domain` の `.model` / `.service` と `.repository` の Repository インタフェース |
| インフラストラクチャ層 | 業務データの永続化と外部システム連携 | `afkgame-domain` の `.repository` のマッピング XML（MyBatis3） |

- ドメイン層の変更でアプリケーション層が変わるのはよいが、**逆は起こしてはならない**
- ドメイン層の実装は「Entity → データアクセス → Service」の順に作る（ガイドライン 3.2.1）。Entity の起点はテーブル定義書（[phases.md](../phases.md) §3.2.1）
- インフラストラクチャ層は Java のパッケージとしてはドメイン層と同居させる（ガイドライン 3.2.4.3 #2 と同じ扱い）。**モジュールを分けない**

## 2. コンポーネントと本プロジェクトでの担当

| ガイドラインのコンポーネント | 役割 | 本プロジェクト |
|---------------------------|------|--------------|
| Controller | ルーティング（URL マッピングと応答の返却） | `web.api` の `<リソース>Api` |
| View | 画面の描画 | **なし**（ガイドライン 2.4.1.1.2 との差分。SPA のため描画は Vue 3 が担い、返すのは JSON だけ） |
| Form | 入出力データの表現と入力チェックルールの宣言（Bean Validation） | `web.resource` の `<用途>Resource`（ガイドライン 2.4.1.1.3 Tip: REST では `Resource` が Form の役割を担い、変換は `HttpMessageConverter` が行う） |
| Helper | Controller を補助する POJO。作成は任意で、Controller の一部として扱ってよい | **作らない**（ガイドライン 2.4.1.1.3・2.4.1.1.4 との差分）。Resource ↔ ドメイン型の変換は Resource の `static from(...)` に集約する（理由は [web.md](web.md) §3 #3） |
| Domain Object | 業務データを表すモデル。Entity はこれに含まれる。**状態のみを持つ**（振る舞いは持たせない） | `domain.model` の Entity（[domain.md](domain.md) §2） |
| Repository | Domain Object の CRUD を担うインタフェース（ドメイン層に定義のみを置く） | `domain.repository` の `<主体Entity>Repository`（§3・`domain.md` §3） |
| Service | 業務処理の提供とトランザクション境界の宣言。**Form・`HttpServletRequest` など Web の情報を扱わない** | `domain.service` の `<領域>Service`（インタフェース）+ `<領域>ServiceImpl`（[domain/service.md](domain/service.md) §3 #1） |
| RepositoryImpl | Repository インタフェースの実装 | **書かない**（§3）。MyBatis3 が Mapper インタフェースの仕組みで実装を生成するため不要（ガイドライン 3.3.1.1。差分ではない） |
| O/R Mapper | DB と Entity の相互マッピング。MyBatis3 では Mapper インタフェースと `SqlSession` が該当する | MyBatis3 の `SqlSession` と、Repository と同名・同パッケージのマッピング XML（`domain.md` §3） |
| Integration System Connector | DB 以外のデータストア（KVS・Web サービス・外部システム）との連携 | **なし**（外部連携を持たない。持つことになったら本表へ追記してから作る） |

## 3. Repository でデータアクセスを抽象化する

ガイドラインは Repository インタフェース（ドメイン層）+ RepositoryImpl（インフラストラクチャ層）でデータアクセスを抽象化する（2.4.1.2.2・3.2.4.2）。**本プロジェクトはこれに従い、Service は Repository インタフェースだけを見る**。Repository を作らず Service から O/R Mapper を直接呼ぶ構成（2.4.2.2）は採らない。

抽象化の目的は永続化技術の差し替えではなく、**業務データへのアクセス操作を Repository へ分離して、Service をビジネスルールの実装に専念させること**（ガイドライン 3.2.4.2 Warning）。SQL・永続先の都合は Repository 側へ寄せる。

MyBatis3 を使うため **RepositoryImpl は書かない**。Repository インタフェースを Mapper インタフェースの仕組みで作れば実装は自動生成されるので、作るのは次の2つだけ（ガイドライン 3.3.1.1）。

| 作るもの | 実体 | 属する層 |
|---------|------|---------|
| Repository インタフェース（メソッド定義） | `domain.repository` の `<主体Entity>Repository` | ドメイン層 |
| マッピングファイル（SQL と O/R マッピング） | 同名・同パッケージの `<主体Entity>Repository.xml` | インフラストラクチャ層 |

Java のパッケージとしては両者をドメイン層に同居させる（ガイドライン 3.2.4.3 #2。§1 の「モジュールを分けない」と同じ扱い）。作成単位・命名・SQL の書き方は `domain.md` §3・§5 が正。

**コンポーネント間の呼び出し可否**（ガイドライン 2.4.2.1 の表を本プロジェクトの実体名にしたもの）。これが層の依存の正。

| 呼ぶ側 \ 呼ばれる側 | `web.api`（Controller） | `domain.service`（Service） | `domain.repository`（Repository） |
|---|---|---|---|
| `web.api`（Controller） | × | ○ | **×** |
| `domain.service`（Service） | × | **△** | ○ |

- **○** 可 / **×** 禁止 / **△** 原則禁止（許す条件と書き方は `domain/service.md` §2）
- Controller から Repository を直接呼ぶと**トランザクション境界が Web 層へ漏れる**。参照系でも Service を通す
- Repository から Service を呼ばない（逆流。`common.md` §2）

## 4. モジュール構成の対応

ガイドラインのマルチプロジェクト構成（2.4.3）と本プロジェクトの対応。

| ガイドライン | 本プロジェクト | 成果物 | 備考 |
|------------|--------------|-------|------|
| `[projectName]-domain` | `afkgame-domain` | jar | ドメイン層 + インフラストラクチャ層（§1） |
| `[projectName]-web` | `afkgame-web` | war | アプリケーション層。Tomcat へデプロイする |
| `[projectName]-env` | `afkgame-env` | jar | 環境依存の設定を集約し、環境ごとのビルドし直しを避けるための層 |
| （対応なし） | `afkgame-initdb` | — | Flyway マイグレーション SQL |

パッケージの割り当てと依存方向は `common.md` §2、ディレクトリツリーは [tech_structure_backend.md](../../tech/basic/tech_structure_backend.md) §4.1 が正。
