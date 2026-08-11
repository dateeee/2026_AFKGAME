package com.afkgame.domain.service;

import org.terasoluna.gfw.common.exception.BusinessException;

import com.afkgame.domain.model.User;

/**
 * 認証のドメインサービス。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §2「ゲストプレイ」・§3「認証フロー」・
 * §4「リフレッシュトークン」と docs/tech/detail/tech_auth/account.md §9〜§15
 * （登録・ログイン・ログアウト）、エラーコードは docs/tech/basic/tech_error_handling.md「AUTH_ コード一覧」。
 *
 * <p>ゲスト作成・リフレッシュ・登録・ログイン・ログアウト・アカウント移行・メール確認・
 * パスワード再設定（docs/tech/detail/tech_auth/password_reset.md §22・§24）と、
 * ユーザー作成時のプレイヤー初期化（tech_auth/init.md §8.2。実体は
 * {@link PlayerInitializationService}）を持つ。
 *
 * <p>実装は {@link AuthServiceImpl}。
 */
public interface AuthService {

    /**
     * ゲストアカウントを作成し、プレイ可能な初期状態を組み立ててトークンペアを発行する。
     *
     * <p>tech_auth/init.md §8.2 の手順1（ユーザー作成）→ 手順2〜6（初期化）→ 手順7（トークン発行）を
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
     * @param refreshToken クライアントが持つ生のリフレッシュトークン
     * @return 新しいトークンペア
     * @throws BusinessException {@code AUTH_REFRESH_INVALID}（不正・再利用・期限切れ）
     */
    AuthResult refresh(String refreshToken);

    /**
     * アクセストークンのユーザーIDから認証ユーザーを取得する。
     *
     * @param userId トークンの {@code sub}
     * @return 該当ユーザー
     * @throws BusinessException {@code AUTH_USER_NOT_FOUND}（トークンは正当だがユーザーが存在しない）
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
     * @throws BusinessException {@code AUTH_EMAIL_TAKEN}（メールが使用済み）
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
     * @throws BusinessException {@code AUTH_INVALID_CREDENTIALS}（未登録・パスワード未設定・不一致）
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
     * @param refreshToken クライアントが持つ生のリフレッシュトークン
     * @throws BusinessException {@code AUTH_REFRESH_INVALID}（該当なし・他人のトークン）
     */
    void logout(String userId, String refreshToken);

    /**
     * ゲストアカウントを本登録化し、新しいトークンペアを発行する。
     *
     * <p>tech_auth/link.md §18。手順7〜10（ハッシュ化・ユーザー更新・確認トークン・トークンペア）を
     * 1つのトランザクション境界にまとめる（手順11）。**ゲームデータは作り直さず**（tech_auth.md §3）、
     * 既存のリフレッシュトークンも失効させない（手順10）。
     *
     * <p>受け取るのは手順1で特定した認証ユーザー<b>そのもの</b>で、IDではない。手順4の判定が
     * 「アクセストークンに載った {@code isGuest}」ではなく「ユーザーの現在値」を見るため（§18 末尾）。
     *
     * <p>{@code email}+{@code rawPassword} と {@code googleAuthCode} のちょうど一方を受ける。
     * Google連携は Phase 2 では成立させない（手順3）。
     *
     * @param user 手順1で特定した認証ユーザー
     * @param email 連携するメールアドレス（Google連携なら null）
     * @param rawPassword 生のパスワード（Google連携なら null）
     * @param googleAuthCode Google の認可コード（メール連携なら null）
     * @return 本登録化したユーザーとトークンペア
     * @throws BusinessException {@code AUTH_LINK_PAYLOAD_INVALID}（連携方法が一意に決まらない）・
     *         {@code AUTH_GOOGLE_NOT_CONFIGURED} / {@code AUTH_GOOGLE_NOT_IMPLEMENTED}（Google連携）・
     *         {@code AUTH_ALREADY_REGISTERED}（本登録済み）・{@code AUTH_EMAIL_TAKEN}（メール使用済み）
     */
    AuthResult linkAccount(User user, String email, String rawPassword, String googleAuthCode);

    /**
     * 確認トークンを検証し、対象ユーザーのメール確認を済ませる。
     *
     * <p>tech_auth/verify.md §20。手順7〜8（確認状態・使用済みの更新）を1つのトランザクション境界に
     * まとめる（手順9）。認証を要求せず、トークンの提示だけで成立する。
     *
     * <p>使用済みのトークンは<b>何も更新せず正常終了する</b>（メール内リンクの再クリックは正常操作。
     * 手順4）。成功時に返す値は持たず、応答 {@code {"status": "ok"}} は Web 層が組む。
     *
     * @param token メール本文のリンクに載った生のトークン。境界ログで伏せるため固定表の名前にする
     *        （logging/application.md §3.1 規約1）
     * @throws BusinessException {@code AUTH_VERIFICATION_INVALID}（該当なし・用途違い・期限切れ・
     *         対象ユーザーが退会済み）
     */
    void verifyEmail(String token);

    /**
     * パスワード再設定を要求し、再設定トークンを作って再設定メールの送信を要求する。
     *
     * <p>tech_auth/password_reset.md §22。手順4〜5（既存トークンの無効化・新しい1件の作成）を
     * 1つのトランザクション境界にまとめ（手順6）、送信要求はコミット後・境界の外で出す（手順7）。
     *
     * <p><b>対象がいない場合もパスワードを持たない場合も例外を投げない</b>（手順2・3）。
     * 応答差でアカウントの存否・種別を推測させないため（§22 末尾）。成功時に返す値は持たず、
     * 応答 {@code {"status": "ok"}} は Web 層が組む。
     *
     * @param email 再設定するアカウントのメールアドレス（正規化してから検索する）
     */
    void requestPasswordReset(String email);

    /**
     * 再設定トークンを検証し、パスワードを更新して全リフレッシュトークンを失効させる。
     *
     * <p>tech_auth/password_reset.md §24。手順7〜9（パスワード更新・トークンの使用済み化・
     * 全端末の切断）を1つのトランザクション境界にまとめる（手順10）。
     * <b>新しいトークンペアは発行しない</b> — フロントはログイン画面へ戻す。
     *
     * <p>使用済みのトークンでの再実行は<b>冪等にしない</b>（メール確認と違い 400 を返す。手順4）。
     * 認めると漏れたメールから何度でもパスワードを書き換えられるため。
     *
     * @param token メール本文のリンクに載った生のトークン。境界ログで伏せるため固定表の名前にする
     *        （logging/application.md §3.1 規約1）
     * @param newPassword 新しい生のパスワード
     * @throws BusinessException {@code AUTH_RESET_TOKEN_INVALID}（該当なし・用途違い・使用済み・
     *         期限切れ・対象ユーザーが退会済み）
     */
    void resetPassword(String token, String newPassword);
}
