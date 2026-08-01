# AFK GAME — 戦闘ログ・戦闘処理仕様

> 技術仕様の全体は [tech_spec.md](tech_spec.md)、ゲーム仕様は [game_spec.md](../design/game_spec.md) を参照。

> 本書は **戦闘ログ保持ポリシー（§1）** と **戦闘処理フロー（§3）** を扱う。オフライン計算（§2 パフォーマンス対策・§4 簡略計算アルゴリズム）は [tech_offline.md](tech_offline.md) に分離した（節番号は分離前を維持）。

---

## 1. 戦闘ログ保持ポリシー

- DBに保存する戦闘ログは **直近100件** を上限とする
- 上限を超えた古いログはtick処理時に自動削除
- フロント側に返却するログも **直近50件** まで（ポーリング1回あたり）
- オフライン復帰時のサマリーは集計結果のみ返却し、個別ログは保持しない
- **注意**: `TickResponse.battle_logs`は生JSONオブジェクトの配列であり、CamelModel変換の対象外。キー名はsnake_case（例: `target_hp`, `max_hp`, `exp_lost`）のまま返却される

## 3. 戦闘処理フロー（Phase 3〜: パーティ・スキル対応）

### 3.1 1ターンの処理フロー

```
0. 初期状態:
   - 塔出発時: 全スキルCD=0（即使用可能）
   - 階移行時: CDは継続（リセットしない）
   - ボスラッシュ: ウェーブ開始時にCD全リセット（§3.1.4参照）

1. 全キャラクター（味方パーティ + 敵）をSPD降順でソート
   - 同SPD時はキャラID順（固定タイブレーク）

2. 各キャラクターの行動（SPD順に処理）:
   a. キャラが戦闘不能（HP=0）ならスキップ
   b. 麻痺判定: 麻痺状態なら30%の確率で行動不能→スキップ
   c. スタン判定: スタン状態なら行動不能→スキップ

   d. 毒DOT適用（行動前）:
      - 毒状態なら maxHP×5% ダメージ
      - 環境毒霧があれば別途 maxHP×2% ダメージ（別系統、重複する）
      - DOTでHP0になった場合、以降の行動をスキップ
      - ※DOT後のポーション再判定はしない

   e. パッシブスキル「リジェネ」適用（ターン開始時HP回復）

   f. ポーション自動使用判定（HP閾値以下なら使用）

   g. アクティブスキル発動判定（キャラ単位で1ターン1スキルまで）:
      - セット枠2つのスキルをチェック
      - 優先度: 回復 > バフ/デバフ > 攻撃
      - 同優先度のスキルが両方条件を満たす場合: セット枠1を優先
      - 沈黙状態の場合: スキル発動をスキップ（通常攻撃のみ）
      - 発動条件:
        - 回復スキル: 味方HP40%以下 + CD完了
        - 蘇生スキル: 味方にHP0のキャラ + CD完了（回復より優先）
        - バフ/デバフスキル: CD完了
        - 攻撃スキル: CD完了
      - 発動したスキルのCDカウンターをリセット

   h. スキル未発動の場合: 通常攻撃

   i. ターゲット選択（行動直前にリアルタイム判定）:
      - 生存者（HP>0）のみが候補
      - 通常攻撃: ランダム1体
      - 単体攻撃スキル: HP割合最大の敵
      - 回復スキル: HP割合最低の味方
      - 同条件のターゲットが複数: キャラID順で決定
      - 敵が全滅済みなら残りキャラの行動をスキップ→階クリア判定へ

   j. ダメージ計算:
      - 通常攻撃: ATK × (1 + random(-0.1, 0.1)) - DEF × 0.5
      - スキル攻撃: ATK × スキル倍率 - DEF × 0.5
      - クリティカル: DEF減算後に乗算 → (ATK×rand - DEF×0.5) × 1.5
        - クリティカル率は合算で上限100%、超過分は切り捨て（game_spec §2.2 キャップ参照）
      - 最低ダメージ保証:
        - 味方→敵: max(計算結果, 1) — 最低1ダメージ保証
        - 敵→味方: max(計算結果, 0) — 0ダメージ許容
      - パッシブ「被ダメ軽減」の適用
        - 実効軽減率は乗算合算後に上限80%（最終ダメージ倍率の下限0.2。game_spec §2.2 キャップ参照）
      - HP吸収装備の回復
      - パッシブ「反撃」の判定（被攻撃時）

   k. 範囲攻撃の場合: 全敵にダメージ × 0.7

   l. 敵撃破判定 → 報酬付与

3. 全キャラのCDカウンターを-1
4. バフ/デバフの残りターンを-1（0になったら解除）
5. 状態異常の残りターンを-1（0になったら解除）
6. 全滅判定（味方全員HP=0 → 強制撤退）
```

### 3.1.1 ステータス計算の適用順序

```python
def calc_final_stat(base, growth, lv, limit_break_pct, rebirth_pct, equip_val, passive_pct, buff_pct, debuff_pct, env_pct):
    raw = base + growth * (lv - 1)                          # ① 素ステータス
    enhanced = raw * (1 + limit_break_pct) * (1 + rebirth_pct)  # ② 限界突破・転生
    with_equip = enhanced + equip_val                        # ③ 装備（加算）
    with_passive = with_equip * (1 + passive_pct)            # ④ パッシブスキル（乗算）
    final = with_passive * (1 + buff_pct) * (1 + debuff_pct) * (1 + env_pct)  # ⑤ バフ/デバフ/環境
    return floor(final)
```

### 3.1.2 バフ/デバフ重複処理

- **同一スキル**（例: 「力の祝福」×2回）: 後発が上書き（効果値・持続ターンともに更新）
- **異なるスキル**（例: 「力の祝福」ATK+15% と「戦いの鼓舞」ATK+15%）: 共存し加算（ATK+30%）
- **加算上限**: なし（将来バランス調整が必要な場合にキャップを後付け可能）
- 持続ターンはスキルごとに個別管理（バフテーブルに `{skill_id, stat, value, remaining_turns, caster_id}` で保持）
- **バフ延長パッシブ**: `caster_id` が自キャラのバフのみ持続ターン+1（他キャラが付与したバフは延長しない）

### 3.1.3 挑発の確率計算

```python
def select_target_with_taunt(enemies_or_allies, taunters):
    """taunters: [{char, taunt_rate}, ...]"""
    total_taunt = sum(t.taunt_rate for t in taunters)

    if total_taunt >= 1.0:
        # 合算100%超え → 挑発者間で按分
        roll = random()
        cumulative = 0
        for t in taunters:
            cumulative += t.taunt_rate / total_taunt
            if roll < cumulative:
                return t.char
    else:
        # 合算100%以下 → 各挑発者 or ランダム
        roll = random()
        cumulative = 0
        for t in taunters:
            cumulative += t.taunt_rate
            if roll < cumulative:
                return t.char
        # 残り確率 → 通常ランダム
        return random.choice([a for a in enemies_or_allies if a.hp > 0])
```

- 挑発は範囲攻撃には無効（範囲攻撃は全体に当たるため）
- 挑発率はスキルレベルで変動: 段階2=50%、段階3=60%、段階4=70%、最大80%

### 3.1.4 ボスラッシュのウェーブ間処理

```
ウェーブ開始時:
1. スキルCDカウンターを全リセット（全スキルが即座に使用可能）
2. バフ/デバフをすべてクリア
3. 状態異常をすべてクリア
4. 5ウェーブごと（Wave5, 10, 15...）: パーティ全員のHPを10%回復
5. ポーションは通常通り使用可能（所持数消費）
```

### 3.2 エンカウント抽選と複数敵の処理

#### エンカウント抽選ロジック
- 各階の出現敵は重み付きプール（`floorEncounters`）からランダムに抽選
- Phase 1-2: 出現数は常に1体固定（プールからの抽選ロジックは全Phase共通）
- Phase 3+: 階層定義の `enemyCountMin` 〜 `enemyCountMax` 範囲で均等確率抽選
  - 例: `1-2体` → 各50%、`2-3体` → 各50%
- ボス階: 出現数1体固定、重み100%

#### 複数敵の処理
- 各階に1-3体の敵が出現（階層データの `floorEncounters` で定義）
- 敵も味方と同様にSPD順で行動に参加
- Phase 1-4: 敵は通常攻撃のみ（ランダムターゲット）
- Phase 5（ボスラッシュWave 11+）: 強化版ボスは敵スキルを使用（CD管理は味方スキルと同様）
- 階クリア条件: その階の全敵のHP=0

#### 敵スキル処理（Phase 5〜）
- 敵スキルの発動判定は味方スキルと同一フロー（CD判定 → 優先度順で1つ発動）
- 敵スキルの優先度: デバフ > 状態異常 > 攻撃スキル > 通常攻撃
- CD管理: 味方と同様にターン経過で減算、塔出発時CD=0
- 敵スキルの詳細は [master_data.md §9A](../data/master_data.md) を参照

### 3.3 ターゲット選択の実装

```python
def select_target(actor, action_type, allies, enemies):
    if action_type == "normal_attack":
        # ランダム1体
        return random.choice([e for e in enemies if e.hp > 0])
    elif action_type == "single_skill_attack":
        # HP割合が最も高い敵（各個撃破）
        alive = [e for e in enemies if e.hp > 0]
        return max(alive, key=lambda e: e.hp / e.maxHp)
    elif action_type == "aoe_skill":
        # 全体
        return [e for e in enemies if e.hp > 0]
    elif action_type == "heal":
        # HP割合が最も低い味方
        alive = [a for a in allies if a.hp > 0]
        return min(alive, key=lambda a: a.hp / a.maxHp)
```


---

> 変更履歴は [tech_spec.md](tech_spec.md) を参照。
