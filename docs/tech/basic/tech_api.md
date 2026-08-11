# AFK GAME — API設計

> [tech_spec.md](../tech_spec.md) §5。呼び出しシーケンスは [api_sequence.md](../../diagrams/api_sequence.md)、認証は [tech_auth.md](../detail/tech_auth.md)。
> エラー形式・エラーコードは [tech_error_handling.md](tech_error_handling.md)、レート制限・認可は [tech_security.md](../nonfunctional/tech_security.md)。

全エンドポイントは子ファイルが正。パス接頭辞で担当が決まる（分割は [api_sequence/](../../diagrams/api_sequence.md) と1:1）。

| パス接頭辞 | 子ファイル | 対象 |
|-----------|-----------|------|
| `/health` / `/api/game/*` / `/api/battle/*` / `/api/notice/*` | [tech_api/core.md](tech_api/core.md) | 運用 / ゲーム状態 / 戦闘（tick）/ お知らせ |
| `/api/auth/*` | [tech_api/auth.md](tech_api/auth.md) | 認証（Phase 2〜） |
| `/api/tower/*` / `/api/shop/*` / `/api/equipment/*` / `/api/item/*` | [tech_api/gameplay.md](tech_api/gameplay.md) | 操作系（プレイヤーのアクション） |
| `/api/party/*` / `/api/skill/*` / `/api/character/*` | [tech_api/character.md](tech_api/character.md) | パーティ・スキル・限界突破（Phase 3〜4） |
| `/api/base/*` / `/api/forge/*` | [tech_api/base.md](tech_api/base.md) | 施設・拠点 / 鍛冶屋（Phase 4） |
| `/api/boss-rush/*` / `/api/abyss/*` / `/api/prestige/*` | [tech_api/endgame.md](tech_api/endgame.md) | ボスラッシュ / 深淵の塔 / イベントダンジョン / 転生（Phase 5） |

## 5.0 共通仕様

規約（パス・ボディ・日時・認証・一覧件数）・共通ヘッダ・HTTPステータスコードの使い分けは [tech_api/common.md](tech_api/common.md) に分割した。全エンドポイントに適用する。

> **設計方針**: `/api/battle/tick` がゲーム進行の中心。オンライン中のポーリングでもオフライン復帰時でも同じAPIを叩く。tickの中で戦闘計算・報酬付与・DB保存をすべて行うため、別途 save や offline/claim のエンドポイントは不要。塔の階層進行・撤退判定もtick処理内で行う。
