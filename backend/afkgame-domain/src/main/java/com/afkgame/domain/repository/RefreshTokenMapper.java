package com.afkgame.domain.repository;

import com.afkgame.domain.model.RefreshToken;

/**
 * {@code refresh_tokens} テーブルの Mapper。
 *
 * <p>ローテーションと不正検知（docs/tech/detail/tech_auth.md §4）に必要な操作を持つ。
 */
public interface RefreshTokenMapper {

    /**
     * トークンハッシュで1件取得する。
     *
     * @param tokenHash 生トークンの SHA-256 ハッシュ
     * @return 該当レコード。存在しなければ null
     */
    RefreshToken selectByTokenHash(String tokenHash);

    /**
     * リフレッシュトークンを登録する。
     *
     * @param refreshToken 登録するレコード
     */
    void insert(RefreshToken refreshToken);

    /**
     * 指定の1件を無効化する（ローテーションで旧トークンを失効させる）。
     *
     * @param id レコードID
     */
    void revokeById(Integer id);

    /**
     * 指定ユーザーの有効なトークンをすべて無効化する（再利用検知時の全失効）。
     *
     * @param userId ユーザーID
     */
    void revokeAllByUserId(String userId);
}
