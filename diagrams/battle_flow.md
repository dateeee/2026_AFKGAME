# 戦闘ターン処理フロー図

> 戦闘仕様: [systems/battle.md](../docs/design/systems/battle.md) / 処理詳細: [tech_battle.md](../docs/tech/detail/tech_battle.md)
> 本書は索引。各フローは [battle_flow/](battle_flow/) 配下の個別ファイルに分割している。

## 索引

| フロー | 内容 | ファイル |
|-------|------|---------|
| 塔探索の全体フロー<br>tick処理フロー | 塔選択から撤退・クリアまで、1 tick（60秒）の処理単位 | [battle_flow/overview.md](battle_flow/overview.md) |
| 1ターンの処理フロー | 行動順決定・スキル判定・ダメージ計算・状態異常処理 | [battle_flow/turn.md](battle_flow/turn.md) |
| オフライン計算フロー | 復帰時のまとめ計算（詳細計算／簡略計算の切替） | [battle_flow/offline.md](battle_flow/offline.md) |
| ボスラッシュ ウェーブ処理フロー | ウェーブ進行・ウェーブ間処理（Phase 5〜） | [battle_flow/bossrush.md](battle_flow/bossrush.md) |
