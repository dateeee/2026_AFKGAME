# 画面遷移図 — 認証・エントリーフロー

> 親: [screen_transition.md](../screen_transition.md)。UI仕様は [systems/ui.md](../../docs/design/systems/ui.md) §3、認証仕様は [tech_auth.md](../../docs/tech/tech_auth.md)。

## 認証・エントリーフロー

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
stateDiagram-v2
    direction TB

    [*] --> 認証判定

    state 認証判定 <<choice>>
    認証判定 --> ゲスト自動作成 : Phase 1\nLocalStorageにtoken無し
    認証判定 --> ログイン画面 : Phase 2~\n未認証
    認証判定 --> ホーム : token有り\n(ゲスト or 認証済み)

    state ログイン画面 {
        [*] --> ログインフォーム
        ログインフォーム --> メール認証 : メール+パスワード
        ログインフォーム --> Google認証 : Googleボタン\n（Phase 2後半・未実装）
        ログインフォーム --> ゲスト自動作成 : ゲストでプレイ
        ログインフォーム --> 登録タブ : 登録はこちら\n(/register も同タブを開く)
        ログインフォーム --> パスワードリセット : パスワードを忘れた\n（Phase 2後半・未実装）

        state 登録タブ {
            [*] --> 登録フォーム
            登録フォーム --> 登録送信 : メール+パスワード送信\n(確認メールは裏で送信)
            登録フォーム --> Google登録 : Googleで登録\n（Phase 2後半・未実装）
        }

        state パスワードリセット {
            [*] --> メール入力
            メール入力 --> リセットメール送信完了
        }
    }

    ゲスト自動作成 --> ホーム : UUID発行\nLocalStorage保存
    メール認証 --> ホーム : JWT発行
    Google認証 --> ホーム : JWT発行
    登録送信 --> ホーム : JWT発行\n未確認でもプレイ可・即ホーム遷移
    Google登録 --> ホーム : JWT発行
```

- **登録は独立画面ではなくログイン画面内のタブ**（UXの観点で統合）。`/register` へ直接アクセスした場合もログイン画面の登録タブへ寄せる
- 登録直後に確認メール送信の完了を待つUI状態は持たない。送信はバックグラウンドで行い、即ホームへ遷移する
- 対応するAPIシーケンスは [api_sequence/auth.md](../api_sequence/auth.md)
- 退会（アカウント削除）とログアウトの画面構成は [main_nav.md](main_nav.md) の設定画面。いずれも実行後は**本図の ログイン画面**へ戻る
