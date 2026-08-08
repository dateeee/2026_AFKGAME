# AFK GAME — テーブル定義書（索引）

> 技術仕様の索引は [tech_spec.md](../tech_spec.md)。本書は **DBスキーマの正**（物理テーブル名・列・型・キー・制約・インデックス）であり、[er_diagram.md](../../diagrams/er_diagram.md) は同じ内容の視覚化。食い違いは常に本書側へ揃える（[spec_ownership.md](../../process/spec_ownership.md) §2、[process/phases.md](../../process/phases.md) §3.2.1）。
> API・マスターデータの JSON 構造は [tech_data.md](tech_data.md) が正であり、本書は永続化スキーマのみを扱う。

---

## 1. 子ファイル索引

| 系統 | ファイル | 収録テーブル |
|------|---------|------------|
| 認証・アカウント | [tech_db/auth.md](tech_db/auth.md) | `users` `refresh_tokens` `email_verification_tokens` |
| プレイヤー・キャラクター | [tech_db/player.md](tech_db/player.md) | `players` `player_settings` `tower_clear_records` `characters` + 未実装4件（`party_members` `learned_skills` `active_skill_slots` `prestige_bonuses`） |
| 装備・アイテム・ショップ・施設 | [tech_db/item.md](tech_db/item.md) | `equipment` `character_equip_slots` `inventory_items` `shop_daily_states` `shop_daily_slots` + 未実装1件（`facilities`） |
| 戦闘・ボスラッシュ | [tech_db/battle.md](tech_db/battle.md) | `battle_logs` + 未実装2件（`boss_rush_states` `boss_rush_milestones`） |

上表で全テーブルを網羅する。ダンジョン・塔・敵・スキル・装備ベースはコード上のマスターデータでありDBテーブルを持たない（§4-6）。

## 2. 命名規約

| 対象 | 規約 | 例 |
|------|------|-----|
| テーブル名 | 小文字スネークケースの**複数形**。ただし不可算・集合名詞は単数形のまま | `players` / `character_equip_slots` / `equipment` |
| 列名 | 小文字スネークケース。外部キーは `<参照先の単数形>_id` | `player_id` / `shop_daily_state_id` |
| 主キー | 列名は `id` 固定 | — |
| 真偽値 | 状態を表す形容詞・過去分詞（`is_` は主語が曖昧な場合のみ） | `cleared` / `revoked` / `is_guest` |
| 日時 | `<動詞の過去分詞>_at` | `created_at` / `last_tick_at` |
| 一意制約 | `uq_<テーブル名>_<列>_<列>` | `uq_tower_clear_records_player_tower` |
| モデルクラス | テーブル名の単数形アッパーキャメル | `players` → `Player` |

API・スキーマ層は camelCase（`CamelModel`）で、DB列名の snake_case との変換はスキーマ層が担う。DB列名に camelCase を持ち込まない。

## 3. 型マッピング

DBMS は `local`・初期の `production` ともに SQLite、規模到達時に PostgreSQL へ移行する（[tech_operations.md](../nonfunctional/tech_operations.md) §12.4）。**SQLite 固有型に依存しない**ため、定義は SQLAlchemy 型で行い、各DBMSの実型は下表の対応に従う。

| 定義書の表記 | SQLAlchemy | SQLite | PostgreSQL | 用途 |
|------------|-----------|--------|-----------|------|
| `VARCHAR(n)` | `String(n)` | `VARCHAR(n)`（長さ非強制） | `varchar(n)` | ID・列挙値・名称 |
| `INTEGER` | `Integer` | `INTEGER` | `integer` | 階層・レベル・件数 |
| `BIGINT` | `BigInteger` | `INTEGER`(64bit) | `bigint` | ゴールド・EXP（32bit桁溢れ回避） |
| `FLOAT` | `Float` | `REAL` | `double precision` | 閾値・倍率 |
| `BOOLEAN` | `Boolean` | `INTEGER`(0/1) | `boolean` | フラグ |
| `DATETIME(tz)` | `DateTime(timezone=True)` | `DATETIME`（UTC文字列） | `timestamptz` | 時刻。**保存・比較は常に UTC** |
| `JSON` | `JSON` | `JSON`（実体は TEXT） | `json` | 要素数が可変で、DB側で検索しない構造（戦闘ログの `entries`） |

`JSON` 列は**アプリ側でのみ解釈する**。DBMS の JSON 関数・JSON インデックスを使う設計にしない（SQLite と PostgreSQL で機能が揃わないため）。列単位の検索が必要になった時点で、正規化したテーブルへ切り出す。

## 4. 共通の列規約

| # | 規約 |
|---|------|
| 1 | 主キーは `id`。採番はアプリ側で行う（`users` は `user_<uuid4>` / `guest_<uuid4>` の接頭辞つき、他は UUID4 文字列。`refresh_tokens`・`email_verification_tokens` のみ `INTEGER` の自動採番） |
| 2 | 既定値はアプリ側（SQLAlchemy の `default=`）で付与し、`server_default` は使わない。**既存テーブルへ列を追加する場合のみ** `nullable` または `server_default` を必須とする（前方互換。[tech_operations.md](../nonfunctional/tech_operations.md) §12.4） |
| 3 | `NULL` 欄が「可」の列だけが NULL を取りうる。`Mapped[T \| None]` と一対一に対応させる |
| 4 | 時刻列は UTC で保存する。ローカル時刻への変換は表示層が行う |
| 5 | 列挙値は `VARCHAR(n)` + 取りうる値の列挙で表現し、DBMS の ENUM 型は使わない（SQLite 非対応・移行容易性のため） |
| 6 | マスターデータ（塔・敵・スキル・装備ベース）はコード上の定義であり DBテーブルを持たない。それらを指す列（`current_tower_id` `tower_id` `skill_id` 等）は**DB外部キーを張らず**、値の妥当性はサービス層が検証する |

## 5. 外部キーの動作

| 項目 | 方針 |
|------|------|
| 参照動作 | 明示指定なし（`ON DELETE`・`ON UPDATE` は DBMS 既定の `NO ACTION`） |
| 削除 | アカウント削除・キャラ削除は**サービス層で子から順に削除**する。DBのカスケード削除に依存しない（削除順を実装から追跡可能にするため） |
| SQLite | 外部キー制約は接続ごとに `PRAGMA foreign_keys` が必要で既定は無効。**制約の実効的な担保はサービス層**とし、定義書の FK 表記は参照関係の宣言として扱う |

## 6. インデックスの方針

| # | 方針 |
|---|------|
| 1 | インデックスは**それを使う検索パターンとセット**で定義する（[process/phases.md](../../process/phases.md) §3.2.1）。パターンの無いインデックスは作らない |
| 2 | 主キー・一意制約が張るインデックスを第一に使う。単独の外部キー列には既定でインデックスを張らない |
| 3 | 二次インデックスの追加は、DBサイズ 850MB 接近（≒1万人規模）の再評価ラインで判断する（[tech_operations.md](../nonfunctional/tech_operations.md) §12.4、[tech_performance.md](../nonfunctional/tech_performance.md) §10.3）。各系統ファイルの「インデックスと検索パターン」表に、評価済みのパターンと現時点の判断を記録する |

## 7. 導入Phase の表記

各テーブルの見出しに `(Phase N)` を付ける。`未実装` と併記したテーブル・列は定義のみが確定しており、実装は該当Phaseの製造で行う。**定義書に無いテーブル・列を実装してはならない**（必要と判明した時点で基本設計へ差し戻す。[process/phases.md](../../process/phases.md) §3.2.1）。
