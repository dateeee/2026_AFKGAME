# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)。

最終更新: 2026-08-08 / 対応コミット: `37d4ef5`（移行 STEP 3-A-1b の Mapper 疎通テストを Red で追加）。`tech_auth.md` §8.3 の分岐一覧12件は 1a・1c の担当で **1b には対応行が無い**ため、Entity/Mapper には分岐マーカーを付けず、往復・NULL 許容列の両側・一意制約4種・取得0/1/2件の観点で28ケースを置いた（`test-list.md` §2「定義のみのため副次的」・既存 `AuthServiceTest` と同じ扱い）。**次は同じ 1b の Green から**。手順・進捗の正は [java_migration.md](java_migration.md)

**並行トラック**: 別セッションが **Phase 4 拠点・施設の詳細設計**（`tech_base.md` 新設・分岐一覧37件）を完了した。Java 移行とは独立に進むため §2 の**優先0**に置いてある。移行の再開（§1）と Phase 4 の続き（優先0）のどちらを先にするかはユーザー判断。

## 1. 次回（コピペ用）

```
/dev 移行 STEP 3-A-1b（初期化対象の Entity + Mapper）: Red 済みの Mapper 疎通テスト28件を Green にする。Entity 5件（Player・PlayerSettings・Character・CharacterEquipSlot・InventoryItem）を com.afkgame.domain.model へ、Mapper 5件（インタフェース + 同名 XML）を com.afkgame.domain.repository へ追加する。要求される表層（メソッド名・null/空リストの扱い）は各テストの Javadoc に書いてある
完了条件: mvn verify が成功・追加した28ケースが全PASS・JaCoCo branch 100%（親POMのしきい値）・changelog へ1行追記・コミット
参照: backend/afkgame-domain/src/test/java/com/afkgame/domain/repository/（Red テスト6件が起点）、docs/tech/basic/tech_db/player.md §1・§2・§4、docs/tech/basic/tech_db/item.md §2・§3、既存書式は UserMapper.java と src/main/resources/com/afkgame/domain/repository/UserMapper.xml
前提: `37d4ef5` で Red 済み（テスト実行前のコンパイルで停止し、未作成の10型ちょうどに「シンボルを見つけられません」が出る状態）。**着手時の要判断2件**: ①`model.Character` は `java.lang.Character` と単純名が衝突する（`users`→`User` と同じ規則を優先して命名済み。改名するなら Green 前に）②`characters.rarity` は Phase 3 の列で V1 スキーマに無いため Entity にも持たせない。**編集は worktree で行う**（[worktree_guide.md](../process/worktree_guide.md) §5）。`python scripts/worktree.py add step3a1b-green` → `EnterWorktree`(path) で移り、完了後に §5.3 で統合する。**環境（2026-08-08 に実行確認済み）**: `mvn`・`java` は PATH に無く、Bash から動くのは `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot" "/c/Users/tubas/AppData/Local/Programs/apache-maven-3.9.11/bin/mvn" -f backend/pom.xml -pl afkgame-domain -am test` の形（JAVA_HOME をインラインで与えないと "JAVA_HOME is not defined correctly" で落ちる）。統合テストDBは zonky 埋め込み PostgreSQL（Docker 未検証）
```

## 2. 候補キュー（最大5行・優先順）

**各行に前提セグメントを書く**（着手可否を選んだ時点で判断できるようにするため）。

| 優先 | タスク | 前提 | 工程スキル |
|------|-------|------|-----------|
| 0 | **Phase 4 詳細設計の残り**（拠点・施設は `tech_base.md` で完了）。①酒場スカウト `POST /api/base/scout`（排出率の正は `master/character.md` §7.3。抽選は `tech_rng.md` に沿う）②鍛冶屋・素材 `/api/forge/*`（強化・製作・分解。コスト倍率の解決は `tech_base.md` §2.1 のしきい値規則を使う）③限界突破 `POST /api/character/limit-break` ④ダンジョン3（塔6〜8）のマスターデータ | なし（Java 移行と独立） | `detail-design` |
| 1 | 移行 STEP 3-A-1a（初期化に使うマスターデータ）。`tech_auth.md` §8.1 の表に沿って `initial_player.yml`・`character_types.yml`（LV1 基礎値のみ。成長率は入れない）・`equipment_slots.yml` と record を追加し `MasterDataLoader` へ登録。分岐一覧 #3・#4・#6・#10 | なし | `test-list` → `dev` |
| 2 | 移行 STEP 3-A-1c（プレイヤー初期化サービス + 結線）。`tech_auth.md` §8.2 の8手順を単一トランザクションで実装し `POST /api/auth/guest` へ結線。分岐一覧 #1・#2・#5・#7〜#9・#11・#12 が対象 | 1a + 1b | `test-list` → `dev` |
| 3 | 移行 STEP 3-A-2（register / login / logout）。`BCryptPasswordEncoder`(strength 12) と `SecurityConfig` の認証不要パス追加を含む（持ち越しの正は java_migration.md §4 の 2-B 表）。初期化は 3-A-1c の手順2以降を再利用する（`tech_auth.md` §8 冒頭） | 3-A-1c | `test-list` → `dev` |
| 4 | 移行 STEP 3-A-3（link-account / verify-email / password-reset）。確認メール送信・トークン検証 | 3-A-2 | `test-list` → `dev` |
| 5 | 移行 STEP 3-B（Phase 1 の game / battle / tower 移植）。game は `tech_state`/`tech_tick`/`tech_polling` §5、battle は `tech_battle`/`tech_rng`/`tech_numeric` §5 に分岐一覧がある。**tower は `tech_tower.md` が存在せず分岐一覧も無い**（auth と同じ欠落。2026-08-08 確認） | 3-A 完了 + tower の詳細設計 | `detail-design` → `test-list` → `dev` |

- 移行 STEP 4（Phase 2 スコープの移植: equipment / shop・日替わり含む）はキュー優先5（STEP 3-B）の完了後。着手前に `tech_shop.md` §7・§8 の分岐一覧が使える粒度かを確認する
- 移行 STEP 5（Phase 3 製造①の実装済み分＝パーティ・スキル操作を移植）。続けて製造②（スキル戦闘処理: skill / environment）・製造③（オフライン期待値計算＝ISSUE-106）を Java で実装する。製造②では `SkillData` へダメージ倍率・対象・状態異常のフィールドを追加する
- **`docs/backlog/java_migration.md` は 7,988字 / 上限8,000字（残り12字）**。STEP 3 以降の進捗を書き足す前に `doc-size` で分割する（STEP 別に子ファイルへ切り出す等）。次の追記は圧縮では吸収できない
- 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。character.md §7.1 の3体目）を追加する。製造①では塔IDがどの仕様書にも宣言されておらず ID を発明しないため見送った（着手は優先1または STEP 5 に合流させる）
- 移行 STEP 6（切替と後始末: Vite プロキシ・`.vscode/launch.json`・デプロイ手順・`backend/` の Python 削除・本ファイル群の整理）は上記すべての完了後。手順は [java_migration.md](java_migration.md) §4 が正
- `docker-compose.yml` は成果物として作成済みだが **Docker 環境が未検証**のため未起動確認。`local` で実際に `docker compose up -d` → `mvn spring-boot:run` → `GET /health` を通すのは Docker が使えるタイミングで行う
- **環境**（2026-08-08 に新規シェルで実行確認済み）: `mvn`・`java` は PATH に無く、**`JAVA_HOME` も設定されていない**。フルパス（JDK `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`、Maven `C:\Users\tubas\AppData\Local\Programs\apache-maven-3.9.11\bin\mvn.cmd`。PowerShell からは `mvn.cmd`）に加え、**`JAVA_HOME` を毎回与える**こと（無いと mvn は "JAVA_HOME is not defined correctly" で落ちる。java 単体はフルパスのみで起動する）。統合テストのDBは zonky 埋め込み PostgreSQL（`@AutoConfigureEmbeddedDatabase(provider = ZONKY)`）。`@ConfigurationProperties` クラスは `afkgame-env` の `com.afkgame.env.config` に置く。親POMに JaCoCo branch しきい値100%が入っているため、追加した分岐はすべてテストで通す
