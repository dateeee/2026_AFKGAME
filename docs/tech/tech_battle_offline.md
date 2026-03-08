# AFK GAME — 戦闘ログ・オフライン計算仕様

> 技術仕様の全体は [tech_spec.md](tech_spec.md)、ゲーム仕様は [game_spec.md](game_spec.md) を参照。

---

## 1. 戦闘ログ保持ポリシー

- DBに保存する戦闘ログは **直近100件** を上限とする
- 上限を超えた古いログはtick処理時に自動削除
- フロント側に返却するログも **直近50件** まで（ポーリング1回あたり）
- オフライン復帰時のサマリーは集計結果のみ返却し、個別ログは保持しない

## 2. オフラインまとめ計算のパフォーマンス対策

24時間放置 = 最大 1,440 tick（60秒間隔）。全tickをシミュレーションすると
API応答が遅くなるため、未処理tick数に応じて計算方式を切り替える。

| 未処理tick数 | 計算方式 | 内容 |
|-------------|---------|------|
| 1〜100 tick | 正規シミュレーション | 1tickずつ戦闘処理を実行。個別ログも生成 |
| 101〜 tick | 簡略計算 | 統計的に勝率・平均報酬を算出し、tick数を掛けて一括計算。個別ログは生成せずサマリーのみ返却 |

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
      - 最低ダメージ保証:
        - 味方→敵: max(計算結果, 1) — 最低1ダメージ保証
        - 敵→味方: max(計算結果, 0) — 0ダメージ許容
      - パッシブ「被ダメ軽減」の適用
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

### 3.2 複数敵の処理

- 各階に1-3体の敵が出現（階層データの `floorEncounters` で定義）
- 敵も味方と同様にSPD順で行動に参加
- 敵は通常攻撃のみ（ランダムターゲット）
- 階クリア条件: その階の全敵のHP=0

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

## 4. オフライン簡略計算の詳細アルゴリズム

```
入力: pending_ticks, party_stats[], target_floor, potion_count, skills[]
出力: 獲得報酬サマリー, 更新後ステータス

1. remaining_ticks = pending_ticks
2. total_rewards = { gold: 0, exp: 0, items: [] }

3. WHILE remaining_ticks > 0:
   a. 現在のパーティステータスで目標階までの1周回を分析:
      - 各階の敵データ（1-3体）から期待ダメージ（与/被）を確定的に算出
      - パーティ全員の攻撃力合計（スキル・パッシブ効果込み）で期待与ダメージを計算
      - 敵全体の攻撃力合計で期待被ダメージを計算（ランダムターゲットのため均等分散）
      - パーティ全員の合計HPが期待被ダメージを下回る場合 → 全滅扱い → BREAK
      - ポーション自動使用を考慮（HP50%以下で使用、所持数を減算）
      - 回復スキル・リジェネの効果を期待値として加算
      - 1周回に必要なtick数と期待報酬を算出

   b. 次のレベルアップまでの必要EXPを算出（パーティメンバーごとに計算、最も早いLVアップを基準）
   c. レベルアップまでに必要な周回数を計算
   d. 消化可能tick数 = min(remaining_ticks, レベルアップまでのtick数)

   e. 消化可能tick数分の報酬を一括加算
   f. remaining_ticks -= 消化可能tick数

   g. レベルアップ発生時:
      - ステータス再計算（タイプごとの成長率に応じて上昇）
      - SP+1（簡略計算ではスキル自動習得しない。復帰後に手動で振る）
      - 期待報酬を再計算
      - ※目標階は変更しない（固定）

4. 塔外待機中のtickがある場合:
   - パーティ全員にHP自然回復を適用: HP += (maxHP * 0.02 + DEF * 0.5) × 待機tick数
   - HPはmaxHPを上限とする

5. RETURN total_rewards, updated_stats[]
```

**全滅判定の基準**: 確定的に判定する。1周回で受ける期待被ダメージ合計（ポーション回復・回復スキル込み）がパーティ全体のHPを超える場合、全滅として扱う。乱数要素は考慮しない（簡略計算のため）。

**目標階の固定**: オフライン中は目標階を変更しない。レベルアップにより上位の階を攻略可能になっても、プレイヤーが設定した目標階で周回を継続する。

**スキルポイントの扱い**: 簡略計算中にレベルアップで獲得したSPは蓄積されるが、スキルの自動習得は行わない。プレイヤーが復帰後に手動でスキルを振る。

---

> 変更履歴は [tech_spec.md](tech_spec.md) を参照。
