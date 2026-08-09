# AFK GAME — Java/Terasoluna 移行計画

> 状態ファイル（[documentation_rules.md](../process/documentation_rules.md) §10）。移行完了時に本ファイルと `java_migration/` をまとめて削除する。
> 移行後の構成の正は [tech_structure.md](../tech/basic/tech_structure.md) §2〜§3 と [tech_structure_backend.md](../tech/basic/tech_structure_backend.md) §4。本書群は**手順と進捗**のみを持ち、仕様は各成果物へ反映して重複させない。
> 本ファイルは索引で、**進捗（§4 の STEP 一覧表）の正**を持つ。各節の本文は分冊にある。

---

## 分冊

| ファイル | 担当節 | 内容 |
|---------|-------|------|
| [tech_selection.md](java_migration/tech_selection.md) | §2 | 採用技術・モジュール構成・採用版と Archetype |
| [steps.md](java_migration/steps.md) | §4 | 各 STEP の詳細（STEP 1 / 2 / 2R / 3〜5 / 6） |
| [changes.md](java_migration/changes.md) | §3・§5 | 実装の起点、移行に伴う仕様変更点 |

## 1. 前提と完了条件

バックエンドを Python/FastAPI から Java/Terasoluna（MyBatis3）へ全面移行する。

| 項目 | 内容 |
|------|------|
| 対象 | `backend/` 配下のみ。フロントエンド（Vue SPA）は**無改修** |
| 方式 | 一括書き換え。並行運用（ストラングラー）は行わない |
| 着手時点 | Phase 1〜2 完了、Phase 3 製造①（パーティ・スキル基盤）まで実装済み |
| 完了条件 | フロント無改修で Phase 1〜3 の E2E が全PASS、かつ単体テストが分岐100% |

**API契約を変えないことが全体の制約**。[tech_api.md](../tech/basic/tech_api.md)・[tech_api_common.md](../tech/basic/tech_api_common.md) が正であり、移行で変更しない。JSON のプロパティ名は camelCase を維持する。

## 2. 技術選定

バックエンドは **Spring Boot ではなく、TERASOLUNA のブランクプロジェクト準拠（war + Tomcat）** で作る。採用版・構成の正は [tech_selection.md](java_migration/tech_selection.md) §2。

## 4. STEP 一覧

進捗はこの表が正（更新は main でのみ行う。[worktree_guide.md](../process/worktree_guide.md) §3）。各 STEP の内容は [steps.md](java_migration/steps.md) §4。

| STEP | 内容 | 状態 |
|------|------|------|
| 0 | 技術選定（§2） | 完了（Spring Boot 前提 → ブランクプロジェクト準拠へ改訂済み） |
| 1 | 基本設計・規約の改訂（ドキュメント先行） | 完了（2R-A で再改訂済み） |
| 2 | Java 側の骨格構築（横断基盤） | 完了。ただし Spring Boot 前提のため **2R で作り直す** |
| 2R | **ブランクプロジェクト構成への再構築** | 着手中（2R-A 完了。次は 2R-B） |
| 3 | Phase 1 スコープの移植 | 着手中（2R 完了まで中断） |
| 4 | Phase 2 スコープの移植 | 未着手 |
| 5 | Phase 3 実装済み分の移植 | 未着手 |
| 6 | 切替と Python 資産の削除 | **一部完了**（Python 削除済み。デプロイ手順の反映と最終確認が残る） |

**STEP 6 を STEP 3〜5 より先に実施した**（2026-08-09・ユーザー判断）。Python は仕様の正ではなく参照実装にすぎず、残すと二重管理になるため。結果として Phase 1〜3 は**どの言語でも未実装**の期間に入る。
