# AFK GAME — 技術仕様書

> プロジェクト概要は [README.md](../../README.md)、ゲーム仕様は [game_spec.md](../design/game_spec.md)、マスターデータは [master_data.md](../data/master_data.md) を参照。
> 本書は索引。各章はレイヤー別ファイルに分割している。変更履歴は本書（§9）に集約する。

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
| 乱数設計（RNG注入・消費順序・再現性） | [tech_rng.md](tech_rng.md) |
| 数値・丸め規約（丸め方向・キャップ・適用順序） | [tech_numeric.md](tech_numeric.md) |
| 進行状態と操作可否（状態機械・不変条件・探索セッション） | [tech_state.md](tech_state.md) |
| フロントエンドのtick制御（ポーリング・多重タブ・ストア反映） | [tech_polling.md](tech_polling.md) |
| 認証システム（JWT・ゲスト・Google OAuth） | [tech_auth.md](tech_auth.md) |

---

## 8. 今後の検討事項

- [ ] デプロイ先の選定（Vercel + Render / Railway / VPS など）→ 実装完了後に決定
- [x] ブラウザ対応範囲 → [tech_structure.md](tech_structure.md) §3 レスポンシブ設計に反映済み
- [x] アクセシビリティ対応 → [tech_architecture.md](tech_architecture.md) アクセシビリティ対応方針に反映済み
- [x] パフォーマンス目標（ログ保持件数の上限など）→ 目標値は [non_functional_requirements.md](../design/non_functional_requirements.md) §1〜§2、実現方式は [tech_performance.md](tech_performance.md) に反映済み

---

## 9. 変更履歴

分割後の子ファイル（`tech_data.md` 〜 `tech_logging.md`）を含む、技術仕様全体の変更履歴を本表に集約する（直近10件）。

| 日付 | 内容 |
|------|------|
| 2026-03-15 | レビュー指摘対応: §2 ディレクトリ構成を新構造（design/tech/data/diagrams/skills）に更新。§1.1 potionAutoUseThreshold重複フィールドを削除、potionThresholdを0.1〜0.5/0.1刻みに統一。§5 ポーション閾値APIを0.1〜0.5に更新 |
| 2026-03-15 | tech_battle.md §3.2 エンカウント抽選ロジック追記（重み付きプール抽選・均等確率体数決定・Phase共通ロジック）、敵スキル処理フロー追記（Phase 5ボスラッシュWave 11+、CD管理は味方と同一） |
| 2026-08-01 | 複数塔対応: `GET /api/tower/list` エンドポイント追加（解放/クリア状態含む）。`/api/tower/select` に未解放塔403の記載を追加 |
| 2026-08-01 | 数値表示ユーティリティに短縮表記ルールの参照を追記。tech_battle.md §4 を更新: ポーション閾値を「50%固定」→プレイヤー設定値参照に修正、§4.1 期待値計算式（期待与/被ダメ・周回解決・ポーション消費モデル）を追加、オフライン中の転生（発生しない・LV9999で成長停止）を追記 |
| 2026-08-01 | レビュー指摘対応: §6 ゲスト認証をJWT現行仕様に更新（旧UUID方式の記述を置換）、§1.1 targetFloorにnull注記、§4 FAST_CALC_THRESHOLDコメント明確化、§2 に development_process.md 追加、ヘッダリンクを新ディレクトリ構造に修正 |
| 2026-08-02 | §2 ディレクトリ構成から docs/・diagrams/ の詳細ツリーを削除し README.md への参照に変更（ドキュメント規約 §5「重複禁止」適用・二重管理の解消）。ヘッダの概要リンクを README.md に変更 |
| 2026-08-02 | [documentation_rules.md](../documentation_rules.md) 適用: §1〜§7 をレイヤー別5ファイルへ分割し、本書を索引化（27,052字 → 上限8,000字以内）。変更履歴を直近10件に整理 |
| 2026-08-02 | 詳細設計の欠落5観点を新規5ファイルで補完: tick進行制御（tech_tick）・乱数設計（tech_rng）・数値／丸め規約（tech_numeric）・進行状態と操作可否（tech_state）・フロントtick制御（tech_polling）。各ファイルに「分岐一覧（単体テスト観点）」と「現行実装との差異」を付与 |
| 2026-08-02 | 未確定仕様の確定を反映。tech_api.md: `/api/tower/select` の `targetFloor` 検証範囲（`min(塔別highestFloor + 1, totalFloors)`）と上限追従ルールを明記、深淵の塔セクション（`/api/abyss/ranking`・`towerId: "abyss_tower"` で既存 `/api/tower/*` を流用）を追加。tech_offline.md: 簡略計算でも上限追従を適用する旨を追記 |
| 2026-08-02 | 基本設計の欠落観点を補完: §5.0 API共通仕様（ベースパス・命名・日時／数値・認証要否・共通ヘッダ・HTTPステータス使い分け）を tech_api.md に新設し `GET /health` を追加。§10 性能・容量設計（tech_performance）・§11 セキュリティ設計（tech_security）・§12 運用設計（tech_operations）の3ファイルを新設。tech_architecture.md の同時実行制御は tech_tick.md を正として不変条件のみ残す形に整理 |
