package com.afkgame.domain.service;

import com.afkgame.domain.exception.AppException;
import com.afkgame.domain.model.User;

/**
 * 認証のドメインサービス。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §2「ゲストプレイ」・§3「認証フロー」・
 * §4「リフレッシュトークン」と docs/tech/detail/tech_auth/account.md §9〜§15
 * （登録・ログイン・ログアウト）、エラーコードは docs/tech/basic/tech_error_handling.md「AUTH_ コード一覧」。
 *
 * <p>ゲスト作成・リフレッシュ・登録・ログイン・ログアウトと、ユーザー作成時のプレイヤー初期化
 * （tech_auth.md §8.2。実体は {@link PlayerInitializationService}）を持つ。
 * アカウント移行・確認メール・パスワード再設定は STEP 3-A-3 以降で追加する
 * （docs/backlog/java_migration.md）。
 *
 * <p>実装は {@link AuthServiceImpl}。
 */
public interface AuthService {

    /**
     * ゲストアカウントを作成し、プレイ可能な初期状態を組み立ててトークンペアを発行する。
     *
     * <p>tech_auth.md §8.2 の手順1（ユーザー作成）→ 手順2〜6（初期化）→ 手順7（トークン発行）を
     * 1つのトランザクション境界にまとめる（手順8）。途中で失敗した場合はユーザーを含めて何も残さない。
     *
     * @return 作成したユーザーとトークンペア
     */
    AuthResult createGuest();

    /**
     * リフレッシュトークンを検証し、ローテーションして新しいトークンペアを発行する。
     *
     * <p>revoked 済みのトークンが使われた場合は窃取とみなし、そのユーザーの全トークンを失効させる
     * （tech_auth.md §4「不正検知」）。この失効は 401 を返す場合でも確定する。
     *
     * @param rawRefreshToken クライアントが持つ生のリフレッシュトークン
     * @return 新しいトークンペア
     * @throws AppException {@code AUTH_REFRESH_INVALID}（不正・再利用・期限切れ）
     */
    AuthResult refresh(String rawRefreshToken);

    /**
     * アクセストークンのユーザーIDから認証ユーザーを取得する。
     *
     * @param userId トークンの {@code sub}
     * @return 該当ユーザー
     * @throws AppException {@code AUTH_USER_NOT_FOUND}（トークンは正当だがユーザーが存在しない）
     */
    User findAuthenticatedUser(String userId);

    /**
     * メールアドレスとパスワードでアカウントを登録し、トークンペアを発行する。
     *
     * <p>tech_auth/account.md §10 の手順2（重複確認）→ 手順3〜7（ユーザー・初期化・確認トークン・
     * トークンペア）を1つのトランザクション境界にまとめる（手順8）。途中で失敗した場合は
     * ユーザーを含めて何も残さない。
     *
     * <p>表示名は設定しない。既定値 {@code 冒険者} は {@link User} のフィールド初期値が持つ
     * （tech_db.md §4-2・§10 手順4）。
     *
     * @param email 登録するメールアドレス
     * @param rawPassword 生のパスワード
     * @return 作成したユーザーとトークンペア
     * @throws AppException {@code AUTH_EMAIL_TAKEN}（メールが使用済み）
     */
    AuthResult register(String email, String rawPassword);

    /**
     * メールアドレスとパスワードで認証し、トークンペアを発行する。
     *
     * <p>tech_auth/account.md §12。手順2〜4 の失敗はいずれも同じコードを返し、メールとパスワードの
     * どちらが誤りかを区別させない。既存のリフレッシュトークンは失効させない（手順7）。
     *
     * @param email メールアドレス
     * @param rawPassword 生のパスワード
     * @return 認証したユーザーとトークンペア
     * @throws AppException {@code AUTH_INVALID_CREDENTIALS}（未登録・パスワード未設定・不一致）
     */
    AuthResult login(String email, String rawPassword);

    /**
     * リフレッシュトークンを失効させてログアウトする。
     *
     * <p>tech_auth/account.md §14。失効済みのトークンでも 200 を返す冪等な操作であり、
     * §4 の再利用検知（ユーザーの全トークン失効）は行わない（手順5）。期限切れのトークンも
     * 期限内と同じく失効させる（手順6）。
     *
     * @param userId 手順1で特定した認証ユーザーのID
     * @param rawRefreshToken クライアントが持つ生のリフレッシュトークン
     * @throws AppException {@code AUTH_REFRESH_INVALID}（該当なし・他人のトークン）
     */
    void logout(String userId, String rawRefreshToken);
}
