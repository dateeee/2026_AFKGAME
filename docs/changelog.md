# AFK GAME — ドキュメント変更履歴

> 全ドキュメントの変更履歴を集約したアーカイブ。**文字数上限の対象外**
> （[documentation_rules.md](documentation_rules.md) §2）。
>
> 個々のドキュメントに変更履歴セクションは置かない。改稿時は本ファイルの先頭
> （最新日付のブロック）へ1行追記する。完全な履歴は Git（`git log -- <path>`）が持つ。

---

## 2026-08-02

| ファイル | 内容 |
|---------|------|
| `docs/tech/tech_shop.md` | 仕様レビュー ISSUE-002/004/005/006/007/011 を反映。§2.5・§2.6 を `## 3. ステータスと価格` へ昇格し以降の節番号を繰り下げ（H2 2,000字超過を解消）。基礎値の式を `master/equipment.md §6.1` へのリンクに置換。**購入時の所持枠上限チェック（`400 SHOP_INVENTORY_FULL`）を追加**。分岐一覧を42件（生成23・購入19）へ拡充（コモンが確定する正常経路、付与数の範囲が単一値のケース、所持枠の真偽2行） |
| `docs/data/master/equipment.md` | ISSUE-001/003。**§6.0 ベース装備一覧（15種）を新設**（ID・名前・スロット・持ち手。従来 `backend/app/master_data/equipment.py` にしか定義が無く、不変条件6「データ駆動」に反していた）。基礎値式・参考値表・売却価格表の変数名を「敵LV」→「装備レベル」へ統一 |
| `docs/design/systems/equipment.md` | ISSUE-004。基礎値式・ステータス補正・売却価格式の再掲を削除し `master/equipment.md §6.0 / §6.1 / §6.3` へのリンクに置換（正を1ファイルに統一） |
| `docs/design/systems/economy.md` | ISSUE-007/010。倉庫節に**所持枠上限の適用範囲**を追加（**Phase 2 から適用**。ショップ購入=`SHOP_INVENTORY_FULL` で失敗 / 戦闘ドロップ=破棄）。日替わりの「更新間隔 24時間」を「毎日 00:00 UTC にリセット（初回のみ24時間未満あり）」へ修正 |
| `docs/tech/tech_logging.md` | ISSUE-007。エラーコード体系の `SHOP_` に `SHOP_INVENTORY_FULL` を追加 |
| `docs/tech/tech_data.md` | ISSUE-012・設計図レビュー ISSUE-104。装備JSON例の `statAtk` を計算式どおりの 11 に修正。マスターに存在しない `iron_sword` を `sword` へ |
| `docs/tech/tech_api.md` | `tech_shop.md` の節番号繰り下げに追従（§5→§6、§3→§4） |
| `README.md`・`CLAUDE.md`・`docs/data/master_data.md` | ISSUE-008/001。詳細設計の索引に `shop` を追加。マスターデータ索引に §6.0 ベース装備一覧を追加 |
| `diagrams/class_diagram/item.md`・`diagrams/class_diagram.md` | 設計図レビュー ISSUE-101/102/107/108。`DailyItem` に `level`・`statAtk/Def/Hp/Spd` を追加し `itemId`→`baseId`（ER図と属性一致）。`EquipCategory`（3値）を新設し `EquipSlot`（9値）と分離。`refreshDaily` に `rng` 引数を明示。H2 を「ショップ」「施設・ボスラッシュ」に分割 |
| `diagrams/api_sequence/gameplay.md` | ISSUE-103/104/106。`nextResetAt`→`dailyResetAt`、「鉄の剣」→「剣」。日替わり購入に失敗経路の `alt`（売り切れ / ゴールド不足 / 所持枠上限）を追加 |
| `diagrams/er_diagram/item.md` | ISSUE-003 と同根。`Equipment.level` の注記を「ドロップ=敵LV / ショップ=最高到達階層」へ |
| `docs/tech/tech_shop.md` | **新規**。Phase 2 日替わりショップの詳細設計（遅延評価による24時間更新・5枠の抽選手順・ステータス算出・購入フロー・データ構造・分岐一覧38件） |
| `docs/tech/tech_spec.md` | 詳細仕様の索引に `tech_shop.md` を追加 |
| `docs/tech/tech_api.md` | `/api/shop/lineup`・`/api/shop/buy` に `tech_shop.md` へのリンクと、常設／日替わり指定の排他（422）を追記 |
| `docs/tech/tech_data.md` | ゲーム状態JSONの `"shop"` コメントを削除し、日替わりショップ状態は `GET /api/shop/lineup` で取得する旨に修正 |
| `docs/data/master/equipment.md` | §6.1 のショップ購入時の装備レベルを確定（**最高到達階層と同じ・下限1**）。定義のない「ショップテーブル参照」を解消 |
| `diagrams/er_diagram/item.md` | `ShopDailySlot` に `level`・`stat_atk/def/hp/spd` を追加し、`item_id` を `base_id` へ改名（**抽選結果を生成時に確定保存**するため） |
| `docs/balance_backlog.md` | B-6（日替わりショップの帯内レアリティ出現率）を追加 |
| `docs/data/towers/TOWERS_OVERVIEW.md` | 仕様レビュー ISSUE-001 を反映。**塔別表「対応フェーズ」・ダンジョン別表「解放フェーズ」を `afa3d41` の後ろ倒し（1ダンジョン=1Phase）へ追従**（塔2=Phase 2／塔3-5=3／塔6-8=4／塔9-10=5）。`game_spec.md §1` を正とする旨を明記 |
| `docs/design/systems/endgame.md` | ISSUE-003。マイルストーン報酬・転生ボーナス一覧・深淵の塔スケーリング式の3テーブルを削除し、`master/endgame.md §15.2 / §16.1 / §18.2` へのリンクに置換（設計意図の記述は残置） |
| `docs/data/towers/000〜010`・`docs/data/skills/000・001` | ISSUE-004。13ファイルに再掲されていたダメージ計算式を削除し、`systems/battle.md`（通常攻撃）・`tech_battle.md §3.1`（スキル）へのリンクに置換。テンプレートも同時に修正し新規ファイルへの再混入を防止 |
| `docs/design/systems/equipment.md`・`docs/data/master/equipment.md` | ISSUE-005。鍛冶屋LV別の強化上限・コスト倍率の具体値を削除し `economy.md §2.9` へのリンクに置換（同表を唯一の正とする） |
| `docs/tech/tech_api.md`・`docs/glossary.md` | ISSUE-006。仮置き値「転生ポイント10pt」を確定値として持っていた2箇所から数値を削除し `master/endgame.md §16.1` へのリンクに置換 |
| `docs/tech/tech_api.md`・`docs/open_specs.md` | ISSUE-002。イベントダンジョンのAPI・データ構造が未定義だった件を「イベントダンジョンの入退場API・データ構造」として `open_specs.md` へ登録し、`tech_api.md` に未確定である旨の節を追加 |
| `frontend/tests/e2e/**`（新規） | **結合テスト L2（E2E / Playwright）を整備**。必須シナリオ #1 認証→ゲーム状態取得 / #2 塔選択→目標階設定 / #5 装備ドロップ→装備変更→ステータス反映 / #6 常設ショップ購入 の4本＋画面遷移で13件。バックは :8100（`DATABASE_URL=sqlite:///./e2e.db`）、フロントは :5174 で自動起動し開発環境と分離。時刻は `last_tick_at` の巻き戻しで進め、乱数を含むドロップ・報酬は条件成立まで進める（固定スリープなし）。12回連続実行で結果が安定することを確認 |
| `frontend/src/App.vue`・`composables/useGameLoop.ts` | E2Eで検出した不具合を修正。初期化が `onMounted` の1回のみで、ゲスト作成・ログイン後にゲーム状態が読み込まれず**ホームが401バナー付きの空表示**になっていた。未認証時は読み込まず、認証状態の変化を監視して初期化する |
| `frontend/src/views/LoginView.vue` | 同上。`/register` からの `?mode=register` を参照しておらず登録フォームが開かなかったのを修正 |
| `frontend/package.json`・`playwright.config.ts`・`tsconfig.e2e.json`・`vite.config.ts` | Playwright を導入し `npm run test:e2e` を追加。`npm run type-check` にE2Eの型チェックを含める。Vite のプロキシ先を `VITE_API_PROXY_TARGET` で差し替え可能に |
| `.claude/project/integration-test.md` | L2 を「整備済み」へ。§1.2「L2 の記述規約」を新設。§3 必須シナリオ表に L2 列を追加し、§3.1 を「意図的に扱わない経路」へ改題（`GET /api/health` の除外を解消） |
| `README.md` | 主なコマンドに `npm run test:e2e` を追加。ディレクトリ構成に `frontend/tests/e2e/`・`backend/tests/` を追加し、`docs/` 直下の個別ファイル列挙は「ドキュメント索引」への参照へ集約 |
| `docs/development_process.md` | §5 の結合テスト欄を「完了（L1・L2）」へ。§5.3 を L1・L2 両方の整備状況（L1 29件・L2 13件）に更新 |
| `docs/known_issues.md` | §3 対応済みへ2件追加（認証直後のゲーム状態未読み込み、`/register` のモード未反映）。いずれもE2Eで検出し実装を修正 |
| `docs/design/game_spec.md` | 要件定義の整合性チェック指摘を反映。§1 に**塔の実装Phase対応表**を新設し、Phase 1〜5 の各節へ追加される塔を明記（Phase 1=塔1／2=塔2／3=塔3-5／4=塔6-8／5=塔9-10+深淵の塔）。§6「今後の検討事項」を削除し `open_specs.md` / `balance_backlog.md` への参照に一本化 |
| `docs/design/systems/dungeon.md` | ダンジョン2〜4の見出しを「Phase Nでは定義のみ」から実装Phase（3〜／4〜／5〜）へ修正。**深淵の塔（Phase 5）の解放条件「天空の塔クリア」が成立しない矛盾を解消**。環境効果を Phase 3〜 と明記 |
| `docs/design/systems/character.md` | 複数挑発時のルールを合算80%上限・比率按分へ修正（`battle.md` の上限規定と統一。残り20%は常にランダム）。キャラレアリティ倍率テーブルを削除し `master/character.md` §7.2 を正とする |
| `docs/design/systems/battle.md` | ポーションの回復量・価格テーブルを削除し `master/item.md` §3.1 を正とする |
| `docs/design/systems/equipment.md` | §2.4 に**装備カテゴリ（9スロット→武器/防具/アクセサリーの対応表）**を新設。装備分解・装備製作のテーブルを削除し `master/equipment.md` §13・§14 を正とする |
| `docs/design/systems/economy.md` | 訓練場EXP獲得率（LV6: 28%→30%）と市場ボーナス（LV4: +18%→+20%、LV6: +28%→+30%）の数列の破れを修正。施設レベルを「LV0（未建設）〜LV10」へ統一。日替わり商品にカテゴリ対応の参照を追加 |
| `docs/design/systems/ui.md` | ナビゲーション構造の見出しを「Phase 2〜」→「Phase 1〜」へ修正（Phase 1 のショップ到達手段が未定義だった問題を解消）。戦闘ログ表示件数の選択肢から到達不能な 200件 を削除 |
| `docs/design/systems/endgame.md` | §2.14 深淵の塔のランキングに公開範囲・ゲスト時の扱い（§2.11 と同一）を明記 |
| `docs/design/product_requirements.md` | §3 塔1本の攻略期間を 3〜7日 → **9〜18日** へ修正（塔10本＝約3〜6ヶ月との算術矛盾を解消）。§4.1 の境界定義に深淵の塔のランキングを追記 |
| `docs/glossary.md` | 塔1〜10 のPhase表記を `game_spec.md` の対応表へ統一（塔6-8: Phase 3→4、塔9-10: Phase 4〜5→5）。挑発の定義に合算80%上限を明記。「装備カテゴリ」を追加 |
| `docs/data/master/item.md` | `speed_boots`（足スロット）をアクセサリーから**防具**へ移し価格を 1,000G→1,200G に修正。防具表にスロット・効果列を追加。ショップ装備の設計方針の重複記述を `economy.md` への参照へ変更 |
| `docs/development_process.md` | §5「現在の工程状況」の結合テスト欄を L1完了 / L2未着手へ更新。§5.3「結合テストの整備状況（L1）」を新設 |
| `docs/known_issues.md` | §3「対応済みの項目」を新設。結合テストで検出した2件（ヘルスチェックの仕様乖離 / `DATABASE_URL` の環境変数未対応）を**実装を仕様へ合わせて**解消し記録。未対応 #6 に環境変数一覧（tech_operations §12.2）の未実装を追加 |
| `README.md` | 環境変数表に `DATABASE_URL` を追加 |
| `.claude/project/integration-test.md` | L1を「整備済み」へ更新。§1.1 記述規約（マーカー・DBセッション・乱数/時刻/ログの扱い）と §3.1「意図的にL1で扱わない経路」を新設 |
| `.claude/skills/test-list/SKILL.md` | 初版作成: 工程4「テストリスト作成」の一般手順。分岐一覧→失敗するテストへの展開、Red確認、工程内検証（§5）を規定 |
| `.claude/skills/test-list/references/patterns.md` | 初版作成: テスト実装パターンの一般形。旧 `unit-test/references/patterns.md` から移設し、固有の値を除去 |
| `.claude/skills/integration-test/SKILL.md` | 初版作成: 工程7「結合テスト」の一般手順。シナリオ設計、失敗時の原因切り分け、工程内検証（§5）を規定 |
| `.claude/skills/dev/SKILL.md` | 工程5「製造」の一般手順へ全面改稿。固有値をプロファイル参照へ退避し、工程内検証（§6）を追加 |
| `.claude/skills/unit-test/SKILL.md` | 工程6「単体テスト」の一般手順へ全面改稿。実装前のテスト作成は `test-list` へ分離し、C1測定・補完に特化。工程内検証（§4）を追加 |
| `.claude/skills/unit-test/references/c1_checklist.md` | 固有の分岐観点を削除しプロファイル参照へ変更。docstring 書式は `test-list` のパターン集へ集約 |
| `.claude/skills/doc-review/SKILL.md` 他6件 | 初版作成: 旧 `.claude/commands/` の7件（doc-review / diagrams-review / backend-review / frontend-review / full-review / fix-specs / resolve-specs）をスキルへ一元化 |
| `.claude/commands/`（7件） | 削除。全機能を同名スキルへ移行 |
| `.claude/references/review-procedure.md` | 節番号の重複を解消（§7 担当範囲の切り分け / §8 出力と報告）。`sonnet` サブエージェントへの委譲をコスト規律へ追加 |
| `.claude/references/review-format.md` | コマンド表記をスキル表記へ変更 |
| `.claude/references/resolve-specs/templates.md` | コマンド表記をスキル表記へ変更 |
| `docs/development_process.md` | §2.3「工程とスキルの対応」を新設（工程↔スキル↔ゲートの対応表・工程内検証の方針）。`/コマンド` 表記をスキル名へ統一 |
| `docs/documentation_rules.md` | 区分Dの対象を `.claude/skills` `.claude/references` `.claude/project` へ更新。コマンド表記をスキル表記へ変更 |
| `CLAUDE.md` | 「作業はすべてスキル経由」「一般手順と固有値の分離」へ改稿。区分Dの対象を更新 |
| `README.md` | ディレクトリ構成に `.claude/` を追加。`docs/` 配下の記述を索引セクションと重複しない粒度へ圧縮。工程↔スキル対応表へのリンクを追加 |
| `.claude/project/INDEX.md` | 初版作成: 一般スキルとプロジェクト固有プロファイルを分離。全7工程にスキルを用意し、旧 `.claude/commands/` の7件をスキルへ一元化 |
| `.claude/project/_TEMPLATE.md` | 初版作成。工程プロファイルの記述スキーマを制定し、`.claude/skills/` + `.claude/references/` を無改造でコピー → `.claude/project/` のみ書き直す再利用手順を定義 |
| `.claude/project/profile.md` | 初版作成。技術スタック・ディレクトリ・常用コマンド・アーキテクチャ不変条件6件・コスト規律を集約（全スキルが最初に読む共通プロファイル） |
| `.claude/project/requirements.md` | 初版作成。要件定義の成果物・参照順・固有観点・`resolve-specs` の運用ルール・カテゴリ別参照先を定義 |
| `.claude/project/basic-design.md` | 初版作成。基本設計の成果物・設計図6点と照合先・固有観点・Mermaid の機械検証項目を定義 |
| `.claude/project/detail-design.md` | 初版作成。詳細設計の成果物・固有観点に加え、**分岐一覧（単体テスト観点）の記載形式と記載ルール**を定義 |
| `.claude/project/test-list.md` | 初版作成。テストリスト作成の対象範囲・共通フィクスチャ6件・記述規約・TDD適用時期を定義 |
| `.claude/project/dev.md` | 初版作成。製造の実装順（バックエンド4層 → フロント5層）・参照順・TDD適用範囲・固有観点を定義 |
| `.claude/project/unit-test.md` | 初版作成。単体テストの前提・コマンド・固有の分岐観点10領域・除外規則・整備状況を定義 |
| `.claude/project/integration-test.md` | 初版作成。結合テスト2レイヤーの配置・シナリオ導出元・Phase 1〜2 の必須シナリオ7件・固有観点を定義 |
| `.claude/project/test-patterns.md` | 初版作成。`unit-test/references/patterns.md` から AFK GAME 固有の実例（乱数固定・時刻固定・エラーコード検証）を分離 |
| `.claude/project/review-docs.md` | 初版作成。`doc-review` / `diagrams-review` / `fix-specs` のパラメータ・差分照合先・全量分担・観点・重要度基準を集約 |
| `.claude/project/review-code.md` | 初版作成。`backend-review` / `frontend-review` の観点26件と重要度基準を集約 |
| `.claude/project/review-fullstack.md` | 初版作成。`full-review` の観点13件・差分照合先・機械的検証4項目を分離（`review-code.md` の上限超過を §6.2 サブシステム分割で解消） |
| `.claude/references/review-procedure.md` | 初版作成。レビュー5スキル共通の一般手順（コスト規律・モード判定・差分特定・全量分担・機械的検証・指摘の書き方・担当範囲の切り分け）をプロジェクト非依存で定義 |
| `.claude/skills/requirements/SKILL.md` | 初版作成。工程1の一般手順。§5 に**工程内整合性チェック**（機械検証／読んで確認／矛盾時の対応）を新設 |
| `.claude/skills/basic-design/SKILL.md` | 初版作成。工程2の一般手順。§5 に**工程内整合性チェック**（図↔テキスト↔上流要件の突き合わせ、非機能の対応漏れ検出）を新設 |
| `.claude/skills/detail-design/SKILL.md` | 初版作成。工程3の一般手順。§5 に**工程内整合性チェック**と §5.3 **分岐一覧の自己検証**（処理フローとの1対1突き合わせ）を新設 |
| `docs/changelog.md` | 新設。全32ファイルの変更履歴（116件）を集約し、各ファイルの変更履歴セクションを廃止（本文から計13,540字を削減） |
| `docs/documentation_rules.md` | §5.1「変更履歴は1ファイルへ集約する」を新設。§2・§3 の除外に `changelog.md` を追加。§6 の「履歴は親に集約」を撤回。§7 に履歴セクション検出を追記 |
| `scripts/check_doc_size.py` | 変更履歴セクションの復活を ERROR として検出する検査を追加。`docs/changelog.md` を除外対象に追加 |
| `CLAUDE.md` | ドキュメント規約の変更履歴ルールを「各ファイルに書かず changelog.md へ集約」に変更 |
| `README.md` | ドキュメント索引に `changelog.md` を追加 |
| `.claude/commands/fix-specs.md` | 変更履歴の追記先を `docs/changelog.md` に変更 |
| `.claude/commands/resolve-specs.md` | 変更履歴の追記先を `docs/changelog.md` に変更 |
| `CLAUDE.md` | README.md を新設し概要・セットアップ・索引を移管。本書はAI開発ルールに特化。ドキュメント規約（文字数上限）を制定 |
| `CLAUDE.md` | `/dev` をスキル化し、単体テスト用スキル `unit-test`（C1観点・実装パターン）を新設 |
| `CLAUDE.md` | 上限超過8ファイルを索引 + 個別ファイルへ分割（全81ファイルが上限内）。索引の対応表を追加 |
| `CLAUDE.md` | 単体テストが C1 100% 到達（306件・ゲート通過）。TDDを採用し7工程へ変更 |
| `docs/balance_backlog.md` | 初版作成。`open_specs.md` から「プレイテスト調整待ち」3項目（ボスラッシュ累積報酬率・スキル数値・転生ポイント数値）を移管し、転生ボーナスリセットコストを独立行に分離。深淵の塔のスケーリング係数を新規追加 |
| `docs/data/master_data.md` | `documentation_rules.md` 適用: §1・§3〜§16 をカテゴリ別5ファイル（`master/`）へ分割し、本書を索引化（19,273字 → 上限8,000字以内）。変更履歴を直近10件に整理 |
| `docs/data/master_data.md` | 未確定仕様2件を確定。§10.2 Phase 4素材のドロップ割当を**塔単位で一括適用**に確定し、塔別基準率表（通常敵／ボス・古代の欠片）へ具体化。各塔ファイル §7.1 は参照のみに変更。§18 **深淵の塔データ**を新設（基準値・スケーリング式・早見表・エンカウントプール）。§2 塔一覧に深淵の塔を追加 |
| `docs/data/towers/001_ゴブリンの塔.md` | §7.1 Phase 4素材の割当を確定: 塔単位で一括適用し、基準率は master_data §10.2 の塔別表に集約（敵別割当は行わない） |
| `docs/data/towers/002_森の塔.md` | §7.1 Phase 4素材の割当を確定: 塔単位で一括適用し、基準率は master_data §10.2 の塔別表に集約（敵別割当は行わない） |
| `docs/data/towers/003_獣の塔.md` | §7.1 Phase 4素材の割当を確定: 塔単位で一括適用し、基準率は master_data §10.2 の塔別表に集約（敵別割当は行わない） |
| `docs/data/towers/004_毒沼の塔.md` | §7.1 Phase 4素材の割当を確定: 塔単位で一括適用し、基準率は master_data §10.2 の塔別表に集約（敵別割当は行わない） |
| `docs/data/towers/005_業火の塔.md` | §7.1 Phase 4素材の割当を確定: 塔単位で一括適用し、基準率は master_data §10.2 の塔別表に集約（敵別割当は行わない） |
| `docs/data/towers/006_氷雪の塔.md` | §7.1 Phase 4素材の割当を確定: 塔単位で一括適用し、基準率は master_data §10.2 の塔別表に集約（敵別割当は行わない） |
| `docs/data/towers/007_砂漠の塔.md` | §7.1 Phase 4素材の割当を確定: 塔単位で一括適用し、基準率は master_data §10.2 の塔別表に集約（敵別割当は行わない） |
| `docs/data/towers/008_深海の塔.md` | §7.1 Phase 4素材の割当を確定: 塔単位で一括適用し、基準率は master_data §10.2 の塔別表に集約（敵別割当は行わない） |
| `docs/data/towers/009_黄昏の塔.md` | §7.1 Phase 4素材の割当を確定: 塔単位で一括適用し、基準率は master_data §10.2 の塔別表に集約（敵別割当は行わない） |
| `docs/data/towers/010_天空の塔.md` | §7.1 Phase 4素材の割当を確定: 塔単位で一括適用し、基準率は master_data §10.2 の塔別表に集約（敵別割当は行わない） |
| `docs/design/game_spec.md` | `documentation_rules.md` 適用: §2 コアシステム仕様・§3 UI構成を `systems/` 配下7ファイルへ分割し、本書を索引化（35,042字 → 上限8,000字以内）。変更履歴を直近10件に整理 |
| `docs/design/game_spec.md` | 未確定仕様3件を確定。§2.2 目標階の選択上限を「その塔の到達済み最高階+1（塔別管理・上限一致時は自動追従、オフラインでも適用）」に確定。§2.14 **深淵の塔**（無限塔・敵×1.08^(階/10)・報酬×1.20^(階/10)）を新設し、LV155〜9999 を埋める常設コンテンツとして転生の到達性を確保。§1 Phase 5 に深淵の塔を追加 |
| `docs/design/non_functional_requirements.md` | 初版作成: 性能・容量・可用性・セキュリティ・プライバシーの各要件を定義 |
| `docs/design/non_functional_requirements.md` | 要件層／設計層の分離を適用: 実現方式を `tech_performance.md`・`tech_security.md`・`tech_operations.md` へ移し、本書は目標値と受入基準のみを持つ構成に整理。§1 応答時間目標を tech側の見積りと整合（tick 1〜100tick を 800ms 等）、§3 に稼働率99%・RTO 4時間・計画停止24時間未満の必須制約を追加、§4 脅威に S-1〜S-8 の識別子を付与 |
| `docs/design/operation_requirements.md` | 初版作成: バランス改定ポリシー（上方／下方修正の告知・補填ルール）、データマイグレーション要件（Phase進行時の既存データ引き継ぎ）、障害・メンテナンス時の扱い、リリース適用フロー、サポート窓口を定義 |
| `docs/design/product_requirements.md` | 初版作成: 背景・目的、ターゲットプレイヤー、想定プレイサイクル（バランス設計の基準・仮置き）、スコープと「シングルプレイ専用」の境界、成功基準（受入観点）を定義 |
| `docs/development_process.md` | **TDDを採用**し7工程へ変更。§3.4 テストリスト作成を新設、§3.5 製造を Red-Green-Refactor 化、テストリストゲートを追加。適用はバックエンド・新規実装から（§5.2）。実装の疑義は known_issues.md へ移管 |
| `docs/development_process.md` | **単体テストゲート通過**: バックエンド全40モジュールが C1 100%（306件 PASS）。§5・§5.1 を更新 |
| `docs/development_process.md` | §6 変更管理に balance_backlog.md（仕様確定済み・数値のみ調整待ちの項目）の運用を追加 |
| `docs/development_process.md` | テスト基盤（pytest / pytest-cov）の導入に伴い §5 の工程状況を更新し、§5.1 単体テストの整備状況（C1カバレッジ）を追加 |
| `docs/development_process.md` | ドキュメント規約（documentation_rules.md）を適用範囲に追加、工程ゲートに「ドキュメント規約ゲート」を追加 |
| `docs/development_process.md` | 仕様書の分割に伴い成果物欄を更新（§3.1 design/systems/、§3.2 tech_* 5点、§3.3 tech_offline.md・data/master/） |
| `docs/documentation_rules.md` | 上限超過8ファイルの分割を完了（§8）。§6 に分割時の必須事項を追加（節番号の維持・変更履歴の親集約・索引更新・リンク検証）。`KNOWN_OVERSIZED` を空にし、超過はすべて ERROR とした |
| `docs/documentation_rules.md` | 初版作成: 文字数上限（区分A〜D）・セクション粒度・分割パターン・判定スクリプトを制定 |
| `docs/known_issues.md` | 初版作成: development_process.md §5.2 から実装の疑義5件を移管 |
| `docs/open_specs.md` | 3項目を確定し削除: **目標階の選択上限**（塔別の到達済み最高階+1・上限追従）、**Phase 4素材のドロップ割当**（塔単位で一括適用、master_data §10.2 に塔別基準率表）、**高レベル帯（LV100+）のステータス検証**（オーバーフローなし。ただしLV9999到達に約4,000億EXPが必要で転生が発動しない問題を検出し、深淵の塔〈§2.14〉の新設で解決）。プレイテスト調整待ちの3項目を `balance_backlog.md` へ移管。残り未確定8件 |
| `docs/open_specs.md` | 仕様書の分割に伴い参照先を更新（design/systems/・tech/・data/master/）。変更履歴を直近10件に整理（ドキュメント規約 §5） |
| `docs/open_specs.md` | 基本設計の見直しで判明した技術・インフラ4項目を追加: Alembic未セットアップ、退会（アカウント削除）未実装、ゲーム内お知らせ未実装、定期ジョブ実行基盤未定。残り未確定8件 |
| `docs/tech/tech_spec.md` | `documentation_rules.md` 適用: §1〜§7 をレイヤー別5ファイルへ分割し、本書を索引化（27,052字 → 上限8,000字以内）。変更履歴を直近10件に整理 |
| `docs/tech/tech_spec.md` | §2 ディレクトリ構成から docs/・diagrams/ の詳細ツリーを削除し README.md への参照に変更（ドキュメント規約 §5「重複禁止」適用・二重管理の解消）。ヘッダの概要リンクを README.md に変更 |
| `docs/tech/tech_spec.md` | 基本設計の欠落観点を補完: §5.0 API共通仕様（ベースパス・命名・日時／数値・認証要否・共通ヘッダ・HTTPステータス使い分け）を tech_api.md に新設し `GET /health` を追加。§10 性能・容量設計（tech_performance）・§11 セキュリティ設計（tech_security）・§12 運用設計（tech_operations）の3ファイルを新設。tech_architecture.md の同時実行制御は tech_tick.md を正として不変条件のみ残す形に整理 |
| `docs/tech/tech_spec.md` | 未確定仕様の確定を反映。tech_api.md: `/api/tower/select` の `targetFloor` 検証範囲（`min(塔別highestFloor + 1, totalFloors)`）と上限追従ルールを明記、深淵の塔セクション（`/api/abyss/ranking`・`towerId: "abyss_tower"` で既存 `/api/tower/*` を流用）を追加。tech_offline.md: 簡略計算でも上限追従を適用する旨を追記 |
| `docs/tech/tech_spec.md` | 目標階上限の実装反映に伴い tech_api.md を更新: `/api/tower/list` のレスポンスに `targetFloorCap`（塔ごとの選択上限）を追加し、クライアントが上限式を再実装しない旨を明記 |
| `docs/tech/tech_spec.md` | 詳細設計の欠落5観点を新規5ファイルで補完: tick進行制御（tech_tick）・乱数設計（tech_rng）・数値／丸め規約（tech_numeric）・進行状態と操作可否（tech_state）・フロントtick制御（tech_polling）。各ファイルに「分岐一覧（単体テスト観点）」と「現行実装との差異」を付与 |

## 2026-08-01

| ファイル | 内容 |
|---------|------|
| `CLAUDE.md` | 開発工程定義書を新設。6工程モデル・テスト標準を追記 |
| `docs/data/master_data.md` | §9.1 スキル振り直しコスト（LV×50G・全SP返却）、§10.3 素材ドロップ個数（通常敵: 前半階層1個/後半階層1-2個、ボス: 範囲均等確率）を新設 |
| `docs/data/master_data.md` | レビュー指摘対応: §1.4 経験値テーブルの誤値5箇所を式準拠に修正、§5 換金アイテム4種追加（古代金貨の袋〜天界の輝石）、§5.5.3 製作品「吸血の指輪」→「ブラッドリング」に改名（ドロップ品との名称衝突解消）、§8.1 限界突破の適用対象文言を式と統一 |
| `docs/data/towers/001_ゴブリンの塔.md` | レビュー指摘対応: §5 game_specリンク修正、§2 出現階層を§5プールの実出現範囲に統一、§7.1 にPhase 4素材の参照注記を追加 |
| `docs/data/towers/002_森の塔.md` | レビュー指摘対応: §5 game_specリンク修正、§2 出現階層を§5プールの実出現範囲に統一、§7.1 にPhase 4素材の参照注記を追加、§7.3 マンティコアの出現階層を20F〜に修正（master_data §5.5.1と統一） |
| `docs/data/towers/003_獣の塔.md` | レビュー指摘対応: §5 game_specリンク修正、§1 環境効果行を追加、§2 敵ID `dire_wolf` を `dire_wolf_beast` にリネーム（001とのID重複解消）、§2 出現階層を§5プールの実出現範囲に統一、§7.1 にPhase 4素材の参照注記を追加、§7.3 「戦象の铠」→「戦象の鎧」・「クリット率」→「クリティカル率」に修正 |
| `docs/data/towers/004_毒沼の塔.md` | レビュー指摘対応: §5 game_specリンク修正、§1 環境効果行を追加、§2 敵ID `poison_dragon` を `poison_drake` に統一（表示名ポイズンドレイクは維持）、§2 出現階層を§5プールの実出現範囲に統一、§7.1 にPhase 4素材の参照注記を追加 |
| `docs/data/towers/005_業火の塔.md` | レビュー指摘対応: §5 game_specリンク修正、§1 環境効果行を追加、§2 出現階層を§5プールの実出現範囲に統一、§7.1 にPhase 4素材の参照注記を追加、§7.3 「クリット率」→「クリティカル率」に修正、§7.4 白金貨の袋の倍率注記を約3.3倍に修正 |
| `docs/data/towers/006_氷雪の塔.md` | レビュー指摘対応: §5 game_specリンク修正、§2 出現階層を§5プールの実出現範囲に統一、§7.1 にPhase 4素材の参照注記を追加 |
| `docs/data/towers/007_砂漠の塔.md` | レビュー指摘対応: §5 game_specリンク修正、§2 出現階層を§5プールの実出現範囲に統一、§7.1 にPhase 4素材の参照注記を追加、§7.3 「クリット率」→「クリティカル率」に修正 |
| `docs/data/towers/008_深海の塔.md` | レビュー指摘対応: §5 game_specリンク修正、§2 出現階層を§5プールの実出現範囲に統一、§7.1 にPhase 4素材の参照注記を追加、§7.3 「クリット率」→「クリティカル率」に修正 |
| `docs/data/towers/009_黄昏の塔.md` | レビュー指摘対応: §5 game_specリンク修正、§2 出現階層を§5プールの実出現範囲に統一、§7.1 にPhase 4素材の参照注記を追加、§7.3 「クリット率」→「クリティカル率」に修正 |
| `docs/data/towers/010_天空の塔.md` | レビュー指摘対応: §5 game_specリンク修正、§2 出現階層を§5プールの実出現範囲に統一、§7.1 にPhase 4素材の参照注記を追加、§7.3 「クリット率」→「クリティカル率」に修正 |
| `docs/design/game_spec.md` | UI/UX 2項目確定: §3 数値表示フォーマット（1,000以上をK/M/B/T/Qa/Qiで短縮、小数1桁・切り捨て）、§3 通知キューのルール（FIFO・待ちキュー上限10件・エラー通知は割り込み/トーストOFFでも表示）。§2.12 オフライン中の転生を追記（発生しない・LV9999で成長停止・超過EXP切り捨て・復帰後手動） |
| `docs/design/game_spec.md` | §2.2 に確率・軽減率の上限（キャップ）を追加: クリティカル率上限100%、被ダメ軽減率・状態異常付与率・挑発率上限80%、ステータス値・回復量は上限なし |
| `docs/design/game_spec.md` | レビュー指摘対応: §2.2 状態異常付与率キャップに確定付与（基礎付与率100%）の例外を明記。§1 Phase 2から装備強化を削除、Phase 1にショップ（HPポーション購入）追加・Phase 2をショップ拡張（日替わり装備販売）に変更・認証を追加。§2.2 オフライン簡略計算切替（101tick以上）とサマリー項目（レベルアップ回数追加・ドロップアイテム削除）を実装準拠に更新。§2.4 ボスドロップ率50-100%に修正。§2.13 報酬倍率の（仮）を削除。ディレクトリ再編に伴う相対リンク切れを一括修正 |
| `docs/development_process.md` | 初版作成: 6工程の定義、V字モデル・Phase単位反復の採用、テスト標準の制定（pytest C1 100% / TestClient / Playwright E2E） |
| `docs/open_specs.md` | UI/UX 2項目（数値表示フォーマット、通知キュー）とオフライン計算2項目（簡略計算の期待値式、オフライン中の転生）を確定済みに更新 |
| `docs/open_specs.md` | レビュー指摘対応: 未管理の先送り事項4件を追加（転生数値仮置き、素材の敵別ドロップ割当、イベントダンジョン敵構成・報酬、酒場キャラ名称）。残り未確定9件 |
| `docs/open_specs.md` | 確定済み（`[x]`）項目を全削除しファイルをスリム化（本ファイルの方針「反映されたら削除」に準拠。確定内容・反映先は git 履歴を参照）。残り未確定5件 |
| `docs/open_specs.md` | 経済・バランス3項目を確定済みに更新: 素材ドロップ個数（master_data §10.3）、スキルリセットコスト（master_data §9.1）、ステータス上限値（game_spec §2.2）。ダンジョン2敵データを反映済み確認により確定済みに更新 |
| `docs/tech/tech_spec.md` | レビュー指摘対応: §6 ゲスト認証をJWT現行仕様に更新（旧UUID方式の記述を置換）、§1.1 targetFloorにnull注記、§4 FAST_CALC_THRESHOLDコメント明確化、§2 に development_process.md 追加、ヘッダリンクを新ディレクトリ構造に修正 |
| `docs/tech/tech_spec.md` | 数値表示ユーティリティに短縮表記ルールの参照を追記。tech_battle.md §4 を更新: ポーション閾値を「50%固定」→プレイヤー設定値参照に修正、§4.1 期待値計算式（期待与/被ダメ・周回解決・ポーション消費モデル）を追加、オフライン中の転生（発生しない・LV9999で成長停止）を追記 |
| `docs/tech/tech_spec.md` | 複数塔対応: `GET /api/tower/list` エンドポイント追加（解放/クリア状態含む）。`/api/tower/select` に未解放塔403の記載を追加 |

## 2026-03-15

| ファイル | 内容 |
|---------|------|
| `CLAUDE.md` | Phase 4 を「拠点建設・素材生産」に修正。diagrams/ をトップレベルへ移動 |
| `docs/data/master_data.md` | §9A 敵スキル一覧を新設（4種: 強打/咆哮/毒液/全体攻撃、Phase 5ボスラッシュWave 11+から段階導入、仮置き数値） |
| `docs/data/master_data.md` | レビュー指摘対応: ヘッダーリンク修正（新ディレクトリ構造対応）。§3.1 ポーション閾値デフォルトを50%→30%に修正。§7.2「Phase 3確定入手キャラ4体」→「初期キャラ含む4体」に修正。§10.1 旧塔名修正（魔獣の塔→獣の塔、煉獄の塔→業火の塔）。§5.5.3 吸収装備スロット制限を明確化（製作は武器/アクセのみ、ドロップは全スロット） |
| `docs/design/game_spec.md` | レビュー指摘対応: §2.3 ポーション閾値をデフォルト30%・10%刻み5択に統一。§2.1 キャラ獲得「計4体」→「新たに3体＋初期キャラ」に修正。§3 ナビゲーションにショップタブ追加（7タブ構成） |
| `docs/design/game_spec.md` | 敵・エンカウント5項目確定: §2.2 敵スキル段階導入を具体化（Phase 5ボスラッシュWave 11+から4種スキル導入、Phase 1-4は通常攻撃のみ）。§2.6 エンカウントプールにPhase 1-2共通ロジック明記。ダンジョン3-4（塔006-010）の環境効果を定義 |
| `docs/open_specs.md` | レビュー指摘対応: ファイルパス参照を新ディレクトリ構造に更新。ダンジョン3-4環境効果の未確定項目を追加 |
| `docs/open_specs.md` | 技術・インフラ2項目を確定済みに更新: アプリケーションログ仕様（ログレベル・フォーマット・ミドルウェア・マスク規則）、バックエンドエラーハンドリング（統一エラー形式・コード体系・例外ハンドラ） |
| `docs/open_specs.md` | 敵・エンカウント5項目を確定済みに更新: 敵スキル定義（4種仮定義）、Phase 1-2エンカウント（プール抽選）、複数敵体数（均等確率）、ボス行動（Phase 5から導入）、ダンジョン3-4環境効果（5塔分定義） |
| `docs/tech/tech_spec.md` | tech_battle.md §3.2 エンカウント抽選ロジック追記（重み付きプール抽選・均等確率体数決定・Phase共通ロジック）、敵スキル処理フロー追記（Phase 5ボスラッシュWave 11+、CD管理は味方と同一） |
| `docs/tech/tech_spec.md` | レビュー指摘対応: §2 ディレクトリ構成を新構造（design/tech/data/diagrams/skills）に更新。§1.1 potionAutoUseThreshold重複フィールドを削除、potionThresholdを0.1〜0.5/0.1刻みに統一。§5 ポーション閾値APIを0.1〜0.5に更新 |

## 2026-03-10

| ファイル | 内容 |
|---------|------|
| `CLAUDE.md` | 設計図6点を追加 |

## 2026-03-09

| ファイル | 内容 |
|---------|------|
| `docs/data/master_data.md` | §5.5.3 吸収装備製作レシピ（6アイテム）追加。§6.1 装備レベル決定ルール明確化。§13 製作装備レベル=最高到達階層を明記。§15.2 Wave30+マイルストーン報酬追加 |
| `docs/data/master_data.md` | §7 キャラ名確定（アカネ・シロナ・ハヤテ）、§7.2 キャラレアリティ（5段階）追加、§7.3 酒場ガチャプール・排出率テーブル追加 |
| `docs/design/game_spec.md` | §5 課金モデルを確定（完全無料）。§3 にアクセシビリティ方針を追加（ベストエフォート） |
| `docs/design/game_spec.md` | 戦闘処理詳細8項目確定: §2.2にクリティカル適用順序（DEF減算後×1.5）、ステータス計算にパッシブスキル（④装備後バフ前に乗算）追加、バフ加算上限なし・延長ルール追加、毒DOT行動前処理・ターゲットリアルタイム判定追加。§2.8にスキル発動（キャラ単位・枠1優先・初期CD=0・ID順タイブレーク）追加 |
| `docs/open_specs.md` | 14項目を確定済みに更新: キャラ名（アカネ/シロナ/ハヤテ）、キャラレアリティ（5段階）、酒場ガチャプール・排出率、イベントダンジョン3種、Wave30+マイルストーン、チュートリアル、ナビ構造、通知仕様、装備管理UI、設定画面、装備レベルルール、吸収装備レシピ。ダンジョン2敵データは方針確定・反映別途 |

## 2026-03-08

| ファイル | 内容 |
|---------|------|
| `CLAUDE.md` | 初版作成 |
| `docs/data/master_data.md` | レビュー指摘対応: §2 塔一覧にダンジョン列追加・塔003〜010を追記（TOWERS_OVERVIEW.md参照リンク追加）。§7.1 scout_001入手条件「魔獣の塔」→「獣の塔」に修正。§15.1 Wave8-10ボス名を「ダンジョン2ボス」から具体名（キングハイドラ/ポイズンドレイク/バアル）に更新 |
| `docs/data/master_data.md` | 戦闘仕様確定: §9.3 回復系統を改訂（全体回復を段階3に移動、蘇生スキルを段階4に追加）。§9.6 挑発スキルに挑発率（50%）・複数挑発時の按分ルールを追記。§9.8 状態異常テーブル新設（毒・スタン・麻痺・沈黙の4種） |
| `docs/data/skills/001_剣術系統.md` | 初版作成（master_data.md § 9.1 から分離） |
| `docs/data/skills/002_魔法系統.md` | 初版作成（master_data.md § 9.2 から分離） |
| `docs/data/skills/003_回復系統.md` | 初版作成（master_data.md § 9.3 から分離） |
| `docs/data/skills/004_強化系統.md` | 初版作成（master_data.md § 9.4 から分離） |
| `docs/data/skills/005_弱体系統.md` | 初版作成（master_data.md § 9.5 から分離） |
| `docs/data/skills/006_生存術系統.md` | 初版作成（master_data.md § 9.6 から分離） |
| `docs/data/skills/SKILLS_OVERVIEW.md` | SP効率セクションに回復系統（9SP）の例外注記を追加 |
| `docs/data/skills/SKILLS_OVERVIEW.md` | 初版作成（master_data.md § 9 から分離） |
| `docs/data/towers/001_ゴブリンの塔.md` | 初版作成 |
| `docs/data/towers/002_森の塔.md` | HP吸収装備のドロップ情報を§7.3に追加（グリフォン〜ベヒーモスの5種が対象） |
| `docs/data/towers/002_森の塔.md` | §1 テーマを「獣系」→「獣・樹木」に修正（TOWERS_OVERVIEWと統一） |
| `docs/data/towers/002_森の塔.md` | 初版作成 |
| `docs/data/towers/003_獣の塔.md` | レビュー指摘対応: §5 に Phase 3 の複数敵出現数テーブルを追加 |
| `docs/data/towers/003_獣の塔.md` | 初版作成 |
| `docs/data/towers/004_毒沼の塔.md` | §5 出現数テーブル追加、フッター注記更新 |
| `docs/data/towers/004_毒沼の塔.md` | 初版作成 |
| `docs/data/towers/005_業火の塔.md` | §5 出現数テーブル追加、フッター注記更新 |
| `docs/data/towers/005_業火の塔.md` | 初版作成 |
| `docs/data/towers/006_氷雪の塔.md` | §5 出現数テーブル追加、フッター注記更新 |
| `docs/data/towers/006_氷雪の塔.md` | §7 ヘッダーのPhase表記を「Phase 3〜」→「Phase 2〜」に統一 |
| `docs/data/towers/006_氷雪の塔.md` | 初版作成 |
| `docs/data/towers/007_砂漠の塔.md` | §5 出現数テーブル追加、フッター注記更新 |
| `docs/data/towers/007_砂漠の塔.md` | 初版作成 |
| `docs/data/towers/008_深海の塔.md` | §5 出現数テーブル追加、フッター注記更新 |
| `docs/data/towers/008_深海の塔.md` | 初版作成 |
| `docs/data/towers/009_黄昏の塔.md` | §5 出現数テーブル追加、フッター注記更新 |
| `docs/data/towers/009_黄昏の塔.md` | §7 ヘッダーのPhase表記を「Phase 4〜」→「Phase 2〜」に統一 |
| `docs/data/towers/009_黄昏の塔.md` | 初版作成 |
| `docs/data/towers/010_天空の塔.md` | §5 出現数テーブル追加、フッター注記更新 |
| `docs/data/towers/010_天空の塔.md` | 初版作成 |
| `docs/design/game_spec.md` | レビュー指摘対応: §2.6 ダンジョン2名称「深淵のダンジョン」→「荒野のダンジョン」に修正。塔3「魔獣の塔」→「獣の塔」推奨LV23-35→26-46、塔4「毒沼の塔」推奨LV30-40→42-62、塔5「煉獄の塔」→「業火の塔」推奨LV38-45→58-78に修正。ダンジョン3（極地のダンジョン: 塔6〜8）・ダンジョン4（終焉のダンジョン: 塔9〜10）の概要を追加。§2.11ボスラッシュWave6-10の塔名を正式名称（獣の塔ボス〈キングハイドラ〉等）に更新 |
