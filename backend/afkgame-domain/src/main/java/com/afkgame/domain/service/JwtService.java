package com.afkgame.domain.service;

import org.terasoluna.gfw.common.exception.BusinessException;

import com.afkgame.env.logging.MaskReturnValue;

/**
 * アクセストークン（JWT）の発行と検証。
 *
 * <p>仕様: docs/tech/detail/tech_auth.md §1（有効期限）・§4（ペイロード）。
 * 失敗はコードで区別する（docs/tech/basic/tech_error_handling.md「AUTH_ コード一覧」）。
 * クライアントは {@code AUTH_TOKEN_EXPIRED} なら refresh を試し、
 * {@code AUTH_INVALID_TOKEN} なら再ログインへ倒すため、両者を混ぜない。
 *
 * <p>共有 Service。{@link AuthService} と Web 層の認証フィルタから利用する
 * （docs/process/coding_standards_backend/domain/service.md §2 #1）。
 * トランザクション境界を持たないため伝播属性は宣言しない。
 *
 * <p>実装は {@link JwtServiceImpl}。
 */
public interface JwtService {

    /**
     * アクセストークンを発行する。
     *
     * <p>{@code role}・{@code isGuest} は載せるだけで検証しない。権限とゲスト種別は DB の
     * {@code users} を正とし、{@link AuthService#findAuthenticatedUser(String)} が引き直す
     * （トークン内の値を信用すると、DB 側の変更がトークン失効まで反映されないため）。
     *
     * @param userId ユーザーID
     * @param guest  ゲストアカウントかどうか
     * @return 署名済みJWT
     */
    @MaskReturnValue
    String createAccessToken(String userId, boolean guest);

    /**
     * アクセストークンを検証してユーザーIDを取り出す。
     *
     * <p>用途クレーム {@code type} が {@code access} でないものは受理しない。同じ署名鍵で発行された
     * 別用途のトークン（メール確認・パスワードリセット等）をアクセストークンとして通さないため。
     *
     * @param token 検証するトークン
     * @return {@code sub} クレームのユーザーID
     * @throws BusinessException 期限切れ（{@code AUTH_TOKEN_EXPIRED}）または不正（{@code AUTH_INVALID_TOKEN}）
     */
    String parseUserId(String token);
}
