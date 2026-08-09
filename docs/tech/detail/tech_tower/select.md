# 入塔（POST /api/tower/select）

> [tech_tower.md](../tech_tower.md) の子ファイル（§7・§8）。要求・応答は tech_tower.md §3、エラーコードは同 §4。
> 検証は**すべて状態変更の前**に行い、最初に失敗した検証のエラーを返す（後続は評価しない）。

## 7. 入塔の処理フロー

検証の順序は固定（入力 → 状態 → 対象 → 権利 → 引数 → 前提）。

1. Bean Validation: `towerId`（必須）・`targetFloor`（必須・**1以上**）・`mode`（`auto_repeat` \| `stop_on_clear`。省略時 `auto_repeat`）。違反は `422`
   - `targetFloor` の**下限**はここで検証する。**上限**は到達状況に依存するため手順6で業務検証する（`400`）
2. `players` 行を行ロックで取得（tech_tower.md §1）。入塔中（`currentTowerId ≠ null`）なら `400 TOWER_ALREADY_IN_TOWER`。ボスラッシュ中（Phase 5〜）も同じ扱い（[tech_state.md §4](../tech_state.md)）
3. `towerId` を塔マスターで解決。存在しなければ `404 TOWER_NOT_FOUND`
4. 解放判定（tech_tower.md §2）。未解放なら `403 TOWER_NOT_UNLOCKED`
5. （Phase 5〜）`difficulty` の整合: イベントダンジョンで欠落、または通常塔・深淵の塔で指定は `400 TOWER_INVALID_DIFFICULTY`。以降の到達記録はこの難易度のキーで引く
6. `targetFloor` を `cap`（tech_tower.md §2）と比較。`targetFloor > cap` なら `400 TOWER_INVALID_FLOOR`
7. パーティに `hp > 0` のキャラが1体もいなければ `400 TOWER_PARTY_WIPED`（[tech_state.md](../tech_state.md) §4。Phase 1〜2 は勇者1体のHPで判定。通常の離脱経路では全員HP0のまま塔外にならないため防御的検証だが、Phase 3〜 は編成替えで到達しうる）
8. 状態を初期化して `200`:
   - `currentTowerId = towerId`・`currentFloor = 1`・`targetFloor`・`towerMode = mode` を反映
   - `currentEnemyId = null`・`currentEnemyHp = null`（敵は次tickのエンカウントで抽選。[tech_battle.md §3.2](../tech_battle.md)）
   - 探索セッションをリセット（[tech_state.md §3](../tech_state.md)）
   - HPは変更しない（入塔で回復しない）

## 8. 分岐一覧（入塔）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | 入力検証 | `towerId` 欠落・`mode` が2値以外・`targetFloor` が1未満または非整数 | `422`（状態を変更しない） |
| 2 | 入力検証 | すべて適合（`mode` 省略は `auto_repeat` として扱う） | 手順2へ進む |
| 3 | 状態 | 入塔中（ボスラッシュ中 Phase 5〜 を含む） | `400 TOWER_ALREADY_IN_TOWER` |
| 4 | 状態 | 塔外（`IDLE`） | 続行 |
| 5 | 塔の解決 | `towerId` が塔マスターに存在しない | `404 TOWER_NOT_FOUND` |
| 6 | 塔の解決 | 存在する | 続行 |
| 7 | 解放 | 未解放の塔 | `403 TOWER_NOT_UNLOCKED` |
| 8 | 解放 | 解放済みの塔 | 続行 |
| 9 | 難易度（Phase 5〜） | イベントで `difficulty` 欠落／通常塔・深淵で指定 | `400 TOWER_INVALID_DIFFICULTY` |
| 10 | 難易度（Phase 5〜） | 整合（イベントで指定・通常塔で省略） | 続行 |
| 11 | 目標階 | `targetFloor > cap` | `400 TOWER_INVALID_FLOOR` |
| 12 | 目標階 | `1 <= targetFloor <= cap` | 続行 |
| 13 | パーティ | 全員HP0 | `400 TOWER_PARTY_WIPED` |
| 14 | パーティ | `hp > 0` が1体以上 | 続行 |
| 15 | 成功 | 手順8の初期化 | `200`。`currentFloor = 1`・敵情報 null・探索セッションリセット・HP変更なし |

> WARN許容 #15: 成功系は1行（各検証の通過側は #2〜#14 の偶数行が担う）。
> #11・#12 は上限が `highestFloor + 1` 側で決まる場合と `totalFloors` 側で決まる場合の**両方**を試験する（[tech_state.md](../tech_state.md) §1.1 階の範囲）。
