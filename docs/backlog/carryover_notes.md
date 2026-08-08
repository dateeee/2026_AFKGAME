# 後工程への申し送りメモ

> [next_session.md](next_session.md)（引き継ぎ）に入り切らない、**複数セッションにまたがる申し送り**を置く。
> 1件1行。消化したら行を消す。next_session.md がポインタ専用でいられるようにするための受け皿。
> **worktree からも追記してよい**（`merge=union` で自動統合。[worktree_guide.md](../process/worktree_guide.md) §3）。
> ただし union は行順を保証しないので、**既存行の書き換えではなく末尾への追加**で書く。

## 1. Java 移行

- **移行 STEP の順序**（内容の正は [java_migration.md](java_migration.md) §4）: 3-A-2（register / login / logout。`BCryptPasswordEncoder` strength 12 と `SecurityConfig` の認証不要パス追加。初期化は 3-A-1c の手順2以降を再利用＝`tech_auth.md` §8 冒頭）→ 3-A-3（link-account / verify-email / password-reset）→ 3-B（Phase 1: game / battle / tower）→ 4（Phase 2: equipment / shop・日替わり）→ 5（Phase 3）→ 6（切替と後始末: Vite プロキシ・`launch.json`・デプロイ手順・`backend/` の Python 削除・本ファイル群の整理）
- 各 STEP の申し送り: **tower は `tech_tower.md` が無く分岐一覧も無い**（auth と同じ欠落。2026-08-08 確認）ため 3-B は `detail-design` から始める（game は `tech_state`/`tech_tick`/`tech_polling` §5、battle は `tech_battle`/`tech_rng`/`tech_numeric` §5 にある）。STEP 4 は着手前に `tech_shop.md` §7・§8 の分岐一覧が使える粒度かを確認する。STEP 5 は製造①（パーティ・スキル操作）の移植に続けて製造②（スキル戦闘処理: skill / environment。`SkillData` へダメージ倍率・対象・状態異常のフィールドを追加）・製造③（オフライン期待値計算＝ISSUE-106）を Java で実装する
- **`docs/backlog/java_migration.md` は 7,988字 / 上限8,000字（残り12字）**。STEP 3 以降の進捗を書き足す前に `doc-size` で分割する（STEP 別に子ファイルへ切り出す等）。次の追記は圧縮では吸収できない
- **`scripts/check_branch_list.py --tests` の Java 未対応は解消済み**（`fbf2073`。2026-08-08）。走査先へ `backend/*/src/test/java/**/*Test.java` を追加し、あわせてマーカーの節番号を枝番許容（`§(\d+(?:\.\d+)*)`）にした——**`§8.3` 形式が正規表現に一致せず、1a で書いたマーカー自体も無視されていた**ため、走査先だけ直しても素通りしたままだった。3-A-1c で §8.3 の12行がそろうのと同じコミット列で入れたので ERROR の窓は生じていない。以後 Java のテストも `--tests` で照合される（手照合は不要）
- **同一モジュールに Red が複数並ぶと、片方だけでは Green を検証できない**（2026-08-08。3-A-1a を単独で verify しようとして判明）。Maven はテストソースを一括コンパイルするため、未実装の型を参照する別の Red があると `mvn test` はテスト実行前に止まる。Red を分割して積むときは、Green も同じ単位でまとめて取る
- **テスト用コンストラクタを足したマスターデータのレジストリは、公開コンストラクタへ `@Autowired` を明示する**（2026-08-08。`CharacterTypes`・`EquipmentSlots`・`InitialPlayer` で発生）。リソースパスを受け取るパッケージプライベートなコンストラクタを併設すると候補が2つになり、Spring は既定コンストラクタを探して `NoSuchMethodException` で Bean 生成に失敗する。単体テストは通り、コンテキストを起こす統合テストだけが落ちる
- **`characters.rarity` は V1 スキーマに無い**（Phase 3 の列）。`Character` Entity にも持たせていないので、Phase 3 の移植（STEP 5）でスキーマ追加と同時に足す

## 2. 仕様・マスターデータ

- **バランス調整バックログ B-7〜B-9 を確定**（`01b8087`。残件は B-1〜B-6）。後工程に効くのは3点。①B-9 で酒場・鍛冶屋のしきい値方式を廃止し5施設とも全10LV定義へ（鍛冶屋は強化上限=施設LV、酒場の排出率は `master/character.md` §7.3。解禁が1LVずつ前倒し）。**`tech_base.md` §8 の分岐一覧が16→15件**のため Phase 4 のテストリストは新番号を起点にする。②B-7 で `master_data.md` §17.3「未読件数の初期化」を新設（既読キー不在なら全件既読）。Phase 3 のお知らせ実装に含める。③B-8 は加入時LV1 で確定（`master/character.md` §7.1）
- **未確定仕様はゼロ**（2026-08-08。`open_specs.md` は削除済み・不在＝未確定ゼロ）。Phase 5 の基本設計前に仕様確定ゲート（`doc-review` → `fix-specs`）を一度通す。主な照合対象は今回の確定分＝`towersCleared` のキー体系（正は `tech_data.md` §1.1）と Phase 5「探索」タブへの導線集約（正は `systems/ui.md`）
- 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。`master/character.md` §7.1 の3体目）を足す。製造①では塔IDが未宣言で ID を発明しないため見送った（塔6〜8 のマスターデータ追加または移行 STEP 5 へ合流させる）
- **鍛冶屋の詳細設計は索引 + 操作別3ファイル**（`40d37c4`。`tech_forge.md` + `tech_forge_{enhance,craft,disassemble}.md`、分岐一覧74件＝強化24／製作30／分解20）。1ファイルでは区分C上限を超えたための分割で、節番号は分割前の通し。**子ファイル名を `tech_*.md` にしているのは `check_branch_list.py` の走査（`TECH_DIR.rglob("tech_*.md")`）から外さないため**——今後 detail 配下を分割するときも同じ制約がかかる
- **`POST /api/forge/craft` は `rank` と `recipeId` の排他パラメータ**（`40d37c4` で `tech_api.md` も更新済み）。`recipeId` は `master/equipment.md` §5.5.3 の吸収装備6件（IDは `equipment.base_id` 兼用）。フロント実装時、製作UIはランダム製作と固定レシピの2系統を1エンドポイントへ出し分ける
- **バランス調整バックログに B-10 を追加**（吸収装備製作レシピの持ち手・基礎ステータスが仮置き。`master/equipment.md` §5.5.3）。残件は B-1〜B-6・B-10 の7件
- **所持枠の数え方を `tech_base.md` §2.3 で確定**（装備1件＝1枠、素材・換金アイテムは1種類＝1枠、数量0の行は数えない）。増やす操作（製作・購入）は枠不足で失敗させ、減らす操作（分解）は切り捨てて成功させる非対称な扱い。Phase 2 の在庫判定（`tech_shop.md` §4 手順5）と同じ数え方で実装する

## 3. 環境・ツール

- **JDK / Maven**（2026-08-08 に新規シェルで実行確認済み。実行するコマンド形は next_session.md §1 の「前提」が持つ）: `mvn`・`java` は PATH にも `JAVA_HOME` にも無く、**フルパス + `JAVA_HOME` を毎回与える**（PowerShell からは `mvn.cmd`。JDK は `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`、Maven は `C:\Users\tubas\AppData\Local\Programs\apache-maven-3.9.11\bin\`）
- 統合テストDBは zonky 埋め込み PostgreSQL（`@AutoConfigureEmbeddedDatabase(provider = ZONKY)`）で worktree ごとに独立。`@ConfigurationProperties` は `afkgame-env` の `com.afkgame.env.config` へ置く。親POMに JaCoCo branch しきい値100%が入っているため、追加した分岐はすべてテストで通す
- `docker-compose.yml` は作成済みだが **Docker 環境が未検証**（未起動確認）。`docker compose up -d` → `mvn spring-boot:run` → `GET /health` を通すのは Docker が使えるタイミングで行う
