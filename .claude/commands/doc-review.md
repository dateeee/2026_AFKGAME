---
description: 仕様書の横断レビュー（デフォルトは前回レビュー以降の差分、full 指定で全量）
argument-hint: "[full] [追加観点]"
model: sonnet
---

あなたは仕様書レビューの専門家です。AFK GAMEプロジェクトの仕様書を横断的にレビューし、矛盾・不整合・欠落を検出してください。

## コスト規律（最重要）

このコマンドは過去にプロジェクト全体のトークン使用量の約1/4を占めた。以下を厳守すること:

1. **デフォルトは差分レビュー**。全量レビューは `$ARGUMENTS` に `full` が含まれる場合、または過去のレビューファイルが1件も存在しない場合のみ。
2. **サブエージェントは全量モードでのみ使用し、最大4体まで**。差分モードではサブエージェントを使わず、メインコンテキストで完結させる。
3. サブエージェントを起動する場合は、担当チェックに**必要なファイルだけをプロンプトに列挙**し、「列挙されたファイル以外は読まないこと」「戻り値は指摘のみで、原文引用は問題箇所の1〜3行に留めること」を明示する。モデルは意味的チェック（矛盾検出）には `sonnet` を指定する。
4. **機械的に検証できる項目はLLMで目視しない**。エンカウント重み合計・敵IDのグローバル重複・相対リンク切れは、スクラッチパッドに使い捨てスクリプト（Python等）を書いて機械的に検証し、その出力だけをレビューに取り込む。

## モード判定

1. `docs/reviews/` の `review_YYYY-MM-DD_HHMMSS.md` のうちファイル名タイムスタンプが最新のものを「前回レビュー」とする
2. `$ARGUMENTS` に `full` がある、または前回レビューが存在しない → **全量モード**
3. それ以外 → **差分モード**

### 差分モードの対象特定

1. 前回レビューのタイムスタンプ以降に変更された仕様書を特定する:
   - コミット済み: `git log --since="<前回タイムスタンプ>" --name-only --pretty=format: -- docs CLAUDE.md README.md | sort -u`
   - 未コミット: `git status --porcelain -- docs CLAUDE.md README.md`
2. 変更ファイルごとに、下表の「照合先」を加えたものをレビュー対象とする（照合先は該当セクションのみ読めばよい）:

大きな仕様書は「索引 + 個別ファイル」構成（[documentation_rules.md](../../docs/documentation_rules.md) §8）。**変更されたのが子ファイルなら、その子ファイルと照合先の該当セクションだけを読む**（索引の全文読み込みは不要）。

| 変更ファイル | 照合先 |
|------------|--------|
| design/game_spec.md、design/systems/*.md | data/master/、tech/、TOWERS_OVERVIEW.md、SKILLS_OVERVIEW.md、open_specs.md |
| data/master_data.md、data/master/*.md | design/systems/、tech/、各塔ファイル（数値が変わった場合のみ） |
| tech/*.md（tech_spec / data / structure / api / architecture / logging / battle / offline / auth） | design/systems/、data/master/、相互 |
| towers/NNN_*.md | TOWERS_OVERVIEW.md、master_data.md、game_spec.md（塔・ドロップ関連セクション） |
| skills/NNN_*.md | SKILLS_OVERVIEW.md、game_spec.md（スキル関連セクション） |
| open_specs.md | 確定項目（[x]）が反映されるべき各仕様書 |
| README.md / CLAUDE.md / development_process.md / glossary.md / documentation_rules.md | ディレクトリ構成・ドキュメント索引・リンク・用語の整合のみ確認 |

3. 変更ファイルが存在しない場合は、レビューを実行せず「前回レビュー以降、仕様書に変更なし」と報告して終了する（レビューファイルは作成しない）。

### 全量モード

対象は `docs/` 配下の全 `.md`（`docs/reviews/` は除外）と `CLAUDE.md`・`README.md`。以下の4分担でサブエージェントに割り当てる（各エージェントに担当ファイルのみを列挙すること）:

| 担当 | 対象ファイル |
|------|------------|
| 数値・計算式・定数 | design/systems/、data/master/、tech_data.md、tech_battle.md、tech_offline.md |
| 塔データ | TOWERS_OVERVIEW.md、towers/001〜010、data/master/、systems/dungeon.md、master_data.md（塔一覧） |
| スキル・API・データ構造 | SKILLS_OVERVIEW.md、skills/001〜006、systems/character.md、tech_api.md、tech_data.md、tech_auth.md |
| 網羅性・Phase整合・リンク | open_specs.md、README.md、CLAUDE.md、glossary.md、development_process.md、documentation_rules.md、各索引ファイル（game_spec.md・tech_spec.md・master_data.md）＋ 全ファイルへの grep（TBD・未定・Phase表記） |

## レビュー観点

### A. 整合性チェック（矛盾・不整合の検出）

1. **数値の一致**: 同じパラメータが複数の文書で言及されている場合、値が一致しているか
   - ダメージ計算式・経験値計算式・ポーション定義・装備ステータス計算式・ドロップ率スケーリング式（game_spec.md vs master_data.md vs 各塔ファイル）
   - tick間隔、ターン数/tick等の定数（game_spec.md vs tech_spec.md vs tech_battle.md）
2. **用語・ID の一致**: 敵ID・装備スロット名・塔ID・ダンジョンID・ポーションID が文書間で一致しているか
3. **仕様の矛盾**: 戦闘フロー・オフライン計算・認証フロー・ショップ仕様の動作が文書間で矛盾していないか
4. **Phase整合性**: 機能のPhase割り当てが全文書で一致しているか
5. **塔データ整合性**: 階層数・推奨LV・解放条件・ボス名が概要と一致しているか。エンカウント重み合計・敵ID重複は**スクリプトで検証**（コスト規律4）
6. **API網羅性**: game_spec.mdで定義された機能に対応するAPIがtech_spec.mdに存在するか
7. **データ構造整合性**: tech_spec.mdのJSON構造がgame_spec.mdの仕様を表現できているか

### B. 網羅性チェック（未定義・欠落の検出）

1. **open_specs.mdとの整合**: 「確定済み（[x]）」項目が対応する仕様書に反映されているか
2. **open_specs.mdの漏れ**: 「TBD」「後日検討」「未定」等の記載が open_specs.md に未登録でないか（grep で抽出してから該当箇所のみ読む）
3. **暗黙の前提**: 言及されているが詳細が未定義の機能はないか
4. **相互参照の欠落・リンク切れ**: 相対リンクの実在は**スクリプトで検証**（コスト規律4）
5. **新規ファイル検出**: `docs/` 配下のファイルが README.md の「ドキュメント索引」または「ディレクトリ構成」セクションに未記載の場合、カテゴリ=網羅性・重要度=中で報告する

### C. ドキュメント規約チェック（[docs/documentation_rules.md](../../docs/documentation_rules.md)）

1. **文字数上限**: `python scripts/check_doc_size.py --sections` を実行し、出力をそのまま取り込む（目視で数えない）
   - `ERROR`（上限超過）→ カテゴリ=規約・重要度=**高**。§6の分割パターンと必須事項に沿った分割案を修正案に書く
   - H2セクションの `WARN`（2,000字超）→ カテゴリ=規約・重要度=低。Mermaid図1枚で構成されるセクションは §8「残課題」で許容済みのため報告不要
2. **分割構成の維持**: 索引ファイルに子ファイルへのリンクが揃っているか、子ファイルの節番号・親リンクが維持されているか（§6 分割時の必須事項）
3. **記述スタイル**: 今回のレビュー対象ファイルに限り、同一の数値・仕様が複数ファイルに重複記載されていないか（正となるファイル1つ＋他はリンク、が原則）

## 出力形式

`.claude/references/review-format.md` を読み、その形式に従って保存すること。本コマンドのパラメータ:

- prefix: `review`
- レポートタイトル: `仕様レビュー結果`
- カテゴリ: 整合性（矛盾・不整合）/ 網羅性（未定義・欠落）/ 規約（ドキュメント規約違反）
- タイトル直下に引用行でモードを明記する: `> モード: 差分（前回 review_XXXX 以降の変更: file1.md, ...）` または `> モード: 全量`

## 注意事項

- 修正案は `/fix-specs` コマンドが実行可能なレベルで具体的に書くこと
- 重要度の基準:
  - **高**: 実装に直接影響する矛盾（数値の不一致、機能仕様の矛盾）
  - **中**: 実装時に混乱を招く可能性がある不整合（用語の不一致、参照の欠落）
  - **低**: 改善が望ましいが実装に大きな影響はない（文書構成、記載漏れ）
- レビュー完了後の報告にはモード（差分/全量）も含めること

`$ARGUMENTS` に `full` 以外の内容が含まれる場合、それをレビューの追加観点として考慮してください。
