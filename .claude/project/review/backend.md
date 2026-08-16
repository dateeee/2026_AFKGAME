# バックエンドコードレビュー — プロジェクト固有プロファイル

> 共通パラメータ・重要度の共通軸は [review.md](../review.md)。一般手順は [review-procedure.md](../../references/review-procedure.md)、出力形式は [review-format.md](../../references/review-format.md)。
> 対象スキル: `backend-review`。フロントは [frontend.md](frontend.md)、仕様↔コードの統合整合は [fullstack.md](fullstack.md)。
> 技術規約は [profile.md](../profile.md) §3、不変条件は §5。

## 0. レビューパラメータ

| 項目 | 値 |
|------|-----|
| 保存先ディレクトリ | `docs/reviews/backend-review/` |
| レポートタイトル | バックエンドコードレビュー結果 |
| カテゴリ | コード品質 / セキュリティ / 一貫性 |

```bash
python .claude/scripts/review_prep.py --dir docs/reviews/backend-review \
    --paths backend --title バックエンドコードレビュー結果 \
    --categories "コード品質 / セキュリティ / 一貫性"
```

## 1. 対象ファイル

`backend/` 配下の全 `.java`（`target/` のビルド生成物除外）。

**全量モードの分担: 分担なし＝1体全量**。対象規模が小さいため、複数体へ分割せずメインコンテキストで完結させる
（[review-procedure.md](../../references/review-procedure.md) §1 規律7 の「`sonnet` 1体へ全体委譲」は可）。

## 2. 観点

**コーディング規約への適合を先に見る**。§5 チェックリスト [review-checklist.md](../../references/coding-standards-backend/review-checklist.md)、§2〜§4 規約本体は索引 [coding-standards-backend.md](../../references/coding-standards-backend.md) の分冊（`layering` / `writing` / `exception-logging`）（正: [coding_standards_backend.md](../../../docs/process/coding_standards_backend.md)）。
規約に書かれた項目の指摘は**規約の節番号を添える**。機械判定できる13ルール（タブ・行長・import・ログ・DI・SQL・日時・乱数・マスク・未参照）は `python scripts/check_java_conventions.py` が判定するので、**先に実行して出力をそのまま取り込み**、目視で重ねて探さない。ただし `// 規約例外:` で抑止した箇所は判定の外側に出るため、**`--suppressed` の一覧を見て「今も妥当か」を再点検する**（ISSUE-909 はこの死角から出た）。下表はそれに加えて見る、AFK GAME 固有の観点。

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

## 3. 重要度の基準

[review.md](../review.md) §2 の具体化。

| 重要度 | 基準 |
|-------|------|
| **高** | セキュリティリスク、データ不整合を招くバグ、重大な設計問題 |
| **中** | ベストプラクティスからの逸脱、将来的な問題の原因 |
| **低** | コードスタイル、動作に影響しない改善 |
