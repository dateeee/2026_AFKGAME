# AFK GAME — 限界突破の処理仕様（Phase 4〜）

> エンドポイント定義の正は [tech_api.md](../basic/tech_api.md)「キャラクター」、状態×操作可否の正は [tech_state.md §4](tech_state.md)、データ構造は [tech_data.md](../basic/tech_data.md) と [tech_db/player.md §4](../basic/tech_db/player.md)。
> **ボーナス量の正は [master/character.md §8.1](../../data/master/character.md)**（本書では値を再掲しない）。実効ステータスへの適用位置は [tech_battle.md §3.1.1](tech_battle.md)、丸めは [tech_numeric.md](tech_numeric.md)。
> ゲーム仕様は [systems/character.md](../../design/systems/character.md)「限界突破」。エラーコードの体系は [tech_error_handling.md](../basic/tech_error_handling.md)。

## 1. 前提

| 項目 | 内容 |
|------|------|
| 対象 | `POST /api/character/limit-break`（基点キャラ1体 + 素材キャラ1体） |
| 必要素材 | 基点と同一マスターキャラ（`characters.master_id` が一致）のキャラ1体。**ゴールド・素材アイテムは消費しない** |
| 上限 | 突破回数5回（`master/character.md` §8.1）。1リクエストで進むのは1回のみ |
| 実行可否 | 塔外（`IDLE`）のみ。探索中は不可（[tech_state.md §4](tech_state.md)） |

- 同一性の判定は `name` ではなく `master_id` で行う（[tech_db/player.md §4](../basic/tech_db/player.md)）。`master_id` は Phase 4 で追加する列で、Phase 3 以前に作られた行は Phase 4 のマイグレーションで名前から補完される
- 重複キャラは Phase 4 の酒場スカウトでのみ発生する（[tech_scout.md §4](tech_scout.md)）。塔クリア報酬は既所持なら付与しない（[tech_party.md §2](tech_party.md)）

## 2. 処理フロー（POST /api/character/limit-break）

1. プレイヤー状態を確認する。`EXPLORING` / `IN_BATTLE` / `BOSS_RUSH` は `400 CHARACTER_LOCKED_IN_TOWER`
2. `characterId`（以下「基点」）が自プレイヤーの所持キャラか確認する。未所持・不存在は `404 CHARACTER_NOT_FOUND`
3. `materialCharacterId`（以下「素材」）を同様に確認する。未所持・不存在は `404 CHARACTER_NOT_FOUND`
4. 基点と素材が**同一行**なら `422 CHARACTER_MATERIAL_SAME_AS_BASE`（自分自身を素材にできない）
5. 両者の `master_id` が一致するか確認する。不一致、または**いずれか・両方が NULL** なら `422 CHARACTER_MATERIAL_MISMATCH`（NULL 同士も一致とみなさない）
6. 基点の `limit_break` が上限に達していれば `400 CHARACTER_LIMIT_BREAK_MAX`
7. 素材がパーティに編成されていれば `422 CHARACTER_MATERIAL_IN_PARTY`（先に編成から外させる）。**基点はパーティ内でもよい**
8. 基点の `limit_break` を +1 する（§3）
9. 素材キャラを削除する（波及は §4）
10. 手順8〜9を1トランザクションでコミットする。途中で失敗した場合は全件ロールバックする
11. `200`。更新後の基点を返す（§5）

出口条件: 基点の突破回数+1と素材1行の削除が**ともに成立**しているか、**いずれも起きていない**かのどちらか。

- 検証（手順1〜7）は**すべて更新の前**に行う。素材を消費したのに突破できない経路を持たない
- 素材の突破回数・LV・EXP・装備・習得スキルは**引き継がない**。基点の突破回数は素材の状態によらず常に +1
- パーティ編成そのものは変更しない（基点が編成中でも枠は動かない）

## 3. 突破後のステータス反映

| 対象 | 反映 |
|------|------|
| `limit_break` | +1（上限5） |
| ボーナス率 | 突破回数から `master/character.md` §8.1 の表を**引く**。回数と率が線形でない（5回目のみ+10ポイント）ため計算式にせず表引きとする。突破回数0はボーナスなし（表に行を持たない） |
| 実効ステータス | [tech_battle.md §3.1.1](tech_battle.md) の②で `(1 + limit_break_pct)` として乗算する。整数化は⑤の後に1回だけ（[tech_numeric.md §1](tech_numeric.md)） |
| `max_hp`・`base_atk`・`base_def`・`base_spd`（保存値） | **変更しない**。実効値は読み取り時に算出する |
| `hp`（現在HP） | **変更しない**。実効最大HPが増えても回復は行わない |
| `level`・`exp`・`skill_points`・`rarity`・習得スキル・装備 | 変更しない |

- 保存値へボーナスを織り込まない（[tech_scout.md §5](tech_scout.md) と同じ理由。読み取り時にも掛けると二重適用になる）
- 探索中は実行できないため、探索セッション中に実効ステータスが変わることはない（[tech_state.md §4](tech_state.md) の根拠）

## 4. 素材キャラ削除時の波及

素材は物理削除する（`characters` に論理削除列を持たない。[tech_db/player.md §4](../basic/tech_db/player.md)）。子テーブルは同一トランザクション内で先に削除する。

| テーブル | 扱い |
|---------|------|
| `party_members` | 手順7で素材がパーティ外であることを保証しているため、削除対象の行は無い |
| `learned_skills` | 素材の行をすべて削除する（習得スキルは引き継がない） |
| `active_skill_slots` | 素材の行をすべて削除する |
| `character_equip_slots` | 素材の行（全9スロット）を削除する。装備解除に相当する |
| `equipment` | **削除しない**。プレイヤー所持のまま残る。所持枠は「装備1件＝1枠」で装着状態によらないため増減せず、枠の判定も不要（[tech_base.md §2.3](tech_base.md)） |
| `prestige_bonuses` | Phase 5 で素材が行を持つ場合は削除する（転生ボーナスは引き継がない） |
| `characters` | 素材の行を削除する |

## 5. API

| 項目 | 内容 |
|------|------|
| リクエスト | `characterId`・`materialCharacterId`（ともに必須・文字列）。欠落・空文字は `422`（スキーマ検証） |
| 成功 | `200`。`character`・`bonusPercent`・`removedCharacterId` を返す |
| 失敗 | §6 のコード。形式は [tech_error_handling.md](../basic/tech_error_handling.md)「統一エラーレスポンス形式」 |

| レスポンス項目 | 内容 |
|--------------|------|
| `character` | 更新後の基点。[tech_data.md §1.1](../basic/tech_data.md) のキャラクターオブジェクトと**同形**（`limitBreak`・`effectiveMaxHp`・`effectiveAtk`・`effectiveDef`・`effectiveSpd` を含む） |
| `bonusPercent` | 適用後の累計ボーナス率をパーセントの整数で返す（3回目なら `15`）。フロントが表を引かずに表示するための導出値 |
| `removedCharacterId` | 消費した素材のID。フロントはこの1行だけをストアから除く |

- 更新後のステータスは `character` に含めて返す（キャラ一覧と同じ型で受け取れるようにするため、専用の差分オブジェクトを作らない）

## 6. 分岐一覧（限界突破）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | リクエスト検証 | `characterId`・`materialCharacterId` がともに指定されている | 続行する |
| 2 | リクエスト検証 | いずれかが欠落、または空文字 | `422`（スキーマ検証）。何も変更しない |
| 3 | 状態 | 塔外（`IDLE`） | 続行する |
| 4 | 状態 | 探索中（`EXPLORING` / `IN_BATTLE` / `BOSS_RUSH`） | `400 CHARACTER_LOCKED_IN_TOWER`。何も変更しない |
| 5 | 基点の所持 | 自プレイヤーの所持キャラ | 続行する |
| 6 | 基点の所持 | 未所持・存在しないID | `404 CHARACTER_NOT_FOUND` |
| 7 | 素材の所持 | 自プレイヤーの所持キャラ | 続行する |
| 8 | 素材の所持 | 未所持・存在しないID | `404 CHARACTER_NOT_FOUND` |
| 9 | 自己指定 | 基点と素材が同一のキャラ | `422 CHARACTER_MATERIAL_SAME_AS_BASE` |
| 10 | 自己指定 | 基点と素材が別のキャラ | 続行する |
| 11 | 同一性 | 両者の `master_id` が一致する | 続行する |
| 12 | 同一性 | `master_id` が不一致 | `422 CHARACTER_MATERIAL_MISMATCH` |
| 13 | 同一性 | いずれか、または両方の `master_id` が NULL | `422 CHARACTER_MATERIAL_MISMATCH`（NULL 同士も一致とみなさない） |
| 14 | 突破回数 | 基点の突破回数が上限未満 | 続行する |
| 15 | 突破回数 | 基点の突破回数が上限に達している | `400 CHARACTER_LIMIT_BREAK_MAX`。素材は消費しない |
| 16 | 素材の編成 | 素材がパーティ外（控え） | 続行する |
| 17 | 素材の編成 | 素材がパーティに編成されている | `422 CHARACTER_MATERIAL_IN_PARTY`。何も変更しない |
| 18 | 基点の編成 | 基点がパーティに編成されている | 実行する（編成は変更しない） |
| 19 | 基点の編成 | 基点がパーティ外（控え） | 実行する |
| 20 | 突破の適用 | 突破回数が0から1になる | `master/character.md` §8.1 の1回目のボーナス率を適用する |
| 21 | 突破の適用 | 突破回数が4から5（上限ちょうど）になる | §8.1 の5回目のボーナス率を適用する。以後の要求は #15 で拒否する |
| 22 | 素材の突破回数 | 素材が1回以上突破済み | 基点は +1 のみ（素材の回数を引き継がない） |
| 23 | 素材の突破回数 | 素材が未突破（0回） | 基点は +1 |
| 24 | 素材の装備 | 素材が装備を装着している | スロットを削除して装備解除する。`equipment` 行は残り所持枠も変わらない |
| 25 | 素材の装備 | 素材が装備を1件も装着していない | 装備の変更は起きない |
| 26 | 素材の習得スキル | 素材が習得スキル・セット枠を持つ | いずれも削除する（引き継がない） |
| 27 | 素材の習得スキル | 素材が習得スキルを持たない | 削除対象なしで続行する |
| 28 | 現在HP | 突破で実効最大HPが増える | 現在HPは変更しない（回復しない） |
| 29 | トランザクション | 素材の削除で失敗した | 基点の突破回数を含めて全件ロールバックする |
| 30 | トランザクション | 全手順が成功した | 突破回数+1と素材削除がともに確定する |

> WARN許容 #28: 実効最大HPが減る突破は存在せず、対になる条件を持たないため1行。

## 7. 新設エラーコード一覧

| コード | HTTP | 発生箇所 |
|--------|------|---------|
| `CHARACTER_LOCKED_IN_TOWER` | 400 | §2 手順1（探索中） |
| `CHARACTER_MATERIAL_SAME_AS_BASE` | 422 | §2 手順4（自分自身を素材に指定） |
| `CHARACTER_MATERIAL_MISMATCH` | 422 | §2 手順5（別キャラ・`master_id` が NULL） |
| `CHARACTER_LIMIT_BREAK_MAX` | 400 | §2 手順6（突破回数が上限） |
| `CHARACTER_MATERIAL_IN_PARTY` | 422 | §2 手順7（素材が編成中） |

既存コード `CHARACTER_NOT_FOUND`（404）は [tech_party.md §7](tech_party.md) が正。
