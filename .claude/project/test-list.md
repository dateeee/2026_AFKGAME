# テストリスト作成 — プロジェクト固有プロファイル

> 一般手順は [.claude/skills/test-list/SKILL.md](../skills/test-list/SKILL.md) を参照。本書は AFK GAME 固有の値のみを持つ。
> 共通の値は [profile.md](profile.md)。

## 1. 成果物

| 成果物 | パス | 状態 |
|-------|------|------|
| 単体テストコード | 各モジュールの `src/test/java/.../<対象クラス>Test.java` | 実装前。**全件 FAIL または ERROR** |

## 2. 対象と適用範囲

| 対象 | 適用 |
|------|------|
| `afkgame-domain` の Service | **厳格に適用**（すべての分岐にテストを先に書く） |
| `afkgame-domain` のマスターデータ | **厳格に適用** |
| `afkgame-web` の `@RestController` | MockMvc で先行作成 |
| Entity/Repository・Resource | 定義のみのため副次的 |
| `frontend/` | **対象外**（TDD非適用。`vue-tsc` と結合テストで検証する） |

**適用時期**: TDDは**新規実装から**適用する。Phase 2 の残り（日替わりショップ）と Phase 3〜5 が対象。実装済み（Phase 1〜2）のテストは遡及整備で C1 100% に到達済みのため**書き直さない**。既存機能の修正・リファクタ時は、先にその変更を表すテストを追加してから実装に着手する。

## 3. 入力

[detail-design.md](detail-design.md) §4 の**分岐一覧（単体テスト観点）**。分岐一覧に無いテストを勝手に足さない。

| 順 | 参照先 | 読む範囲 |
|----|--------|---------|
| 1 | `docs/tech/detail/tech_<対象処理>.md` | 分岐一覧のセクションのみ |
| 2 | `docs/data/master/` | テストで使う数値のみ |
| 3 | 共通テストユーティリティ | 一覧は §4（後述）。Java化後の参考実装クラスは STEP 2 骨格構築後に整備する |

**列挙値は定義元を読んでから書く**: 装備スロット・敵ID・塔ID・エラーコード等をテストへ書く前に定義元の節を読む（装備スロットは `docs/tech/basic/tech_db/item.md` §1、用語は `docs/glossary.md`）。**推測で書いて後から照合しない**（実値と食い違うと全ケースの書き直しになる）。

**対象モジュールが未作成の場合**（関数名・データ構造が分岐一覧にない）: `docs/tech/basic/tech_structure.md` の services 一覧を確認する。そこにも無ければ**探索を打ち切り**、テストの docstring で表層（モジュール名・関数シグネチャ）を定義して製造工程へ申し送る。コード側を読み回して表層を推測しない。

## 4. 共通テストユーティリティ

| ユーティリティ | 内容 |
|------------|------|
| `db` | 埋め込み PostgreSQL（`@AutoConfigureEmbeddedDatabase(provider = ZONKY)`）への接続。`@SpringBootTest` ごとにスキーマを作り直す |
| `user` | `test-user` / 非ゲスト |
| `player` | gold=1000、`PlayerSettings(potionThreshold=0.3)`、hp_potion×5、初期キャラを持つ |
| `character` | `player` の初期キャラクター |
| `client` | 認証ヘッダ付与済みの `MockMvc` |
| `towerRecord` | `towerRecord(towerId, highestFloor=0, cleared=false)` で塔別クリア記録を作るファクトリ |

不足するユーティリティはテストクラス内にローカル定義する（共通ユーティリティは全テスト共通のものだけ）。

### Repository のテスト基盤（`afkgame-domain`）

Repository を追加するときは以下を使う（毎回の再調査を避けるための台帳）。テストクラスは Repository 1つに1つ（`<主体Entity>RepositoryTest`）で、従 Entity 分の観点は `@Nested` で分ける。

| 部品 | パス / 内容 |
|------|-----------|
| 基底クラス | `src/test/java/com/afkgame/domain/repository/RepositoryTestSupport.java`（abstract。`@Tag("integration")` + `@SpringBootTest` + `@AutoConfigureEmbeddedDatabase(ZONKY)` + `@Transactional`。`jdbcTemplate`・時刻固定 `FIXED_NOW`・親レコード生成 `givenUser` / `givenPlayer` / `givenCharacter` / `givenEquipment`・`uuid(prefix)` を提供） |
| 起動クラス | `src/test/java/com/afkgame/domain/RepositoryTestApplication.java`（`@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@MapperScan("com.afkgame.domain.repository")`。`afkgame-domain` には本番の起動クラスが無いため、これが最小コンテキストになる） |
| pom のテスト依存 | `afkgame-domain/pom.xml` に `spring-boot-starter-test` と `io.zonky.test:embedded-database-spring-test` を追加済み（`afkgame-web` の設定を持ち込まない） |

## 5. 記述規約

**正は [docs/process/coding_standards_backend/test.md](../../docs/process/coding_standards_backend/test.md)**（配置・タグ・命名・対応マーカー）。本書では再掲しない。

実装パターンは一般形を [.claude/skills/test-list/references/patterns.md](../skills/test-list/references/patterns.md)、AFK GAME のモジュール名・エラーコードを使った実例を [test-patterns.md](test-patterns.md) に置いている。

## 6. 固有の観点

| # | 観点 | 内容 |
|---|------|------|
| 1 | 乱数 | ダメージ分散・ドロップ抽選・エンカウント抽選は `random.seed` またはモックで固定する |
| 2 | 時刻 | オフライン計算・tick進行は現在時刻をモックし、経過時間を確定させる |
| 3 | マスターデータ | 未知IDを渡したときの経路を必ず1件持つ |
| 4 | DB | 未登録レコード（塔記録なし・装備なし）の経路を必ず1件持つ |
| 5 | 認証 | ルーターは「認証あり / なし」の両方を持つ |
| 6 | 境界値 | HP0、最大階、所持金不足、レベル上限、しきい値ちょうど（`<=` か `<` か）を分ける |

## 7. 完了基準

一般スキルの完了基準に加え、以下を満たすこと。

- 分岐一覧の全項目にテストが対応している: `python scripts/check_branch_list.py --tests` が exit 0（マーカーで機械照合）
- 実行して**期待どおりに失敗する**（Red の確認）: `cd backend && mvn test -Dtest=<対象クラス>Test`（対象を限定。既存テスト全体の Green 確認は製造工程で行う）
- 実装を先に書いていない（テストの後追いで書いていない）

## 8. 次工程

| 次にやること | 手段 |
|------------|------|
| 製造へ | `dev` スキル（Red-Green-Refactor を1テストずつ回す） |
