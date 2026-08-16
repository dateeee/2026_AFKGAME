# AFK GAME — 実装の疑義バックログ

> テスト整備・レビューで検出した「実装と仕様の乖離」「デッドコード」「規約違反」のうち、**未対応**のものを管理する。
> 運用ルールは [development_process.md](../process/development_process.md) §6 変更管理を参照。

---

## 1. 運用ルール

- 単体テスト・結合テスト・コードレビューで検出した実装側の疑義を、対応するまでここに記録する
- 対応方針は「仕様書を実装に合わせる」か「実装を修正する」かを都度判断する（`development_process.md` §6）
- **未確定仕様**は `open_specs.md`、**数値のみ調整待ち**は [balance_backlog.md](balance_backlog.md) に置き、本書には含めない
- 対応が完了した項目は**行ごと削除する**。記録は [changelog.md](../changelog.md) と `git log` が持ち、本書は未解決だけを残す（[directories.md](../process/documentation_rules/directories.md) §10「解消したら消す」）
- **Java 移行中の扱い**: Python 実装は削除済みのため、「対象」列は**機能・仕様の所在**を指す（コード固有の疑義は削除時に消えた）。残っているのは言語を変えても残る**仕様欠落・未実装**であり、[steps.md](java_migration/steps.md) の各 STEP で該当機能を実装する際に1件ずつ解消する

## 2. 未対応の項目

| # | 対象 | 内容 | 影響度 | 検出元 |
|---|------|------|--------|--------|
| 7 | `afkgame-web`（認証API） | [tech_security.md](../tech/nonfunctional/tech_security.md) §11.6 のレート制限（`RATE_LIMIT_EXCEEDED` / 429）が未実装。`/api/auth/login`（ブルートフォース）・`/api/auth/guest`（無制限のアカウント作成でDB肥大）・`/api/auth/password-reset/request`（トークン大量発行）が無防備。**アプリ層（Bucket4j 等）で持つか、インフラ層（WAF・ALB）へ寄せるかの方針決定が先**（決めたうえで tech_security.md へ明記する） | 中（本番公開前に必須） | backend-review ISSUE-103 |
| 14 | 装備ドロップの仕様 | 装備ドロップに所持枠上限（`EQUIPMENT_STORAGE_LIMIT`=50）の扱いが無い。ショップ購入は `SHOP_INVENTORY_FULL`（400）で止まるため、同じ「装備取得」で上限の扱いが経路により非対称になる。**上限到達時の挙動（ドロップ消滅／強制オートセル／上限撤廃）の仕様確定が先**（[economy.md](../design/systems/economy.md) §倉庫） | 中（仕様欠落） | backend-review ISSUE-107 |
| 15 | 戦闘ログのDB保存 | `max-battle-log-records`（100）は設定にあるが、`battle_logs` への書き込み・読み出し・ローテーションが未実装。戦闘ログはtick応答で返すのみで、[ui.md](../design/systems/ui.md) §設定画面「上限はDB保存件数100件」に対応する保存機構が無い。**DB保存を実装するか、Phase 1〜2 では保存しない設計に確定してテーブル・設定値を削除するかの決定が先**（[tech_db/battle.md](../tech/basic/tech_db/battle.md) §1 は `entries` を**配列**と定める） | 中（仕様と実装の不一致） | backend-review ISSUE-111 |
| 16 | `views/SettingsView.vue`, `composables/usePolling.ts` | 設定「通知表示（トースト）」の `toastEnabled` は保存・永続化されるが、トーストを表示するコンポーネント・呼び出しが存在せず、ONにしても何も起きない。`TickResponse.equipmentDrops` / `equipmentAutoSold` も受け取ったまま未使用。**トーストを実装するか、Phase 2 スコープ外として設定項目を外すかの決定が先**（`ui.md` §設定画面） | 中（設定が機能していない） | frontend-review ISSUE-802 |
| 8 | `composables/useBattleLocal.ts` | 開発時フォールバック（[CLAUDE.md](../../CLAUDE.md) アーキテクチャ不変条件4「バックエンド未起動でもフロント単体で動作可」）が実質未実装のスタブで、どこからも import されていない。`VITE_USE_API=false` では初期化がスキップされ、空の画面になる（クラッシュはしない）。**不変条件が求める水準の確定が先**（ダミー状態＋簡易ローカルtickを実装するか、不変条件を「クラッシュせず起動する」へ緩めるか） | 中（不変条件と実装の乖離） | コードレビュー |
| 10 | 深淵の塔（塔マスター・戦闘・API） | 深淵の塔（[endgame.md](../design/systems/endgame.md) §2.14 の無限塔）は総階数を持たないが、塔マスターの `totalFloors` は非 null を前提にしている。Phase 5 着手時に目標階の上限 / tick の目標到達判定 / `TowerInfo.totalFloors` をまとめて null 対応させる（片側だけ対応すると判定が非対称になる） | 低（Phase 5 の前提条件） | コードレビュー |
| 11 | 全滅ペナルティ | 「**塔内取得アイテム全ロスト**」が未実装（[battle/progress.md](../design/systems/battle/progress.md)「全滅時の処理」3項目のうちEXP・ゴールドのみ）。ドロップ装備を即座に永続化しオートセル益も即ゴールドへ加算すると、取り消し対象として管理できない。仕様・[battle_flow/overview.md](../diagrams/battle_flow/overview.md) が正 | 中（仕様乖離） | diagrams-review ISSUE-515 |
| 12 | 換金アイテム（`/api/item/*`） | 換金アイテムが**ドロップも売却APIも未実装**。仕様では [item.md §5](../data/master/item.md) の換金アイテムが Phase 2〜 ドロップし、[tech_api.md](../tech/basic/tech_api.md) の `POST /api/item/sell` で Phase 2〜 売却できる（素材分は Phase 4〜）。塔ファイル §7.4 のドロップテーブルが実装に反映されていない | 中（Phase 2 の機能欠落） | doc-review ISSUE-1020 |
| 20 | `afkgame-web`（`web.xml`・`SpringMvcConfig`・`ApplicationContextConfig`・`RequestLogFilter`） | 横断処理の規約（[filter.md](../process/coding_standards_backend/filter.md) §5・[interceptor.md](../process/coding_standards_backend/interceptor.md) §4）を新設したが、雛形の gfw ロギング部品が残っている。不採用としたのは①`exceptionLoggingFilter`②`traceLoggingInterceptor`③`handlerExceptionResolverLoggingInterceptor` + `Advisor` の3件（①③は `ExceptionLogger` 依存で [exception.md](../process/coding_standards_backend/exception.md) §5 #3 に反し、コードも `e.xx.fw.*` で体系外。②は `RequestLogFilter` と二重計測）。連鎖で `exceptionLogger`・`exceptionCodeResolver` Bean も不要。④除去すると**フィルタ内の予期しない例外が記録されなくなる**ため、`RequestLogFilter` へ捕捉 + ERROR + 500（統一形式）を追加する（`filter.md` §4 #4。現在は再送出のみ）。⑤`userIdMDCPutFilter`（`SpringSecurityConfig`）は**除去済み**（backend-review ISSUE-704。移行 STEP 3-A-2 修正セグメントB） | 中（規約と実装の乖離。①〜③は挙動据え置き、④は穴塞ぎ） | 規約整備 |
| 26 | `views/ShopView.vue`・`views/SettingsView.vue` | #21（`GameView.vue`）と同じ規約違反が2画面に残っている。`ShopView` が `getShopLineup`・`postShopBuy`・`postShopBuyDaily`・`getGameState`、`SettingsView` が `putSettings` を画面から直接呼ぶ（[layering.md](../process/coding_standards_frontend/layering.md) §2 #2）。加えて `SettingsView` は `USE_API` の分岐を画面に持ち、同 §4 #1（分岐は `api/`・`composables/` に閉じる）にも反する。**ショップは専任ストアが無いため `shopStore` の新設が要り、設定は楽観更新の巻き戻しごと `gameStore` へ移す設計判断を伴う**（#21 のような単純な移送では済まない）。`errorMessage`（`@/api/errors`）の画面 import も同 §2 #2 の字面には反するが、通信ではなく整形関数なので**規約側で例外を明記するかを併せて決める** | 中（規約違反・修正対象） | 規約整備（frontend。#21 修正時に検出） |
| 27 | `main.ts` | 想定外エラーのグローバル捕捉（`app.config.errorHandler` + `unhandledrejection` リスナー）が未実装。規約（[api.md](../process/coding_standards_frontend/api.md) §6）は置き場を `main.ts` と定め「known_issues の修正対象」と書いているが、**本書に対応する行が無かった**ため登録する。捕捉後の汎用エラー表示（既存の `ConnectionBanner` に寄せるか専用の表示を足すか）を決めてから実装する | 低（想定外エラーが黙って消える） | 規約整備（frontend。#21 修正時に検出） |
| 13 | 退会API（`/api/auth/delete-account`） | 退会（アカウント削除）が未実装。[main_nav.md](../diagrams/screen_transition/main_nav.md) は退会導線を Phase 2〜 として描き、`tech_api.md`・[api_sequence/auth.md](../diagrams/api_sequence/auth.md) §14 に再認証つきの削除フローを定義済み | 中（Phase 2 の機能欠落） | diagrams-review ISSUE-509 |
