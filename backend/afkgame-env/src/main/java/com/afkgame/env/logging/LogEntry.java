package com.afkgame.env.logging;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import org.slf4j.MDC;

/**
 * 1件のログイベントに積む項目を集める入れ物。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「ログフォーマット」。
 * 項目は**メッセージへ埋め込まず** MDC へ載せる。text 形式では末尾へ {@code key=value} が並び、
 * JSON 形式では独立フィールドになる（エンコーダは {@code logback-appenders-*.xml}）。
 *
 * <p>生成は {@link AppLogger} が行う。{@link #log()} を呼ぶまで何も出力されない。
 */
public final class LogEntry {

    /** レベルごとの出力先（{@code logger::warn} 等）。 */
    private final BiConsumer<String, Object[]> sink;

    private final String message;
    private final Object[] messageArgs;
    private final Map<String, String> fields = new LinkedHashMap<>();

    private Throwable cause;

    LogEntry(BiConsumer<String, Object[]> sink, String message, Object[] messageArgs) {
        this.sink = sink;
        this.message = message;
        this.messageArgs = messageArgs;
    }

    /**
     * 項目を積む。マスクが要る項目は {@link LogKey} の規則で自動的に伏せる。
     *
     * @param key 項目名
     * @param value 値。{@code null} なら項目そのものを積まない
     * @return 自身
     */
    public LogEntry with(LogKey key, String value) {
        if (value != null) {
            fields.put(key.field(), key.masked(value));
        }
        return this;
    }

    /**
     * 数値の項目を積む。
     *
     * @param key 項目名
     * @param value 値
     * @return 自身
     */
    public LogEntry with(LogKey key, long value) {
        fields.put(key.field(), String.valueOf(value));
        return this;
    }

    /**
     * 失敗理由（{@code reason}）を積む。
     *
     * @param reason 失敗理由
     * @return 自身
     */
    public LogEntry reason(LogReason reason) {
        return with(LogKey.REASON, reason.value());
    }

    /**
     * 原因例外を添える。スタックトレースが出力される。
     *
     * @param throwable 原因例外
     * @return 自身
     */
    public LogEntry cause(Throwable throwable) {
        this.cause = throwable;
        return this;
    }

    /**
     * 積んだ項目とともに出力する。
     *
     * <p>項目は出力の間だけ MDC へ載せ、終わったら元の状態へ戻す。横断項目（{@code request_id} 等）を
     * 各所で詰め直さないための後始末（{@code common.md} §7 #5）。
     */
    public void log() {
        Map<String, String> previous = new LinkedHashMap<>();
        try {
            fields.forEach((field, value) -> {
                previous.put(field, MDC.get(field));
                MDC.put(field, value);
            });
            sink.accept(message, arguments());
        } finally {
            previous.forEach(LogEntry::restore);
        }
    }

    /** 出力前の値へ戻す。元が無かった項目は取り除く。 */
    private static void restore(String field, String previousValue) {
        if (previousValue == null) {
            MDC.remove(field);
        } else {
            MDC.put(field, previousValue);
        }
    }

    /** SLF4J へ渡す引数列。原因例外は末尾に置くとスタックトレースとして扱われる。 */
    private Object[] arguments() {
        if (cause == null) {
            return messageArgs;
        }

        Object[] merged = Arrays.copyOf(messageArgs, messageArgs.length + 1);
        merged[messageArgs.length] = cause;
        return merged;
    }
}
