# 次回セッションの開始プロンプト

> **使い方**: 新セッションの最初のメッセージで `/next` と送る（または §1 のコードブロックを貼り付ける）。
> **更新**: タスク完了のコミット前に §1 を次のタスクへ書き換える（Stop フックがリマインドする）。
> **鮮度**: 開始側は「前提」のコミットIDと git log を突合し、完了済みに見えたら開始せずユーザーへ確認する。
> 本ファイルは**ポインタ専用**。Phase 進捗の正は [development_process.md](../process/development_process.md) §5、書式の正は [.claude/project/next.md](../../.claude/project/next.md)。

最終更新: 2026-08-08 / 対応コミット: e22f26c の次。**移行 STEP 2-C（骨格の設定・データ基盤）を実装**したコミット。`GameProperties`（`afkgame.*` の `@ConfigurationProperties`）・`RandomFactory`（`java.util.Random` を1リクエスト1個生成）・マスターデータの YAML ローダ（不正なら起動失敗）が入り `mvn verify` が通る（69件 PASS・branch 100%）。**これで STEP 2（骨格構築）は完了**。手順・進捗の正は [java_migration.md](java_migration.md)

## 1. 次回（コピペ用）

```
/retro 効率メモの棚卸し: docs/backlog/efficiency_memo.md に未反映エントリが9件（2026-08-08 01:29〜16:05）溜まっているため、原因をスキル・プロファイル・成果物の改善へ反映して反映済みエントリを削除する。同型の再発（残量WARN のファイルへ追記して上限超過 → 圧縮の往復）が3回記録されており、これを最優先で潰す
完了条件: 各エントリの改善案が反映先ファイルへ入っている・反映済みエントリを削除・check_doc_size.py と check_docs.py が OK・changelog へ1行追記・コミット
参照: docs/backlog/efficiency_memo.md（未反映エントリ9件）、.claude/project/retro.md（反映先マップ・しきい値）
前提: 移行 STEP 2 完了。メモは 7,259字（区分C 8,000字・残り741字）で、次の自動追記2〜3回で上限に達する。エントリ内で名指しされている反映先は `.claude/project/profile.md` §7（追記前に残量と追記予定字数を突き合わせる）と `.claude/project/dev.md` §5（`@Nested` 使用時のテスト件数は surefire の *.xml で確認する）。STEP 3 は複数セッションに及ぶため、着手前にここで整地しておく
```

## 2. 候補キュー（最大5行・優先順）

| 優先 | タスク | 工程スキル |
|------|-------|-----------|
| 1 | 移行 STEP 3-A-1（ゲスト作成の初期化）。`POST /api/auth/guest` で Player・キャラクター・装備スロット・初期ポーションを作る。初期値がマスターデータ側にあるため、**必要な範囲のマスターデータを YAML + record へ移す**（ローダ基盤は 2-C で完成済み） | `test-list` → `dev` |
| 2 | 移行 STEP 3-A-2（register / login / logout）。`BCryptPasswordEncoder`(strength 12) と `SecurityConfig` の認証不要パス追加を含む（持ち越しの正は java_migration.md §4 の 2-B 表） | `test-list` → `dev` |
| 3 | 移行 STEP 3-A-3（link-account / verify-email / password-reset）。確認メール送信・トークン検証 | `test-list` → `dev` |
| 4 | 移行 STEP 3-B（Phase 1 の game / battle / tower 移植）。分岐一覧から JUnit テストを起こす Red → Green | `test-list` → `dev` |
| 5 | 移行 STEP 4（Phase 2 スコープの移植: equipment / shop・日替わり含む） | `test-list` → `dev` |

- 移行 STEP 5（Phase 3 製造①の実装済み分＝パーティ・スキル操作を移植）。続けて製造②（スキル戦闘処理: skill / environment）・製造③（オフライン期待値計算＝ISSUE-106）を Java で実装する。製造②では `SkillData` へダメージ倍率・対象・状態異常のフィールドを追加する
- 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。character.md §7.1 の3体目）を追加する。製造①では塔IDがどの仕様書にも宣言されておらず ID を発明しないため見送った（着手は優先1または STEP 5 に合流させる）
- 移行 STEP 6（切替と後始末: Vite プロキシ・`.vscode/launch.json`・デプロイ手順・`backend/` の Python 削除・本ファイル群の整理）は上記すべての完了後。手順は [java_migration.md](java_migration.md) §4 が正
- `docker-compose.yml` は成果物として作成済みだが **Docker 環境が未検証**のため未起動確認。`local` で実際に `docker compose up -d` → `mvn spring-boot:run` → `GET /health` を通すのは Docker が使えるタイミングで行う
- **環境**: `mvn`・`java` は PATH に無い。`JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"` と `"C:\Users\tubas\AppData\Local\Programs\apache-maven-3.9.11\bin\mvn.cmd"` のフルパスで実行する（PowerShell からは `mvn.cmd`）。統合テストのDBは zonky 埋め込み PostgreSQL（`@AutoConfigureEmbeddedDatabase(provider = ZONKY)`）。`@ConfigurationProperties` クラスは `afkgame-env` の `com.afkgame.env.config` に置く。親POMに JaCoCo branch しきい値100%が入っているため、追加した分岐はすべてテストで通す
