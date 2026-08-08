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
| ドメイン層 | 業務ロジックと、その対象となる業務データ。トランザクション境界を宣言する | `afkgame-domain` の `.model` / `.service` |
| インフラストラクチャ層 | 業務データの永続化と外部システム連携 | `afkgame-domain` の `.repository`（MyBatis3） |

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
| Repository | Domain Object の CRUD を担うインタフェース（ドメイン層に定義のみを置く） | **作らない**（§3） |
| Service | 業務処理の提供とトランザクション境界の宣言。**Form・`HttpServletRequest` など Web の情報を扱わない** | `domain.service`（[domain_service.md](domain_service.md)） |
| RepositoryImpl | Repository インタフェースの実装 | **なし**（§3） |
| O/R Mapper | DB と Entity の相互マッピング。MyBatis3 では Mapper インタフェースと `SqlSession` が該当する | `domain.repository` の Mapper インタフェース + XML（[domain.md](domain.md) §3） |
| Integration System Connector | DB 以外のデータストア（KVS・Web サービス・外部システム）との連携 | **なし**（外部連携を持たない。持つことになったら本表へ追記してから作る） |

## 3. Repository を作らない構成

ガイドラインは Repository インタフェース（ドメイン層）+ RepositoryImpl（インフラストラクチャ層）を推奨するが（2.4.1.2.2）、**データアクセスの抽象化が必要ないなら Repository を作らず、Service から O/R Mapper を直接呼んでよい**とも明記している（2.4.2.2）。本プロジェクトは後者を採る（ガイドラインが認める構成のため差分ではない）。

| # | 理由 |
|---|------|
| 1 | 永続化技術を差し替える計画がない（PostgreSQL + MyBatis3 固定。[tech_selection.md](../../backlog/java_migration/tech_selection.md) §2） |
| 2 | SQL は Mapper XML へ外出しされており、Mapper インタフェースが既に永続化の詳細を隠している。抽象化をもう一段重ねても隠れるものが増えない |
| 3 | シングルプレイ専用の小規模構成で、ガイドラインが Repository の利点として挙げる「複数体制でのデータアクセス共通化」が発生しない |

**コンポーネント間の呼び出し可否**（ガイドライン 2.4.2.2 の表を本プロジェクトの実体名にしたもの）。これが層の依存の正。

| 呼ぶ側 \ 呼ばれる側 | `web.api`（Controller） | `domain.service`（Service） | `domain.repository`（Mapper） |
|---|---|---|---|
| `web.api`（Controller） | × | ○ | **×** |
| `domain.service`（Service） | × | **△** | ○ |

- **○** 可 / **×** 禁止 / **△** 原則禁止（許す条件と書き方は [domain_service.md](domain_service.md) §2）
- Controller から Mapper を直接呼ぶと**トランザクション境界が Web 層へ漏れる**。参照系でも Service を通す
- Mapper から Service を呼ばない（逆流。[common.md](common.md) §2）

## 4. モジュール構成の対応

ガイドラインのマルチプロジェクト構成（2.4.3）と本プロジェクトの対応。

| ガイドライン | 本プロジェクト | 成果物 | 備考 |
|------------|--------------|-------|------|
| `[projectName]-domain` | `afkgame-domain` | jar | ドメイン層 + インフラストラクチャ層（§1） |
| `[projectName]-web` | `afkgame-web` | war | アプリケーション層。Tomcat へデプロイする |
| `[projectName]-env` | `afkgame-env` | jar | 環境依存の設定を集約し、環境ごとのビルドし直しを避けるための層 |
| （対応なし） | `afkgame-initdb` | — | Flyway マイグレーション SQL |

パッケージの割り当てと依存方向は [common.md](common.md) §2、ディレクトリツリーは [tech_structure.md](../../tech/basic/tech_structure.md) §2 が正。
