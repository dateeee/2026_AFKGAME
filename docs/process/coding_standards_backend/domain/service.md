# バックエンドコーディング規約 — ドメイン層の Service（`afkgame-domain`）

> [coding_standards_backend.md](../../coding_standards_backend.md) の分冊。全層共通の規約は [common.md](../common.md) が先、Entity・Repository は [domain.md](../domain.md)、層の位置づけは [layering.md](../layering.md)。
> ベースはガイドライン `ImplementationAtEachLayer/DomainLayer`（3.2.5〜3.2.7）。本書はそこからの差分だけを持つ（準拠元は [basis.md](../basis.md) §1）。

---

## 1. Service の役割

Service は次の2つを担う（ガイドライン 3.2.5.1）。

| # | 役割 |
|---|------|
| 1 | **Controller に業務ロジックを提供する。** 業務データの参照・更新は Repository へ委譲し、Service は**ビジネスルールに関わる処理に専念する** |
| 2 | **トランザクション境界を宣言する。** 境界は原則 Service に置く（§4）。Web 層に境界があるのは業務ロジックの抽出漏れの兆候なので、見直す |

**Controller と Service の責任分界点**（ガイドライン 3.2.5.1 の Note）。

| 処理 | 担当 |
|------|------|
| リクエストデータの単項目チェック・相関項目チェック | Controller（Bean Validation。[web.md](../web.md) §2） |
| Service へ渡すデータへの変換（型変換・形式変換・Bean 変換） | Controller（Resource。`web.md` §3） |
| **ビジネスルールに関わる処理**と、そのための業務データへのアクセス | **Service** |
| Service が返したデータのレスポンス向け変換 | Controller（Resource） |

- Service は **Form・`HttpServletRequest` など Web に関わる情報を扱わない**（ガイドライン 2.4.1.2.3）。必要な値はドメインの型・プリミティブで受ける
- ビジネスルール違反を項目単位のエラーとして返したい場合も、**チェックロジック自体は Service に置いて Web 層から呼ぶ**（ガイドライン 3.2.7.1）

## 2. クラス構成と Service 間の呼び出し

ガイドラインは、再利用しないロジックを持つ **Service クラス**と、再利用されるロジックを持つ **SharedService クラス**を分け、Service から他の Service を呼ぶことを原則禁止している（3.2.5.2）。

**本プロジェクトはクラスを分けない**。Service が数クラス規模で、二分しても「再利用してよいメソッドか」の判別はクラス名頼りのまま変わらないため、**共有される Service を Javadoc と伝播属性で示す**方式を採る。ガイドラインが避けたい「再利用の可否が読めない状態」と「トランザクションの入れ子が見えない状態」は #1〜#4 で担保する。

| # | 規約 |
|---|------|
| 1 | 複数の Service から呼ばれる Service は、**インタフェースの**クラス Javadoc に `<p>共有 Service。<呼び出し元>から利用する。` を書く（例: `JwtService`・`PlayerInitializationService`） |
| 2 | **共有 Service は他の Service を呼ばない。** Service 間の呼び出しは1段まで（循環と、境界の入れ子が追えなくなる状態を作らない。ガイドライン 3.2.5.2.2 の趣旨） |
| 3 | 共有 Service の public メソッドには**伝播属性を明示する**。呼び出し元の境界に含めるなら `@Transactional(propagation = Propagation.MANDATORY)`、独立させるなら `REQUIRES_NEW` を実装クラス側へ書き（§4 #2）、**要否と理由はインタフェースのメソッド Javadoc**に添える（呼び出し側が読むのはインタフェースのため。§4 #5・#7） |
| 4 | 共有でない Service を他の Service から呼びたくなったら、**呼ぶ前に共有 Service へ格上げする**（#1〜#3 を満たす）。黙って呼ばない |

## 3. クラスとメソッドの作り方

| # | 規約 |
|---|------|
| 1 | **インタフェースと実装クラスの2つを作る**（ガイドライン 3.2.5.4.1）。インタフェースは `<領域>Service`、実装は**同じパッケージ**の `<領域>ServiceImpl` で、`@Service` は**実装クラスに付ける**（インタフェースには付けない）。Controller・フィルタ・他の Service が参照する型は**インタフェースだけ**にする |
| 2 | **公開する Javadoc はインタフェースが持つ**（仕様の参照先・`@param`・`@return`・`@throws` とエラーコード）。実装クラスは `@Override` を付け、**実装上の注記だけ**を Javadoc に書く（インタフェースの記述を写さない） |
| 3 | 依存は**コンストラクタ注入**で `private final`（`common.md` §4 #1）。ガイドラインの例はフィールド `@Inject` だが、本プロジェクトはコンストラクタ注入に統一する |
| 4 | 引数・戻り値に `Serializable` を課さない（ガイドライン 3.2.5.4.3 との差分）。単一 war で分散デプロイをしないため |
| 5 | 複数の値を返すときは `.service` に不変の `record` を置く（例: `AuthResult`）。Entity をそのまま返してもよい |
| 6 | 計算式・判定のしきい値は詳細設計が正。Service に数値を直書きしない（`common.md` §5 #10） |
| 7 | 現在時刻は `Clock`、乱数は `RandomFactory` を注入して受ける（`common.md` §4 #2） |
| 8 | メール送信など**外部への送信境界も同じ形**（インタフェース + `Impl`）で作る。`.service` に置き、実装差し替えとテストのスタブ化をインタフェースで受ける（例: `VerificationMailSender`） |

## 4. トランザクション管理

宣言型トランザクション管理（`@Transactional`）を使う（ガイドライン 3.2.6.1）。

| # | 規約 |
|---|------|
| 1 | 境界は **Service の public メソッド**。Repository・Controller・フィルタに `@Transactional` を付けない |
| 2 | **実装クラス（`<領域>ServiceImpl`）のメソッドに付ける。** インタフェースには付けない（Spring の推奨。CGLIB プロキシではインタフェースの属性が読まれず、プロキシ方式の違いで境界が変わるため）。ガイドラインはクラス既定 + メソッド上書きを例示するが、本プロジェクトは境界を持つメソッドが限られるため、**付いているメソッドだけが境界**と読める形にする |
| 3 | 複数 Repository をまたぐ更新は1メソッドに閉じる |
| 4 | 参照だけのメソッドに境界が要るときは `@Transactional(readOnly = true)`（ガイドライン 3.2.5.4.2） |
| 5 | 呼び出し元の境界に必ず含めたい共有 Service のメソッドは `Propagation.MANDATORY`（境界が無ければ例外になり、付け忘れに気づける） |
| 6 | 失敗時にロールバックさせたくない副作用（不正検知による失効など）は `@Transactional(noRollbackFor = ...)` を明示し、理由を Javadoc に書く |
| 7 | `REQUIRES_NEW` は親トランザクションを中断させる（ガイドライン 3.2.6.1.3）。使うときは中断してよい理由を Javadoc に書く |
| 8 | 同一クラス内の自己呼び出しはプロキシを通らず境界が効かない。境界の要るメソッドを内部から呼ばない |

## 5. 作成単位

ガイドラインは Entity 毎 / ユースケース毎 / イベント毎の3通りを挙げ、**業務データ中心に設計する小〜中規模のアプリケーションでは Entity 毎を推奨**している（3.2.5.3）。

| # | 規約 |
|---|------|
| 1 | 原則**主体となる Entity（業務データ）ごとに1 Service**。ユースケース単位の Service は、複数 Entity をまたぐ初期化・集約処理に限って作ってよい（例: `PlayerInitializationService`） |
| 2 | 名前はインタフェースが `<領域>Service`、実装が `<領域>ServiceImpl`（`domain.md` §5）。Phase をまたいで機能が増えるときは Service を分けて足す（既存 Service を肥大させない） |
| 3 | 置き場は**業務領域ごとのサブパッケージ** `com.afkgame.domain.service.<領域>`（`common.md` §2.1）。インタフェース・`Impl`・戻り値の `record` は**同じサブパッケージへ同居**させる（`impl` で切らない。ガイドライン 5.1.6.7 の `domain.service.member` と同じ形）。領域をまたいで呼ぶときは §2 #1 の共有 Service として宣言する |

## 6. 例外とメッセージ

**正は [exception.md](../exception.md)**（3分類・クラスの対応・送出の作法。ガイドライン 3.2.5.6 の業務例外／システム例外／`ResultMessages` との対応もそちらが持つ）。本書では再掲しない。

トランザクションとの関係だけ本書が持つ: ビジネス例外・システム例外はいずれも `RuntimeException` 派生のため `@Transactional` の既定でロールバックされる（ガイドライン 3.2.5.6.4 Note と同じ理由）。ロールバックさせたくない副作用は §4 #6。
