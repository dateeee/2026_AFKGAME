# AFK GAME — 未確定仕様

> 仕様は Phase 1〜5 まで確定済みが原則（[CLAUDE.md](../CLAUDE.md) 開発方針）。本書はその原則から外れた項目を一時的に管理する。
> **確定して各仕様書へ反映したら、行を削除する。全行が消えたら本ファイルごと削除する。**
> 数値のみ未定の項目は本書ではなく [balance_backlog.md](balance_backlog.md) で管理する。
> 決定期限が対象Phaseより後の項目は仕様確定ゲートをブロックしない（[development_process.md](development_process.md) §4）。期限を過ぎた項目は残してはならない。

## 一覧

| # | 項目 | 正となる仕様書 | 決定期限 |
|---|------|--------------|---------|
| 1 | Phase 3 以降のモバイルナビで「その他」へまとめる対象タブ | [design/systems/ui.md](design/systems/ui.md) ナビゲーション構造 | Phase 3 の基本設計 |
| 2 | お知らせの既読状態のクライアント保持先 | [design/operation_requirements.md](design/operation_requirements.md) §3.1 | Phase 3 の基本設計 |
| 3 | 難易度別到達記録（`towersCleared`）のキー体系 | [tech/tech_data.md](tech/tech_data.md) | Phase 5 の基本設計 |

## 1. モバイルナビ「その他」の対象タブ

| 項目 | 内容 |
|------|------|
| 確定している範囲 | モバイルは5枠が上限（[tech/tech_design_system.md](tech/tech_design_system.md) §3）。5項目を超えたら末尾を「その他」へまとめる。PCは全項目を並べる |
| 未確定な範囲 | 「その他」へ入れるタブの組み合わせ |
| 背景 | タブは Phase 1: 3項目 → Phase 2: 5項目 → Phase 3: 6項目 → Phase 4: 7項目 と増える。超過が起きるのは Phase 3 から |
| 決定時にすること | `ui.md` ナビゲーション構造に対象タブを追記し、本書の行を削除する。実装は `navItems.ts` の1箇所 |

## 2. お知らせの既読状態のクライアント保持先

| 項目 | 内容 |
|------|------|
| 確定している範囲 | 既読状態はクライアント側で保持し、サーバーはプレイヤーごとの既読状態を持たない（[design/operation_requirements.md](design/operation_requirements.md) §3.1）。ヘッダに未読件数を表示する（[design/systems/ui.md](design/systems/ui.md)） |
| 未確定な範囲 | クライアントのどこへ保持するか（LocalStorage / Pinia の永続化 / セーブデータ同梱） |
| 背景 | お知らせは Phase 3 実装。保持先によって機種変更・ブラウザ変更時に既読が引き継がれるかが変わる |
| 決定時にすること | `operation_requirements.md` §3.1 と [tech/tech_state.md](tech/tech_state.md) へ保持先を明記し、本書の行を削除する |

## 3. 難易度別到達記録（`towersCleared`）のキー体系

| 項目 | 内容 |
|------|------|
| 確定している範囲 | 難易度ごとに到達済み最高階を個別管理する（[design/systems/endgame.md](design/systems/endgame.md)）。到達記録の保持先は `towersCleared`（[tech/tech_data.md](tech/tech_data.md)） |
| 未確定な範囲 | 難易度を `towersCleared` のキーへどう畳み込むか（`"{towerId}_{difficulty}"` 形式 / 値をオブジェクト化 など） |
| 背景 | 難易度は Phase 5（エンドコンテンツ）で追加される。Phase 1〜4 のセーブデータとの後方互換が要る |
| 決定時にすること | `tech_data.md` のセーブデータ構造へキー体系を定義し、本書の行を削除する |
