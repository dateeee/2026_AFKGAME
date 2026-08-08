# AFK GAME — 正の所在マップ（spec ownership）

> 「同じ数値・仕様を複数ファイルに書かない。正となるファイルを1つ決め、他はリンクする」
> （[documentation_rules.md](documentation_rules.md) §5）を運用するための台帳。
> トピックごとに**正となるファイル**を宣言し、`python scripts/check_docs.py`（--owner）が逸脱を機械検出する。

---

## 1. 使い方

| 場面 | すること |
|------|---------|
| 仕様を書く・直すとき | 対象トピックが下表にあれば**正ファイルにだけ**書く。他ファイルからはリンクする |
| 新しい仕様ファイルを追加したとき | 索引（`README.md`）へ登録し、既存ファイルと記述が重なるトピックは下表へ行を追加して境界を宣言する |
| `doc-review` が重複記載を指摘したとき | `fix-specs` で正を決めて修正し、**下表へ行を追加**する（検出パターンを書けば再発を機械検出できる） |
| 正を移すとき | 下表の正ファイル列を更新し、旧・正ファイル側の記載をリンクに置き換える |

- **検出パターン**列は正規表現。`check_docs.py` が `docs/design/` `docs/tech/` `docs/data/` `CLAUDE.md` `.claude/**` を走査し、正・許可以外のファイルでパターンが一致したら ERROR にする
- **許可**列は「現状すでに記載があり、参照として妥当な箇所」の凍結リスト。新たな転載を防ぐのが目的であり、許可は増やさず縮減していく
- パターンが書けない（文章的な）トピックは検出パターンを `—` にする。境界の宣言だけでも `doc-review` の照合基準になる

## 2. 対応表

| トピック | 正ファイル | 許可（参照可） | 検出パターン | 備考 |
|---------|-----------|--------------|-------------|------|
| 経験値計算式（100×level^1.5） | `docs/data/master/character.md` | `docs/design/systems/character.md`, `docs/design/game_spec.md`, `docs/glossary.md` | `\^1\.5` | 数値テーブルの導出元。許可3件は縮減候補 |
| ゲスト削除猶予（90日） | `docs/tech/detail/tech_auth.md` | `docs/design/requirements/non_functional_requirements.md`, `docs/tech/nonfunctional/tech_operations.md` | `90日` | 要求値は nfr、実現方式の正は auth |
| ダメージ計算式 | `docs/tech/detail/tech_battle.md` | `docs/design/systems/battle.md` | — | 図（`battle_flow/`）は視覚化として再掲可、値の変更は正から |
| ヘッダの構成要素 | `docs/design/systems/ui.md` | `docs/tech/detail/tech_design_system.md` | — | ISSUE-601 で確定（2026-08-02） |
| モーダルの表示位置・閉じ方 | `docs/tech/detail/tech_design_system.md` | `docs/design/systems/ui.md` | — | ISSUE-502 で確定（2026-08-02） |
| ナビゲーション項目と「その他」まとめ | `docs/design/systems/ui.md` | `docs/tech/detail/tech_design_system.md`, `docs/diagrams/screen_transition/main_nav.md` | — | ISSUE-603 で確定。「その他」対象タブは 2026-08-05、Phase 5 の導線（「探索」タブへ集約）は 2026-08-08 に確定 |
| お知らせの既読管理（保持先） | `docs/design/requirements/operation_requirements.md` | `docs/tech/basic/tech_api.md`, `docs/design/systems/ui.md` | — | §3.1 が正。localStorage 保持で確定（2026-08-05） |
| ログアウトの挙動（フロー・トークン失効） | `docs/tech/detail/tech_auth.md` | `docs/design/systems/ui.md`, `docs/tech/basic/tech_api.md` | — | エンドポイント定義そのものは `tech_api.md` が正（ISSUE-602） |
| リフレッシュトークンの保管先 | `docs/tech/detail/tech_auth.md` | `docs/tech/basic/tech_architecture.md`, `docs/tech/nonfunctional/tech_security.md` | `httpOnly` | §7 が正。LocalStorage で確定（ISSUE-704）。XSSリスクの受容判断は `tech_security.md` §11.7 |
| 未確定仕様・調整待ち数値の管理ルール | `docs/process/development_process.md` | `docs/backlog/open_specs.md`, `docs/backlog/balance_backlog.md`, `CLAUDE.md` | — | §6 が正。台帳は open_specs / balance_backlog、振り分けと解消フローの正はプロセス側（ISSUE-701〜703） |
| コスト規律（サブエージェント運用・読み方・工程区切り） | `.claude/project/profile.md` | `CLAUDE.md`, `.claude/references/review-procedure.md` | `同時最大4体` | §6 が正。CLAUDE.md は常時読込のため要約 + リンクを残す。review-procedure.md は一般手順としての原則再掲のみ可（固有値は持たない）。ISSUE-901 で確定（2026-08-03） |
| 確率・軽減率の上限（挑発率・状態異常付与率の合算80%） | `docs/design/systems/battle.md` | `docs/design/systems/character.md`, `docs/tech/detail/tech_battle.md`, `docs/data/skills/006_生存術系統.md` | — | 「確率・軽減率の上限（キャップ）」が正。合算80%・残り20%は必ずランダム。按分の実装式は `tech_battle.md` §3.1.3（ISSUE-1001・1002 で確定） |
| Phaseごとの開発進捗（工程の完了状況） | `docs/process/development_process.md` | — | — | §5 が正。README.md は Phase の**内容**のみを持ち、状況列は持たない（ISSUE-1019 で確定） |
| キャラクター成長式と LV 別ステータス | `docs/data/master/character.md` | `docs/design/systems/character.md`, `docs/data/towers/*.md` | — | §1.2 の `base + growth × (LV - 1)` が正。塔ファイル §4 の勇者参考値は導出値（ISSUE-1008 で確定） |
| 次セッションの開始タスク（引き継ぎ） | `docs/backlog/next_session.md` | `docs/backlog/carryover_notes.md` | — | ポインタ専用（§0 並行作業のルール +「次回」1件 + 候補キュー最大5行）。Phase 進捗の正は `development_process.md` §5、書式の正は `.claude/project/next.md`。**複数セッションにまたがる申し送り**は `carryover_notes.md` が持つ（引き継ぎ側へ転記しない） |
| 回復量+%（回復の心得）の適用範囲 | `docs/tech/detail/tech_skill.md` | `docs/data/skills/003_回復系統.md` | — | §1 #5 が正。ATK係数の回復スキル（`heal_1`・`heal_2`）にのみ乗算し、maxHP基準の蘇生・リジェネとポーションには適用しない（ISSUE-1101 で確定） |
| お知らせマスターの項目定義・掲示件数の上限 | `docs/data/master_data.md` | `docs/tech/basic/tech_api.md`, `docs/design/requirements/operation_requirements.md`, `docs/backlog/balance_backlog.md` | — | §17 が正。掲示件数20件・title 40字・body 400字は Phase 3 の仮置き（ISSUE-1104 で確定）。既読管理の保持先は別行（`operation_requirements.md` §3.1 が正） |
| DBスキーマ（物理テーブル名・列・型・キー・インデックス・制約） | `docs/tech/basic/tech_db.md` + `tech_db/` | `docs/diagrams/er_diagram/*.md`（**視覚化として再掲可**・値の変更は正から）, `docs/tech/basic/tech_data.md`（API/マスターの JSON 構造のみ） | — | 図は正にならない（§3）。反映順は定義書 → ER図 → Flyway DDL（`afkgame-initdb`）→ Entity + Mapper（`afkgame-domain`）。**スキーマの実装側の正は Flyway DDL**（Java の Entity は列メタデータを持たない POJO のため機械照合の対象外）。運用手順（適用・ロールバック）の正は `tech_operations.md` §12.4 |
| 到達記録（`towersCleared`）のキー体系 | `docs/tech/basic/tech_data.md` | — | `\{towerId\}_\{difficulty\}` | §1.1 が正。キーは塔ID、イベントダンジョンのみ難易度を畳み込む。難易度パラメータの受け渡し（`/api/tower/select` の `difficulty`）の正は `tech_api.md`「イベントダンジョン」（2026-08-08 に確定） |
| バックエンドのコーディング規約（層の責務・命名・記述・例外・ログ・Javadoc・テストコード） | `docs/process/coding_standards_backend.md`（索引）+ `coding_standards_backend/`（common / domain / web / test の4分冊） | `.claude/references/coding-standards-backend.md`（**派生**・固有値を持たないチェックリスト）, `.claude/project/profile.md`（§3 技術スタック表のみ）, `.claude/project/review-code.md`（§2 レビュー観点のみ）, `.claude/project/test-list.md`（§5 はポインタのみ）, `docs/process/phases.md` | — | **ベースは TERASOLUNA 開発ガイドライン 5.11.0.RELEASE 日本語版**（URL は索引 §1）。規約はそこからの差分だけを持つ。正 → 派生の順に同じ変更で改訂する（`phases.md` §3.2.2）。ロガー名体系・エラーコードは `docs/tech/basic/tech_logging.md`、DB列名は `tech_db.md` が正 |
| ゲーム設定の選択肢・範囲・刻み・既定値（設定画面の4項目） | `docs/design/systems/ui.md` | `docs/design/systems/battle.md`, `docs/tech/basic/tech_db/player.md`, `docs/tech/basic/tech_data.md`, `docs/tech/nonfunctional/tech_security.md` | `0\.1〜0\.5` | §設定画面「設定項目」表が正（ポーション閾値・戦闘ログ件数・トースト通知・自動売却レアリティ）。正は%表記・技術層は小数表記のため検出パターンは小数側のみ。列の型・NULL・既定値は「DBスキーマ」行（`tech_db/` が正）。ISSUE-1201・1203 で確定（2026-08-08） |

## 3. 境界の一般原則

個別の行が無いトピックは、ファイルの役割から正を判断する。

| 内容 | 正の置き場 |
|------|-----------|
| 要求値（性能・容量・期限などの「満たすべき値」） | `docs/design/*_requirements.md` |
| ゲーム仕様の意味・ルール（何がどうなるか） | `docs/design/systems/` |
| 処理・計算式・分岐（どう計算するか） | `docs/tech/tech_*.md` |
| 数値の具体値（マスターデータ） | `docs/data/` |
| ビジュアル・コンポーネント規約（色・部品・トーン） | `docs/tech/detail/tech_design_system.md` |
| 画面の構成・遷移・機能配置 | `docs/design/systems/ui*.md` |

図（`docs/diagrams/`）はテキスト仕様の視覚化であり正にならない。図とテキストが食い違ったらテキスト側の正を起点に揃える（`diagrams-review`）。
