# スキル全体リファクタ — レビュー結果と改善手順書（2026-08-06）

`.claude/`（スキル16件・共有リファレンス2件・プロファイル17件）を8観点で精査し、
指摘51件を**敵対的検証**（一次ソースとの機械照合による反証の試行）にかけたうえで、
安全な22件を即時適用（Stage 1・完了）、構造的な修正を Stage 2〜3 の手順書として本書に確定した。

前回レビュー（[2026-08-04_skill-improvement.md](2026-08-04_skill-improvement.md)）で
意図的に見送った項目（`paths` 自動ロード・`when_to_use` 分離・`!command` 埋め込み等）は再提起していない。

## 1. 検証観点（8点）

| # | 観点 | 出所 |
|---|------|------|
| P1 | 公式ベストプラクティス準拠（frontmatter・500行以内・具体例・時限情報） | ユーザー指定 |
| P2 | references/ scripts/ assets/ の活用 | ユーザー指定 |
| P3 | 無理な最小構成の解消（圧縮しすぎによる曖昧化） | ユーザー指定 |
| P4 | 役割分離（スキル=一般手順 / project=固有値 の境界違反） | 自前 |
| P5 | 重複・整合性（スキル間重複・矛盾・参照の握手切れ） | 自前 |
| P6 | トリガー/description 品質（自動起動と隣接スキルの判別） | 自前 |
| P7 | 実行時コンテキスト効率（起動時の強制読込量・二度読み） | 自前 |
| P8 | 前提条件・エラー処理（初回・引数なし・ファイル欠如時の分岐） | 自前 |

公式仕様の照合結果（code.claude.com/docs/en/skills.md）: frontmatter は全フィールド任意、
`description`（+`when_to_use`）は合計1,536字が自動起動判定に常駐、SKILL.md 本文は500行以内推奨、
scripts/ は「実行のみでコンテキスト非読込」・`${CLAUDE_SKILL_DIR}/scripts/*` を `allowed-tools` に書けば許可自動化可。
**本プロジェクトは全スキルが description 250字以下・本文100行前後で、サイズ面の違反なし。**

## 2. 総評

| 評価 | 内容 |
|------|------|
| 健全 | 3層構造（SKILL=一般手順 / references=共有手順 / project=固有値）は公式の progressive disclosure と一致。全16スキルの frontmatter・骨格（§0必読→作業→工程内検証→報告→次工程→注意事項）が統一されている |
| 最大の問題 | (1) スキル→プロファイルの**セクション参照の握手切れ**が系統的に発生（機械検証されていない）。(2) **一般手順がプロファイル側へ逆流**した重複（改訂時に規範が割れる）。(3) 完了基準が**次工程ゲートの結果を要求する循環**が2工程にあった |
| scripts/ | 現状ゼロ。レビューのモード判定・差分特定・ISSUE採番は決定的手順であり、スクリプト化で LLM の手作業ミス（採番誤り・タイムゾーン差の取りこぼし）を構造的に排除できる → Stage 3 |
| assets/ | **採用箇所なしと判断**。全テンプレートは会話出力用の雛形で references が適所。実行時にコピーして使う素材が存在せず、導入は「無理な適用」になる（Stage 3 のレポート雛形生成に移行する場合のみ scripts/ 同梱で再検討） |

## 3. 敵対的検証の結果

指摘51件（サブエージェント49 + メイン精査2）を一次ソースと機械照合した。

| 判定 | 件数 | 代表例 |
|------|------|--------|
| 確定 | 38 | B-02（参照パス切れ）、C-02（図↔コード観点の二重定義）、B-04/05（完了基準の循環） |
| 降格・部分採用 | 5 | B-01「patterns ほぼ全複製」→ 実測は行一致17%（一般例+固有実例の設計意図どおり。重複行のみ削減へ降格）、A-02「手順7割重複」→ 行一致13%（構造の類似は事実） |
| 棄却（反証成立） | 8 | B-03（一致0行。要約+深掘りの正しい階層化）、B-16（コピー単位は skills/ 一式なので横断リンクは破損しない）、B-17/C-13b（check_doc_size.py はフェンス内H2を除外済み）、C-16（procedure §8 のリンクで到達可能な正しい遅延読み）、D-02（review-docs §6 は実在。grep打ち切りによる見誤り） |

自前の改善案への反証も実施: 「profile.md をセクション指定で部分読みさせる」案（A-14/B-18）は、
§番号ピン留めが改番に脆く（今回の握手切れ4件と同種の欠陥を増やす）、profile はセッション1回読みで
節約効果が小さいため**採用せず**、Stage 2 の「profile 痩身」で代替する。

## 4. Stage 1 — 適用済み（本日・17ファイル24編集）

| ID | ファイル | 修正内容 |
|----|---------|---------|
| A-01 | `skills/requirements/SKILL.md` | description から「未確定仕様を確定させたい」を除去し「未確定管理ファイルの項目確定は resolve-specs の担当」の境界宣言を追加（トリガー衝突の解消） |
| A-03 | `skills/resolve-specs/references/templates.md` | 完了報告テンプレートの手順番号 5→6（SKILL 側の実番号と一致） |
| A-04 | `project/detail-design.md` | 成果物一覧に `tech_shop.md`（処理仕様）と `tech_design_system.md`（横断規約）を追加（工程管理外だった2ファイルを収容） |
| A-05 | `project/basic-design.md` | §4見出し「Mermaid の検証」→「機械検証（Mermaid）」（SKILL が参照する表名と握手） |
| A-06 | `skills/resolve-specs/SKILL.md` | §1 に「未確定管理ファイル不在→未確定ゼロと報告して終了」の分岐を追加 |
| A-09 | `project/requirements.md` | システム別仕様の7件列挙（ui_onboarding 漏れ）→「配下全ファイル・一覧は索引が正」（増減で陳腐化しない表現） |
| B-02 | `project/test-list.md` | 入力参照先 `docs/tech/tech_*` → `docs/tech/detail/tech_*`（実在しないパスの修正） |
| B-04 | `project/dev.md` | 完了基準から「レビュー指摘対応の完了」を除去し §7 ゲート行へ「指摘対応まで完了してゲート通過」と注記（次工程の結果を要求する循環の解消） |
| B-05 | `project/integration-test.md` | 同上（`full-review` 乖離ゼロを完了基準→次工程の期待結果へ移動） |
| B-06 | `project/unit-test.md` | 揮発的統計（40モジュール・306件・1,578 stmts 等・pragma 0件）を「pytest 実行結果を正とする」へ置換（手動転記の陳腐化を構造的に排除） |
| B-08 | `skills/unit-test/SKILL.md` | §1 に「カバレッジ計測未設定→導入可否をユーザー確認」の分岐を追加（integration-test と対称化） |
| B-09 | `skills/dev/SKILL.md` | 「数値の埋め込み」を機械検証（§6.1・手段未定義）から目視確認（§6.2 #8）へ移動 |
| B-10 | `project/test-list.md` | Red 確認コマンドを対象限定 + `--no-cov` へ（全306件+カバレッジ計測の無駄を排除） |
| B-13 | `skills/test-list/SKILL.md` | references/patterns.md を「テストを書き始める前に読む」と時機を明示（unit-test と対称化） |
| B-15 | `project/integration-test.md` | conftest パスに `backend/` プレフィックスを補完 |
| C-06 | `references/review-format.md` | ISSUE採番の基準を「最新レポート」→「直下の全レポートの最大番号」（最新が「指摘なし」の場合の未定義を解消） |
| C-07 | `skills/fix-specs/SKILL.md` | §1 に3分岐（引数パス不実在／レポートゼロ／最新が指摘なし）を追加 |
| C-08 | `skills/fix-specs/SKILL.md` | §0 に review-format.md（解析の契約）を追加し、§2 の項目列挙を ISSUE表参照へ置換（欠けていた「該当箇所」「検出可能工程」を包含） |
| C-09 | `references/review-procedure.md` | §1 #2「サブエージェントは全量のみ」と #7「1体委譲可」の矛盾を「分担分割は全量のみ・1体委譲はモード不問」に整理 |
| C-12 | `skills/diagrams-review/SKILL.md` | description 末尾に「プロファイルから読み込む」文を追加（他5レビュー系と統一） |
| C-13 | `skills/doc-review/SKILL.md`・`skills/diagrams-review/SKILL.md` | §0 の profile.md 行にセクション指定（§2/§5/§6）を追加（backend-review 等と統一） |

## 5. Stage 2 — 構造的な重複・整合の解消（別セッション適用）

コスト規律（レビュー→適用の分離・工程区切りで /clear）に従い、別セッションで適用する。
各項は独立に適用可能。**適用順は上から**（依存が薄い順）。

| # | ID | 手順 |
|---|----|------|
| 2-1 | A-11/A-12 | 参照規約の新設: `_TEMPLATE.md` の frontmatter 規約節へ「スキル→プロファイルの参照は**ファイル名+見出し名**で書く（§番号のみ・名無し表参照を禁止）」を追記し、既存の曖昧参照を修正（resolve-specs「プロファイル §4」→「requirements.md §4 未確定仕様の確定」、requirements「整合性チェック」表→「固有の観点」表） |
| 2-2 | B-14/C-11 | プロファイル側の一般規則再掲を削除: `project/test-list.md` §5 規約6（1テスト1観点）、`project/unit-test.md` §5 #1 と SKILL 注意事項の重複、`project/review-docs.md` §6 ルール2〜4（fix-specs SKILL §3 と同文）、`project/review-fullstack.md` 末尾2文（SKILL 注意事項と同文） |
| 2-3 | A-07 | `project/detail-design.md` の分岐一覧ルール1〜4（SKILL §4 とほぼ逐語）を削除し、固有ルール5〜7 + プロジェクト固有の例示（HP/全滅・マスターデータ未知ID）のみ残す |
| 2-4 | A-02/A-08/A-15 | requirements ↔ resolve-specs の委譲構造化: requirements §3（対話による確定）を「未確定項目の確定対話は resolve-specs スキルの手順に従う」への委譲に置換し、resolve-specs §4 のプロファイル手順インライン再掲を削除、Grep→offset/limit 手順を resolve-specs 側の1箇所へ集約（`project/requirements.md` の同手順文も削除） |
| 2-5 | C-02 | 図↔コード観点の所有権確定: **diagrams-review が図の絡む照合をすべて担当**（procedure §7 どおり）。`project/review-fullstack.md` §3 の #2・#6・#13 等から図との照合を除き、照合先を仕様書（tech_api.md・ui*.md）へ付け替え。procedure §7 に「Phase完了ゲートで図の再確認が必要な場合は diagrams-review を併走させる」と注記 |
| 2-6 | C-03 | 全量モードの分担表を `project/review-diagrams.md`・`review-code.md`・`review-fullstack.md` へ追加（分担しない場合も「分担なし=1体全量」と宣言）。diagrams-review SKILL 内の「最大3体」をプロファイル §0 へ移す |
| 2-7 | C-01 | references の固有値抽象化: `review-procedure.md` §8 と `review-format.md` 保存先の `docs/reviews/{スキル名}/`・`python scripts/rotate_reviews.py --apply` を「プロファイル §0 の保存先/ローテーションコマンドに従う」へ置換し、4レビュープロファイルの §0 に両値を明記（6箇所の重複を4箇所へ一本化。無改造コピー規約の回復） |
| 2-8 | C-04/A-10/B-07 | 一般層の固有語ポリシー: description の「Python / FastAPI」「Vue 3 / TypeScript」「open_specs」を一般語へ置換（トリガーは意味マッチで機能する）。テスト系スキルの pytest 前提は排除せず、`_TEMPLATE.md` に「テスト系の一般例は pytest 前提。他スタックでは references を差し替える」と適用範囲を宣言 |
| 2-9 | B-01（降格） | `project/test-patterns.md` から一般 patterns.md と重複する行（骨格・pytestmark・monkeypatch 説明等）を削り、モジュール名・エラーコード・フィクスチャ対応の**固有実例のみ**へ縮約 |
| 2-10 | B-12 | 引数未指定時の「候補を提示して選んでもらう」を test-list / unit-test / integration-test の §1 へ追加（dev と対称化） |
| 2-11 | C-14/A-13 | 時限的列挙の相対化: `project/review-docs.md` §2「towers/001〜010」「skills/001〜006」→ `NNN_*` glob 表記、`project/review-diagrams.md`「Phase 3〜5」→「未実装Phaseの追加仕様」。`project/detail-design.md` の旧形式移行注記は移行完了時に削除する旨を残件管理へ |
| 2-12 | D-01 | profile.md 痩身（4,995字/5,000字 = 99.9%）: 2-2 の重複削除と §6 規律の procedure §1 への委譲分を差し引き、**4,500字以下**を目標とする（今後の追記余地の確保） |
| 2-13 | B-11 | integration-test SKILL §5.1「経路の網羅」の検証手段を定義（機械検証から目視へ移すか、手段をプロファイルで指定） |

適用後は `python scripts/check_doc_size.py` と `python scripts/check_docs.py` を実行し、
`doc-review` の差分モードで仕様書側への影響がないことを確認する。

## 6. Stage 3 — scripts/ の導入（任意・効果検証つき）

決定的手順の LLM 手作業を排除する。**可搬性維持のため、パス等は引数で受け取り固有値を持たない。**

| # | 内容 |
|---|------|
| 3-1 | `.claude/scripts/review_prep.py` を新設（レビュー系5スキル共有・プロジェクト非依存）。機能: (a) 保存先直下の最新レポート特定とモード判定、(b) レポート記録の HEAD SHA からの `git diff --name-only` による差分ファイル特定（現行の `git log --since` + ファイル名タイムスタンプ方式の TZ差・rebase 取りこぼしを解消 = C-05/C-10）、(c) 直下全レポートを走査した次 ISSUE 番号の算出（C-06 の機械化）、(d) レポート雛形（review-format 準拠・HEAD SHA 記録付き）の生成 |
| 3-2 | `review-procedure.md` §2/§3/§8 をスクリプト実行前提へ書き換え（出力をそのまま取り込む）。`review-format.md` に「モード行へ HEAD SHA を記録する」を追記 |
| 3-3 | `_TEMPLATE.md` に scripts/ の配置規約を追記: スキル専用は `skills/<名>/scripts/`、レビュー系共有は `.claude/scripts/`、固有値は引数渡し。`allowed-tools` への `Bash(python .claude/scripts/*)` 登録で許可プロンプトを省略 |
| 3-4 | 効果測定: 導入後の最初のレビュー実行で、モード判定〜雛形生成のツール呼び出し回数と誤採番の有無を効率メモで観測し、`/retro` で評価する |

見送り: 数値埋め込み検証（dev §6.2 #8）のスクリプト化は誤検出設計が重いため、必要が実証されてから。

## 7. 棄却・見送り一覧（再提起しないための記録）

| 項目 | 理由 |
|------|------|
| assets/ の導入 | 実行時にコピーして使う素材が現スキル群に存在しない（§2 総評参照） |
| profile.md のセクション指定部分読み | §番号ピン留めの脆さが節約効果を上回る。痩身（2-12）で代替 |
| unit-test SKILL §2 の表記表削除（B-03） | c1_checklist との重複は反証済み。要約+深掘りの正しい階層化 |
| c1_checklist のスキル横断リンク（B-16） | コピー単位は skills/ 一式（_TEMPLATE 明記）で破損しない |
| フェンス内H2の修正（B-17/C-13b） | check_doc_size.py がフェンス除外を実装済み |
| review-format の必読化（C-16） | procedure §8 のリンク到達で足りる正しい遅延読み（fix-specs のみ契約として §0 へ追加済み = C-08） |
| レポート雛形の assets/ 切り出し（C-15） | 5スキル共有のため共有 references が適所 |
| 2026-08-04 レビューの見送り6項目 | 前回の判断を維持（paths / when_to_use / context:fork / !command / rules分割 / ゴール駆動化） |
