# 後工程への申し送りメモ

> [next_session.md](next_session.md)（引き継ぎ）に入り切らない、**複数セッションにまたがる申し送り**を置く。
> 1件1行。消化したら行を消す。next_session.md がポインタ専用でいられるようにするための受け皿。
> **worktree からも追記してよい**（`merge=union` で自動統合。[worktree_guide.md](../process/worktree_guide.md) §3）。
> ただし union は行順を保証しないので、**既存行の書き換えではなく末尾への追加**で書く。
> **行の削除は main で行う**（union は削除を伝播せず、worktree 側で消した行は統合時に復活する）。
> **恒久的な知見はここへ残さない**。規約・コマンド表・仕様書の正へ移してから行を消す（[spec_ownership.md](../process/spec_ownership.md)）。

## 1. Java 移行

- **残りの STEP 順序**（各 STEP の内容は [steps.md](java_migration/steps.md) §4、進捗は [java_migration.md](java_migration.md) §4 が正）: **3-B**（Phase 1: game / battle / tower）→ **4**（Phase 2: equipment / shop・日替わり）→ **5**（Phase 3: party / skill）→ **6**（切替と後始末）。3-A（auth）は 3-A-3 の製造完了ゲートまで消化済み
- **tower（3-B セグメント②）の分岐一覧は `tech_tower.md`**（索引 + `tech_tower/` 4分冊）。テストリストは tick・戦闘サービスの実装後に着手する（階進行が tick 処理内のため）
- **STEP 4 は着手前に `tech_shop.md` §7・§8 の分岐一覧が使える粒度かを確認する**。STEP 5 は製造①（パーティ・スキル操作）の移植に続けて製造②（スキル戦闘処理: skill / environment。`SkillData` へダメージ倍率・対象・状態異常のフィールドを追加）・製造③（オフライン期待値計算＝ISSUE-106）を Java で実装する
- **STEP 4・5 の移植量（2026-08-08 実測）**: 装備1,512行 / ショップ1,233行 / スキル1,100行 / パーティ461行（`routers`+`services`+`models`+`schemas`+`master_data`+テストの合計）。**領域ごとに1セグメント**へ割るとキュー1行の規模に収まる。各領域ともテストが半分以上を占めるため `test-list` → `dev` の2セッションを見込む
- **`characters.rarity` は V1 スキーマに無い**（Phase 3 の列）。`Character` Entity にも持たせていないので、STEP 5 でスキーマ追加と同時に足す
- **`uq_players_user_id` 違反に業務エラーコードは新設しない**（2026-08-09 決着）。AUTH_ 一覧に該当が無く公開APIからは到達しない経路のため、`DuplicateKeyException` をそのまま送出し 500 `INTERNAL_UNEXPECTED_ERROR` として扱う
- **`afkgame-domain` のテストモジュールは製造①-i（表層の実装）まで test-compile が通らない**（3-B の Red が未実装型を参照するため。**既存の単体306件も同モジュールでは走らない**）。`test-list.md` §7 の想定どおり。表層を入れて `test-compile` を通したあとは `-Dtest=<クラス>` で領域ごとに Green を取れる。`afkgame-web`・`afkgame-env` は影響を受けない
- **3-B の表層（①-a・①-b・差し戻しの回が定義）は各テストクラスの Javadoc「製造工程への申し送り」が正**（`BattleServiceImplTest`・`OfflineCalculatorImplTest`・`BattleSimulatorImplTest` ほか。追加型の一覧は changelog の 2026-08-11 ブロック）。製造①はこの署名に合わせ、**別名の表層を新設しない**。製造①の追加対象は `LoggerName.BATTLE("afkgame.battle")`・`LogReason.CLOCK_SKEW("clock_skew")`・`PlayerRepository#findByIdForUpdate` / `#updateTickState`・`ErrorCatalog` への `BATTLE_TICK_BUSY`(503) と `INTERNAL_MASTER_DATA_INVALID`（後者は `tech_error_handling.md` へも登録する）
- **製造①で決める3点**（①-b の申し送り）: **乱数源の生成点は `BattleSimulatorImpl#simulate` の入口**（`RandomFactory#create()` を1回 → 以降は引数で配る）、**`FloorProgression`・`FloorCatalog` の中身はセグメント②**（テストは継ぎ目だけを置いてモックする）、**クリティカル率の供給元は未定**（`tech_rng.md` §6 の節削除とあわせて決める。テストは `StatCalculator#effectiveCritRate` の実効値だけを使うので供給元に依存しない）
- **`tech_polling.md` §5 の10件は JUnit へ展開しない**（①-a で判断）。フロントは TDD 非適用（`test-list.md` §2）で、同 §5 自身が「単体レベルの検証はE2E（Playwright）に統合する」と定めている。**`integration-test` スキルの担当**。マーカー0件のままなら `check_branch_list.py --tests` の照合対象外で exit 0 は維持される
- **`httpclient5` は STEP 4（Phase 2 の Google OAuth）で `afkgame-domain/pom.xml` へ戻す**（ISSUE-905 で先行投入を削除した。技術選定は有効で、正は [tech_selection.md](java_migration/tech_selection.md) §2・[tech_backend.md](../tech/basic/tech_backend.md) §4.3）。`RestClient` の `ClientHttpRequestFactory` を Bean 構成する回に、同じコミットで依存を足す

## 2. 仕様・マスターデータ

- **Phase 4 の再開時に戻す1件**（2026-08-09・Java 移行を優先するユーザー判断で `next_session.md` のキューから外した）: **テストリスト作成** — 拠点・施設・鍛冶屋（`tech_base.md` §7・§8 の36件 + `tech_forge/` の74件）+ 限界突破（`tech_limitbreak.md` §6 の30件）。詳細設計は拠点・施設・酒場スカウト・鍛冶屋・限界突破まで完了済み
- **Phase 5 の詳細設計は3点が未作成**（仕様確定ゲートの ISSUE-1302／1306）: ①ボスラッシュ `tech_bossrush.md`、②転生 `tech_prestige.md`（いずれも処理フロー + 分岐一覧。索引は `tech_spec.md` §1 に予定行だけ置いてある）、③イベントダンジョンのマスターデータ `docs/data/master/event_dungeon.md` §19（`master_data.md` の索引に節番号だけ採番済み）。**実ファイル作成と索引のリンク張り替えは同じ変更にまとめる**（先にリンクを張ると `check_docs.py --links` が落ちる）。深淵の塔・イベントダンジョンの塔側処理は `tech_tower.md` + `tech_tower/` へ統合済みで追加不要
- **深淵の塔 461F の想定上限を再試算する**（ISSUE-1309 で `master/endgame.md` §18.1 の通常敵を `arch_dragon` LV152 の実データへ揃え §18.3 を再計算したが、**461F の「素の melee LV9999 で安定周回できる想定上限」は旧基準値（HP 4,500）時点の試算のまま**）。Phase 5 の詳細設計で行う。`balance_backlog.md` B-5 の「約115日」も同じ前提に立つ
- **Phase 5 で `bossRush`・`prestige` を実体化するときは、`tech_data.md` §1.1 のキー表と子 `tech_data/game_state.md` §1.1.3 の予約コメントの両方**を更新する（ISSUE-1311 の分割で親＝キー一覧表 + `towersCleared` のキー体系、子＝JSON 例に分かれたため）
- **`tech_numeric.md` §6「入力値の検証」2件は `PUT /api/game/settings` の Resource を作る回に消化する**（①-b で §5「丸め・クランプ・飽和」12件と分けた）
- **`tech_offline.md` §7（期待値計算式・スキル/パッシブ依存）12行は Phase 3 の製造で Red へ展開する**（差し戻しの回で追加。Phase 1 の編成では到達しないためマーカーを付けておらず、`check_branch_list.py --tests` の照合対象外）。`skill_factor`・攻撃スキル2枠・範囲攻撃 `×0.7×敵数`・被ダメ軽減の実効上限0.8・挑発の按分・回復期待値の差し引きが対象
- 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。`characters/CHARACTERS_OVERVIEW.md` §3 の3体目）を足す。製造①では塔IDが未宣言で ID を発明しないため見送った（塔6〜8 のマスターデータ追加または STEP 5 へ合流させる）

## 3. 環境・ツール

- **更新系 SQL の条件は実DBテストでしか検証されない**（サービス単体テストはモックで素通りする）。`AND used = FALSE` のような条件を足したら `RepositoryTestSupport` 継承の統合テストを同じコミットで足す
- **統合テストでフィクスチャを直接書き換えるときは `WebIntegrationTestSupport#updateFixture` を通す**（`dataSource` が `defaultAutoCommit = false` のため、素の `jdbcTemplate.update` は更新件数が返るのに値が残らない）
- **`check_java_conventions.py` の判定13（`--unused`）の現在値は WARN 13件**（`AuthSettings` 3・`GameSettings` 9・`LogKey.TOKEN`）。ゼロを強制せず**増減だけ見る**
- **境界ログのマスクが効かないパラメータが4件ある**（`logging/application.md` §3.1 規約1の固定表は `rawPassword` のみ一致し、`rawRefreshToken`・`rawToken`・`rawVerificationToken` は固定表の `refreshToken`/`token` と完全一致しないためマスクされない。`AuthServiceImpl#refresh`/`#logout`・`VerificationMailSenderImpl#send` 等の引数）。**固定表を拡張するかパラメータ名を揃えるかは規約側の決定が要る**ため、次に `logging/application.md` §3.1 を触るセッションで判断する
- **`worktree_guide.md` §5 は H2 上限（2,000字）を構造的に超えている**（3,316字。ファイル全体は 6,718/8,000 で余裕あり）。§5.4「前提と注意」が肥大の主因なので、`doc-size` で **§5 を子ファイル（`worktree_guide/session.md` 等）へ分割**する。参照元の `profile.md` §8・`next.md`・`next_session.md` §0 が §5.1〜§5.4 を節番号で指すため**節番号は維持**すること。**着手は `.claude/project/**` の doc-size（`wt/docsize-claude-project`）の統合後**（参照元の `profile.md` が同 worktree の担当領域と重なる）
