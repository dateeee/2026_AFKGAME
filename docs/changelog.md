# AFK GAME — ドキュメント変更履歴

> 全ドキュメントの変更履歴を集約したアーカイブ。**文字数上限の対象外**
> （[documentation_rules.md](documentation_rules.md) §2）。
>
> 個々のドキュメントに変更履歴セクションは置かない。改稿時は本ファイルの先頭
> （最新日付のブロック）へ1行追記する。完全な履歴は Git（`git log -- <path>`）が持つ。

---

## 2026-08-03

| ファイル | 内容 |
|---------|------|
| `docs/reviews/full-review/` | Phase 2 完了ゲートの `full-review` を実施（12件・高2/中8/低2）。機械的検証（エンドポイント24件・型15ペア・数値ハードコード・tick間隔）は指摘ゼロ。高は「設定画面アカウント欄5項目の未実装（ISSUE-104）」「`tech_polling.md` §6 の是正対象5件が未着手のまま製造完了と判定（ISSUE-111）」。12件中6件が「`ui.md` の Phase 列と実装の突き合わせ漏れ」に起因するため、`dev` の工程内検証への追加と `check_phase_scope.py` の常設化を提案 |
| `frontend/tests/e2e/tower.spec.ts` / `docs/known_issues.md` / `docs/development_process.md` | known_issues #9（L2の既存失敗）を解消。未解放の塔カードの `role="radio"` + `aria-disabled="true"` + `tabindex="-1"` は正しい表現のため実装は変えず、テスト側で無効であることを属性検証し、ハンドラのガードは `click({ force: true })` で確認する形にした。§5 の Phase 2 結合テストを「完了（L1・L2）」、§5.3 を L1 29件・L2 13件 PASS へ更新 |
| `docs/known_issues.md` / `docs/development_process.md` | 製造完了ゲートのレビュー指摘を反映（backend 18件・frontend 12件）。known_issues は解消4件（トークン用途分離・`Query.get()`・オートセル未知値・ログ設定の環境変数）を §3 へ移し、判断待ち4件（オフライン簡易計算の仕様乖離・`AUTH_*` コード移行・開発時フォールバックの水準確定・深淵の塔の `None` 対応）とE2Eの既存失敗1件を §2 へ登録。§5 は Phase 2 製造を「完了」、単体402件 PASS / C1 100%、L2 は12件PASS/1件FAILへ更新 |
| `docs/reviews/backend-review/` / `docs/reviews/frontend-review/` | 製造完了ゲートの初回コードレビューを実施（backend 18件・frontend 12件の指摘） |
| `backend/requirements.txt` | レビューの「ruff の常設化」提案に沿って `ruff` を追加（未使用import・E402・print混入の検出用。実行コマンドはファイル内コメント）。`pydantic[email]` も追加（`EmailStr` 検証） |
| `.claude/project/profile.md` / `CLAUDE.md` / `docs/spec_ownership.md` / `scripts/check_docs.py` | **ISSUE-901**: profile.md §6 #6 を CLAUDE.md と同内容（`/clear` 既定・レビュー→修正適用は別セッション）へ更新。コスト規律の正を profile.md §6 に確定して spec_ownership.md へ登録し、CLAUDE.md 側は要約 + リンクへ縮約。再発防止として重複ルール改稿時の突合ルールを profile.md §7 へ追加し、`check_docs.py --owner` の走査範囲を CLAUDE.md・`.claude/**` へ拡張 |

## 2026-08-02

| ファイル | 内容 |
|---------|------|
| `docs/development_process.md` / `docs/process/phases.md`（新設） / `docs/tech/tech_{tick,polling,performance}.md` / `docs/design/{non_functional,operation}_requirements.md` / `README.md` | **ISSUE-804①**: 残量28字まで逼迫した工程定義書を索引 + 個別ファイルへ分割。§3 工程定義（3,682字）を `process/phases.md` へ移設（節番号 §3.x 維持）、§3.x を直接参照する5ファイルのリンクを張り替え |
| `.claude/project/review-docs.md` / `.claude/project/review-diagrams.md`（新設） / `.claude/skills/diagrams-review/SKILL.md` / `.claude/project/INDEX.md` | **ISSUE-804②**: 残量44字の review-docs.md から `diagrams-review` 固有分（パラメータ・観点・重要度基準）を review-diagrams.md へ切り出し。空いた容量で fix-specs 運用ルール11（移管指摘の全文検索範囲・台帳の言及元列挙）と doc-review 観点15（`--pending --ledger` 取り込み）を追加 |
| `scripts/check_docs.py` / `README.md` / `.claude/project/profile.md` / `docs/documentation_rules.md` | 機械検証を2件常設化（レビュー2巡連続の提案）: `--pending`（決定先送りの台帳リンク検査）・`--ledger`（open_specs.md の存否と本文断定の整合検査）。コマンド表の説明を更新 |
| `diagrams/screen_transition/endgame.md` / `diagrams/screen_transition/main_nav.md` | `diagrams-review` 持ち越し分: 導線の決定先送り2行を台帳（open_specs.md #4）リンクへ置換、「タブ構成は Phase 4 から変更なし」の断定を導線確定までの据え置きに条件化、ログアウト表示条件を `ui.md`（ゲスト時は警告後に実行可）へ整合 |
| `CLAUDE.md` / `.claude/skills/fix-specs/SKILL.md` | コスト規律の改善: 工程区切りの既定を `/clear` に変更（レビュー→修正適用は別セッション）。fix-specs に `model: sonnet` を指定し、引数による ISSUE・重要度の絞り込みを明記 |
| `docs/tech/tech_api.md` / `docs/open_specs.md` | **ISSUE-801**: ISSUE-703 の移管漏れ（`tech_api.md` イベントダンジョン・お知らせ節に残っていた決定先送り2行）へ台帳リンクを付与し、既読保持先の「確定」の主語を台帳へ移管。逆方向の取り残し防止として `open_specs.md` #2・#3 の「決定時にすること」へ `tech_api.md` の該当節を追加 |
| `docs/known_issues.md` / `.claude/project/dev.md` / `.claude/project/requirements.md` | **ISSUE-802**: ISSUE-701 の走査範囲外に残っていた「`open_specs.md` は不在／未確定ゼロ」の断定3箇所を運用記述（不在＝未確定ゼロ）へ統一し、実ファイルと一致しないチェックリスト形式（`[ ]` / `[x]`）前提の記述3箇所を「一覧表 + 詳細ブロック」前提へ修正 |
| `docs/open_specs.md` / `docs/design/systems/ui.md` | **ISSUE-803**: 設計図にしか無かった4件目の未確定仕様「ボスラッシュ・イベントダンジョンへの導線（タブ追加かホーム内セクションか）」を台帳 #4 として登録（§4 詳細ブロック付き）し、正となる記述を `ui.md` ナビゲーション構造へ追加。図側2ファイルの追随は `diagrams-review` の担当 |
| `docs/development_process.md` / `CLAUDE.md` / `docs/design/game_spec.md` / `docs/balance_backlog.md` | **ISSUE-701**: `open_specs.md` 新設（ISSUE-604）に追従していなかった「未確定仕様はゼロ・ファイルは不在」の断定を、「原則ゼロ。生じた場合のみ台帳を置く（不在＝未確定ゼロ）」の運用記述へ4ファイル一括で統一（`balance_backlog.md:5` はレビュー未検出分を grep で追加検出）。**ISSUE-702**: 仕様確定ゲートの判定手段を「未確定仕様の解消」から「`open_specs.md` の未解消が対象Phaseの期限内」へ変更し、期限付き先送りを正規運用として `open_specs.md` 前書きにも明記。区分C上限内へ収めるため §5 のテスト基盤・遡及整備の2行を1行へ統合。修正後検証で `development_process.md` 内に旧表現の残留2箇所を検出し、§2.2 の工程状態表と §3.1 要件定義の完了基準（重複していた「未確定仕様の解消」を §4 ゲート参照へ委譲）も併せて修正 |
| `docs/open_specs.md` | **ISSUE-703**: 本文に残っていた同種の決定先送り2件を台帳へ移管（#2 お知らせ既読状態のクライアント保持先 / #3 難易度別到達記録 `towersCleared` のキー体系）。各項目に §1 と同形式の4行詳細ブロック（確定範囲 / 未確定範囲 / 背景 / 決定時にすること）を追加 |
| `docs/design/operation_requirements.md` / `docs/design/systems/endgame.md` / `docs/balance_backlog.md` | **ISSUE-703**: 「Phase N の基本設計で確定する」の本文記述を台帳参照へ置換。数値である「お知らせ掲示件数の上限値」は `balance_backlog.md` へ B-7 として登録（未着手Phaseのため仮置き値は「未設定」。その表記ルールを §1 に追記） |
| `docs/tech/tech_auth.md` | **ISSUE-704**: §7 のリフレッシュトークン保管先の2択（`httpOnly` Cookie または LocalStorage）を **LocalStorage に確定**（キー: `refresh_token`）。`tech_security.md` §11.7 の XSS リスク受容判断・`tech_architecture.md` の既存記述と整合し、§3 ログアウト 2. をフロント完結で破棄できる手順へ明示化 |
| `docs/spec_ownership.md` | **ISSUE-706**: 「ログアウトの挙動」行の許可列へ `tech_api.md` を追加（エンドポイント定義の正）。加えて2トピックを新規登録 — 「リフレッシュトークンの保管先」（正: `tech_auth.md`、検出パターン `httpOnly`）、「未確定仕様・調整待ち数値の管理ルール」（正: `development_process.md` §6）。いずれも複数指摘がまたがったファイル組の境界確定（`fix-specs` ルール9） |
| `README.md` | **ISSUE-705**: 「主なコマンド」表へ `check_docs.py` と `check_branch_list.py` を追加（`CLAUDE.md` が必須としているコマンドの掲載漏れ）。区分B上限内へ収めるため、直後の「ドキュメント索引」と重複するディレクトリツリーの `docs/` 配下4行を1行へ集約 |
| `scripts/check_docs.py` | **新規（プロセス改善）**: ドキュメント機械検証の常設化（リンク切れ・索引到達性・曖昧語・正の逸脱）。レビューで再発していた機械検出可能な指摘クラスを LLM 目視から移管し、仕様確定ゲートの判定に追加。初回実行で `SKILLS_OVERVIEW.md` の `000_テンプレート.md` 索引未登録を検出・解消 |
| `scripts/check_branch_list.py` | **新規（プロセス改善）**: 分岐一覧の構造検証（連番・空セル・真偽対・ループ）と、`--tests` でのテスト対応照合（docstring マーカー「`分岐: tech_<x>.md §<節> #<行>`」）。テストリストゲートの判定に追加 |
| `docs/spec_ownership.md` | **新規（プロセス改善）**: 正の所在マップ。トピック→正ファイルの宣言台帳（経験値式・ゲスト削除90日・UI境界ほか7件を初期登録）。`check_docs.py --owner` が逸脱を機械検出。重複禁止ルール（`documentation_rules.md` §5）の運用装置 |
| `scripts/check_doc_size.py` | 上限90%超のファイルに残量WARNを追加（ISSUE-608 型の「修正反映で超過」を予防し、分割・圧縮を先行させる） |
| `.claude/references/review-format.md` / `review-procedure.md` | ISSUE番号をディレクトリ内一意（100の位切り上げ採番）に変更、「検出可能工程」欄を新設（工程改善への還元用）。差分モードに前回指摘ファイルのペア全体照合を追加、常設スクリプト優先を明記 |
| `.claude/skills/fix-specs/SKILL.md` / `basic-design/SKILL.md` | 収束改善: 同一ファイル組に2件以上の指摘はペア全体を突合して境界確定、修正後検証は別コンテキストで実施。新規仕様ファイル追加時の3点（索引登録・境界明記・正の宣言）を追加、基本設計のゲート2種を並走させ修正を1パスに統合 |
| `.claude/project/` review-docs / detail-design / test-list / basic-design / profile | doc-review 観点9・11・14を `check_docs.py` へ移管、fix-specs 固有ルール（ペア突合・検証サブエージェント）を追加。分岐一覧の標準形式（4列）と検証コマンドを規定、常用コマンド表へ新スクリプト2件を登録 |
| `docs/development_process.md` / `documentation_rules.md` / `README.md` / `CLAUDE.md` | 工程ゲートの判定手段へ常設スクリプトを追加（仕様確定・設計整合・テストリスト）。正の所在マップを索引・重複禁止ルールへ接続 |
| `docs/design/systems/ui_onboarding.md` | **ISSUE-608（新規ファイル）**: `ui.md` が区分C上限まで残り27字となり以降の追記ができないため、「認証画面（Phase 2〜）」と「チュートリアル（Phase 1〜）」を切り出した（`documentation_rules.md` §6）。`ui.md` は 7,973 → 6,121字。索引（`README.md` / `CLAUDE.md` / `game_spec.md` §3）と `.claude/project/**` の対象ファイル指定（`systems/ui.md` → `systems/ui*.md`）を更新 |
| `docs/design/systems/ui.md` | **ISSUE-601**: 設定タブに「ヘッダ右端の設定導線と同一画面（導線は2箇所）」を明記。**ISSUE-602**: ログアウト行の参照を `tech_auth.md` §3 へ。**ISSUE-604**: 「その他」まとめの対象項目を `open_specs.md` 管理へ変更（本文から未確定事項を排除）。**ISSUE-605**: Phase 1 レイアウト例にボトムナビ行を追加。**ISSUE-606**: 数値表示の実装先参照を索引 `tech_spec.md` §3 から実体 `tech_structure.md` §3 へ |
| `docs/tech/tech_design_system.md` | **ISSUE-601**: `AppHeader` の構成要素をブランド・ゴールド・設定・お知らせ（Phase 3〜）へ揃え、一覧は `systems/ui.md` ヘッダ表が正と明記。**ISSUE-603**: 「その他」まとめを「検討する」から確定表現（PCは全項目を並べる）へ改め、対象項目の正を `systems/ui.md` に置いた |
| `docs/tech/tech_auth.md` | **ISSUE-602**: §3 に「ログアウト」小節を新設。リフレッシュトークン無効化・トークン破棄に加え、ゲストは LocalStorage のゲストID破棄により元データへ戻れないことを規定（`ui.md` の警告文の根拠）。API定義は `tech_api.md` が正 |
| `docs/open_specs.md` | **ISSUE-604（新規ファイル）**: 未確定仕様の管理台帳（`CLAUDE.md` 開発方針）。「Phase 3 以降のモバイルナビで『その他』へまとめる対象タブ」1件を登録。確定・反映後に行ごと削除する |
| `README.md` | **ISSUE-607**: 描画方式を「UIアイコンはSVG、アイテムは画像」へ改訂。索引に `open_specs.md` と `systems/ui_onboarding` を追加。区分B上限内へ収めるため、ディレクトリツリーの注釈と設計図索引の重複表記（ファイル名と和名の重複）を圧縮 |
| `docs/reviews/doc-review/2026-08-02_203214.md` | **新規**: ISSUE-501〜507 反映（`c0e6b3d`）の再確認レビュー。前回7件はすべて解消済みを確認。新規指摘8件（中5 / 低3）。ISSUE-504・505 の追記が根拠なき参照（`tech_auth.md` にログアウト記載なし）と未確定事項を持ち込んだ点、および編集3ファイルが文字数上限まで残り数十字である点が主 |
| `docs/tech/tech_structure.md` | **ISSUE-507**: フロントエンド構成表に「スタイル = Tailwind CSS v4」の行を追加（トークンは `tokens.css` の `@theme`）。ルーティングの役割を `router/index.ts` を正とする記述へ簡略化。区分C上限内へ収めるため、レスポンシブ設計表の「見た目の規約」行を新設のスタイル行へ統合し、「数値表示ユーティリティ」の短縮表記ルール再掲（§14 重複）を `ui.md` への参照へ置換 |
| `docs/design/systems/ui.md` | **ISSUE-502**: モーダルの表示方法を「PC: 画面中央 / モバイル: 下寄せ」に修正し、実装上の位置・閉じ方は `tech_design_system.md` §2 が正と明記。**ISSUE-503**: ヘッダ表にゴールド行を追加し、Phase 1 レイアウト例の最下部ゴールド帯をヘッダへ移動。**ISSUE-504**: アカウント欄にログアウト行を追加（ゲスト時は警告のうえ実行可）。**ISSUE-505**: タブが5項目を超える Phase 3 以降のモバイル「その他」まとめ方針を追記。**ISSUE-506**: レイアウト例の絵文字を `[金]` `[EXP]` `[!]` の文字表記へ置換し、描画方針をインラインSVG（絵文字禁止）へ改訂。上記追記で区分C上限（8,000字）を超えたため、ASCIIレイアウト例4点の枠線幅を内容幅へ正規化（余剰パディング除去・情報の削除なし）し、同一ファイル内の重複参照とナビ／モーダル記述を圧縮。副次的に「認証画面」H2 の2,000字超過も解消 |
| `README.md` / `CLAUDE.md` | **ISSUE-501**: 詳細設計の索引に `tech_design_system.md` を追加。README は区分B上限内へ収めるため、直前行と重複する `documentation_rules.md` §6 への参照を削除 |
| `docs/reviews/doc-review/2026-08-02_191502.md` | **新規**: デザインシステム導入（`4eeebf0`）後の仕様確定ゲート再確認。新規指摘7件（中5 / 低2）。前回 10件はすべて解消済みを確認。指摘はいずれも `tech_design_system.md` と既存仕様書（主に `systems/ui.md`）の突き合わせから発生 |
| `diagrams/system_architecture/application.md` | **ISSUE-401/402/403/405**: `Components` を実装の3層（`ui/` UIプリミティブ / `layout/` アプリシェル / `equipment/`）へ描き替え、実在しない6部品と `useAuth.ts` を削除。`SettingsView` `equipmentStore` `api/auth.ts` を追加。`Assets` に `tokens.css` / `main.css` を明記し、依存辺を実装の参照関係（UIプリミティブはストア非参照）に合わせて再構成 |
| `diagrams/screen_transition/modal.md` | **ISSUE-404**: モーダルの表示方法を「PC: 画面中央 / モバイル: 下寄せ」に修正し、実体が `components/ui/BaseModal` 1点であることと `tech_design_system.md` §2 が正である旨を追記 |
| `docs/reviews/diagrams-review/2026-08-02_190219.md` | **新規**: デザインシステム導入（`4eeebf0`）後の設計整合ゲート再確認。新規指摘5件（高1 / 中3 / 低1）。前回 ISSUE-301〜305 はすべて解消済みを確認 |
| `docs/tech/tech_design_system.md` | **新規**: デザインシステム（トークン / UIプリミティブ / アプリシェルの3層、禁止事項、画面追加手順）を定義。フロントの見た目の一貫性を構造で担保する |
| `docs/tech/tech_spec.md` | 関連詳細仕様の一覧に `tech_design_system.md` を追加 |
| `docs/tech/tech_structure.md` | §2 のフロント構成に `components/ui/`・`components/layout/`・`assets/styles/tokens.css` を追加。§3 レスポンシブ設計にタップ領域44px・入力16px固定・セーフエリア・`@media (hover: hover)` の規約を追加 |
| `diagrams/screen_transition/main_nav.md` | **ISSUE-301**: 設定画面に `ログアウト`（Phase 2〜・認証ユーザーのみ表示）を追加。実行後は認証フローのログイン画面へ戻る旨と `POST /api/auth/logout` の呼び出しを箇条書きに明記 |
| `diagrams/screen_transition/auth.md` | **ISSUE-303**: 退会・ログアウトの遷移先を「本図のログイン画面」と明示し、`main_nav.md` との相互参照ループを解消 |
| `docs/tech/tech_structure.md` | **ISSUE-302**: §2 の `routers/` に `abyss.py`（深淵の塔ランキング・Phase 5〜）を追加。`GET /api/abyss/ranking` の実装配置を確定 |
| `diagrams/system_architecture/application.md` | **ISSUE-302**: Routers に `abyss.py` を追加。**ISSUE-304**: `player.py` の Schemas に `SettingsResponse` を追加（`SettingsUpdate` との対を明示） |
| `diagrams/api_sequence/endgame.md` | **ISSUE-302**: §11.7 にランキング取得の担当ルーター（`routers/abyss.py`）を注記。**ISSUE-305**: 見出しを「ボスラッシュ・イベント・深淵の塔・転生」へ変更し収録フローと一致させた |
| `diagrams/api_sequence.md` | **ISSUE-305**: 索引の `character.md` の呼称を見出しに合わせて「パーティ・スキル・限界突破（Phase 3）」へ変更 |
| `docs/reviews/diagrams-review/2026-08-02_181653.md` | 設計整合ゲートの確認レビュー（差分モード）。ISSUE-201〜204 の解消を全件確認し、新規指摘5件（高1・中2・低2）を記録 |
| `diagrams/system_architecture/application.md` | **ISSUE-201**: Schemas サブグラフを `backend/app/schemas/` の実装配置へ修正。実在しない `character.py` を削除し、`tower.py`・`equipment.py`（Phase 2〜）・`shop.py` を追加。`CharacterResponse` / `GameStateResponse` を `player.py` へ、存在しない `BattleLogEntry` を削除、`TokenResponse` → `AuthResponse`。Phase 3〜5 の追加スキーマは図に描かない旨を注記 |
| `docs/tech/tech_structure.md` | **ISSUE-202**: §2 の `routers/` ツリーに `party.py`（Phase 3〜）・`boss_rush.py`・`prestige.py`（Phase 5〜）を追加。システム構成図が描く11ルーターと一致させた |
| `diagrams/api_sequence/auth.md` | **ISSUE-203**: 「ログアウト」シーケンス（`POST /api/auth/logout` → RefreshToken 無効化 → トークン破棄）を「トークンリフレッシュ」の直後に追加 |
| `diagrams/api_sequence/core.md` | **ISSUE-203**: §3.5「設定変更」（`PUT /api/game/settings`）を新設。保存ボタン無し=変更即時反映、未指定フィールドは不変、`autoSellRarity` は null でOFFに戻せることを明記 |
| `diagrams/api_sequence/character.md` | **ISSUE-203**: §6.5「パーティ編成フロー」（`PUT /api/party/edit`、Phase 3）を追加。タイトルを「パーティ・スキル・限界突破」へ変更 |
| `diagrams/api_sequence/endgame.md` | **ISSUE-203**: §11.7「深淵の塔ランキング」（`GET /api/abyss/ranking`、Phase 5）を追加。同階は先着上位・登録は認証ユーザーのみを明記 |
| `diagrams/api_sequence.md` | 索引の節一覧に 3.5 / 6.5 / 11.7 を追加 |
| `diagrams/screen_transition/main_nav.md` | **ISSUE-204**: `退会確認 --> ログイン画面` を削除。`ログイン画面` が `メインナビ` の子状態として暗黙生成されるのを防ぎ、遷移先が `screen_transition/auth.md` の認証フローである旨を箇条書きへ移動 |
| `docs/reviews/diagrams-review/2026-08-02_175941.md` | 設計整合ゲートの**確認レビュー**（`diagrams-review` 差分モード）を追加。前回指摘13件の解消を全件確認したうえで、新規指摘4件（高1 / 中2 / 低1）。ISSUE-201: システム構成図の Schemas に実在しない `character.py`、実装済み3ファイルの欠落。ISSUE-202: `tech_structure.md` の routers 一覧に `party.py`・`boss_rush.py`・`prestige.py` が無い。ISSUE-203: APIシーケンス図に `POST /api/auth/logout`・`PUT /api/game/settings`（実装済み）ほか4フローが無い。ISSUE-204: 画面遷移図の分割で `ログイン画面` がメインナビの子として描画される。機械検証（Mermaid構文・リンク切れ・TODO残存・ER図↔models・図↔`tech_api.md`↔routers）はすべて OK |
| `.gitignore` | `.claude/worktrees/` を追加。サブエージェント用ワークツリーの残骸が未追跡ファイルとして残るため |
| `docs/reviews/**` | **ディレクトリ構成を変更**。フラットな21ファイルを `docs/reviews/{スキル名}/YYYY-MM-DD_HHMMSS.md` へ再配置し（`doc-review` 14件 / `diagrams-review` 5件 / `full-review` 2件）、ファイル名から種別プレフィックスを削除。直下10件を超える `doc-review` の古い4件を `doc-review/archive/` へ退避（削除はしていない） |
| `docs/documentation_rules.md` | **§9「レビュー結果アーカイブの運用」を新設**。1スキル=1ディレクトリ・直下は最新10件・超過分は `archive/` へ退避（削除しない）・差分モードで読むのは直下の最新1件のみ、を規定。§9.2 にディレクトリ分割だけでは件数の増加が止まらない理由を明記。§2・§3 の除外行から §9 を参照 |
| `scripts/rotate_reviews.py` | **新規**。レビュー結果のローテーション（引数なし=退避対象の確認、`--apply`=`archive/` へ移動、`--list`=全件と文字数）。移動は `git mv` で行い履歴を維持する |
| `README.md` | コマンド表に `rotate_reviews.py` を追加。ディレクトリ構成の `reviews/` 説明を新構成へ更新 |
| `.claude/references/review-format.md`・`review-procedure.md` | 保存先を `docs/reviews/{スキル名}/YYYY-MM-DD_HHMMSS.md` へ変更。モード判定は保存先ディレクトリ直下の最新1件（`archive/` を見ない）と明記し、出力手順にローテーション実行を追加 |
| `.claude/project/review-docs.md`・`review-code.md`・`review-fullstack.md`・`_TEMPLATE.md`・`profile.md` | §0 のパラメータ表を「prefix」から「保存先ディレクトリ」へ置換。`fix-specs` の対象を `docs/reviews/doc-review/` 直下の最新へ変更 |
| `diagrams/system_architecture.md` | 設計図レビュー指摘ISSUE-110。7,967字で追記余地が無かったため、索引 + `system_architecture/`（`application.md` / `tick_flow.md` / `authority.md` / `deployment.md`）へ分割。他5図と同じハブ&スポーク構成に統一 |
| `diagrams/system_architecture/deployment.md` | 設計図レビュー指摘ISSUE-110。**新規**。本番構成（AWS）の図を追加。CloudFront/S3 によるSPA配信、EC2（Nginx → uvicorn → EBS上のDB、OS cron）、バックアップ経路、別オリジンである旨を図示（数値・設定値は `tech_operations.md` §12 が正） |
| `diagrams/system_architecture/application.md` | 設計図レビュー指摘ISSUE-101・ISSUE-104。Models に `shop.py`（ShopDailyState / ShopDailySlot）を追加し `item.py` から ShopDailyState を削除。Services を実装配置に合わせ、`shop_service.py`→`shop_daily_service.py`、実在しない `tower_service.py` を削除（塔・エンカウントは `battle_service.py`）、`game_state_builder.py` を追加。DB は「SQLite（local / production 初期）→ §12.4 の判断ラインで PostgreSQL」へ修正（Phase での切替という誤りを解消） |
| `diagrams/system_architecture/tick_flow.md` | 設計図レビュー指摘ISSUE-101。実在しない `tower_service.py` への参照を `master_data/`（塔・敵マスター）へ差し替え |
| `diagrams/screen_transition.md` | ISSUE-102・103 の追記で 8,387字（区分C 8,000字）を超えたため、索引 + `screen_transition/`（`auth.md` / `main_nav.md` / `endgame.md` / `modal.md`）へ分割。これで全6図がハブ&スポーク構成になった |
| `diagrams/screen_transition/` | 設計図レビュー指摘ISSUE-102・103・105・106・113。ヘッダ（全画面共通）の注記とお知らせ画面（Phase 3〜）を追加。設定画面にアカウント欄4項目（問い合わせ先・規約類・クレジット・退会）と退会確認（再認証→削除確認）の遷移を追加。ログ表示件数の `200` を削除し `20/50/100`（上限=DB保存100件）へ修正。Phase 5 タブ構成から仕様に無い「ボスラッシュ・イベント」を外し、導線は Phase 5 の基本設計で確定する旨を注記。冒頭のUI仕様リンクのラベルを参照先（`systems/ui.md` §3）に一致させた |
| `diagrams/api_sequence/auth.md` | 設計図レビュー指摘ISSUE-107。実装済みで図に無かった `POST /api/auth/register` と `GET /api/auth/verify-email` のシーケンスを追加 |
| `diagrams/api_sequence/base.md` | 設計図レビュー指摘ISSUE-108。強化フローの例示値 `iron_sword_001` を `sword_001` へ修正（Equipment.id は UUID、`iron_sword` はベース装備に存在しない） |
| `diagrams/api_sequence/gameplay.md` | 設計図レビュー指摘ISSUE-111。`GET /api/shop/lineup` の `daily` レスポンス例を実装の `ShopDailyItemResponse` に合わせ12項目へ修正（category / name / slot / statDef / statHp / statSpd を追加） |
| `diagrams/er_diagram/player.md` | 設計図レビュー指摘ISSUE-109。`TowerClearRecord.highest_floor_at` に Phase 5〜 の注記を追加（未実装列であることを図から判別できるようにした） |
| `diagrams/class_diagram.md` | 設計図レビュー指摘ISSUE-112。索引の item 行から列挙型 `EquipSlot`・`EquipCategory` を外して主要クラスのみに統一し、列挙型は各ファイル参照である旨を明記 |
| `README.md` / `CLAUDE.md` / `.claude/project/basic-design.md` | 設計図レビュー指摘ISSUE-110。システム構成図・画面遷移図の索引化に伴い、設計図の索引・図数表記（4図→6図）・検証対象を更新 |
| `scripts/check_doc_size.py` | `.claude/worktrees/`（エージェント用の作業コピー。リポジトリの複製で成果物ではない）を走査対象から除外。旧スナップショットの上限超過・変更履歴セクションが ERROR として大量に出ていたため |
| `docs/reviews/diagrams-review/2026-08-02_171929.md` | 設計整合ゲート（`diagrams-review` 差分モード）の結果を追加。指摘13件（高3 / 中7 / 低3）。Mermaid構文・リンク切れ・図↔モデルの機械照合はすべて OK |
| `docs/tech/tech_spec.md` | レビュー指摘ISSUE-001。§8「今後の検討事項」のデプロイ先の選定を `[x]` へ変更し、AWS（EC2 1台 + S3/CloudFront）確定と `tech_operations.md` §12.1 への反映を明記（Vercel/Render/Railway/VPS の候補列挙を削除） |
| `docs/data/master/item.md` | レビュー指摘ISSUE-002。§4.2 の固定商品表12件（`wooden_sword`〜`hero_amulet`）を**削除**。ベース装備一覧に存在しないIDで、`tech_shop.md` の生成方式とも矛盾していたため。節名を「日替わり装備（Phase 2〜）」とし、抽選対象・生成手順・算出式・設計方針の正へのリンク表に置換 |
| `docs/data/master_data.md` | レビュー指摘ISSUE-002。索引から `master/item.md` の内容欄「日替わり候補プール」を削除（正は `master/equipment.md` §6.0） |
| `docs/tech/tech_operations.md` | レビュー指摘ISSUE-003。§12.1 のバックアップ行を「方式・頻度・保持期間・保管先は §12.5 が正」に変更し、§12.5 の方式を**論理バックアップ（`VACUUM INTO`／`pg_dump`）と EBS 日次スナップショットの併用**として確定。復旧は論理バックアップを第一手段とする根拠と、実行を §12.6 と同じ OS cron で行うことを追記 |
| `docs/tech/tech_state.md` | レビュー指摘ISSUE-004。§1.1 不変条件の階の範囲と §5 分岐#4 を `min(塔別 highestFloor + 1, 総階数)` へ修正（従来は総階数のみで、到達済み最高階+1 超過の 400 分岐が欠落していた）。#4 は上限がどちら側で決まる場合も試験する旨を追記 |
| `docs/design/systems/endgame.md` | レビュー指摘ISSUE-005。§2.13 に、難易度別の到達記録を `towersCleared` へ保持するキー体系は Phase 5 の基本設計で確定する旨を追記 |
| `docs/tech/tech_api.md` | レビュー指摘ISSUE-005・006。イベントダンジョン節に難易度別到達記録のキー体系の確定担当を、お知らせ節に既読状態のクライアント保持先の確定担当（基本設計）とマスター項目定義の担当（詳細設計）を追記 |
| `docs/design/operation_requirements.md` | レビュー指摘ISSUE-006・010。§3.1 にお知らせマスターの項目定義・掲示件数上限は Phase 3 詳細設計、既読保持先は Phase 3 基本設計で確定する旨を追記。ゲスト削除の告知不可の説明から実数値「90日」を外し `tech_auth.md` を正として参照 |
| `docs/design/systems/ui.md` | レビュー指摘ISSUE-007。「ナビゲーション構造」に**ヘッダ（全画面共通）**を新設（タイトル／お知らせ＝Phase 3／設定、保存操作は置かない）。参照のみで未定義だった「全画面共通のヘッダ」を解消し、Phase 1 レイアウト図から `[セーブ]` を削除 |
| `docs/design/product_requirements.md` | レビュー指摘ISSUE-008。§6（アセット調達方針）末尾に残っていた旧§6「未確定事項」の1行を §3 想定プレイサイクルの直下へ移動 |
| `docs/documentation_rules.md` | レビュー指摘ISSUE-009。§5.1 の移行前後比較表から、削除済みの `open_specs.md` 行を削除 |
| `docs/reviews/doc-review/2026-08-02_162707.md` | Phase 2〜5 要件定義の仕様確定ゲート結果（差分モード・指摘10件: 高4/中3/低3） |
| `docs/open_specs.md` | **ファイルごと削除**。Phase 2〜5 の未確定仕様8件をすべて確定し、Phase 1〜5 の未確定仕様がゼロになったため（本書の方針「全項目が解消されたらファイル自体を削除」に準拠） |
| `docs/tech/tech_operations.md` | 要件定義でデプロイ先を確定。§12.1 に**本番構成（AWS）**を新設（フロント=S3+CloudFront／API=EC2 1台に Nginx+FastAPI／DB=同EC2のEBS上／定期ジョブ=OS cron／バックアップ=EBS日次スナップショット）。マネージドコンテナ（App Runner・ECS Fargate）を不採用とする理由（FSが揮発し SQLite と OS cron を継続できない）を明記。§12.6 の「デプロイ先が未定」注記を解消し、退会削除処理を Phase 2 実装として確定 |
| `docs/design/non_functional_requirements.md` | 退会（アカウント削除）を確定。**§5.1 を新設**し Phase 2 実装・設定画面からの導線・再認証による誤操作防止・全ゲームデータの即時物理削除（猶予期間なし）を定義。「規約類」行の宙に浮いた `open_specs.md` 参照を設定画面への掲示に置換 |
| `docs/design/operation_requirements.md` | ゲーム内お知らせを確定。**§3.1 を新設**し Phase 3 実装・マスターデータ配信（DBテーブル/管理画面なし）・ヘッダからの参照と未読件数・既読はクライアント保持・掲示件数に上限を設けることを定義。§1 の「プレイテスト後に調整」の参照先を `balance_backlog.md` へ修正 |
| `docs/design/product_requirements.md` | §6「未確定事項」を**アセット調達方針**へ置換。BGM/SE=商用可のフリー素材、キャラ絵/背景=AI生成、外注・有償素材は不使用。ライセンス一覧のリポジトリ常設と設定画面クレジット掲示を定義 |
| `docs/design/systems/endgame.md` | イベントダンジョン（§2.13）に**進行モデル**と**敵プールの調達**を追加。塔と同じ階層制で1ダンジョン10階・10階ボス撃破でクリア・周回自由、難易度ごとに到達最高階を個別管理。雑魚は塔1〜10の既存敵を難易度別の固定ステータスで流用し、10階ボスのみイベント専用9体（3種×3難易度）を新規定義 |
| `docs/design/systems/ui.md` | 設定画面に**アカウント欄（Phase 2〜）**を追加（問い合わせ先・規約類・クレジット・退会。退会は最下部へ視覚的に分離）。**お知らせ（Phase 3〜）**を追加（ヘッダアイコン＋未読バッジ、モーダルで操作を強制しない） |
| `docs/design/game_spec.md` | §1 Phase 2 に「退会（アカウント削除）」、Phase 3 に「ゲーム内お知らせ」を追加。§5 末尾の未確定管理の記述を「Phase 1〜5 全確定」へ更新 |
| `docs/data/master/character.md` | §7.3 に**命名規約**を追加。酒場専用16体は2〜4文字のカタカナ和風名、IDは §7.1 と同じ `<役割英名>_<連番3桁>`（`hero`/`mage`/`healer`/`scout`）。実名の定義は Phase 4 の詳細設計 |
| `docs/tech/tech_api.md` | イベントダンジョン節の「入退場APIの方式は未確定」を解消。進行が塔と同じ階層制に確定したため `/api/tower/*` へ難易度パラメータを足して再利用する方針を明記（確定は Phase 5 の基本設計） |
| `docs/tech/tech_security.md`・`docs/tech/tech_numeric.md` | 確定済みの「目標階の選択上限」を参照したまま残っていた記述を修正。`1 <= targetFloor <= min(塔別 highestFloor + 1, 総階数)` を明記し `tech_api.md` を正とした。デプロイ先の未定注記も `tech_operations.md §12.1` への参照へ置換 |
| `README.md`・`CLAUDE.md`・`docs/development_process.md`・`docs/balance_backlog.md`・`docs/known_issues.md`・`.claude/project/{requirements,dev,review-docs}.md` | `open_specs.md` 削除に伴う参照更新。索引から除外し、管理フロー自体は「未確定が生じたら同名で作成する」として維持 |
| `docs/tech/tech_operations.md` | §12.4 のツール欄を「未セットアップ」から実配置（`alembic.ini` / `env.py` が `app.config.DATABASE_URL` を参照）へ更新 |
| `docs/tech/tech_structure.md` | Phase 2 日替わりショップの製造に追従。`models/shop.py`・`services/shop_daily_service.py`・`alembic.ini` / `alembic/` の内訳を追加 |
| `diagrams/api_sequence/gameplay.md` | §5 ショップ購入フローの「Phase 2後半・未実装」注記を解消。`GET /api/shop/lineup` の応答に `daily` / `dailyResetAt` を、購入側に鮮度判定の注記を追加 |
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
