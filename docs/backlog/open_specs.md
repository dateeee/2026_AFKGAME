# AFK GAME — 未確定仕様

> 仕様は Phase 1〜5 まで確定済みが原則（[CLAUDE.md](../../CLAUDE.md) 開発方針）。本書はその原則から外れた項目を一時的に管理する。
> **確定して各仕様書へ反映したら、行を削除する。全行が消えたら本ファイルごと削除する。**
> 数値のみ未定の項目は本書ではなく [balance_backlog.md](balance_backlog.md) で管理する。
> 決定期限が対象Phaseより後の項目は仕様確定ゲートをブロックしない（[development_process.md](../process/development_process.md) §4）。期限を過ぎた項目は残してはならない。

## 一覧

| # | 項目 | 正となる仕様書 | 決定期限 |
|---|------|--------------|---------|
| 1 | Phase 1〜2 における計画メンテナンスの告知手段 | [design/requirements/operation_requirements.md](../design/requirements/operation_requirements.md) §3.1 | 早期（`operation_requirements.md` §2 のPhase進行時ダウンタイム規定はPhase 1→2 移行から適用されるため） |

> 項目番号は確定済みの行を削除しても振り直さない（他ドキュメントからの参照を保つ）。

## 1. Phase 1〜2 における計画メンテナンスの告知手段

| 項目 | 内容 |
|------|------|
| 確定している範囲 | 計画メンテナンスは事前にゲーム内お知らせで告知する（`operation_requirements.md` §3）。告知手段（ゲーム内お知らせ、同 §3.1）は **Phase 3** で実装し、それまでは下方修正を伴う改定を行わない（同 §3.1 末尾）。データマイグレーションは Phase 進行のたびに既存データを保持したまま行い、ダウンタイムは24時間未満に収める（`operation_requirements.md` §2、`non_functional_requirements.md` §3） |
| 未確定な範囲 | Phase 1〜2（お知らせ実装前）に計画メンテナンス（Phase進行に伴うスキーマ移行等）を行う場合の告知手段。spot-review（2026-08-16 19:18:41 ISSUE-1401）は次の2案を挙げたが、いずれも副作用があり選定できていない。**①案A（暫定告知手段を新設）**: ログイン画面への静的な告知文で事前掲示する案。ただし認証画面（ログイン画面）自体が **Phase 2〜** の機能（[systems/ui/onboarding.md](../design/systems/ui/onboarding.md)「認証画面（Phase 2〜）」）であり、**Phase 1 には掲示先が存在しない**（spot-review では未検証だった）。**②案B（Phase 1〜2は計画メンテナンスを行わないと定める）**: `operation_requirements.md` §2 の「Phase進行によるスキーマ拡張は既存データを保持したまま移行する」「ダウンタイムは24時間未満に収める」という一般規定と衝突しうる（Phase 1→2 移行のスキーマ拡張にダウンタイムが伴わない保証がない） |
| 背景 | `non_functional_requirements.md` §3 は計画メンテナンスの停止時間を24時間未満の必須制約として定義しており、メンテナンス自体は Phase 1〜2 でも発生しうる前提になっている。一方、告知手段は Phase 3 実装のため、Phase 1〜2 に計画停止が生じた場合の告知経路が要件のどこにも定義されていない。`tech_maintenance.md`（§12.4〜§12.7）も暫定手段を定義していないことを確認済み |
| 決定時にすること | 案A・案Bいずれかを選ぶか、第3の案（例: Phase 1 は認証がなく永続データの重要度が低いとみなして対象外とし、Phase 2 のみ暫定手段を設ける 等）を確定し、`operation_requirements.md` §3.1 へ反映する。案Aを採る場合は Phase 1 向けの掲示先を [systems/ui/onboarding.md](../design/systems/ui/onboarding.md) と突き合わせて確定する。反映後、本書の行を削除する |
