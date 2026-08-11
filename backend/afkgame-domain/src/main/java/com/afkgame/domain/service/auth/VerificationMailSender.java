package com.afkgame.domain.service.auth;

import com.afkgame.domain.model.User;

/**
 * 確認メール・パスワード再設定メールの送信境界。
 *
 * <p>仕様: docs/tech/detail/tech_auth/account.md §10 手順9、
 * docs/tech/detail/tech_auth/password_reset.md §22 手順7、
 * docs/tech/detail/tech_auth/mail.md §16。呼び出し元（{@link AuthService}）は送信の成否を
 * 応答へ反映しない。「コミット後・トランザクションの外で送る」担保と、失敗を WARN ログに
 * とどめる責務はいずれも実装側が持つ。
 *
 * <p>種別ごとに変わるのは件名・リンク・有効期限だけで、送信のタイミングと失敗の扱いは共通
 * （mail.md §16.1・§16.3）。
 *
 * <p>送信手段を差し替えられるようインタフェースで受ける
 * （docs/process/coding_standards_backend/domain/service.md §3 #8）。
 * 実装は {@link VerificationMailSenderImpl}。
 */
public interface VerificationMailSender {

    /**
     * 確認メールの送信を要求する。
     *
     * <p>呼び出し元がトランザクションに参加している場合、送信はコミット後に行われる。
     * <b>送信の失敗で例外を投げない</b>（失敗は WARN ログだけを残す）。呼び出し元は
     * 送信結果を待たず、握るための {@code try-catch} も持たない。
     *
     * @param user 宛先のユーザー
     * @param token 確認トークンの生値（メール本文のリンクに載せる）
     */
    void send(User user, String token);

    /**
     * パスワード再設定メールの送信を要求する（password_reset.md §22 手順7）。
     *
     * <p>送信のタイミングと失敗の扱いは {@link #send(User, String)} と同じ。異なるのは
     * 件名・リンクの経路・本文へ書く有効期限だけ（mail.md §16.3）。
     *
     * @param user 宛先のユーザー
     * @param token 再設定トークンの生値（メール本文のリンクに載せる）
     */
    void sendPasswordReset(User user, String token);
}
