package com.afkgame.domain.repository;

import com.afkgame.domain.model.RefreshToken;

/**
 * {@code refresh_tokens} の Repository。
 *
 * <p>ローテーションと不正検知（docs/tech/detail/tech_auth.md §4）に必要な操作を持つ。
 * トークンハッシュで独立して検索・失効されるため、{@code users} の従ではなく主体として扱う。
 */
public interface RefreshTokenRepository {

    /**
     * トークンハッシュで1件取得する。
     *
     * @param tokenHash 生トークンの SHA-256 ハッシュ
     * @return 該当レコード。存在しなければ null
     */
    // 規約例外: 受け取るのは SHA-256 ハッシュであり、境界ログに出ても生トークンは復元できない
    RefreshToken findByTokenHash(String tokenHash);

    /**
     * リフレッシュトークンを登録する。
     *
     * @param refreshToken 登録するレコード
     */
    void save(RefreshToken refreshToken);

    /**
     * 指定の1件を無効化する（ローテーションで旧トークンを失効させる）。
     *
     * <p>未失効の行だけを更新する。同じトークンで並走したリクエストのうち更新できるのは1本だけになり、
     * 呼び出し側は更新件数0を「他のリクエストが先に失効させた＝再利用」として扱える。
     *
     * @param id レコードID
     * @return 更新件数（0 なら対象が既に失効済み）
     */
    int updateRevokedById(Integer id);

    /**
     * 指定ユーザーの有効なトークンをすべて無効化する（再利用検知時の全失効）。
     *
     * @param userId ユーザーID
     */
    void updateRevokedByUserId(String userId);
}
