# AFK GAME — API設計: 認証（Phase 2〜）

> 親: [tech_api.md](../tech_api.md) §5（索引）。全エンドポイントに適用する共通仕様は [common.md](common.md) §5.0。
> 処理フローと分岐一覧は [tech_auth.md](../../detail/tech_auth.md)、呼び出し順は [api_sequence/auth.md](../../../diagrams/api_sequence/auth.md)。

---

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
| POST | `/api/auth/delete-account` | 退会（アカウント削除）。**再認証必須**（`password` または Google の `idToken`）。ユーザー・プレイヤーデータ・全トークンをカスケード削除し、以後ログイン不可。再認証失敗は401 |

