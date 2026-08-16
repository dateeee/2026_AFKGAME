# 後工程への申し送りメモ

> [next_session.md](next_session.md)（引き継ぎ）に入り切らない、**複数セッションにまたがる申し送り**を置く。
> 1件1行。消化したら行を消す。next_session.md がポインタ専用でいられるようにするための受け皿。
> **worktree からも追記してよい**（`merge=union` で自動統合。[worktree_guide.md](../process/worktree_guide.md) §3）。
> ただし union は行順を保証しないので、**既存行の書き換えではなく末尾への追加**で書く。
> **行の削除は main で行う**（union は削除を伝播せず、worktree 側で消した行は統合時に復活する）。
> **恒久的な知見はここへ残さない**。規約・コマンド表・仕様書の正へ移してから行を消す（[spec_ownership.md](../process/spec_ownership.md)）。

## 1. Java 移行

- **残りの STEP 順序**（各 STEP の内容は [steps.md](java_migration/steps.md) §4、進捗は [java_migration.md](java_migration.md) §4 が正）: **3-B**（Phase 1: game / battle / tower）→ **4**（Phase 2: equipment / shop・日替わり）→ **5**（Phase 3: party / skill）→ **6**（切替と後始末）。3-A（auth）は 3-A-3 の製造完了ゲートまで消化済み
- **STEP 4 は着手前に `tech_shop/lineup.md` §7・`tech_shop/buy.md` §8 の分岐一覧が使える粒度かを確認する**。STEP 5 は製造①（パーティ・スキル操作）の移植に続けて製造②（スキル戦闘処理: skill / environment。`SkillData` へダメージ倍率・対象・状態異常のフィールドを追加）・製造③（オフライン期待値計算＝ISSUE-106）を Java で実装する
- **STEP 4・5 の移植量（2026-08-08 実測）**: 装備1,512行 / ショップ1,233行 / スキル1,100行 / パーティ461行（`routers`+`services`+`models`+`schemas`+`master_data`+テストの合計）。**領域ごとに1セグメント**へ割るとキュー1行の規模に収まる。各領域ともテストが半分以上を占めるため `test-list` → `dev` の2セッションを見込む
- **`characters.rarity` は V1 スキーマに無い**（Phase 3 の列）。`Character` Entity にも持たせていないので、STEP 5 でスキーマ追加と同時に足す
- **`uq_players_user_id` 違反に業務エラーコードは新設しない**（2026-08-09 決着）。AUTH_ 一覧に該当が無く公開APIからは到達しない経路のため、`DuplicateKeyException` をそのまま送出し 500 `INTERNAL_UNEXPECTED_ERROR` として扱う
- **`LapAnalyzerImpl` のクリティカル率だけ未配線**で、合算値0を `StatCalculator#effectiveCritRate` へ渡している（テストが定める注入に `CharacterTypes` が無いため。供給元の正は `tech_rng.md` §6）。**表層を変える回（セグメント②で `@Service` を付ける回が最有力）に、テストの生成箇所ごと直す**
- **`tech_polling.md` §5 の10件は JUnit へ展開しない**（①-a で判断）。フロントは TDD 非適用（`test-list.md` §2）で、同 §5 自身が「単体レベルの検証はE2E（Playwright）に統合する」と定めている。**`integration-test` スキルの担当**。マーカー0件のままなら `check_branch_list.py --tests` の照合対象外で exit 0 は維持される
- **`httpclient5` は STEP 4（Phase 2 の Google OAuth）で `afkgame-domain/pom.xml` へ戻す**（ISSUE-905 で先行投入を削除した。技術選定は有効で、正は [tech_selection.md](java_migration/tech_selection.md) §2・[tech_backend.md](../tech/basic/tech_backend.md) §4.3）。`RestClient` の `ClientHttpRequestFactory` を Bean 構成する回に、同じコミットで依存を足す
- **サブパッケージを新設したら AOP 境界ログのポイントカットを確認する**。`afkgame.properties` の式は `..`（配下）で書いてあるので現状は追随不要だが、`.*.`（直下のみ）へ戻すと境界ログが**テストに検出されないまま消える**
- **`LapAnalyzerImpl#analyze` の `lapsToLevelUp` は常に `Integer.MAX_VALUE`（＝レベルアップに到達しない）**。口は `CharacterGrowth#requiredExpToNextLevel` として**追加済み**（2026-08-16。本体は `UnsupportedOperationException` で Red 済み）。**キャラ成長の製造で Green にし、同じ回で `LapAnalyzerImpl` を配線する**。オフライン周回中のレベルアップ分岐（`tech_offline.md` §5 #7・#8）は `OfflineCalculatorImpl` 側で実装済みなので、口が通れば動く
- **全滅後の「全員 `maxHP` へ全回復」（`tech_state.md` §5 #6）は未実装**。`OfflineCalculatorImpl` の全滅ペナルティは §4 の表（EXP50%減・強制撤退・残tick破棄 + HP自然回復）までで、`tech_state.md` §3 のペナルティ適用順ごとセグメント②の担当
- **`tech_state.md` §5 は Phase 別に分けないとテストへ展開できない**（2026-08-16・②-c で判明）。同節の7行は **Phase 1**（#3・#5〜#7 ＝ 全滅ペナルティのゴールド・EXP・HP と不変条件違反）・**Phase 2**（#4 装備の没収。`run_equipment_ids` 列も `EquipmentRepository` も未存在）・**Phase 3**（#1・#2 `party/edit`。`tech_backend.md` §4.1 が `party/` を Phase 3〜 と定義）にまたがるが、`check_branch_list.py` は**節単位で全行の対応を求める**ため部分展開では違反0にできない。②-c は同節へマーカーを1つも置かず先送りした結果、**`FloorProgression#onPartyWiped` に Red が無く製造②が実装できない**。**マーカー未参照の今なら番号を振り直せる**ので、分割は②-d（`next_session.md` §1）で行う

## 2. 仕様・マスターデータ

- **Phase 4 の再開時に戻す1件**（2026-08-09・Java 移行を優先するユーザー判断で `next_session.md` のキューから外した）: **テストリスト作成** — 拠点・施設・鍛冶屋（`tech_base.md` §7・§8 の36件 + `tech_forge/` の74件）+ 限界突破（`tech_limitbreak.md` §6 の30件）。詳細設計は拠点・施設・酒場スカウト・鍛冶屋・限界突破まで完了済み
- **Phase 5 の詳細設計は残り2件が未作成**（仕様確定ゲートの ISSUE-1302／1306。ボスラッシュ `tech_bossrush.md` + 分冊4件は 2026-08-16 に完了）: ①転生 `tech_prestige.md`（処理フロー + 分岐一覧。3API・数値の正は `master/endgame.md` §16。索引は `tech_spec.md` §1 に予定行だけ置いてある）、②イベントダンジョンのマスターデータ `docs/data/master/event_dungeon.md` §19（`master_data.md` の索引に節番号だけ採番済み。`spec_ownership.md` §3 に正ファイルとして登録済みだがファイルが未作成）。**実ファイル作成と索引のリンク張り替えは同じ変更にまとめる**（先にリンクを張ると `check_docs.py --links` が落ちる）。深淵の塔・イベントダンジョンの塔側処理は `tech_tower.md` + `tech_tower/` へ統合済みで追加不要
- **深淵の塔 461F の想定上限を再試算する**（ISSUE-1309 で `master/endgame.md` §18.1 の通常敵を `arch_dragon` LV152 の実データへ揃え §18.3 を再計算したが、**461F の「素の melee LV9999 で安定周回できる想定上限」は旧基準値（HP 4,500）時点の試算のまま**）。Phase 5 の詳細設計で行う。`balance_backlog.md` B-5 の「約115日」も同じ前提に立つ
- **Phase 5 で `bossRush`・`prestige` を実体化するときは、`tech_data.md` §1.1 のキー表と子 `tech_data/game_state.md` §1.1.3 の予約コメントの両方**を更新する（ISSUE-1311 の分割で親＝キー一覧表 + `towersCleared` のキー体系、子＝JSON 例に分かれたため）
- **`tech_numeric.md` §6「入力値の検証」2件は `PUT /api/game/settings` の Resource を作る回に消化する**（①-b で §5「丸め・クランプ・飽和」12件と分けた）
- **`tech_offline.md` §7（期待値計算式・スキル/パッシブ依存）12行は Phase 3 の製造で Red へ展開する**（差し戻しの回で追加。Phase 1 の編成では到達しないためマーカーを付けておらず、`check_branch_list.py --tests` の照合対象外）。`skill_factor`・攻撃スキル2枠・範囲攻撃 `×0.7×敵数`・被ダメ軽減の実効上限0.8・挑発の按分・回復期待値の差し引きが対象
- 獣の塔（`docs/data/towers/003_獣の塔.md`）をマスターデータへ追加する際、`FLOOR_CHARACTERS` へ `scout_001` ハヤテ（獣の塔10Fクリア。`characters/CHARACTERS_OVERVIEW.md` §3 の3体目）を足す。製造①では塔IDが未宣言で ID を発明しないため見送った（塔6〜8 のマスターデータ追加または STEP 5 へ合流させる）
- **ボスラッシュ詳細設計（2026-08-16）の図への波及3件**は、いずれも diagrams-review 2026-08-11 の**未適用 ISSUE と同じ箇所**（`er_diagram/battle.md` の `best_wave_hp` 注釈＝ISSUE-708／`api_sequence/endgame.md` の終了・リタイア・ランキング＝ISSUE-702・704・705／`battle_flow/bossrush.md` の記録更新位置とリタイア経路＝ISSUE-701・703）。`fix-specs` で ISSUE-701〜708 をまとめて適用する回に一緒に消化する
- **要件定義 spot-review（[2026-08-16_141726.md](../reviews/spot-review/2026-08-16_141726.md)）の「プロセスへの還元」4件と担当範囲外2件が未適用**（指摘10件の仕様反映のみ実施）。内訳は同レポート末尾が正。①④（`requirements` の成果物チェック2点・観点表2行）は `retro`、②③（`--owner` へ `tech_db.md` からのテーブル名抽出・`--glossary` 新設）は常設スクリプトを触る回、`master/item.md` §4.2 の参照節ずれは `doc-review` で消化する。**②を台帳の検出パターン列へ直接書く案は不可**（`_states\.` 等は `tech_db/` 分冊・`tech_bossrush*`・`master/endgame.md` の正当な5箇所に誤検出＝実測済み）

## 3. 環境・ツール

- **`check_java_conventions.py` の判定13（`--unused`）の現在値は WARN 6件**（`AuthSettings` 3・`GameSettings` 2・`LogKey.TOKEN`。13 → 7 は製造①-iii、7 → 6 は①-iv で読み手が付いたため）。ゼロを強制せず**増減だけ見る**
- **境界ログのマスクが効かないパラメータが4件ある**（`logging/application.md` §3.1 規約1の固定表は `rawPassword` のみ一致し、`rawRefreshToken`・`rawToken`・`rawVerificationToken` は固定表の `refreshToken`/`token` と完全一致しないためマスクされない。`AuthServiceImpl#refresh`/`#logout`・`VerificationMailSenderImpl#send` 等の引数）。**固定表を拡張するかパラメータ名を揃えるかは規約側の決定が要る**ため、次に `logging/application.md` §3.1 を触るセッションで判断する
