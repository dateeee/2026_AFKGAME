# バックエンドコーディング規約 — ドメイン層の Service（`afkgame-domain`）

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。全層共通の規約は [common.md](common.md) が先、Entity・Mapper は [domain.md](domain.md)、層の位置づけは [layering.md](layering.md)。
> ベースはガイドライン `ImplementationAtEachLayer/DomainLayer`（3.2.5〜3.2.7）。本書はそこからの差分だけを持つ（準拠元は [basis.md](basis.md) §1、逸脱番号は同 §3）。

---

## 1. Service の役割

Service は次の2つを担う（ガイドライン 3.2.5.1）。

| # | 役割 |
|---|------|
| 1 | **Controller に業務ロジックを提供する。** 業務データの参照・更新は Mapper へ委譲し、Service は**ビジネスルールに関わる処理に専念する** |
| 2 | **トランザクション境界を宣言する。** 境界は原則 Service に置く（§4）。Web 層に境界があるのは業務ロジックの抽出漏れの兆候なので、見直す |

**Controller と Service の責任分界点**（ガイドライン 3.2.5.1 の Note）。

| 処理 | 担当 |
|------|------|
| リクエストデータの単項目チェック・相関項目チェック | Controller（Bean Validation。[web.md](web.md) §2） |
| Service へ渡すデータへの変換（型変換・形式変換・Bean 変換） | Controller（Resource。[web.md](web.md) §3） |
| **ビジネスルールに関わる処理**と、そのための業務データへのアクセス | **Service** |
| Service が返したデータのレスポンス向け変換 | Controller（Resource） |

- Service は **Form・`HttpServletRequest` など Web に関わる情報を扱わない**（ガイドライン 2.4.1.2.3）。必要な値はドメインの型・プリミティブで受ける
- ビジネスルール違反を項目単位のエラーとして返したい場合も、**チェックロジック自体は Service に置いて Web 層から呼ぶ**（ガイドライン 3.2.7.1）

## 2. クラス構成と Service 間の呼び出し

ガイドラインは、再利用しないロジックを持つ **Service クラス**と、再利用されるロジックを持つ **SharedService クラス**を分け、Service から他の Service を呼ぶことを原則禁止している（3.2.5.2）。

**本プロジェクトはクラスを分けない**（逸脱 #3）。Service が数クラス規模で、二分しても「再利用してよいメソッドか」の判別はクラス名頼りのまま変わらないため、**共有される Service を Javadoc と伝播属性で示す**方式を採る。ガイドラインが避けたい「再利用の可否が読めない状態」と「トランザクションの入れ子が見えない状態」は #1〜#4 で担保する。

| # | 規約 |
|---|------|
| 1 | 複数の Service から呼ばれる Service は、クラス Javadoc に `<p>共有 Service。<呼び出し元>から利用する。` を書く（例: `JwtService`・`PlayerInitializationService`） |
| 2 | **共有 Service は他の Service を呼ばない。** Service 間の呼び出しは1段まで（循環と、境界の入れ子が追えなくなる状態を作らない。ガイドライン 3.2.5.2.2 の趣旨） |
| 3 | 共有 Service の public メソッドには**伝播属性を明示する**。呼び出し元の境界に含めるなら `@Transactional(propagation = Propagation.MANDATORY)`、独立させるなら `REQUIRES_NEW` を書き、理由を Javadoc に添える（§4 #5・#7） |
| 4 | 共有でない Service を他の Service から呼びたくなったら、**呼ぶ前に共有 Service へ格上げする**（#1〜#3 を満たす）。黙って呼ばない |

## 3. クラスとメソッドの作り方

| # | 規約 |
|---|------|
| 1 | `@Service` を付けた**具象クラスのみ**を作り、Service インタフェースを設けない（逸脱 #2）。ガイドラインがインタフェースを推奨する理由は AOP のプロキシ方式とスタブ化だが、Spring Boot は CGLIB プロキシで足り、テストは Mockito が具象クラスをモックできるため |
| 2 | 依存は**コンストラクタ注入**で `private final`（[common.md](common.md) §4 #1）。ガイドラインの例はフィールド `@Inject` だが、本プロジェクトはコンストラクタ注入に統一する |
| 3 | 引数・戻り値に `Serializable` を課さない（逸脱 #4）。単一 war で分散デプロイをしないため |
| 4 | 複数の値を返すときは `.service` に不変の `record` を置く（例: `AuthResult`）。Entity をそのまま返してもよい |
| 5 | 計算式・判定のしきい値は詳細設計が正。Service に数値を直書きしない（[common.md](common.md) §5 #10） |
| 6 | 現在時刻は `Clock`、乱数は `RandomFactory` を注入して受ける（[common.md](common.md) §4 #2） |

## 4. トランザクション管理

宣言型トランザクション管理（`@Transactional`）を使う（ガイドライン 3.2.6.1）。

| # | 規約 |
|---|------|
| 1 | 境界は **Service の public メソッド**。Mapper・Controller・フィルタに `@Transactional` を付けない |
| 2 | **クラスではなくメソッドに付ける。** ガイドラインはクラス既定 + メソッド上書きを例示するが、本プロジェクトは境界を持つメソッドが限られるため、**付いているメソッドだけが境界**と読める形にする |
| 3 | 複数 Mapper をまたぐ更新は1メソッドに閉じる |
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
| 2 | 名前は `<領域>Service`（[domain.md](domain.md) §5）。Phase をまたいで機能が増えるときは Service を分けて足す（既存 Service を肥大させない） |

## 6. 例外とメッセージ

ガイドラインは業務例外 `BusinessException`、システム例外 `SystemException`、メッセージオブジェクト `ResultMessages`（`terasoluna-gfw`）を用意し、要件を満たせない場合はプロジェクトで作ることを認めている（3.2.5.6.4・3.2.5.6.5）。**本プロジェクトは `AppException` に一本化する**（逸脱 #5）。HTTP ステータスを持たせて Web 層での変換を1か所にまとめられるため。

| ガイドライン | 本プロジェクト | 使う場面 |
|------------|--------------|---------|
| `BusinessException` + `ResultMessages` | `AppException(code, message, status)` | ビジネスルール違反をクライアントへ通知する。HTTP ステータスは `int` で保持し、応答への変換は Web 層（[web.md](web.md) §5） |
| `SystemException` | `IllegalStateException` / `MasterDataException` | 事前に存在するはずのマスターデータ・設定が無いなど、クライアントが対処できない異常 |
| `ResultMessages`（警告メッセージ） | 戻り値の `record` に載せる | 処理は成功するが注意を促す場合。例外にしない（ガイドライン 3.2.5.6.3 と同じ「戻り値で返す」方式） |

- どちらも `RuntimeException` 派生にする。`@Transactional` の既定ロールバック対象に載せるためで、ガイドライン 3.2.5.6.4 の Note と同じ理由。検査例外を新設しない（[common.md](common.md) §6 #2）
- Service は**メッセージ文言を解決しない**。持つのは**エラーコードと埋め込み値だけ**（ガイドライン 3.2.5.6.2 の考え方）。文言はフロントエンドが持ち、コード体系の正は [tech_logging.md](../../tech/basic/tech_logging.md)
