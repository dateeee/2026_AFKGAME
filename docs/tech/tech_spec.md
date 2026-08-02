# AFK GAME — 技術仕様書

> プロジェクト概要は [README.md](../../README.md)、ゲーム仕様は [game_spec.md](../design/game_spec.md)、マスターデータは [master_data.md](../data/master_data.md) を参照。
> 本書は索引。各章はレイヤー別ファイルに分割している。

---

## 1. 章構成

| 章 | 内容 | ファイル |
|----|------|---------|
| 1. データ設計 | ゲーム状態JSON・敵／塔／環境効果・戦闘ログ・施設／装備強化のデータ構造 | [tech_data.md](tech_data.md) |
| 2. ディレクトリ構成<br>3. フロントエンド構成<br>4. バックエンド構成 | リポジトリ構成、Vue 3 / FastAPI の内部構成・設定値 | [tech_structure.md](tech_structure.md) |
| 5. API設計 | 全エンドポイント一覧（認証・ゲーム状態・tick・操作系・Phase 3〜5） | [tech_api.md](tech_api.md) |
| 6. アーキテクチャ方針<br>7. ゲームループ | サーバー権威・ゲスト永続化・エラーハンドリング・ハイブリッドtick制 | [tech_architecture.md](tech_architecture.md) |
| 6. ログ設計 | ログレベル・フォーマット・マスク規則・統一エラーレスポンス | [tech_logging.md](tech_logging.md) |
| 10. 性能・容量設計 | 負荷見積り・平滑化・容量見積り・劣化時の対処順序・検証方法 | [tech_performance.md](tech_performance.md) |
| 11. セキュリティ設計 | 通信／CORS・入力検証・認可・レート制限・秘密情報管理 | [tech_security.md](tech_security.md) |
| 12. 運用設計 | 環境区分・環境変数・ヘルスチェック／監視・マイグレーション・バックアップ・定期ジョブ | [tech_operations.md](tech_operations.md) |

> §10〜§12 は **実現方式**を扱う。目標値・満たすべき要件は要件定義工程の成果物（[non_functional_requirements.md](../design/non_functional_requirements.md)・[operation_requirements.md](../design/operation_requirements.md)）が正であり、本章群では再掲しない。

### 関連する詳細仕様（詳細設計工程の成果物）

| 内容 | ファイル |
|------|---------|
| 戦闘ログ保持ポリシー・戦闘処理フロー | [tech_battle.md](tech_battle.md) |
| オフラインまとめ計算・簡略計算アルゴリズム | [tech_offline.md](tech_offline.md) |
| tick進行制御（tick数算出・排他・トランザクション） | [tech_tick.md](tech_tick.md) |
| 日替わりショップ（品揃え生成・24時間更新・購入） | [tech_shop.md](tech_shop.md) |
| 乱数設計（RNG注入・消費順序・再現性） | [tech_rng.md](tech_rng.md) |
| 数値・丸め規約（丸め方向・キャップ・適用順序） | [tech_numeric.md](tech_numeric.md) |
| 進行状態と操作可否（状態機械・不変条件・探索セッション） | [tech_state.md](tech_state.md) |
| フロントエンドのtick制御（ポーリング・多重タブ・ストア反映） | [tech_polling.md](tech_polling.md) |
| 認証システム（JWT・ゲスト・Google OAuth） | [tech_auth.md](tech_auth.md) |

---

## 8. 今後の検討事項

- [x] デプロイ先の選定 → **AWS**（EC2 1台 + S3/CloudFront）。[tech_operations.md](tech_operations.md) §12.1 に反映済み
- [x] ブラウザ対応範囲 → [tech_structure.md](tech_structure.md) §3 レスポンシブ設計に反映済み
- [x] アクセシビリティ対応 → [tech_architecture.md](tech_architecture.md) アクセシビリティ対応方針に反映済み
- [x] パフォーマンス目標（ログ保持件数の上限など）→ 目標値は [non_functional_requirements.md](../design/non_functional_requirements.md) §1〜§2、実現方式は [tech_performance.md](tech_performance.md) に反映済み
