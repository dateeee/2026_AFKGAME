# 単体テスト — プロジェクト固有プロファイル

> 一般手順は [.claude/skills/unit-test/SKILL.md](../skills/unit-test/SKILL.md) を参照。本書は AFK GAME 固有の値のみを持つ。
> テストの書き方・フィクスチャは [test-list.md](test-list.md)、実装パターンは [test-patterns.md](test-patterns.md)。

## 1. 前提

| 項目 | 値 |
|------|-----|
| 対象 | `@Tag("unit")` を付けたテスト（`afkgame-env`・`afkgame-domain`・`afkgame-web`）。Service・Controller・フィルタ・マスターデータが主 |
| 対象外 | `@Tag("integration")`（Repository・`@SpringBootTest`）。**カバレッジの分母にも入らない**（下記「実行の分離」） |
| 配置 | 各モジュールの `src/test/java/.../<対象クラス>Test.java` |
| 設定 | 各モジュールの `pom.xml`（`jacoco-maven-plugin`。branch カバレッジ・しきい値100%を設定） |
| 実行の分離 | `backend/pom.xml` の surefire が `integration` タグを除外し、failsafe が `integration` タグだけを `integration-test` フェーズで回す。failsafe 側は `argLine` を上書きして JaCoCo agent を外しているため、**C1 は単体テストだけで測られる** |
| レポート | `mvn verify` のターミナル出力と `target/site/jacoco/index.html`（未実行行=赤、部分分岐=黄） |
| カバレッジ基準 | **C1（分岐網羅）100%** |
| フロントエンド | 対象外。単体レベルの検証は結合テスト（Playwright）に統合し、型検証は `vue-tsc` を製造工程で実施 |

## 2. コマンド

| 目的 | コマンド |
|------|---------|
| 現状測定 | `cd backend && mvn verify -DskipITs`（単体のみ実行 + JaCoCo レポート生成。**`mvn test` では `target/site/jacoco/index.html` は作られない**。`report` は verify フェーズにバインドしてある） |
| クラスを絞る | `mvn test -Dtest=<対象クラス>Test`（速い確認用。HTML レポートも C1 判定も行われない） |
| 結合テストも含めて回す | `cd backend && mvn verify` |
| 完了判定 | `cd backend && mvn verify`（JaCoCo の branch カバレッジしきい値100%で判定。exit 0 が条件） |

## 3. 固有の分岐観点

一般的な構文別チェックリストは [.claude/skills/unit-test/references/c1_checklist.md](../skills/unit-test/references/c1_checklist.md)。以下は AFK GAME でのみ必要な観点。

| 領域 | 押さえる分岐 |
|------|------------|
| 乱数 | ダメージ分散の上振れ/下振れ/中央、クリティカル発生/非発生、ドロップ有/無、エンカウント抽選。`RandomFactory` 由来の `nextDouble() < rate` は **rate 未満と rate 以上の両方**を固定して通す |
| 境界値 | `>=` と `>` の境界そのものの値、0、負値、上限クランプ（例: `target_floor_cap` の `highest == total_floors`） |
| マスターデータ | 既知ID / **未知ID**（`null` またはフォールバック）、空リスト、有限塔 `total_floors` あり と無限塔 `null` |
| DB状態 | レコードなし（初回） / あり、塔別に独立していること、複数レコードでの絞り込み |
| 認証 | ゲスト / 正規ユーザー、トークンなし / 期限切れ / 署名不正 |
| tick・オフライン | `TURNS_PER_TICK` 内で決着 / 決着せず持ち越し、経過0tick / 1tick / 大量tick、上限クランプ |
| 戦闘 | 先攻/後攻（SPD比較の同値含む）、HP がちょうど0、最低ダメージのクランプ（プレイヤー1 / 敵0）、全滅、ボス/通常敵 |
| 回復・在庫 | ポーション閾値の境界（`hp/max == threshold`）、在庫あり / 0個 |
| 経済 | gold 不足 / ちょうど / 十分、インベントリ上限、自動売却 有効/無効 |
| API（`afkgame-web` の `api/`） | 正常系 200 + **各エラーコードごと**の 4xx、Bean Validation エラー、権限違反 |

## 4. 除外規則

除外は各モジュール `pom.xml` の `jacoco-maven-plugin` の `<configuration><excludes>` で行い、**理由コメント必須**。許容されるのは分岐を持たない起動・設定クラスと、実行環境依存で再現できない例外ハンドラのみ。

```xml
<configuration>
  <excludes>
    <!-- 除外する理由をこの行に書く（分岐を持たない起動・設定クラス等） -->
    <exclude>com/afkgame/web/config/&lt;クラス名&gt;.class</exclude>
  </excludes>
</configuration>
```

上は書式の例で、**現時点の除外指定は0件**（`afkgame-domain`・`afkgame-web` が branch 100%。`afkgame-env` は単体テスト未整備でゲート対象外）。`@Generated` が付いた自動生成コードは JaCoCo が自動的に除外するため、手書きの指定は要らない。

カバレッジを通すためだけに除外を足すことは**禁止**（新規に足す場合は理由コメントを必須とし、完了報告で明示する）。

## 5. 現在の整備状況

**単体テストゲート通過（2026-08-02）**: `app/` 全モジュール C1 100% 達成済み。現況の件数・分岐数はテスト実行結果（`mvn verify` の JaCoCo レポート）を正とする（本書へ転記しない）。

| # | ルール |
|---|-------|
| 1 | 実装済み（Phase 1〜2）のテストは遡及整備で C1 100% に到達済みのため**書き直さない** |
| 2 | 本工程で補完した分岐は、[detail-design.md](detail-design.md) §4 の分岐一覧へ**逆反映**する |
| 3 | 検出した実装の疑義（仕様乖離・デッドコード等）は `docs/backlog/known_issues.md` へ記録する |

## 6. 完了基準

一般スキルの完了基準に加え、以下を満たすこと。

- 全テスト PASS かつ **C1カバレッジ100%**（`mvn verify` が exit 0。JaCoCo の branch カバレッジしきい値100%）
- 補完した分岐が詳細設計の分岐一覧へ反映されている
- 検出した実装の疑義が `docs/backlog/known_issues.md` に登録されている

## 7. 次工程

| 次にやること | 手段 |
|------------|------|
| 結合テストへ | `integration-test` スキル |

## 8. Terasoluna 単体テストガイドラインとの差分

**正は [coding_standards_backend/test.md](../../docs/process/coding_standards_backend/test.md) §5**（Boot 流儀への読み替え・意図して採らない項目・新規実装から適用するルール）。本書では再掲しない。
