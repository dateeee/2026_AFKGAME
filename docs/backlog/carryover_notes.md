# 後工程への申し送りメモ

> [next_session.md](next_session.md)（引き継ぎ）に入り切らない、**複数セッションにまたがる申し送り**を置く。
> 1件1行。消化したら行を消す。next_session.md がポインタ専用でいられるようにするための受け皿。
> **worktree からも追記してよい**（`merge=union` で自動統合。[worktree_guide.md](../process/worktree_guide.md) §3）。
> ただし union は行順を保証しないので、**既存行の書き換えではなく末尾への追加**で書く。
> **行の削除は main で行う**（union は削除を伝播せず、worktree 側で消した行は統合時に復活する）。
> **恒久的な知見はここへ残さない**。規約・コマンド表・仕様書の正へ移してから行を消す（[spec_ownership.md](../process/spec_ownership.md)）。

## 1. Java 移行

- **移行 STEP の順序**（内容の正は [steps.md](java_migration/steps.md) §4、進捗の正は [java_migration.md](java_migration.md) §4）: 3-A-2（register / login / logout。`BCryptPasswordEncoder` strength 12 と `SecurityConfig` の認証不要パス追加。初期化は 3-A-1c の手順2以降を再利用＝`tech_auth.md` §8 冒頭）→ 3-A-3（link-account / verify-email / password-reset）→ 3-B（Phase 1: game / battle / tower）→ 4（Phase 2: equipment / shop・日替わり）→ 5（Phase 3）→ 6（切替と後始末: Vite プロキシ・`launch.json`・デプロイ手順・本ファイル群の整理）
- 各 STEP の申し送り: 分岐一覧の所在 — game は `tech_state`/`tech_tick`/`tech_polling` §5、battle は `tech_battle`/`tech_rng`/`tech_numeric` §5、**tower は `tech_tower.md`（索引 + `tech_tower/` 4分冊。2026-08-10 詳細設計完了）**。tower のテストリストは tick・戦闘サービスの実装後に着手する（階進行が tick 処理内のため）。STEP 4 は着手前に `tech_shop.md` §7・§8 の分岐一覧が使える粒度かを確認する。STEP 5 は製造①（パーティ・スキル操作）の移植に続けて製造②（スキル戦闘処理: skill / environment。`SkillData` へダメージ倍率・対象・状態異常のフィールドを追加）・製造③（オフライン期待値計算＝ISSUE-106）を Java で実装する
- **STEP 4・5 の移植量（2026-08-08 実測）**: STEP 4 = 装備1,512行 / ショップ1,233行、STEP 5 = スキル1,100行 / パーティ461行（`routers`+`services`+`models`+`schemas`+`master_data`+テストの合計）。**領域ごとに1セグメント**（装備 / ショップ / パーティ / スキル）へ割るとキュー1行の規模に収まる。各領域ともテストが半分以上を占めるため `test-list` → `dev` の2セッションを見込む
- **`characters.rarity` は V1 スキーマに無い**（Phase 3 の列）。`Character` Entity にも持たせていないので、Phase 3 の移植（STEP 5）でスキーマ追加と同時に足す
- **`uq_players_user_id` 違反に業務エラーコードは新設しない**（2026-08-09 決着）。AUTH_ 一覧に該当が無く公開APIからは到達しない経路のため、`DuplicateKeyException` をそのまま送出し 500 `INTERNAL_UNEXPECTED_ERROR` として扱う（3-A-2 の register でも同じ判断を使う）
- **STEP 6 で `tech_db/` の「実装:」行を Entity 参照へ替えても `check_schema_triple.py` は止まらない**（DDL はテーブル名で対応づけるため）。Java 側でスキーマの正を持つのは Flyway の `V1__initial_schema.sql`（照合仕様の正はスクリプトの docstring）
- **`MailSettings` の7フィールドは読み手が未実装**（2026-08-10・ISSUE-803。`known_issues.md` #6 と対）。STEP 3-A-3（メール送信）の完了条件へ「7フィールドすべてに読み手ができること」を入れる。3-A-3 を終えてなお未参照が残るフィールドは、その時点で削除する
- **`httpclient5` は STEP 4（Phase 2 の Google OAuth）で `afkgame-domain/pom.xml` へ戻す**（2026-08-11・ISSUE-905 で先行投入を削除した）。技術選定そのものは有効で、正は [tech_selection.md](java_migration/tech_selection.md) §2・[tech_backend.md](../tech/basic/tech_backend.md) §4.3。`RestClient` の `ClientHttpRequestFactory` を Bean 構成する回に、同じコミットで依存を足す

## 2. 仕様・マスターデータ

- **バランス調整バックログ B-9 の波及**: 酒場・鍛冶屋のしきい値方式を廃止し5施設とも全10LV定義にした結果、**`tech_base.md` §8 の分岐一覧が16→15件**になっている。Phase 4 のテストリストは新番号を起点にする
- **未確定仕様はゼロ**（2026-08-08。`open_specs.md` は削除済み・不在＝未確定ゼロ）。Phase 5 の基本設計前に仕様確定ゲート（`doc-review` → `fix-specs`）を一度通す。主な照合対象は `towersCleared` のキー体系（正は `tech_data.md` §1.1）と Phase 5「探索」タブへの導線集約（正は `systems/ui.md`）
- **Phase 4 の再開時に戻す1件**（2026-08-09・Java 移行を優先するユーザー判断で `next_session.md` のキューから外した）: **テストリスト作成** — 拠点・施設・鍛冶屋（`tech_base.md` §7・§8 の36件 + `tech_forge/` の74件）+ **限界突破**（`tech_limitbreak.md` §6 の30件）。詳細設計は拠点・施設・①酒場スカウト・②鍛冶屋・③限界突破まで完了済み（③は 2026-08-11 に `tech_limitbreak.md` で確定。④ダンジョン3＝塔6〜8のマスターデータは既に `docs/data/towers/` と索引に登録済みで、この行は誤って残っていたもの）
- **Phase 5 の詳細設計は3点が未作成**（2026-08-11・仕様確定ゲートの ISSUE-1302／1306）: ①ボスラッシュ `tech_bossrush.md`、②転生 `tech_prestige.md`（いずれも処理フロー + 分岐一覧。索引は `tech_spec.md` §1 に予定行だけ置いてある）、③イベントダンジョンのマスターデータ `docs/data/master/event_dungeon.md` §19（`master_data.md` の索引に節番号だけ採番済み）。**実ファイル作成と索引のリンク張り替えは同じ変更にまとめる**（先にリンクを張ると `check_docs.py --links` が落ちる）。深淵の塔・イベントダンジョンの塔側処理は `tech_tower.md` + `tech_tower/` へ統合済みで追加不要
- **深淵の塔の基準値を改定した**（2026-08-11・ISSUE-1309。`master/endgame.md` §18.1 の通常敵を `arch_dragon` LV152 の実データへ揃え、§18.3 早見表を再計算）。**461F の「素の melee LV9999 で安定周回できる想定上限」は旧基準値（HP 4,500）時点の試算のまま**なので、Phase 5 の詳細設計で再試算する。`balance_backlog.md` B-5 の「約115日」も同じ前提に立つ
- **`tech_data.md` §1.1 の分割は完了**（2026-08-11・ISSUE-1311 消化）。JSON 例は `tech_data/game_state.md` の §1.1.1 プレイヤー状態 / §1.1.2 キャラクター / §1.1.3 装備と予約キーが持ち、**親 §1.1 はトップレベルキー一覧表と `towersCleared` のキー体系（正）**。Phase 5 で `bossRush`・`prestige` を実体化するときは、**親のキー表の行と子 §1.1.3 の予約コメントの両方**を更新する
- 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。`characters/CHARACTERS_OVERVIEW.md` §3 の3体目）を足す。製造①では塔IDが未宣言で ID を発明しないため見送った（塔6〜8 のマスターデータ追加または移行 STEP 5 へ合流させる）

## 3. 環境・ツール

- **`afkgame-initdb` は surefire・failsafe とも `<skip>true</skip>` にしてある**（2R-B）。SQL のみで `src/test` を持たないため、親POMで surefire に `groups`/`excludedGroups` を与えると `require TestNG, JUnit48+ or JUnit 5` でビルドが落ちる（**増分ビルドのときだけ出る**）。このガードを外さないこと
- **ログ3種別（規約 `coding_standards_backend/logging.md`）は2026-08-10に実装済み**（`LayerLoggingInterceptor`・`afkgame.comm`・3appender等。main `701e67a`）。**パラメータ名によるマスク（application.md §3.1 規約1の固定表）は `rawPassword` のみ一致し、`rawRefreshToken`・`rawToken`・`rawVerificationToken` は固定表の `refreshToken`/`token` と完全一致しないためマスクされない**（`AuthServiceImpl#refresh`/`#logout`/`VerificationMailSenderImpl#send` 等の引数）。固定表を拡張するかパラメータ名を揃えるかは規約側の決定が要るため、次に `logging/application.md` §3.1 を触るセッションで判断する
- **Java の Javadoc が指すドキュメントパスは `check_docs.py` の走査対象外**（Markdown しか見ない）。ドキュメントを移動・改名したら `backend/**/*.java` のコメント内パスも grep して直すこと。2026-08-09 の配置換えでは `domain_service.md` を指したままの2件（`JwtService`・`VerificationMailSender`）が残り、翌タスクまで自動検出されなかった
- **`worktree_guide.md` §5 は H2 上限（2,000字）を構造的に超えている**（2026-08-11 の `/retro` 反映後で 3,284字。ファイル全体は 6,718/8,000 で余裕あり）。§5.4「前提と注意」が肥大の主因なので、`doc-size` で **§5 を子ファイル（`worktree_guide/session.md` 等）へ分割**する。参照元は `profile.md` §8・`next.md`・`next_session.md` §0 が §5.1〜§5.4 を節番号で指しているため、**分割時は節番号を維持**すること
