package com.afkgame.domain.repository;

import com.afkgame.domain.model.EmailVerificationToken;

/**
 * {@code email_verification_tokens} の Repository。
 *
 * <p>確認・再設定トークンはトークンハッシュで独立して検索されるため、{@code users} の従ではなく
 * 主体として扱う（{@code refresh_tokens} と同じ扱い。docs/process/coding_standards_backend/domain.md §3）。
 *
 * <p>登録（docs/tech/detail/tech_auth/account.md §10 手順6）とメール確認
 * （docs/tech/detail/tech_auth/verify.md §20 手順2・8）で必要な操作を持つ。
 * パスワード再設定で使う操作は STEP 3-A-3 セグメント②で追加する。
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
     * @param id 対象レコードのID
     */
    void updateUsedById(Integer id);
}
