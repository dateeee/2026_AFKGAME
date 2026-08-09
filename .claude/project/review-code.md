# コード品質レビュー — プロジェクト固有プロファイル

> 一般手順は [.claude/references/review-procedure.md](../references/review-procedure.md)、出力形式は [review-format.md](../references/review-format.md)。本書は AFK GAME 固有の値のみを持つ。
> 対象スキル: `backend-review`、`frontend-review`。仕様↔コードの統合整合は [review-fullstack.md](review-fullstack.md)。
> 技術規約は [profile.md](profile.md) §3、不変条件は §5。

## 0. レビューパラメータ

| スキル | 保存先ディレクトリ | レポートタイトル | カテゴリ |
|-------|-----------------|---------------|---------|
| `backend-review` | `docs/reviews/backend-review/` | バックエンドコードレビュー結果 | コード品質 / セキュリティ / 一貫性 |
| `frontend-review` | `docs/reviews/frontend-review/` | フロントエンドコードレビュー結果 | コード品質 / 状態管理 / エラーハンドリング・UX / パフォーマンス |

ファイル名は `YYYY-MM-DD_HHMMSS.md`。
ローテーションは `python scripts/rotate_reviews.py --apply`（直下を最新10件に保ち、超過分は `archive/` へ移動）。
該当箇所は行番号（`xxx.java 行N〜M`）で示す。

準備コマンド（モード判定・差分特定・ISSUE採番。全量時は `--full` を追加）:

```bash
python .claude/scripts/review_prep.py --dir docs/reviews/backend-review \
    --paths backend --title バックエンドコードレビュー結果 \
    --categories "コード品質 / セキュリティ / 一貫性"

python .claude/scripts/review_prep.py --dir docs/reviews/frontend-review \
    --paths frontend/src frontend/index.html frontend/vite.config.ts frontend/package.json frontend/tsconfig.json \
    --title フロントエンドコードレビュー結果 \
    --categories "コード品質 / 状態管理 / エラーハンドリング・UX / パフォーマンス"
```

## 1. 対象ファイル

| スキル | 対象 |
|-------|------|
| `backend-review` | `backend/` 配下の全 `.java`（`target/` のビルド生成物除外） |
| `frontend-review` | `frontend/src/` 配下の全 `.vue` `.ts` `.css` + `index.html`・`vite.config.ts`・`package.json`・`tsconfig.json` |

**全量モードの分担: 分担なし＝1体全量**。対象規模が小さいため、複数体へ分割せずメインコンテキストで完結させる
（`review-procedure.md` §1 規律7 の「`sonnet` 1体へ全体委譲」は可）。

## 2. `backend-review` の観点

**コーディング規約への適合を先に見る**。チェックリストは [.claude/references/coding-standards-backend.md](../references/coding-standards-backend.md) §5（正: [docs/process/coding_standards_backend.md](../../docs/process/coding_standards_backend.md)）。
規約に書かれた項目の指摘は**規約の節番号を添える**。下表はそれに加えて見る、AFK GAME 固有の観点。

| 分類 | # | 観点 |
|------|---|------|
| コード品質 | 1 | **Spring MVC**: `@RestController`（マッピングアノテーション・パス設計）、コンストラクタ注入によるDI、HTTPステータス（200/201/400/401/404/422）、`@RestControllerAdvice` による例外ハンドリングの一貫性、パス・クエリパラメータの型・バリデーション |
| コード品質 | 2 | **MyBatis3**: Repository インタフェースとマッピング XML の対応、主体 Entity 単位で作れているか、SQL のパラメータバインド、`@Transactional` によるトランザクション境界、**N+1問題**（Repository でのJOIN取得・バッチ取得） |
| コード品質 | 3 | **Resource + Bean Validation**: `@NotNull` 等の制約アノテーションの活用、Create/Update/Response の分離、`@Valid` によるコントローラ側の検証 |
| コード品質 | 4 | **Java一般**: 型の網羅性、命名規則（camelCase）、不要な `Object`、マジックナンバー |
| セキュリティ | 5 | SQLインジェクション（生SQLのパラメータバインド） |
| セキュリティ | 6 | 保護すべきエンドポイントに認証チェック（`Depends`）があるか |
| セキュリティ | 7 | 入力バリデーション（Bean Validation アノテーションによる検証） |
| セキュリティ | 8 | 重要エンドポイント（ログイン・購入）のレート制限が考慮されているか |
| セキュリティ | 9 | エラーレスポンスに内部情報（スタックトレース・DB構造）が漏れていないか |
| セキュリティ | 10 | パスワードが適切にハッシュ化されているか（平文保存でないか） |
| 一貫性 | 11 | 全エンドポイントで統一エラーレスポンス形式（`error.code`）が使われているか |
| 一貫性 | 12 | レスポンス構造の統一、命名パターンの統一 |
| 一貫性 | 13 | ログ出力の箇所とログレベルが `logging_config` 準拠で一貫しているか |
| 一貫性 | 14 | 環境変数・設定値が `afkgame-env` の設定保持 Bean で一元管理されているか |
| 一貫性 | 15 | ロジックが `services/` に集約され、ルーターに漏れていないか |

## 3. `frontend-review` の観点

型整合チェックは目視でなく `cd frontend && npm run type-check`（`vue-tsc --noEmit`）の実行結果を用いる。

| 分類 | # | 観点 |
|------|---|------|
| コード品質 | 1 | **Vue 3**: `<script setup lang="ts">`（Options API でないか）、`ref()` / `reactive()` / `computed()` の使い分け、`watch` / `watchEffect` の不要な再実行、ライフサイクルフック、テンプレート内ロジックの過剰 |
| コード品質 | 2 | **TypeScript**: `any` の使用、型定義の網羅性（`types/` への配置）、不適切な `as`、null/undefined ハンドリング |
| コード品質 | 3 | **コンポーネント設計**: 単一責任、`defineProps<T>()` / `defineEmits<T>()` の型定義、再利用性、`v-for` の `:key` |
| コード品質 | 4 | **Composables**: `use` プレフィックス、関心の分離（UI / ロジック / API）、リアクティブ値の返却 |
| 状態管理 | 5 | Pinia が **Setup Store 形式**か、ストアの責務が明確か、state / getters / actions の分離 |
| 状態管理 | 6 | ストア間の循環依存、ログアウト時のリセット処理 |
| 状態管理 | 7 | API通信が actions 内で行われ、ローディング・エラー状態が管理されているか |
| UX | 8 | API エラー時のフィードバック表示、ローディング表示、空状態の表示、クライアントバリデーション |
| UX | 9 | **開発時フォールバック**: `composables/useBattleLocal.ts` が正しく機能する設計か |
| パフォーマンス | 10 | 不要な再レンダリング、`onUnmounted` でのタイマー・リスナーのクリーンアップ |
| パフォーマンス | 11 | ポーリングの開始・停止が適切に管理されているか（画面離脱時の停止等） |

**CSSの見た目に関する指摘は対象外**（機能的な問題のみ指摘する）。

## 4. 重要度の基準

| 重要度 | `backend-review` | `frontend-review` |
|-------|-----------------|------------------|
| **高** | セキュリティリスク、データ不整合を招くバグ、重大な設計問題 | UXに直接影響するバグ、セキュリティ問題、重大な設計問題 |
| **中** | ベストプラクティスからの逸脱、将来的な問題の原因 | ベストプラクティスからの逸脱、型安全性の欠如 |
| **低** | コードスタイル、動作に影響しない改善 | コードスタイル、パフォーマンス最適化の余地 |

担当範囲の切り分けは `review-procedure.md` §7 を参照。
