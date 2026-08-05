# APIシーケンス図

> API定義: [tech_api.md](../docs/tech/basic/tech_api.md) / ゲームループ: [tech_architecture.md](../docs/tech/basic/tech_architecture.md)
> 本書は索引。各フローは [api_sequence/](api_sequence/) 配下の個別ファイルに分割している（節番号は維持）。

## 索引

| 節 | フロー | ファイル |
|----|-------|---------|
| 1. 初回アクセス（ゲスト作成）<br>2. 再訪問（オフライン復帰）<br>3. オンライン中（ポーリングループ）<br>3.5. 設定変更<br>3.7. お知らせ確認（Phase 3〜）<br>13. 通信エラー時（リトライ） | 基本ループ | [api_sequence/core.md](api_sequence/core.md) |
| 14. 認証フロー概要 | 認証（Phase 2〜） | [api_sequence/auth.md](api_sequence/auth.md) |
| 4. 塔選択<br>5. ショップ購入<br>6. 装備変更 | 塔・ショップ・装備 | [api_sequence/gameplay.md](api_sequence/gameplay.md) |
| 6.5. パーティ編成<br>7. スキル習得・リセット<br>8. 限界突破 | パーティ・スキル・限界突破（Phase 3） | [api_sequence/character.md](api_sequence/character.md) |
| 9. 施設建設・レベルアップ<br>10. 鍛冶屋操作 | 拠点（Phase 4） | [api_sequence/base.md](api_sequence/base.md) |
| 11. ボスラッシュ<br>11.5. イベントダンジョン<br>11.7. 深淵の塔ランキング<br>12. 転生 | エンドコンテンツ（Phase 5） | [api_sequence/endgame.md](api_sequence/endgame.md) |
