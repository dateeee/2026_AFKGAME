# AFK GAME — 未確定仕様

> 仕様は Phase 1〜5 まで確定済みが原則（[CLAUDE.md](../CLAUDE.md) 開発方針）。本書はその原則から外れた項目を一時的に管理する。
> **確定して各仕様書へ反映したら、行を削除する。全行が消えたら本ファイルごと削除する。**
> 数値のみ未定の項目は本書ではなく [balance_backlog.md](balance_backlog.md) で管理する。
> 決定期限が対象Phaseより後の項目は仕様確定ゲートをブロックしない（[development_process.md](development_process.md) §4）。期限を過ぎた項目は残してはならない。

## 一覧

| # | 項目 | 正となる仕様書 | 決定期限 |
|---|------|--------------|---------|
| 3 | 難易度別到達記録（`towersCleared`）のキー体系 | [tech/tech_data.md](tech/tech_data.md) | Phase 5 の基本設計 |
| 4 | ボスラッシュ・イベントダンジョンへの導線 | [design/systems/ui.md](design/systems/ui.md) ナビゲーション構造 | Phase 5 の基本設計 |

> 項目番号は確定済みの行を削除しても振り直さない（他ドキュメントからの参照を保つ）。

## 3. 難易度別到達記録（`towersCleared`）のキー体系

| 項目 | 内容 |
|------|------|
| 確定している範囲 | 難易度ごとに到達済み最高階を個別管理する（[design/systems/endgame.md](design/systems/endgame.md)）。到達記録の保持先は `towersCleared`（[tech/tech_data.md](tech/tech_data.md)） |
| 未確定な範囲 | ① 難易度を `towersCleared` のキーへどう畳み込むか（`"{towerId}_{difficulty}"` 形式 / 値をオブジェクト化 など）／② 難易度パラメータを `/api/tower/select` へどう渡すか（追加パラメータ / `towerId` に畳み込む） |
| 背景 | 難易度は Phase 5（エンドコンテンツ）で追加される。Phase 1〜4 のセーブデータとの後方互換が要る。専用エンドポイントを設けず `/api/tower/*` を再利用する方針までは確定済み |
| 決定時にすること | `tech_data.md` のセーブデータ構造へキー体系を定義し、[tech/tech_api.md](tech/tech_api.md)「イベントダンジョン」節と [diagrams/api_sequence/endgame.md](../diagrams/api_sequence/endgame.md) §11.5 へ反映し、本書の行を削除する |

## 4. ボスラッシュ・イベントダンジョンへの導線

| 項目 | 内容 |
|------|------|
| 確定している範囲 | ボスラッシュ（[design/systems/endgame.md](design/systems/endgame.md) §2.11）・イベントダンジョン（同 §2.13）は Phase 5 で追加される。現行のタブ構成図（[diagrams/screen_transition/main_nav.md](../diagrams/screen_transition/main_nav.md)「Phase別タブ構成」）は導線確定までタブ構成を Phase 4 のまま据え置いて描いている |
| 未確定な範囲 | 導線をタブ追加とするか、ホーム内セクションとするか |
| 背景 | タブは Phase 4 で7項目に達しており、タブ追加はモバイル5枠上限（#1）に直結する。#1 の「その他」対象タブの決定と連動する |
| 決定時にすること | `ui.md` ナビゲーション構造へ導線を明記し、`diagrams/screen_transition/` の2図（endgame / main_nav）を追随させ、本書の行を削除する |
