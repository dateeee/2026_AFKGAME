# AFK GAME — バックエンドコーディング規約（Java）索引

> `backend/` の Java 実装が従う規約の**正**。本書は**適用範囲・準拠元・原則・索引**だけを持ち、規約の本体は `coding_standards_backend/` の4分冊（共通 / ドメイン層 / Web層 / テスト）にある。
> フロントエンド（Vue 3 / TypeScript）は別書 `coding_standards_frontend.md`（未整備）。
> 位置づけ・改訂手順は [phases.md](phases.md) §3.2.2、遵守の判定は [development_process.md](development_process.md) §4「製造完了ゲート」。
> 技術スタック一覧は [profile.md](../../.claude/project/profile.md) §3 が正。エージェント向けの要約は [.claude/references/coding-standards-backend.md](../../.claude/references/coding-standards-backend.md)（本書からの派生）。

---

## 1. 適用範囲と準拠元

| 項目 | 内容 |
|------|------|
| 対象 | `backend/` 配下の全 `.java`、Mapper XML、`application*.yml`、`logback-*.xml`、Flyway SQL |
| 非対象 | `frontend/`（別書）、`scripts/`・`.claude/scripts/`（開発補助の Python） |
| 準拠元 | [TERASOLUNA Server Framework for Spring 開発ガイドライン 5.11.0.RELEASE 日本語版](https://github.com/terasolunaorg/terasolunaorg.github.com/tree/master/guideline/5.11.0.RELEASE/ja)（以下「ガイドライン」） |

**設計・実装はガイドラインをベースに作る**。本書と各分冊はガイドラインとの**差分**（本プロジェクト固有の決定・上書き）だけを持ち、書かれていない事柄はガイドラインに従う。参照する版は **5.11.0.RELEASE**（実装ライブラリの `terasoluna-gfw` も 5.11.0.RELEASE へそろえる。[tech_selection.md](../backlog/java_migration/tech_selection.md) §2。適用は移行 STEP 2R-A）。

| 分冊 | ベースにするガイドラインの章 |
|------|---------------------------|
| 共通 | `Overview/ApplicationLayering`、`ArchitectureInDetail` |
| ドメイン層 | `ImplementationAtEachLayer/DomainLayer`、`ImplementationAtEachLayer/InfrastructureLayer`（MyBatis3） |
| Web層 | `ImplementationAtEachLayer/ApplicationLayer`、`Security` |
| テスト | `UnitTest` |

## 2. 原則

| # | 原則 |
|---|------|
| 1 | **分冊にもガイドラインにも無い判断は近傍の既存コードに倣う**。同じ層の既存クラスと書き方を揃えることを好みより優先する |
| 2 | **レイヤの責務を越えない**（[common.md](coding_standards_backend/common.md) §2 の依存方向と各層の分冊） |
| 3 | **仕様の正はドキュメント**。バランス数値・エラーコード・スキーマをコードに埋め込まない（[spec_ownership.md](spec_ownership.md)） |
| 4 | 規約と既存コードが食い違っていたら、直さずに [known_issues.md](../backlog/known_issues.md) へ記録する |
| 5 | ガイドラインと違う決め方をするときは、**理由を添えて分冊へ明記してから**実装する（暗黙の逸脱を作らない） |

## 3. 分冊索引

節番号は分割前の本書のものを維持している（共通が §2〜§9 を引き継ぎ、旧 §4「レイヤ別の規約」のうち層に依存する規約を domain / web の分冊へ移した）。

| 分冊 | 内容 | 読むとき |
|------|------|---------|
| [common.md](coding_standards_backend/common.md) | §2 モジュールと依存方向 / §3 命名 / §4 全層共通のルール / §5 Java 記述規約 / §6 例外 / §7 ログ / §8 Javadoc / §9 禁止事項 | 常に最初に読む |
| [domain.md](coding_standards_backend/domain.md) | `afkgame-domain`: Service・Mapper/Entity・マスターデータ・命名 | ドメイン層を書くとき |
| [web.md](coding_standards_backend/web.md) | `afkgame-web`: Controller・Resource・設定/フィルタ・エラー応答・命名 | Web層を書くとき |
| [test.md](coding_standards_backend/test.md) | テストコードの配置・記述規約・単体/統合の分離 | テストを書くとき |

## 4. 適用と検証

| 手段 | 対象 | コマンド・スキル |
|------|------|----------------|
| コンパイル | 構文・型 | `cd backend && mvn -q compile` |
| テスト・カバレッジ | 振る舞い・C1 100% | `cd backend && mvn verify`（JaCoCo） |
| レビュー | 本書と分冊への適合（命名・層の責務・セキュリティ・一貫性） | `backend-review` スキル（観点の正は [.claude/project/review-code.md](../../.claude/project/review-code.md) §2） |

- 新規・改修したコードは本書と分冊に従う。**既存コードの一括是正はしない**（見つけた逸脱は [known_issues.md](../backlog/known_issues.md) へ記録し、その箇所を触るときに直す）
- 改訂は基本設計工程で行う（[phases.md](phases.md) §3.2.2）。改訂したら `.claude/references/coding-standards-backend.md` を**同じ変更で**追随させる
- **Checkstyle・Spotless は未導入**。書式・import 順・命名は `backend-review` の目視で担保する（自動化の検討は [steps.md](../backlog/java_migration/steps.md) STEP 6 以降）
