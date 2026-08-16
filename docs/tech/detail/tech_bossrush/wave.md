# ウェーブ進行（tick内・正規シミュレーション）

> [tech_bossrush.md](../tech_bossrush.md) の子ファイル（§7・§8）。敵構成・強化倍率・刻みの解決は同 §2、数値の正は [master/endgame.md](../../../data/master/endgame.md) §15。
> 本書は未処理tickが100以下のときの1tickずつの進行を定義する。101tick以上は [offline.md §9](offline.md)、終了処理そのものは [control.md §11](control.md) が持つ。

## 7. 1tick内のウェーブ進行

`active = true` のプレイヤーのtick処理は、塔の階進行（[tech_tower/progress.md](../tech_tower/progress.md)）ではなく本節を通る。

1. **ウェーブの解決**: `current_wave` から敵構成と強化倍率を決める（tech_bossrush.md §2）。ウェーブ途中のtickでは前tickの敵HP・味方HPを引き継ぐ
2. **ターン処理**: [tech_battle.md §3.1](../tech_battle.md) をそのまま実行する（ポーション自動使用・スキル・状態異常を含む）
3. **決着判定**
   - 敵が全滅 → 手順4（突破処理）
   - 味方が全滅 → [control.md §11](control.md) の終了処理（全滅）へ。**残tickは破棄する**
   - どちらも残っている → 同一ウェーブのまま次tickへ持ち越す
4. **突破処理**（順序は固定。突破したウェーブを `wave` とする）
   - a. **累積報酬**: `floor(そのウェーブに出現した敵の Gold 合計 × 累積報酬係数)` を `accumulated_gold` へ、EXP も同様に `accumulated_exp` へ加算する（係数の正は master/endgame.md §15.3）。強化倍率適用**後**の値を基準にし、丸めは1回だけ
   - b. **自己ベスト更新**: 突破直後の在籍パーティ残HP合計を `hpSum` とし、`(wave, hpSum)` が `(best_wave, best_wave_hp)` を辞書順で上回るときだけ更新する
   - c. **マイルストーン付与**: `wave` が刻み（tech_bossrush.md §2）に一致するなら `boss_rush_milestones` を `(boss_rush_state_id, wave)` で引き、無ければ `claimed = false` で行を作成 → 報酬を付与 → `claimed = true`・`claimed_at = 現在時刻`
   - d. **定期回復**: `wave` が5の倍数なら、生存者（`hp > 0`）へ `floor(effectiveMaxHp × 定期回復率)` を加算する。上限は `effectiveMaxHp`
   - e. **次ウェーブへ**: `current_wave = wave + 1`。全キャラのスキルCDを0にリセットし、バフ・デバフ・状態異常をすべて解除する
5. 残tickがあれば手順1へ戻る（1tickで複数ウェーブを突破しうる）

- 手順4-b を辞書順比較にする理由: 同じウェーブへ再到達したときに残HPが高ければタイブレークの記録も改善されるべきで、`best_wave` の更新時だけに限ると初回到達時の値で固定されてしまうため（順位指標の正は [systems/endgame.md §2.11](../../../design/systems/endgame.md)「ランキング」）
- 手順4-c を2段階（行作成 → 付与 → `claimed`）にするのは、付与の途中でtickが中断しても再開できるようにするため（[tech_db/battle.md §3](../../basic/tech_db/battle.md)）。素材の所持枠上限は [tech_base.md §8](../tech_base.md) が持ち、本書では扱わない
- 手順4-d を生存者に限るのは階進行の環境効果回復（tech_tower/progress.md 手順2）と同じ扱い。戦闘不能のメンバーはウェーブ跨ぎでも復帰しない
- ゴールド・EXPは挑戦中プレイヤーへ渡らないため、**ウェーブ進行中にレベルアップは起きない**（付与は control.md §11）

## 8. 分岐一覧（ウェーブ進行）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | ウェーブ帯 | `wave <= 5` | 塔 `00wave` の `totalFloors − 1` 階の敵構成を倍率1.0で用いる |
| 2 | ウェーブ帯 | `6 <= wave <= 10` | 塔 `00(wave − 5)` の最上階ボス1体を倍率1.0で用いる |
| 3 | ウェーブ帯 | `wave >= 11` | Wave 10 の構成へ `1.1 ^ (wave − 10)` を乗じ、倍率適用後に1回だけ丸める |
| 4 | 決着判定 | 敵が全滅した | 突破処理（手順4）へ進む |
| 5 | 決着判定 | 味方が全滅した | 終了処理（control.md §11）へ進み、残tickを破棄する |
| 6 | 決着判定 | 敵・味方とも残っている | 同一ウェーブのまま次tickへ持ち越す |
| 7 | 累積報酬 | ウェーブを突破した | 敵のGold／EXP合計に係数を掛けた値を `accumulated_*` へ加算する |
| 8 | 累積報酬 | 加算後が `MAX_GOLD` を超える | `MAX_GOLD` で飽和させる（[tech_numeric.md §3](../tech_numeric.md)） |
| 9 | 自己ベスト | `wave > best_wave` | `best_wave = wave`・`best_wave_hp = hpSum` を同時に更新する |
| 10 | 自己ベスト | `wave = best_wave` かつ `hpSum > best_wave_hp` | `best_wave_hp` だけを更新する |
| 11 | 自己ベスト | `wave = best_wave` かつ `hpSum <= best_wave_hp`（同値を含む） | 更新しない |
| 12 | 自己ベスト | `wave < best_wave` | 更新しない |
| 13 | マイルストーン | 刻みに一致し、行が無い | 行を作成して報酬を付与し `claimed = true`・`claimed_at` を記録する |
| 14 | マイルストーン | 刻みに一致し、行があり `claimed = false` | 付与を再開して `claimed = true` にする（行は作り直さない） |
| 15 | マイルストーン | 刻みに一致し、行があり `claimed = true` | 付与しない（初回到達時のみ） |
| 16 | マイルストーン | 刻みに一致しない | 付与しない |
| 17 | 定期回復 | `wave` が5の倍数 | 生存者へ `floor(effectiveMaxHp × 定期回復率)` を加算する |
| 18 | 定期回復 | `wave` が5の倍数でない | 回復しない |
| 19 | 定期回復 | 回復後のHPが `effectiveMaxHp` を超える | `effectiveMaxHp` でクランプする（戦闘不能のメンバーは対象外） |
| 20 | ウェーブ移行 | ウェーブを突破した | `current_wave` を+1し、CD・バフ・デバフ・状態異常をすべてクリアする |
| 21 | ウェーブ移行 | ウェーブを突破していない | CD・バフ・デバフ・状態異常を維持したまま次tickへ引き継ぐ |
| 22 | 敵構成の解決 | 参照先の塔・階・ボスがマスターデータに無い | ERRORログ（`INTERNAL_MASTER_DATA_INVALID`）+ `500 INTERNAL_UNEXPECTED_ERROR` |
| 23 | 1tick内の突破数 | 0ウェーブ（決着しない） | 突破処理を実行しない |
| 24 | 1tick内の突破数 | 1ウェーブ | 突破処理を1回だけ実行する |
| 25 | 1tick内の突破数 | 2ウェーブ以上 | ウェーブごとに手順4を繰り返し、報酬・記録・マイルストーンを都度確定する |

> WARN許容 #22: 例外経路（マスターデータ不正）。正常側は #1〜#3 が解決結果を持つ。
