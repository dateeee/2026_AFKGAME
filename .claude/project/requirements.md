# 要件定義 — プロジェクト固有プロファイル

> 一般手順は [.claude/skills/requirements/SKILL.md](../skills/requirements/SKILL.md) を参照。本書は AFK GAME 固有の値のみを持つ。
> 共通の値は [profile.md](profile.md)。

## 1. 成果物

| 成果物 | パス | 役割 |
|-------|------|------|
| ゲーム仕様（索引） | `docs/design/game_spec.md` | Phase一覧・システム別ファイルへの索引 |
| システム別仕様 | `docs/design/systems/` | 配下全ファイル（一覧は索引 `game_spec.md` が正） |
| プロダクト要件 | `docs/design/requirements/product_requirements.md` | 何を誰に届けるか |
| 非機能要件 | `docs/design/requirements/non_functional_requirements.md` | 性能・可用性・セキュリティの要求値 |
| 運用要件 | `docs/design/requirements/operation_requirements.md` | 運用・変更管理 |
| 用語集 | `docs/glossary.md` | 用語の正 |
| 未確定管理 | `docs/backlog/open_specs.md` | 未確定仕様の一覧（**原則ゼロ**。生じたときだけ作成する） |
| 数値調整待ち | `docs/backlog/balance_backlog.md` | 仕様確定済み・数値のみ未定の項目 |

## 2. 参照先（読む順）

| 順 | 参照先 | 読む範囲 |
|----|--------|---------|
| 1 | `docs/backlog/open_specs.md` | 全文（一覧表 + 詳細ブロックで軽い）。不在なら未確定ゼロと判断する |
| 2 | `docs/design/game_spec.md` | 索引の該当Phase行のみ |
| 3 | `docs/design/systems/<対象>.md` | 該当セクションのみ |
| 4 | `docs/glossary.md` | 対象の用語のみ（Grepで行を特定） |
| 5 | `docs/data/towers/` `docs/data/skills/` | 項目が名指しするファイルのみ |

## 3. 固有の観点

| # | 観点 | 判定基準 |
|---|------|---------|
| 1 | Phase割り当て | 機能がどのPhase（1〜5）に属するか全文書で一致しているか |
| 2 | 放置ゲーム性 | オフライン中も進行が成立するか。操作を強制する仕様になっていないか |
| 3 | 不変条件との整合 | [profile.md](profile.md) §5 の6条件（特にサーバー権威・シングルプレイ）に反していないか |
| 4 | 数値の一意性 | 同じ数値が複数ファイルに書かれていないか（正を1つ決めて他はリンク） |
| 5 | 仮置きの明示 | 未確定の数値は「仮置き」と明記されているか |
| 6 | 用語の統一 | 敵ID・装備スロット名・塔ID・ダンジョンID・ポーションID が glossary.md と一致しているか |

## 4. 未確定仕様の確定（`resolve-specs` スキル）

### 進め方の固有ルール

| # | ルール |
|---|-------|
| 1 | **1回の実行で扱う項目は最大3件**。4件以上残る場合は「続きは再実行してください」と案内して終了する |
| 2 | **サブエージェントを使わない**。すべてメインコンテキストで処理する |
| 3 | 出力テンプレートは [.claude/skills/resolve-specs/references/templates.md](../skills/resolve-specs/references/templates.md) を、該当ステップに到達してから読む |

### カテゴリ → 参照先の対応表

| カテゴリ | 参照先 |
|---------|--------|
| キャラクター・成長・スキル | `design/systems/character.md`、`data/skills/` |
| 戦闘・ダメージ計算 | `design/systems/battle.md`、`tech/detail/tech_battle.md`、`tech/detail/tech_rng.md` |
| 装備・ドロップ | `design/systems/equipment.md`、`data/master/` |
| 経済・ショップ | `design/systems/economy.md`、`data/master/` |
| ダンジョン・塔 | `design/systems/dungeon.md`、`data/towers/` |
| エンドゲーム | `design/systems/endgame.md`、`data/master/` |
| UI・画面 | `design/systems/ui*.md`、`docs/diagrams/screen_transition.md` |
| オフライン・tick | `tech/detail/tech_offline.md`、`tech/detail/tech_tick.md`、`tech/detail/tech_polling.md` |
| 認証 | `tech/detail/tech_auth.md` |

### 確定後の反映先

1. 該当仕様書へ反映（数値・計算式・分岐条件を一意に書く）
2. `docs/changelog.md` の先頭（最新日付ブロック）へ追記
3. `docs/backlog/open_specs.md` の該当行と詳細ブロックを削除する
4. 全項目が確定したら `open_specs.md` をファイルごと削除する

## 5. 完了基準

一般スキルの完了基準に加え、以下を満たすこと。

- 対象の未確定項目がすべて解消され、`game_spec.md` 系へ反映されている（`open_specs.md` があれば該当行を削除。全解消ならファイルごと削除）
- 数値のみ未定の項目は `docs/backlog/balance_backlog.md` へ移してある（`open_specs.md` に残さない）
- `python scripts/check_doc_size.py` が exit 0

## 6. 次工程

| 次にやること | 手段 |
|------------|------|
| 仕様確定ゲート | `doc-review` スキル → 指摘を `fix-specs` スキルで反映 |
| 基本設計へ | `basic-design` スキル |
