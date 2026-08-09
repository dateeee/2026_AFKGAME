# バックエンドコーディング規約 — 準拠元と原則

> [coding_standards_backend.md](../coding_standards_backend.md) の分冊。**規約に無い判断をするとき・規約を改訂するとき**に読む。
> 個々の規約は [layering.md](layering.md)（層）・[common.md](common.md)（全層共通）・[domain.md](domain.md) / [domain_service.md](domain_service.md) / [web.md](web.md) / [test.md](test.md) が持つ。

---

## 1. 適用範囲と準拠元

| 項目 | 内容 |
|------|------|
| 対象 | `backend/` 配下の全 `.java`、マッピング XML、`application*.yml`、`logback-*.xml`、Flyway SQL |
| 非対象 | `frontend/`（別書）、`scripts/`・`.claude/scripts/`（開発補助の Python） |
| 準拠元 | [TERASOLUNA Server Framework for Spring 開発ガイドライン 5.11.0.RELEASE 日本語版](https://github.com/terasolunaorg/terasolunaorg.github.com/tree/master/guideline/5.11.0.RELEASE/ja)（以下「ガイドライン」。HTML は [terasolunaorg.github.io](https://terasolunaorg.github.io/guideline/current/ja/)） |

**設計・実装はガイドラインをベースに作る**。各分冊はガイドラインとの**差分**（本プロジェクト固有の決定・上書き）だけを持ち、書かれていない事柄はガイドラインに従う。参照する版は **5.11.0.RELEASE**（実装ライブラリの `terasoluna-gfw` も 5.11.0.RELEASE へそろえる。[tech_selection.md](../../backlog/java_migration/tech_selection.md) §2。適用は移行 STEP 2R-A）。

| 分冊 | ベースにするガイドラインの章 |
|------|---------------------------|
| [layering.md](layering.md) | `Overview/ApplicationLayering`（2.4） |
| [common.md](common.md) | `ArchitectureInDetail`（7.1 ロギングほかの横断機能） |
| [exception.md](exception.md) | `ArchitectureInDetail/WebApplicationDetail/ExceptionHandling`（4.3） |
| [domain.md](domain.md) | `ImplementationAtEachLayer/DomainLayer`（3.2.1〜3.2.4）、`ImplementationAtEachLayer/InfrastructureLayer`（MyBatis3） |
| [domain_service.md](domain_service.md) | `ImplementationAtEachLayer/DomainLayer`（3.2.5〜3.2.7） |
| [web.md](web.md) | `ImplementationAtEachLayer/ApplicationLayer`（3.4）、`ArchitectureInDetail/WebServiceDetail/REST`（5.1）、`Security`（9） |
| [test.md](test.md) | `UnitTest`（10） |

## 2. 原則

| # | 原則 |
|---|------|
| 1 | **分冊にもガイドラインにも無い判断は近傍の既存コードに倣う**。同じ層の既存クラスと書き方を揃えることを好みより優先する |
| 2 | **レイヤの責務を越えない**（[layering.md](layering.md) §1〜§3 の呼び出し可否、[common.md](common.md) §2 の依存方向） |
| 3 | **仕様の正はドキュメント**。バランス数値・エラーコード・スキーマをコードに埋め込まない（[spec_ownership.md](../spec_ownership.md)） |
| 4 | 規約と既存コードが食い違っていたら、直さずに [known_issues.md](../../backlog/known_issues.md) へ記録する |
| 5 | ガイドラインと違う決め方をするときは、**該当する分冊へ「ガイドラインの章番号・本プロジェクトの決定・理由」の3点を書いてから**実装する（暗黙の逸脱を作らない）。差分の**正は各分冊**が持ち、横断の一覧をどこにも二重に持たない（明記済みかどうかは §1 の表で分冊を特定して判定する） |

## 3. 適用と検証

| 手段 | 対象 | コマンド・スキル |
|------|------|----------------|
| コンパイル | 構文・型 | `cd backend && mvn -q compile` |
| テスト・カバレッジ | 振る舞い・C1 100% | `cd backend && mvn verify`（JaCoCo） |
| レビュー | 規約への適合（命名・層の責務・セキュリティ・一貫性） | `backend-review` スキル（観点の正は [.claude/project/review-code.md](../../../.claude/project/review-code.md) §2） |

- 新規・改修したコードは規約に従う。**既存コードの一括是正はしない**（見つけた逸脱は [known_issues.md](../../backlog/known_issues.md) へ記録し、その箇所を触るときに直す）
- 改訂は基本設計工程で行う（[phases.md](../phases.md) §3.2.2）。改訂したら `.claude/references/coding-standards-backend.md` を**同じ変更で**追随させる
- **Checkstyle・Spotless は未導入**。書式・import 順・命名は `backend-review` の目視で担保する（自動化の検討は [steps.md](../../backlog/java_migration/steps.md) STEP 6 以降）
