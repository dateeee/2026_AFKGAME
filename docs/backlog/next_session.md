# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)。

最終更新: 2026-08-08 / 対応コミット: 1822d69 の次。**`/retro` で効率メモ10件を反映**したコミット。再発2件（文字数超過→圧縮の往復4回 / 引き継ぎの環境前提が未検証3回）を `profile.md` §7 規約7・`next.md` §2.1・`next` SKILL §2 へ反映し、メモを空にした。文字数台帳（`KNOWN_OVERSIZED`）も空。STEP 2 は完了済みで、次は移行 STEP 3。手順・進捗の正は [java_migration.md](java_migration.md)

## 1. 次回（コピペ用）

```
/test-list → /dev 移行 STEP 3-A-1（ゲスト作成の初期化）: POST /api/auth/guest で Player・キャラクター・装備スロット・初期ポーションを作る。初期値がマスターデータ側にあるため、必要な範囲のマスターデータを YAML + record へ移す（ローダ基盤は STEP 2-C で完成済み）
完了条件: 分岐一覧から起こした JUnit テストが Red → Green・JaCoCo branch 100%（親POMのしきい値）・mvn verify が成功・changelog へ1行追記・コミット
参照: docs/tech/detail/tech_auth.md（分岐一覧の正）、docs/tech/basic/tech_db/player.md（列・一意制約の正）、docs/data/master/character.md（初期値）、docs/backlog/java_migration.md §4（STEP 3 の手順）
前提: 移行 STEP 2 完了（1822d69 時点）。効率メモは空。**環境（2026-08-08 に新規シェルで実行確認済み）**: `mvn`・`java` は PATH に無く、Bash から動くのは `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.8-hotspot" "/c/Users/tubas/AppData/Local/Programs/apache-maven-3.9.11/bin/mvn" -v` の形（JAVA_HOME をインラインで与えないと mvn は "JAVA_HOME is not defined correctly" で落ちる。java 単体はフルパスで起動する）。Docker は未検証のため統合テストは zonky 埋め込み PostgreSQL を使う
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | 移行 STEP 3-A-2（register / login / logout）。`BCryptPasswordEncoder`(strength 12) と `SecurityConfig` の認証不要パス追加を含む（持ち越しの正は java_migration.md §4 の 2-B 表） | `test-list` → `dev` |
| 2 | 移行 STEP 3-A-3（link-account / verify-email / password-reset）。確認メール送信・トークン検証 | `test-list` → `dev` |
| 3 | 移行 STEP 3-B（Phase 1 の game / battle / tower 移植）。分岐一覧から JUnit テストを起こす Red → Green | `test-list` → `dev` |
| 4 | 移行 STEP 4（Phase 2 スコープの移植: equipment / shop・日替わり含む） | `test-list` → `dev` |

- 移行 STEP 5（Phase 3 製造①の実装済み分＝パーティ・スキル操作を移植）。続けて製造②（スキル戦闘処理: skill / environment）・製造③（オフライン期待値計算＝ISSUE-106）を Java で実装する。製造②では `SkillData` へダメージ倍率・対象・状態異常のフィールドを追加する
- 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。character.md §7.1 の3体目）を追加する。製造①では塔IDがどの仕様書にも宣言されておらず ID を発明しないため見送った（着手は優先1または STEP 5 に合流させる）
- 移行 STEP 6（切替と後始末: Vite プロキシ・`.vscode/launch.json`・デプロイ手順・`backend/` の Python 削除・本ファイル群の整理）は上記すべての完了後。手順は [java_migration.md](java_migration.md) §4 が正
- `docker-compose.yml` は成果物として作成済みだが **Docker 環境が未検証**のため未起動確認。`local` で実際に `docker compose up -d` → `mvn spring-boot:run` → `GET /health` を通すのは Docker が使えるタイミングで行う
- **環境**（2026-08-08 に新規シェルで実行確認済み）: `mvn`・`java` は PATH に無く、**`JAVA_HOME` も設定されていない**。フルパス（JDK `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`、Maven `C:\Users\tubas\AppData\Local\Programs\apache-maven-3.9.11\bin\mvn.cmd`。PowerShell からは `mvn.cmd`）に加え、**`JAVA_HOME` を毎回与える**こと（無いと mvn は "JAVA_HOME is not defined correctly" で落ちる。java 単体はフルパスのみで起動する）。統合テストのDBは zonky 埋め込み PostgreSQL（`@AutoConfigureEmbeddedDatabase(provider = ZONKY)`）。`@ConfigurationProperties` クラスは `afkgame-env` の `com.afkgame.env.config` に置く。親POMに JaCoCo branch しきい値100%が入っているため、追加した分岐はすべてテストで通す
