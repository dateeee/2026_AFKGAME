# 後工程への申し送りメモ

> [next_session.md](next_session.md)（引き継ぎ）に入り切らない、**複数セッションにまたがる申し送り**を置く。
> 1件1行。消化したら行を消す。next_session.md がポインタ専用でいられるようにするための受け皿。
> **worktree からも追記してよい**（`merge=union` で自動統合。[worktree_guide.md](../process/worktree_guide.md) §3）。
> ただし union は行順を保証しないので、**既存行の書き換えではなく末尾への追加**で書く。
> **行の削除は main で行う**（union は削除を伝播せず、worktree 側で消した行は統合時に復活する）。
> **恒久的な知見はここへ残さない**。規約・コマンド表・仕様書の正へ移してから行を消す（[spec_ownership.md](../process/spec_ownership.md)）。

## 1. Java 移行

- **移行 STEP の順序**（内容の正は [steps.md](java_migration/steps.md) §4、進捗の正は [java_migration.md](java_migration.md) §4）: 3-A-2（register / login / logout。`BCryptPasswordEncoder` strength 12 と `SecurityConfig` の認証不要パス追加。初期化は 3-A-1c の手順2以降を再利用＝`tech_auth.md` §8 冒頭）→ 3-A-3（link-account / verify-email / password-reset）→ 3-B（Phase 1: game / battle / tower）→ 4（Phase 2: equipment / shop・日替わり）→ 5（Phase 3）→ 6（切替と後始末: Vite プロキシ・`launch.json`・デプロイ手順・本ファイル群の整理）
- 各 STEP の申し送り: **tower は `tech_tower.md` が無く分岐一覧も無い**（auth と同じ欠落。2026-08-08 確認）ため 3-B は `detail-design` から始める（game は `tech_state`/`tech_tick`/`tech_polling` §5、battle は `tech_battle`/`tech_rng`/`tech_numeric` §5 にある）。STEP 4 は着手前に `tech_shop.md` §7・§8 の分岐一覧が使える粒度かを確認する。STEP 5 は製造①（パーティ・スキル操作）の移植に続けて製造②（スキル戦闘処理: skill / environment。`SkillData` へダメージ倍率・対象・状態異常のフィールドを追加）・製造③（オフライン期待値計算＝ISSUE-106）を Java で実装する
- **STEP 4・5 の移植量（2026-08-08 実測）**: STEP 4 = 装備1,512行 / ショップ1,233行、STEP 5 = スキル1,100行 / パーティ461行（`routers`+`services`+`models`+`schemas`+`master_data`+テストの合計）。**領域ごとに1セグメント**（装備 / ショップ / パーティ / スキル）へ割るとキュー1行の規模に収まる。各領域ともテストが半分以上を占めるため `test-list` → `dev` の2セッションを見込む
- **`characters.rarity` は V1 スキーマに無い**（Phase 3 の列）。`Character` Entity にも持たせていないので、Phase 3 の移植（STEP 5）でスキーマ追加と同時に足す
- **`uq_players_user_id` 違反に業務エラーコードは新設しない**（2026-08-09 決着）。AUTH_ 一覧に該当が無く公開APIからは到達しない経路のため、`DuplicateKeyException` をそのまま送出し 500 `INTERNAL_UNEXPECTED_ERROR` として扱う（3-A-2 の register でも同じ判断を使う）
- **STEP 6 で `tech_db/` の「実装:」行を Entity 参照へ替えても `check_schema_triple.py` は止まらない**（DDL はテーブル名で対応づけるため）。Java 側でスキーマの正を持つのは Flyway の `V1__initial_schema.sql`（照合仕様の正はスクリプトの docstring）

## 2. 仕様・マスターデータ

- **バランス調整バックログ B-9 の波及**: 酒場・鍛冶屋のしきい値方式を廃止し5施設とも全10LV定義にした結果、**`tech_base.md` §8 の分岐一覧が16→15件**になっている。Phase 4 のテストリストは新番号を起点にする
- **未確定仕様はゼロ**（2026-08-08。`open_specs.md` は削除済み・不在＝未確定ゼロ）。Phase 5 の基本設計前に仕様確定ゲート（`doc-review` → `fix-specs`）を一度通す。主な照合対象は `towersCleared` のキー体系（正は `tech_data.md` §1.1）と Phase 5「探索」タブへの導線集約（正は `systems/ui.md`）
- **認証の入力長で仕様が2点ぶれている**（2026-08-09・STEP 3-A-2 のテストリスト作成で検出）。①メール長が `tech_security.md` §11.3「254文字」と `tech_auth_account.md` §11 #3/#4「255文字」で食い違う（`users.email` は `VARCHAR(255)`）②`AuthSettings.passwordMaxLength`（128）と `tech_security.md` §11.3「8〜128文字」が定めるパスワード上限に対応する分岐行が §11 に無い（下限 #5/#6 のみ）。**テストは分岐一覧（255文字・上限なし）を正として書いてある**ので、確定した側へ寄せる際は `AuthApiTest` の該当テストも直す。3-A-3（`tech_auth_account.md` へ追記する回）か Phase 5 の仕様確定ゲートで片付ける
- 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。`master/character.md` §7.1 の3体目）を足す。製造①では塔IDが未宣言で ID を発明しないため見送った（塔6〜8 のマスターデータ追加または移行 STEP 5 へ合流させる）

## 3. 環境・ツール

- **`afkgame-initdb` は surefire・failsafe とも `<skip>true</skip>` にしてある**（2R-B）。SQL のみで `src/test` を持たないため、親POMで surefire に `groups`/`excludedGroups` を与えると `require TestNG, JUnit48+ or JUnit 5` でビルドが落ちる（**増分ビルドのときだけ出る**）。このガードを外さないこと
