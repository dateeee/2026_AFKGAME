# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)。

最終更新: 2026-08-08 / 対応コミット: c04d98a の次。**移行 STEP 3-A-1 の詳細設計**を確定したコミット。`test-list` が「`tech_auth.md` に分岐一覧が無い」ことを理由に着手前停止したのが起点で、`tech_auth.md` §8（処理フロー8手順 + 分岐一覧12件）を新設し、未文書化だった `hp_potion×5` と表示名 `冒険者` の正を宣言、初期値の Java 側配置を「値の正の所在で振り分ける」へ確定した。**製造はここから**。手順・進捗の正は [java_migration.md](java_migration.md)

## 1. 次回（コピペ用）

```
/test-list → /dev 移行 STEP 3-A-1a（初期化に使うマスターデータ）: tech_auth.md §8.1 の表に沿って initial_player.yml・character_types.yml・equipment_slots.yml と対応する record を afkgame-domain へ追加し、既存の MasterDataLoader へ登録する。character_types.yml は LV1 基礎値のみ（成長率は入れない）
完了条件: 分岐一覧 #3・#4・#6・#10（マスター検証で起動を中止する経路）の JUnit テストが Red → Green・JaCoCo branch 100%（親POMのしきい値）・mvn verify が成功・changelog へ1行追記・コミット
参照: docs/tech/detail/tech_auth.md §8.1（YAML と正の対応表）・§8.3（分岐一覧）、backend/afkgame-domain/src/main/resources/masterdata/items.yml（既存YAMLの書式）
前提: 移行 STEP 2 完了 + STEP 3-A-1 詳細設計完了（c04d98a の次）。分岐一覧は `check_branch_list.py` 23件・WARN 0 で通っている。**未コミットの別タスクあり**: worktree 並行作業一式（`.gitattributes`・`docs/process/worktree_guide.md`・`scripts/worktree.py` の3件が未追跡 + `README.md` の索引に1行追加）が作業ツリーに残っている。本セッションの成果物ではないため意図的にコミットへ含めていない。着手前にユーザーへ扱い（コミットするか破棄するか）を確認すること。**環境（2026-08-08 に新規シェルで実行確認済み）**: `mvn`・`java` は PATH に無く、Bash から動くのは `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot" "/c/Users/tubas/AppData/Local/Programs/apache-maven-3.9.11/bin/mvn" -v` の形（JAVA_HOME をインラインで与えないと mvn は "JAVA_HOME is not defined correctly" で落ちる。java 単体はフルパスで起動する）。Docker は未検証のため統合テストは zonky 埋め込み PostgreSQL を使う
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | 移行 STEP 3-A-1b（初期化対象の Entity + Mapper）。`players` / `player_settings` / `characters` / `character_equip_slots` / `inventory_items` の Entity と MyBatis3 Mapper（インタフェース + XML）。列・一意制約の正は `tech_db/player.md` §1・§2・§4 と `tech_db/item.md` §2・§3 | `test-list` → `dev` |
| 2 | 移行 STEP 3-A-1c（プレイヤー初期化サービス + 結線）。`tech_auth.md` §8.2 の8手順を単一トランザクションで実装し `POST /api/auth/guest` へ結線。分岐一覧 #1・#2・#5・#7〜#9・#11・#12 が対象 | `test-list` → `dev` |
| 3 | 移行 STEP 3-A-2（register / login / logout）。`BCryptPasswordEncoder`(strength 12) と `SecurityConfig` の認証不要パス追加を含む（持ち越しの正は java_migration.md §4 の 2-B 表）。初期化は 3-A-1c の手順2以降を再利用する（`tech_auth.md` §8 冒頭） | `test-list` → `dev` |
| 4 | 移行 STEP 3-A-3（link-account / verify-email / password-reset）。確認メール送信・トークン検証 | `test-list` → `dev` |
| 5 | 移行 STEP 3-B（Phase 1 の game / battle / tower 移植）。着手前に各 `tech_*.md` の分岐一覧の有無を確認する（auth と同じ欠落があれば `detail-design` を先に回す） | `test-list` → `dev` |

- 移行 STEP 4（Phase 2 スコープの移植: equipment / shop・日替わり含む）はキュー優先5（STEP 3-B）の完了後。着手前に `tech_shop.md` §7・§8 の分岐一覧が使える粒度かを確認する
- 移行 STEP 5（Phase 3 製造①の実装済み分＝パーティ・スキル操作を移植）。続けて製造②（スキル戦闘処理: skill / environment）・製造③（オフライン期待値計算＝ISSUE-106）を Java で実装する。製造②では `SkillData` へダメージ倍率・対象・状態異常のフィールドを追加する
- **`docs/backlog/java_migration.md` は 7,988字 / 上限8,000字（残り12字）**。STEP 3 以降の進捗を書き足す前に `doc-size` で分割する（STEP 別に子ファイルへ切り出す等）。次の追記は圧縮では吸収できない
- 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。character.md §7.1 の3体目）を追加する。製造①では塔IDがどの仕様書にも宣言されておらず ID を発明しないため見送った（着手は優先1または STEP 5 に合流させる）
- 移行 STEP 6（切替と後始末: Vite プロキシ・`.vscode/launch.json`・デプロイ手順・`backend/` の Python 削除・本ファイル群の整理）は上記すべての完了後。手順は [java_migration.md](java_migration.md) §4 が正
- `docker-compose.yml` は成果物として作成済みだが **Docker 環境が未検証**のため未起動確認。`local` で実際に `docker compose up -d` → `mvn spring-boot:run` → `GET /health` を通すのは Docker が使えるタイミングで行う
- **環境**（2026-08-08 に新規シェルで実行確認済み）: `mvn`・`java` は PATH に無く、**`JAVA_HOME` も設定されていない**。フルパス（JDK `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`、Maven `C:\Users\tubas\AppData\Local\Programs\apache-maven-3.9.11\bin\mvn.cmd`。PowerShell からは `mvn.cmd`）に加え、**`JAVA_HOME` を毎回与える**こと（無いと mvn は "JAVA_HOME is not defined correctly" で落ちる。java 単体はフルパスのみで起動する）。統合テストのDBは zonky 埋め込み PostgreSQL（`@AutoConfigureEmbeddedDatabase(provider = ZONKY)`）。`@ConfigurationProperties` クラスは `afkgame-env` の `com.afkgame.env.config` に置く。親POMに JaCoCo branch しきい値100%が入っているため、追加した分岐はすべてテストで通す
