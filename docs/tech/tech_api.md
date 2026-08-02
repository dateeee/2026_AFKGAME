# AFK GAME — API設計

> [tech_spec.md](tech_spec.md) §5。呼び出しシーケンスは [api_sequence.md](../../diagrams/api_sequence.md)、認証は [tech_auth.md](tech_auth.md)。
> エラー形式・エラーコードは [tech_logging.md](tech_logging.md)、レート制限・認可は [tech_security.md](tech_security.md)。

## 5.0 共通仕様

| 項目 | 規約 |
|------|------|
| ベースパス | `/api`（バージョン番号なし）。破壊的変更が必要になった場合のみ `/api/v2/...` を併設し、旧版を一定期間並行維持する |
| パス命名 | 小文字ケバブケース（例: `/api/boss-rush/start`）。リソース名は単数形 |
| ボディのキー | **camelCase**（バックエンドは `CamelModel` で snake_case ↔ camelCase を自動変換） |
| 日時 | ISO 8601 の UTC（例: `2026-03-15T12:00:00Z`）。ローカル時刻は返さない |
| 数値 | すべて整数（ゴールドは64bit）。割合は 0〜1 の小数 |
| 未知フィールド | リクエストの未定義フィールドは 422 で拒否（`extra="forbid"`） |
| 認証 | **全エンドポイントで `Authorization: Bearer <access_token>` 必須**。例外は下表のみ |
| 認証不要な例外 | `/api/auth/guest`, `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/verify-email`, `/api/auth/google`, `/api/auth/password-reset/*`, `/health` |
| 一覧系の件数 | ページングは設けない（1プレイヤーのデータ量が上限で抑えられているため）。`/api/boss-rush/ranking` と `/api/abyss/ranking` のみ上位100件固定 |

**共通ヘッダ**

| ヘッダ | 方向 | 内容 |
|--------|------|------|
| `Authorization` | Req | `Bearer <access_token>` |
| `Content-Type` | Req | `application/json`（ボディを持つ場合） |
| `X-Request-ID` | Res | リクエスト単位のUUID。ログとの突合に使う（[tech_logging.md](tech_logging.md)） |
| `Retry-After` | Res | 429 応答時のみ。再試行可能になるまでの秒数 |

**HTTPステータスコードの使い分け**

| コード | 用途 | 例 |
|--------|------|-----|
| 200 | 正常（レスポンスボディあり） | 取得・更新・アクション成功 |
| 400 | 状態が不正で実行できない | 入塔中の塔選択、ロック中装備の売却 |
| 401 | 未認証・トークン不正/期限切れ | `AUTH_TOKEN_EXPIRED` |
| 403 | 認証済みだが権限・解放条件を満たさない | 未解放の塔を選択 |
| 404 | 対象が存在しない／**他ユーザーのリソース** | 存在秘匿のため 403 ではなく 404 |
| 422 | 型・範囲・必須のバリデーション違反 | `quantity` が範囲外 |
| 429 | レート制限超過 | `RATE_LIMIT_EXCEEDED`（[tech_security.md](tech_security.md) §11.6） |
| 500 | サーバー内部エラー | `INTERNAL_UNEXPECTED_ERROR` |
| 503 | 一時的に処理不能 | `BATTLE_TICK_BUSY`（tick処理のロック競合。[tech_tick.md](tech_tick.md) §3.1） |

- 201・204 は使わない（作成系も更新後の状態を 200 で返す方針に統一）
- エラーボディは全コード共通で `{"error": {"code", "message", "requestId"}}`（[tech_logging.md](tech_logging.md)）

## 運用（認証不要）
| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/health` | 死活監視。DB疎通を含む。正常 200 / DB異常 503（[tech_operations.md](tech_operations.md) §12.3） |

## 認証（Phase 2〜）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/auth/guest` | ゲストアカウント作成・JWT発行 |
| POST | `/api/auth/register` | メール+パスワードでユーザー登録。確認メール送信 |
| POST | `/api/auth/login` | メール+パスワードでログイン。JWT発行 |
| POST | `/api/auth/refresh` | リフレッシュトークンで新アクセストークン取得（ローテーションあり） |
| POST | `/api/auth/logout` | リフレッシュトークン無効化（ログアウト） |
| GET | `/api/auth/verify-email?token=xxx` | メール確認トークンの検証・アカウント有効化 |
| POST | `/api/auth/google` | Google認可コードでログイン/登録 |
| POST | `/api/auth/link-account` | ゲストアカウントをメール/Googleに紐づけ（ゲスト→本登録） |
| POST | `/api/auth/password-reset/request` | パスワードリセットメール送信 |
| POST | `/api/auth/password-reset/confirm` | パスワードリセット実行 |

## ゲーム状態
| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/game/state` | ゲーム状態の取得（起動時・復帰時に呼ぶ） |
| PUT | `/api/game/settings` | プレイヤー設定の更新（ポーション閾値・戦闘ログ表示数・通知設定・自動売却レアリティ） |

## 戦闘（tick）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/battle/tick` | 現在時刻までの未処理tickをまとめて計算しDB反映。戦闘ログ・更新後ステータスを返却。オンライン中のポーリングでもオフライン復帰時でも同じエンドポイントを使用 |

## 操作系（プレイヤーのアクション）
| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/tower/list` | 全塔の一覧を取得（名前・階数・解放条件・解放/クリア状態・最高到達階・`targetFloorCap`）（Phase 2〜） |
| POST | `/api/tower/select` | 塔・目標階の選択（`towerId`, `targetFloor`, `mode`: `auto_repeat` \| `stop_on_clear`）。未解放の塔は403、入塔中は400、`targetFloor` が範囲外は400 |
| POST | `/api/tower/retire` | 塔からリタイア（獲得済み報酬は保持・ペナルティなし） |
| PUT | `/api/tower/mode` | 進行モードの切り替え（進行中でも変更可） |
| PUT | `/api/tower/retreat-conditions` | 撤退条件の更新（`hpThreshold`: 0〜1） |
| GET | `/api/shop/lineup` | ショップの現在の品揃えを取得。Phase 1: 常設のみ。Phase 2〜: 常設＋日替わり5枠＋次回更新時刻（[tech_shop.md §6](tech_shop.md)） |
| POST | `/api/shop/buy` | ショップでアイテム購入。常設商品: `itemId` + `quantity`（ポーションID等は常設扱い、在庫無制限）。Phase 2〜: 日替わり商品は `dailySlotIndex`（枠番号指定、各1個限り）を追加。両方の指定・どちらも未指定は 422（[tech_shop.md §4](tech_shop.md)） |
| GET | `/api/equipment/list` | プレイヤーの全装備一覧を取得（Phase 2〜） |
| POST | `/api/equipment/equip` | 装備の変更（Phase 2〜） |
| POST | `/api/equipment/sell` | 装備売却（`equipmentIds`）。装備を消費してゴールドを獲得（売却価格 = 5 × レアリティ倍率 × 装備レベル）（Phase 2〜） |
| POST | `/api/equipment/lock` | 装備のロック/アンロック切替（`equipmentId`）（Phase 2〜） |
| POST | `/api/item/sell` | アイテム売却（`itemId`, `quantity`）。換金アイテム・素材を売却してゴールドを獲得（Phase 4〜） |

> **`targetFloor` の検証範囲**: `1 <= targetFloor <= min(その塔の TowerClearRecord.highestFloor + 1, totalFloors)`。塔ごとに個別判定し、範囲外は 400。深淵の塔（`abyss_tower`）は総階数を持たないため `highestFloor + 1` のみで判定する。この上限は `/api/tower/list` が塔ごとに `targetFloorCap` として返すため、クライアントは式を再実装しない。
> **上限追従**: 目標階が上限と一致している状態で新しい階をクリアした場合、サーバーが tick 処理内で `targetFloor` を +1 する（クライアントからの再設定は不要）。目標階が上限未満なら追従しない。
> 仕様は [systems/battle.md](../design/systems/battle.md) 「目標階設定」・[systems/endgame.md](../design/systems/endgame.md) §2.14 を参照。

## パーティ・スキル（Phase 3〜）
| メソッド | パス | 説明 |
|---------|------|------|
| PUT | `/api/party/edit` | パーティ編成の変更（`memberIds`: キャラID配列、最大4人） |
| POST | `/api/skill/learn` | スキル習得（`characterId`, `skillId`）。SP消費。前提スキル未習得時はエラー |
| PUT | `/api/skill/set-active` | アクティブスキルのセット変更（`characterId`, `activeSlots`: スキルID配列、最大2） |
| POST | `/api/skill/reset` | スキル全リセット（`characterId`）。ゴールド消費（LV×50G）。全SP返却 |
| POST | `/api/character/limit-break` | 限界突破（`characterId`, `materialCharacterId`）。素材キャラを消費 |

## 施設・拠点（Phase 4〜）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/base/build` | 施設を建設（`facilityId`）。ゴールド+素材を消費してLV0→LV1 |
| POST | `/api/base/upgrade` | 施設をレベルアップ（`facilityId`）。ゴールド+素材を消費 |
| POST | `/api/base/scout` | 酒場でスカウト実行。ゴールドを消費してキャラ1体をランダム獲得 |

## 鍛冶屋（Phase 4〜）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/forge/enhance` | 装備強化（`equipmentId`）。強化石+ゴールドを消費して+1 |
| POST | `/api/forge/craft` | 装備製作（`rank`: 1-5）。素材+ゴールドを消費してランダム装備を生成 |
| POST | `/api/forge/disassemble` | 装備分解（`equipmentId`）。装備を消費して素材を獲得 |

## ボスラッシュ（Phase 5〜）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/boss-rush/start` | ボスラッシュ開始。通常塔探索を停止してボスラッシュモードに移行 |
| POST | `/api/boss-rush/retire` | ボスラッシュリタイア。現在の戦闘完了後に終了し、累積報酬を確定取得 |
| GET | `/api/boss-rush/ranking` | サーバーランキング取得（上位100件）。認証必須 |

## 深淵の塔（Phase 5〜）
| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/api/abyss/ranking` | 最深到達階のサーバーランキング取得（上位100件）。認証必須 |

> 深淵の塔への入塔・目標階設定・リタイアは通常の塔と同じ `/api/tower/*` を `towerId: "abyss_tower"` で使用する（専用エンドポイントは設けない）。`/api/tower/list` では `totalFloors` を `null` で返し、階数無限を表す。

## イベントダンジョン（Phase 5〜）

機能仕様は [systems/endgame.md §2.13](../design/systems/endgame.md)（常設3種 × 固定難易度3段階）。進行は通常の塔と同じ階層制のため、**`/api/tower/*` に難易度パラメータを足して再利用する方針**とする。エンドポイントと、難易度別の到達記録を `towersCleared`（[tech_data.md](tech_data.md)）へ保持するキー体系の確定、および本節への追記は Phase 5 の基本設計で行う（キー体系の未確定は [open_specs.md](../open_specs.md) で管理）。

## お知らせ（Phase 3〜）

要件は [operation_requirements.md](../design/operation_requirements.md) §3.1（マスターデータ配信・既読はクライアント保持）。エンドポイントの定義は Phase 3 の基本設計で行う（既読状態のクライアント保持先の未確定は [open_specs.md](../open_specs.md) で管理）。

## 転生（Phase 5〜）
| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/api/prestige` | 転生実行（`characterId`）。LV9999チェック後、LV/EXP/SPリセット・転生ポイントを付与（付与量は [master/endgame.md §16.1](../data/master/endgame.md)） |
| PUT | `/api/prestige/invest` | 転生ポイント投資（`characterId`, `stat`, `points`）。指定のボーナスにポイントを割り振る |
| POST | `/api/prestige/reset` | 転生ボーナス全リセット（`characterId`）。ゴールド消費で全ポイント返還 |

> **設計方針**: `/api/battle/tick` がゲーム進行の中心。オンライン中のポーリングでもオフライン復帰時でも同じAPIを叩く。tickの中で戦闘計算・報酬付与・DB保存をすべて行うため、別途 save や offline/claim のエンドポイントは不要。塔の階層進行・撤退判定もtick処理内で行う。
