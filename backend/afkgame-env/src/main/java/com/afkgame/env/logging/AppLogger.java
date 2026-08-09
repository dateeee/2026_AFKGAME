package com.afkgame.env.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ログ出力の共通部品。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md、規約: coding_standards_backend/common.md §7。
 * ロガー名を {@link LoggerName} に、項目名を {@link LogKey} に、失敗理由を {@link LogReason} に
 * 閉じ込め、各クラスが文字列リテラルでログの体裁を組み立てないようにする。
 *
 * <p>使い方:
 * <pre>{@code
 * private static final AppLogger logger = AppLogger.of(LoggerName.AUTH);
 *
 * logger.info("ログイン").with(LogKey.USER_ID, user.getId()).log();
 * logger.warn("ログイン失敗").reason(LogReason.PASSWORD_MISMATCH).log();
 * logger.error("ヘルスチェック: DB疎通に失敗").cause(e).log();
 * }</pre>
 *
 * <p>レベルの使い分けは仕様書「ログレベル方針」に従う。DEBUG は出力箇所が無いため用意していない
 * （必要になった実装で足す）。
 */
public final class AppLogger {

    private final Logger logger;

    private AppLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * ロガー名を指定して共通ロガーを得る。
     *
     * @param name ロガー名（体系は {@link LoggerName}）
     * @return 共通ロガー
     */
    public static AppLogger of(LoggerName name) {
        return new AppLogger(LoggerFactory.getLogger(name.loggerName()));
    }

    /**
     * 正常系イベントを INFO で組み立てる。
     *
     * @param message メッセージ（プレースホルダ {@code {}} を使える）
     * @param args メッセージへ埋め込む値
     * @return 項目を積むための入れ物（{@link LogEntry#log()} で出力する）
     */
    public LogEntry info(String message, Object... args) {
        return new LogEntry(logger::info, message, args);
    }

    /**
     * 想定内のエラーを WARNING で組み立てる。
     *
     * @param message メッセージ（プレースホルダ {@code {}} を使える）
     * @param args メッセージへ埋め込む値
     * @return 項目を積むための入れ物（{@link LogEntry#log()} で出力する）
     */
    public LogEntry warn(String message, Object... args) {
        return new LogEntry(logger::warn, message, args);
    }

    /**
     * 想定外のエラーを ERROR で組み立てる。
     *
     * @param message メッセージ（プレースホルダ {@code {}} を使える）
     * @param args メッセージへ埋め込む値
     * @return 項目を積むための入れ物（{@link LogEntry#log()} で出力する）
     */
    public LogEntry error(String message, Object... args) {
        return new LogEntry(logger::error, message, args);
    }
}
