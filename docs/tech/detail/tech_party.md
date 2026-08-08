# AFK GAME — パーティ・スキル操作の処理仕様（Phase 3〜）

> エンドポイント定義の正は [tech_api.md](../basic/tech_api.md)「パーティ・スキル」、状態×操作可否の正は [tech_state.md §4](tech_state.md)、データ構造は [tech_data.md](../basic/tech_data.md) と [er_diagram/player.md](../../diagrams/er_diagram/player.md)、数値の正は [master/character.md](../../data/master/character.md)・[skills/](../../data/skills/SKILLS_OVERVIEW.md)。
> エラーコードの体系は [tech_logging.md](../basic/tech_logging.md)。本書の各表に個別コードを定義する。

## 1. パーティ編成変更（PUT /api/party/edit）

1. プレイヤー状態を確認する。探索中（`EXPLORING` / `IN_BATTLE` / `BOSS_RUSH`）は `400 PARTY_LOCKED_IN_TOWER`
2. `memberIds` を検証する: 1〜4件（スキーマ検証）・重複なし・全IDが所持キャラ
3. パーティを全置換する。配列順は表示順のみに使用する（行動順・タイブレークはSPD順とキャラID順が正。[tech_battle §3.1](tech_battle.md)）
4. `200`: 更新後の編成を返す

### 1. 分岐一覧（単体テスト観点）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | 状態 | 塔外（`IDLE`） | 編成を更新する |
| 2 | 状態 | 探索中 | `400 PARTY_LOCKED_IN_TOWER`。編成は変更しない |
| 3 | 件数 | 1〜4件 | 受理する |
| 4 | 件数 | 0件、または5件以上 | `422`（スキーマ検証。編成は変更しない） |
| 5 | 重複 | 配列内に同一キャラIDがある | `422 PARTY_MEMBER_DUPLICATED` |
| 6 | 重複 | 重複なし | 受理する |
| 7 | 所持 | 未所持のキャラIDを含む | `422 PARTY_MEMBER_NOT_OWNED` |
| 8 | 所持 | 全IDが所持キャラ | 受理する |

## 2. キャラクター獲得（塔クリア報酬・tick処理内）

階クリア処理で、確定入手キャラ（[master/character.md §7.1](../../data/master/character.md)）の入手条件（塔・階）に一致するかを判定する。

1. 対象階のクリア時、該当キャラを**未所持なら**付与する。初期状態は LV1・EXP0・SP0・スキル未習得・装備なし・HP=maxHP（加入時LV1 の根拠は [master/character.md §7.1](../../data/master/character.md)）
2. 既所持（周回・再クリア）なら何もしない（Phase 3 に重複の概念はない。重複→限界突破素材は Phase 4 の酒場ガチャのみ）
3. 付与してもパーティへ自動編入しない（控えとして加入。編成はプレイヤー操作）
4. 戦闘ログに `type: "character_join"` の行を追加する（生JSON・snake_case。[tech_battle §1](tech_battle.md)）

### 2. 分岐一覧（単体テスト観点）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | 入手判定 | 対象塔・階をクリアし未所持 | キャラを付与する（LV1・SP0・スキル未習得） |
| 2 | 入手判定 | 対象塔・階をクリアしたが既所持 | 付与しない |
| 3 | 入手判定 | 入手条件のない階をクリア | 判定しない |
| 4 | 自動編入 | キャラを付与した | パーティは変更しない（控えに加入） |
| 5 | オフライン | 簡略計算中に対象階を初めてクリア | 同様に付与し、オフラインサマリーに含める |
| 6 | オフライン | 簡略計算中の周回で再クリア | 付与しない |

> WARN許容 #4: 付与後の無条件処理で、対になる条件を持たないため1行（付与しない場合は編成に触れる余地がない）。

## 3. スキル習得（POST /api/skill/learn）

1. `characterId` の所持を確認する。未所持・不存在は `404 CHARACTER_NOT_FOUND`
2. `skillId` がスキルマスターに存在するか確認する。未知IDは `422 SKILL_UNKNOWN`
3. 習得済みなら `400 SKILL_ALREADY_LEARNED`
4. 前提スキル（ツリー上の直前スキル。[skills/](../../data/skills/SKILLS_OVERVIEW.md) 各系統ファイル §2）が未習得なら `400 SKILL_PREREQUISITE_NOT_MET`
5. 必要SPが未使用SP（`Character.skill_points`）を超えるなら `400 SKILL_INSUFFICIENT_SP`
6. `LearnedSkill` を追加し `skill_points` を減算して `200`

探索中も許可する（[tech_state §4](tech_state.md)。次tickの戦闘から反映）。

### 3. 分岐一覧（単体テスト観点）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | キャラ確認 | 所持キャラ | 続行する |
| 2 | キャラ確認 | 未所持・存在しないID | `404 CHARACTER_NOT_FOUND` |
| 3 | スキルID | マスターに存在する | 続行する |
| 4 | スキルID | 未知のID | `422 SKILL_UNKNOWN` |
| 5 | 習得済み判定 | 既に習得している | `400 SKILL_ALREADY_LEARNED` |
| 6 | 習得済み判定 | 未習得 | 続行する |
| 7 | 前提スキル | 前提を習得済み、またはTier1（前提なし） | 続行する |
| 8 | 前提スキル | 前提が未習得 | `400 SKILL_PREREQUISITE_NOT_MET` |
| 9 | SP判定 | 未使用SPが必要SP以上（ちょうども含む） | SPを減算して習得する |
| 10 | SP判定 | 未使用SPが必要SP未満 | `400 SKILL_INSUFFICIENT_SP` |

## 4. アクティブスキルセット変更（PUT /api/skill/set-active）

1. キャラ所持確認（`404 CHARACTER_NOT_FOUND`）
2. `activeSlots`（スキルID配列・0〜2件。配列順がセット枠1・2）を検証する
3. `ActiveSkillSlot` を全置換して `200`

CDカウンターは**習得スキルごとに保持**し、セット変更で変化しない（未セット中もターン経過で減算される。塔出発時の全CD=0が唯一のリセット。[tech_battle §3.1](tech_battle.md)）。探索中も許可する。

### 4. 分岐一覧（単体テスト観点）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | 件数 | 0〜2件（0件=全解除） | 受理する |
| 2 | 件数 | 3件以上 | `422`（スキーマ検証） |
| 3 | 重複 | 同一スキルを2枠に指定 | `422 SKILL_SLOT_DUPLICATED` |
| 4 | 種別 | パッシブスキルを指定 | `422 SKILL_NOT_ACTIVE` |
| 5 | 種別 | アクティブスキルのみ | 受理する |
| 6 | 習得確認 | 未習得のスキルを指定 | `400 SKILL_NOT_LEARNED` |
| 7 | 習得確認 | 全て習得済み | セットを全置換する |
| 8 | CD保持 | セット変更した | 各スキルのCDカウンターは変化しない |
| 9 | 重複 | 重複なし | 受理する |

> WARN許容 #8: セット変更が成功した場合の不変条件で、対になる条件を持たないため1行。

## 5. スキルリセット（POST /api/skill/reset）

1. キャラ所持確認（`404 CHARACTER_NOT_FOUND`）
2. 習得スキルが0件なら `400 SKILL_NOTHING_TO_RESET`（誤操作の課金防止）
3. コスト = `キャラLV × 50`G（整数演算のみ・丸めなし）。所持ゴールド不足は `400 SKILL_INSUFFICIENT_GOLD`
4. 成功時: ゴールド減算 → `LearnedSkill` 全削除 → `ActiveSkillSlot` 全解除 → `skill_points` に削除したスキルの必要SP合計を加算 → `200`

### 5. 分岐一覧（単体テスト観点）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | キャラ確認 | 未所持・存在しないID | `404 CHARACTER_NOT_FOUND` |
| 2 | キャラ確認 | 所持キャラ | 続行する |
| 3 | 習得数 | 習得スキルが0件 | `400 SKILL_NOTHING_TO_RESET`。ゴールドは減算しない |
| 4 | 習得数 | 1件以上 | 続行する |
| 5 | ゴールド | 所持ゴールドがコスト未満 | `400 SKILL_INSUFFICIENT_GOLD`。状態は変更しない |
| 6 | ゴールド | コストちょうど、または超過 | 減算して実行する（ちょうどの場合は残0） |
| 7 | 返却 | 成功した | 消費SP合計が未使用SPへ戻り、習得・セットが全て解除される |

> WARN許容 #7: 成功時の無条件処理で、失敗側は #1・#3・#5 が持つため1行。

## 6. SP獲得（戦闘処理・オフライン簡略計算内）

レベルアップ1回につき `skill_points` を+1する（[character.md §2.8](../../design/systems/character.md)）。付与タイミングは戦闘処理のレベルアップ確定時（オンライン・オフラインの双方）。

### 6. 分岐一覧（SP獲得）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | SP付与 | レベルアップ1回 | SP+1 |
| 2 | SP付与 | 同一tickで複数レベルアップ | 上がったレベル数だけ加算する |
| 3 | SP付与 | レベルアップなし | 変化なし |

> オフライン中のスキル自動習得は行わない（[tech_offline.md §4](tech_offline.md)「スキルポイントの扱い」）。

## 7. 新設エラーコード一覧

| コード | HTTP | 発生箇所 |
|--------|------|---------|
| `PARTY_MEMBER_DUPLICATED` | 422 | §1（配列内の重複） |
| `PARTY_MEMBER_NOT_OWNED` | 422 | §1（未所持キャラ指定） |
| `CHARACTER_NOT_FOUND` | 404 | §3〜§5（キャラ不存在・未所持） |
| `SKILL_UNKNOWN` | 422 | §3（未知のスキルID） |
| `SKILL_ALREADY_LEARNED` | 400 | §3 |
| `SKILL_NOT_ACTIVE` | 422 | §4（パッシブ指定） |
| `SKILL_SLOT_DUPLICATED` | 422 | §4 |
| `SKILL_NOT_LEARNED` | 400 | §4 |
| `SKILL_NOTHING_TO_RESET` | 400 | §5 |
| `SKILL_INSUFFICIENT_GOLD` | 400 | §5 |

既存コード `PARTY_LOCKED_IN_TOWER`・`SKILL_INSUFFICIENT_SP`・`SKILL_PREREQUISITE_NOT_MET` は [tech_state.md](tech_state.md)・[tech_logging.md](../basic/tech_logging.md) を参照。
