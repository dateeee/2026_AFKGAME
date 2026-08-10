package com.afkgame.domain.repository;

import org.apache.ibatis.annotations.Param;

import com.afkgame.domain.model.EmailVerificationToken;

/**
 * {@code email_verification_tokens} の Repository。
 *
 * <p>確認・再設定トークンはトークンハッシュで独立して検索されるため、{@code users} の従ではなく
 * 主体として扱う（{@code refresh_tokens} と同じ扱い。docs/process/coding_standards_backend/domain.md §3）。
 *
 * <p>登録（docs/tech/detail/tech_auth/account.md §10 手順6）・メール確認
 * （docs/tech/detail/tech_auth/verify.md §20 手順2・8）・パスワード再設定
 * （docs/tech/detail/tech_auth/password_reset.md §22 手順4・§24 手順2・8）で必要な操作を持つ。
 */
public interface EmailVerificationTokenRepository {

    /**
     * 確認・再設定トークンを登録する。
     *
     * @param emailVerificationToken 登録するレコード
     */
    void save(EmailVerificationToken emailVerificationToken);

    /**
     * トークンハッシュでレコードを取得する（tech_auth/verify.md §20 手順2）。
     *
     * <p>用途（{@code purpose}）で絞らずに引き、用途の判定は呼び出し側が行う。
     * 再設定トークンの流用を「該当なし」ではなく用途違いとして扱うため（§21 #6）。
     *
     * @param tokenHash 生トークンの SHA-256（16進小文字）
     * @return 該当レコード。存在しなければ null
     */
    // 規約例外: 受け取るのは SHA-256 ハッシュであり、境界ログに出ても生トークンは復元できない
    EmailVerificationToken findByTokenHash(String tokenHash);

    /**
     * トークンを使用済みにする（tech_auth/verify.md §20 手順8）。
     *
     * <p>同じユーザーの他のトークンは変更しない（期限切れで自然に無効になる）。
     *
     * <p>未使用の行だけを更新する。同じトークンで並走したリクエストのうち更新できるのは1本だけになり、
     * 呼び出し側は更新件数0を「他のリクエストが先に使い切った」として扱える
     * （docs/tech/detail/tech_auth/password_reset.md §24 手順7）。
     *
     * @param id 対象レコードのID
     * @return 更新件数（既に使用済みなら 0）
     */
    int updateUsedById(Integer id);

    /**
     * 指定ユーザーの未使用トークンを用途で絞って一括で使用済みにする
     * （tech_auth/password_reset.md §22 手順4）。
     *
     * <p>有効な再設定トークンを常に最新の1本だけに保つために、新しい1件を作る前に呼ぶ。
     * 用途で絞るため、確認トークン（{@code verify_email}）は巻き込まない。
     *
     * <p>呼び出し元は件数で経路を分けない（0件でも新しい1件を作る）ため更新件数を返さない。
     * 件数ごとの振る舞いは {@code EmailVerificationTokenRepositoryTest} が実DBで固定する。
     *
     * @param userId ユーザーID
     * @param purpose 対象の用途（{@code password_reset}）
     */
    void updateUsedByUserIdAndPurpose(@Param("userId") String userId,
            @Param("purpose") String purpose);
}
