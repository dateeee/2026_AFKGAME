# テーブル定義 — 戦闘・ボスラッシュ

> 親: [tech_db.md](../tech_db.md)。命名規約・型マッピング・共通の列規約・外部キー動作は親が正であり、本書では繰り返さない。
> 視覚化は [er_diagram/battle.md](../../../diagrams/er_diagram/battle.md)。戦闘ログの保持ポリシーは [tech_battle.md](../../detail/tech_battle.md) §1、ログ要素の JSON 構造は [tech_data.md](../tech_data.md) §1.3、ボスラッシュの仕様は [systems/endgame.md](../../../design/systems/endgame.md) §2.11 が正。
> ダンジョン・塔・敵・環境効果はコード上のマスターデータでありDBテーブルを持たない（親 §4-6）。ER図の「ダンジョン・塔・敵系」ブロックは論理設計の視覚化であって、本書の対象外。

---

## 1. `battle_logs`（Phase 2）

実装予定: `com.afkgame.domain.model.BattleLog`。1行 = 1tick分の戦闘ログ。

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `player_id` | `VARCHAR(36)` | 不可 | — | FK → `players.id` |
| `tick_number` | `INTEGER` | 不可 | — | tick通番。プレイヤー単位の連番で、ログの並び順と削除順の判定に使う |
| `timestamp` | `DATETIME(tz)` | 不可 | 現在時刻 | tick処理時刻。日時列の `_at` 規約（親 §2）の**例外**で、実装済みのため現名を正とする |
| `entries` | `JSON` | 不可 | — | そのtickの行動ログ**配列**。要素の構造は `tech_data.md` §1.3 が正 |

保持は直近100件を上限とし、超過分はtick処理時に古い順へ削除する（`tech_battle.md` §1）。件数の上限をアプリ側で管理するため、保持期限を表す列は持たない。

`entries` の中身はDB側で検索しない（DBMS の JSON 関数に依存しない。親 §3 の型マッピング方針）。行の絞り込みは `player_id` と `tick_number` だけで行い、ログ要素の解釈はサービス層とフロントが担う。

## 2. `boss_rush_states`（Phase 5・未実装）

実装予定: `com.afkgame.domain.model.BossRushState`。プレイヤーごとに1件を持ち、**進行中の挑戦**（`active` 〜 `accumulated_exp`）と**挑戦をまたいで残る自己ベスト**（`best_*`）を同一行で保持する。

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `player_id` | `VARCHAR(36)` | 不可 | — | FK → `players.id`、UNIQUE（1プレイヤーにつき1行） |
| `active` | `BOOLEAN` | 不可 | `false` | 挑戦中か。`true` の間は `players.current_tower_id` が NULL（塔探索と排他。[tech_state.md](../../detail/tech_state.md) §1） |
| `current_wave` | `INTEGER` | 不可 | `0` | 進行中の挑戦のウェーブ。開始のたびに 1 へ戻す（持ち越さない） |
| `accumulated_gold` | `BIGINT` | 不可 | `0` | 進行中の挑戦の累積ゴールド。終了時に `players.gold` へ加算して 0 に戻す |
| `accumulated_exp` | `BIGINT` | 不可 | `0` | 同じ扱いのEXP。全滅時も没収しない |
| `best_wave` | `INTEGER` | 不可 | `0` | 自己ベスト到達ウェーブ。ランキングの順位指標 |
| `best_wave_hp` | `INTEGER` | 不可 | `0` | ベスト到達時の残HP合計。同ウェーブのタイブレーク。`best_wave` を更新したウェーブの**突破直後に同時更新**する（全滅したウェーブは突破していないため対象外） |

自己ベストを別テーブルに分けず同一行に置く。ランキング（`GET /api/boss-rush/ranking`）が1プレイヤー1行の走査で済み、挑戦の開始・終了で行を作り直す必要がなくなるため。

真偽値は形容詞そのままの `active` とする（親 §2。行の主語がボスラッシュ状態そのもので曖昧さがなく、API の `bossRush.active`（`tech_state.md` §1）とも一致する）。

## 3. `boss_rush_milestones`（Phase 5・未実装）

実装予定: `com.afkgame.domain.model.BossRushMilestone`。初回到達したマイルストーンを1行ずつ記録する。報酬内容の正は [master/endgame.md](../../../data/master/endgame.md) §15.2。

| 列 | 型 | NULL | 既定 | 制約・備考 |
|----|----|------|------|-----------|
| `id` | `VARCHAR(36)` | 不可 | UUID4 | PK |
| `boss_rush_state_id` | `VARCHAR(36)` | 不可 | — | FK → `boss_rush_states.id` |
| `wave` | `INTEGER` | 不可 | — | 到達ウェーブ。刻み幅は `systems/endgame.md` §2.11 が正 |
| `claimed` | `BOOLEAN` | 不可 | `false` | 報酬を付与済みか |
| `claimed_at` | `DATETIME(tz)` | 可 | — | 付与時刻。未付与は NULL |

一意制約: `uq_boss_rush_milestones_state_wave` = (`boss_rush_state_id`, `wave`)（報酬は初回到達時のみのため、同一ウェーブは1行）

受取エンドポイントは設けず（[tech_api/endgame.md](../tech_api/endgame.md)「ボスラッシュ」）、到達したtick処理の中でサーバーが付与する。行の存在が「到達済み」、`claimed` が「付与完了」を表し、付与の途中でtickが中断しても再開できるよう2段階に分ける。

## 4. インデックスと検索パターン

主キーと一意制約が張るインデックスのみを持ち、二次インデックスは持たない（方針は `tech_db.md` §6）。

| 検索パターン | 使うインデックス | 判断 |
|------------|---------------|------|
| プレイヤーの戦闘ログを `tick_number` 順に引く | なし（`battle_logs.player_id`） | 二次インデックスを張らない。1プレイヤー100件の上限があり走査対象が小さい |
| 100件超の古いログを削除する | 同上 | 同上。削除は同じ走査結果を使う |
| プレイヤーのボスラッシュ状態を引く | `boss_rush_states.player_id`（UNIQUE） | 充足 |
| 到達済みマイルストーンを引く | `uq_boss_rush_milestones_state_wave` | 充足（左端が `boss_rush_state_id`） |
| ランキング上位100件を `best_wave` 降順で引く | なし（全行走査 + ソート） | 全プレイヤー横断クエリの1つで（もう1本は [player.md](player.md) §9 の深淵の塔ランキング）、行数が利用者数に比例する。(`best_wave`, `best_wave_hp`) の複合インデックス追加は `tech_db.md` §6-3 の再評価ラインで判断する |
