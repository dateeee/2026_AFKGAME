# フロントエンドコードレビュー — プロジェクト固有プロファイル

> 共通パラメータ・重要度の共通軸は [review.md](../review.md)。一般手順は [review-procedure.md](../../references/review-procedure.md)、出力形式は [review-format.md](../../references/review-format.md)。
> 対象スキル: `frontend-review`。バックエンドは [backend.md](backend.md)、フロント↔バックの統合整合は [fullstack.md](fullstack.md)。
> 技術規約は [profile.md](../profile.md) §3、不変条件は §5。

## 0. レビューパラメータ

| 項目 | 値 |
|------|-----|
| 保存先ディレクトリ | `docs/reviews/frontend-review/` |
| レポートタイトル | フロントエンドコードレビュー結果 |
| カテゴリ | コード品質 / 状態管理 / エラーハンドリング・UX / パフォーマンス |

```bash
python .claude/scripts/review_prep.py --dir docs/reviews/frontend-review \
    --paths frontend/src frontend/index.html frontend/vite.config.ts frontend/package.json frontend/tsconfig.json \
    --title フロントエンドコードレビュー結果 \
    --categories "コード品質 / 状態管理 / エラーハンドリング・UX / パフォーマンス"
```

## 1. 対象ファイル

`frontend/src/` 配下の全 `.vue` `.ts` `.css` + `index.html`・`vite.config.ts`・`package.json`・`tsconfig.json`。

**全量モードの分担: 分担なし＝1体全量**。対象規模が小さいため、複数体へ分割せずメインコンテキストで完結させる
（[review-procedure.md](../../references/review-procedure.md) §1 規律7 の「`sonnet` 1体へ全体委譲」は可）。

## 2. 観点

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

## 3. 重要度の基準

[review.md](../review.md) §2 の具体化。

| 重要度 | 基準 |
|-------|------|
| **高** | UXに直接影響するバグ、セキュリティ問題、重大な設計問題 |
| **中** | ベストプラクティスからの逸脱、型安全性の欠如 |
| **低** | コードスタイル、パフォーマンス最適化の余地 |
