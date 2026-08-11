# AFK GAME — API設計: 運用・ゲーム状態・戦闘（tick）・お知らせ

> 親: [tech_api.md](../tech_api.md) §5（索引）。全エンドポイントに適用する共通仕様は [common.md](common.md) §5.0。
> 基本ループのAPI。呼び出し順は [api_sequence/core.md](../../../diagrams/api_sequence/core.md)。

---

## 運用（認証不要）
| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/health` | 死活監視。DB疎通を含む。正常 200 / DB異常 503（[tech_operations.md](../../nonfunctional/tech_operations.md) §12.3） |

## ゲーム状態
| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/game/state` | ゲーム状態の取得（起動時・復帰時に呼ぶ） |
| PUT | `/api/game/settings` | プレイヤー設定の更新（ポーション閾値・戦闘ログ表示数・通知設定・自動売却レアリティ） |

## 戦闘（tick）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/battle/tick` | 現在時刻までの未処理tickをまとめて計算しDB反映。戦闘ログ・更新後ステータスを返却。オンライン中のポーリングでもオフライン復帰時でも同じエンドポイントを使用 |

## お知らせ（Phase 3〜）
| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/notice/list` | お知らせ一覧の取得。`notices`: `noticeId`・`title`・`body`・`publishedAt` の配列を `publishedAt` 降順（新しい順）で返す。本文はマスターデータ配信（DBテーブルなし・更新はデプロイ）。掲示件数はマスター側の上限で抑えるためページングなし |

> 要件は [operation_requirements.md](../../../design/requirements/operation_requirements.md) §3.1（既読はクライアントの localStorage 保持。保持先の正は同節）。サーバーは既読状態を持たないため、エンドポイントは一覧取得のみ。未読件数はクライアントが一覧と既読 `noticeId` の突合で算出し、一覧を開いた時点で表示中の全件を既読として保存する。既読キーが存在しない場合の初期化規則は [master_data.md §17.3](../../../data/master_data.md) が正。
> 取得は起動時（`GET /api/game/state` 後）の1回のみ（内容の更新はデプロイでしか起きないため。フローは [api_sequence/core.md §3.7](../../../diagrams/api_sequence/core.md)）。マスターの項目定義・掲示件数の上限（20件）は `master_data.md` §17 が正。

