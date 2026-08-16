# AFK GAME — 技術仕様書

> プロジェクト概要は [README.md](../../README.md)、ゲーム仕様は [game_spec.md](../design/game_spec.md)、マスターデータは [master_data.md](../data/master_data.md) を参照。
> 本書は索引。各章は工程別ディレクトリに分割している —
> `basic/`（基本設計）・`nonfunctional/`（非機能）・`detail/`（詳細設計）。

---

## 1. 章構成

| 章 | 内容 | ファイル |
|----|------|---------|
| 0. DB設計 | 物理テーブル名・列の型・NULL／既定・キー・一意制約・インデックス・命名規約（**DBスキーマの正**） | [tech_db.md](basic/tech_db.md) |
| 1. データ設計 | ゲーム状態JSON・敵／塔／環境効果・戦闘ログ・施設／装備強化のデータ構造 | [tech_data.md](basic/tech_data.md)（索引兼本文） → [tech_data/game_state.md](basic/tech_data/game_state.md)（§1.1 の JSON 例） |
| 2. ディレクトリ構成<br>3. フロントエンド構成 | リポジトリ構成と Vue 3 SPA の内部構成・レスポンシブ設計 | [tech_structure.md](basic/tech_structure.md) |
| 4. バックエンド構成 | Terasoluna の技術スタック・モジュール構成・設定値 | [tech_backend.md](basic/tech_backend.md) |
| 5. API設計 | 全エンドポイントの索引（core / auth / gameplay / character / base / endgame の6分冊。`api_sequence/` と1:1） | [tech_api.md](basic/tech_api.md) |
| 5.0 API共通仕様 | 規約（パス・ボディ・日時・認証）・共通ヘッダ・ステータスコードの使い分け | [tech_api/common.md](basic/tech_api/common.md) |
| 6. アーキテクチャ方針<br>7. ゲームループ | サーバー権威・ゲスト永続化・エラーハンドリング・ハイブリッドtick制 | [tech_architecture.md](basic/tech_architecture.md) |
| 6. ログ設計 | ログレベル・フォーマット・ロガー名体系・ログ項目・マスク規則・失敗理由（`reason`） | [tech_logging.md](basic/tech_logging.md) |
| 9. エラーハンドリング | 統一エラーレスポンス形式・エラーコード体系・`AUTH_` コード一覧・グローバル例外ハンドラ | [tech_error_handling.md](basic/tech_error_handling.md) |
| 10. 性能・容量設計 | 負荷見積り・平滑化・容量見積り・劣化時の対処順序・検証方法 | [tech_performance.md](nonfunctional/tech_performance.md) |
| 11. セキュリティ設計 | 通信／CORS・入力検証・認可・レート制限・秘密情報管理 | [tech_security.md](nonfunctional/tech_security.md) |
| 12.1〜12.3 運用設計（環境） | 環境区分・環境変数・ヘルスチェック／監視 | [tech_operations.md](nonfunctional/tech_operations.md) |
| 12.4〜12.7 運用設計（手順） | マイグレーション・バックアップ・定期ジョブ・リリース／障害対応 | [tech_maintenance.md](nonfunctional/tech_maintenance.md) |

> §10〜§12 は **実現方式**を扱う。目標値・満たすべき要件は要件定義工程の成果物（[non_functional_requirements.md](../design/requirements/non_functional_requirements.md)・[operation_requirements.md](../design/requirements/operation_requirements.md)）が正であり、本章群では再掲しない。

### 関連する詳細仕様（詳細設計工程の成果物）

| 内容 | ファイル |
|------|---------|
| 戦闘ログ保持ポリシー・戦闘処理フロー | [tech_battle.md](detail/tech_battle.md) |
| オフラインまとめ計算・簡略計算アルゴリズム | [tech_offline.md](detail/tech_offline.md) |
| スキル・状態異常・環境効果の戦闘内処理（一意化・分岐一覧） | [tech_skill.md](detail/tech_skill.md) |
| パーティ・スキル操作（編成・キャラ獲得・習得/セット/リセット） | [tech_party.md](detail/tech_party.md) |
| tick進行制御（tick数算出・排他・トランザクション） | [tech_tick.md](detail/tech_tick.md) |
| 日替わりショップ（品揃え生成・24時間更新・購入） | [tech_shop.md](detail/tech_shop.md) |
| 拠点・施設（建設・レベルアップ・施設効果の解決） | [tech_base.md](detail/tech_base.md) |
| 酒場スカウト（排出設定の解決・ガチャ抽選・重複判定） | [tech_scout.md](detail/tech_scout.md) |
| 限界突破（素材の同一性判定・突破回数の上限・素材の消費） | [tech_limitbreak.md](detail/tech_limitbreak.md) |
| 鍛冶屋（強化・製作・分解の索引） | [tech_forge.md](detail/tech_forge.md) → [enhance](detail/tech_forge/enhance.md) / [craft](detail/tech_forge/craft.md) / [disassemble](detail/tech_forge/disassemble.md) |
| 乱数設計（RNG注入・消費順序・再現性） | [tech_rng.md](detail/tech_rng.md) |
| 数値・丸め規約（丸め方向・キャップ・適用順序） | [tech_numeric.md](detail/tech_numeric.md) |
| 進行状態と操作可否（状態機械・不変条件・探索セッション） | [tech_state.md](detail/tech_state.md) |
| フロントエンドのtick制御（ポーリング・多重タブ・ストア反映） | [tech_polling.md](detail/tech_polling.md) |
| 認証システム（JWT・ゲスト・Google OAuth） | [tech_auth.md](detail/tech_auth.md) → [プレイヤー初期化](detail/tech_auth/init.md)・[登録・ログイン・ログアウト](detail/tech_auth/account.md) |
| デザインシステム（トークン・UIプリミティブ・アプリシェル） | [tech_design_system.md](detail/tech_design_system.md) |
| ボスラッシュ（ウェーブ進行・HP回復・マイルストーン付与・全滅判定・ランキング更新） | [tech_bossrush.md](detail/tech_bossrush.md) → [開始](detail/tech_bossrush/start.md) / [ウェーブ進行](detail/tech_bossrush/wave.md) / [オフライン簡略計算](detail/tech_bossrush/offline.md) / [終了・ランキング](detail/tech_bossrush/control.md) |
| 転生（LV9999判定・リセット範囲の適用・ポイント投資の上限検証） | `tech_prestige.md`（Phase 5 で新設予定・**未作成**） |

---

## 8. 今後の検討事項

- [x] デプロイ先の選定 → **AWS**（EC2 1台 + S3/CloudFront）。[tech_operations.md](nonfunctional/tech_operations.md) §12.1 に反映済み
- [x] ブラウザ対応範囲 → [tech_structure.md](basic/tech_structure.md) §3.2 レスポンシブ設計に反映済み
- [x] アクセシビリティ対応 → [tech_architecture.md](basic/tech_architecture.md) アクセシビリティ対応方針に反映済み
- [x] パフォーマンス目標（ログ保持件数の上限など）→ 目標値は [non_functional_requirements.md](../design/requirements/non_functional_requirements.md) §1〜§2、実現方式は [tech_performance.md](nonfunctional/tech_performance.md) に反映済み
