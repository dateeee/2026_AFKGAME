# 詳細設計 — プロジェクト固有プロファイル

> 一般手順は [.claude/skills/detail-design/SKILL.md](../skills/detail-design/SKILL.md) を参照。本書は AFK GAME 固有の値のみを持つ。
> 共通の値は [profile.md](profile.md)。

## 1. 成果物

### 処理仕様

| 成果物 | パス | 内容 |
|-------|------|------|
| 戦闘処理 | `docs/tech/detail/tech_battle.md` | ダメージ計算・ターン進行 |
| スキル・状態異常・環境効果 | `docs/tech/detail/tech_skill.md` | 発動判定・効果適用の一意化と分岐一覧 |
| パーティ・スキル操作 | `docs/tech/detail/tech_party.md` | 編成・キャラ獲得・習得/セット/リセット |
| オフライン計算 | `docs/tech/detail/tech_offline.md` | 復帰時の一括計算 |
| tick進行制御 | `docs/tech/detail/tech_tick.md` | 60秒tickの進行判定 |
| フロントtick制御 | `docs/tech/detail/tech_polling.md` | ポーリング間隔・停止条件 |
| ショップ処理 | `docs/tech/detail/tech_shop.md` | 日替わりショップの品揃え抽選・購入 |
| 拠点・施設 | `docs/tech/detail/tech_base.md` | 建設・レベルアップ・施設効果の解決 |
| 酒場スカウト | `docs/tech/detail/tech_scout.md` | 排出設定の解決・ガチャ抽選・重複判定 |
| 鍛冶屋 | `docs/tech/detail/tech_forge.md`（索引）+ `tech_forge_{enhance,craft,disassemble}.md` | 強化コスト・製作の生成規則・分解・所持枠 |

### 横断規約

| 成果物 | パス | 内容 |
|-------|------|------|
| 乱数 | `docs/tech/detail/tech_rng.md` | シード管理・抽選方式 |
| 数値・丸め | `docs/tech/detail/tech_numeric.md` | 丸め方向・桁・オーバーフロー |
| 進行状態 | `docs/tech/detail/tech_state.md` | 状態遷移と操作可否 |
| デザインシステム | `docs/tech/detail/tech_design_system.md` | 画面・コンポーネントの共通規約 |

### 数値（マスターデータ）

| 成果物 | パス |
|-------|------|
| 索引 | `docs/data/master_data.md` |
| 個別 | `docs/data/master/` — character / item / equipment / base / endgame |
| 塔別 | `docs/data/towers/`（`TOWERS_OVERVIEW.md` + `NNN_*.md`） |
| スキル別 | `docs/data/skills/`（`SKILLS_OVERVIEW.md` + `NNN_*.md`） |

## 2. 参照先（読む順）

| 順 | 参照先 | 読む範囲 |
|----|--------|---------|
| 1 | `docs/design/systems/<対象>.md` | 対象機能のセクションのみ |
| 2 | `docs/tech/basic/tech_api.md`・`tech_data.md` | 対象エンドポイント・テーブルの行のみ |
| 3 | `docs/tech/detail/tech_rng.md`・`tech_numeric.md`・`tech_state.md` | 乱数・丸め・状態を扱う場合のみ |
| 4 | `docs/data/master/` | 対象の数値定義のみ |
| 5 | `docs/diagrams/battle_flow/`・`api_sequence/` | 対象処理の図のみ |

## 3. 固有の観点

| # | 観点 | 判定基準 |
|---|------|---------|
| 1 | 一意な実装可能性 | 計算式・分岐条件が**仕様書だけで一意に実装できる**か。「適宜」「おおよそ」等の曖昧語がないか |
| 2 | 丸めの明示 | 除算・乗算の結果に対する丸め方向（切り捨て/四捨五入）と桁が `tech_numeric.md` に沿って明記されているか |
| 3 | 乱数の再現性 | 乱数を使う処理でシードの決まり方が `tech_rng.md` に沿って定義されているか（テストで固定できるか） |
| 4 | 境界値 | 上限・下限（HP0、最大階、所持金不足、レベル上限）の挙動が定義されているか |
| 5 | 仮置きの明示 | 未確定の数値に「仮置き」と明記されているか。確定待ちは `docs/backlog/balance_backlog.md` に登録されているか |
| 6 | データ駆動 | 数値が仕様書側にあり、実装へのハードコードを要求していないか |

## 4. 分岐一覧（単体テスト観点）— 本工程の必須成果物

各処理仕様に**分岐一覧**を記載する。これが `test-list` 工程のテスト、および `unit-test` 工程のC1網羅の**唯一の導出元**になる。

### 記載形式（標準形式）

```markdown
### 分岐一覧（単体テスト観点）

| # | 分岐点 | 条件 | 期待する振る舞い |
|---|-------|------|----------------|
| 1 | HP判定 | HP > 0 | 戦闘を継続する |
| 2 | HP判定 | HP <= 0 | 敗北として tick を終了する |
| 3 | ポーション | HP割合 <= しきい値 かつ 所持あり | ポーションを消費して回復する |
| 4 | ポーション | HP割合 <= しきい値 かつ 所持なし | 回復せず継続する |
```

4列（`# | 分岐点 | 条件 | 期待する振る舞い`）が標準形式。`python scripts/check_branch_list.py` がこの形式を機械検証する。
旧形式（3列）のセクションが残っているファイルは、**改稿するタイミングで標準形式へ移行**する（**全ファイルの移行が完了したら本注記を削除する**。残件は引き継ぎファイルの候補キューで追跡する）。

### 記載ルール

一般ルール（真偽の両方・ループの0/1/2周以上・例外経路・仕様上の意味で書く・1行1観点）は
[detail-design/SKILL.md](../skills/detail-design/SKILL.md) §4「分岐一覧を作る」に従う。以下は AFK GAME の追加ルール。

| # | ルール |
|---|-------|
| 1 | 製造中に一覧に無い分岐を発見したら、**詳細設計へ追記してからテストを追加**する（実装を先に直さない） |
| 2 | 同一条件の真・偽の行には**同じ分岐点名**を使う（`check_branch_list.py` が対として検証する） |
| 3 | 記載後に `python scripts/check_branch_list.py` を実行し ERROR を解消する。WARN は残さない（下記の確認と注記で0件にする） |
| 4 | 「1行のみ」「ループに0周・1周・2周がない」の WARN は、片側欠落でないことを確認したうえで表の直後に注記を置いて抑止する。書式: `> WARN許容 #21・#22: <理由>`（`#` 番号はコロンの前に列挙する。理由は必須） |

本プロジェクトでの当てはめ:

| 一般ルール | AFK GAME での具体 |
|-----------|-----------------|
| 例外経路 | マスターデータの**未知ID**、DB未登録レコード（塔記録なし・装備なし）、**認証失敗** |
| 仕様上の意味で書く | 「HPが残っている / 全滅した」「所持金が足りる / 足りない」（`if x > 0` と書かない） |

## 5. 完了基準

一般スキルの完了基準に加え、以下を満たすこと。

- 対象Phase機能の数値・計算式・分岐条件が仕様書から一意に実装できる（数値は仮置き可、ただし「仮置き」と明記）
- 各処理仕様に**分岐一覧**（標準形式）が記載されている
- `python scripts/check_doc_size.py`・`python scripts/check_docs.py`・`python scripts/check_branch_list.py` がすべて exit 0

## 6. 次工程

| 次にやること | 手段 |
|------------|------|
| 仕様確定ゲート | `doc-review` スキル |
| テストリスト作成へ | `test-list` スキル（分岐一覧を失敗するテストへ落とす） |
