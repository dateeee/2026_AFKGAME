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

## 2. 仕様・マスターデータ

- **バランス調整バックログ B-9 の波及**: 酒場・鍛冶屋のしきい値方式を廃止し5施設とも全10LV定義にした結果、**`tech_base.md` §8 の分岐一覧が16→15件**になっている。Phase 4 のテストリストは新番号を起点にする
- **未確定仕様はゼロ**（2026-08-08。`open_specs.md` は削除済み・不在＝未確定ゼロ）。Phase 5 の基本設計前に仕様確定ゲート（`doc-review` → `fix-specs`）を一度通す。主な照合対象は `towersCleared` のキー体系（正は `tech_data.md` §1.1）と Phase 5「探索」タブへの導線集約（正は `systems/ui.md`）
- **Phase 4 の再開時に戻す3件**（2026-08-09・Java 移行を優先するユーザー判断で `next_session.md` のキューから外した）: ①**③限界突破の詳細設計** — `POST /api/character/limit-break` を `tech_limitbreak.md`（新規）へ。素材＝同一 `master_id` のキャラ1体で `limit_break` +1（上限5回）。起点は `master/character.md` §8・§8.1、可否は `tech_state.md` §4、`canLimitBreak` は `tech_scout.md` §6。`characters.master_id` は Phase 4 で追加する未実装列 ②**④ダンジョン3（塔6〜8）のマスターデータ** — `docs/data/towers/` へ3ファイル追加し `TOWERS_OVERVIEW.md`・`master_data.md` の索引を更新（書式は `009_黄昏の塔.md` に倣う） ③**テストリスト作成** — 拠点・施設・鍛冶屋（`tech_base.md` §7・§8 の36件 + `tech_forge/` の74件）。**詳細設計は拠点・施設・①酒場スカウト・②鍛冶屋まで完了済み**
- 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。`master/character.md` §7.1 の3体目）を足す。製造①では塔IDが未宣言で ID を発明しないため見送った（塔6〜8 のマスターデータ追加または移行 STEP 5 へ合流させる）

## 3. 環境・ツール

- **`afkgame-initdb` は surefire・failsafe とも `<skip>true</skip>` にしてある**（2R-B）。SQL のみで `src/test` を持たないため、親POMで surefire に `groups`/`excludedGroups` を与えると `require TestNG, JUnit48+ or JUnit 5` でビルドが落ちる（**増分ビルドのときだけ出る**）。このガードを外さないこと
- **ログ3種別（規約 `coding_standards_backend/logging.md`）は2026-08-10に実装済み**（`LayerLoggingInterceptor`・`afkgame.comm`・3appender等。main `701e67a`）。**パラメータ名によるマスク（application.md §3.1 規約1の固定表）は `rawPassword` のみ一致し、`rawRefreshToken`・`rawToken`・`rawVerificationToken` は固定表の `refreshToken`/`token` と完全一致しないためマスクされない**（`AuthServiceImpl#refresh`/`#logout`/`VerificationMailSenderImpl#send` 等の引数）。固定表を拡張するかパラメータ名を揃えるかは規約側の決定が要るため、次に `logging/application.md` §3.1 を触るセッションで判断する
- **Java の Javadoc が指すドキュメントパスは `check_docs.py` の走査対象外**（Markdown しか見ない）。ドキュメントを移動・改名したら `backend/**/*.java` のコメント内パスも grep して直すこと。2026-08-09 の配置換えでは `domain_service.md` を指したままの2件（`JwtService`・`VerificationMailSender`）が残り、翌タスクまで自動検出されなかった
