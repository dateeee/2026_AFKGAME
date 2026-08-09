package com.afkgame.env.logging;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.encoder.EncoderBase;

/**
 * 本番用の構造化JSONログを組み立てる（{@code LOG_FORMAT=json}）。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「ログフォーマット」。
 * 固定項目（timestamp・level・logger・message）に MDC の項目を同じ階層で並べ、
 * 例外があれば {@code exception} を加える。logback-appenders-json.xml から参照する。
 *
 * <p>MDC のキーはログ項目名そのものとして扱う（付与は
 * {@code com.afkgame.web.filter.RequestLogFilter} 他）。
 *
 * <p>logback の {@link EncoderBase} として直接アペンダへ挿す。Spring Boot の
 * {@code StructuredLogFormatter}／{@code StructuredLogEncoder} は使わない（移行 STEP 2R-D）。
 * 項目の組み立ては {@link #format(ILoggingEvent)} が持ち、エンコードは UTF-8 に固定する。
 */
public class JsonLogFormatter extends EncoderBase<ILoggingEvent> {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /** ログレベルの表記（tech_logging.md「ログレベル方針」）。logback の WARN は WARNING として出す。 */
    private static final String WARNING = "WARNING";

    @Override
    public byte[] headerBytes() {
        return null;
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        return format(event).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] footerBytes() {
        return null;
    }

    /**
     * 1件のログイベントを1行の JSON へ組み立てる。
     *
     * @param event ログイベント
     * @return 改行終端の JSON 1行
     */
    public String format(ILoggingEvent event) {
        StringBuilder json = new StringBuilder(256).append('{');
        appendField(json, "timestamp", TIMESTAMP.format(event.getInstant()));
        appendField(json, "level", levelName(event.getLevel()));
        appendField(json, "logger", event.getLoggerName());
        appendField(json, "message", event.getFormattedMessage());

        for (Map.Entry<String, String> mdc : event.getMDCPropertyMap().entrySet()) {
            appendField(json, mdc.getKey(), mdc.getValue());
        }

        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            appendField(json, "exception", ThrowableProxyUtil.asString(throwable));
        }

        // 固定項目が必ず1件以上あるため、末尾は常に appendField が付けたカンマになる
        json.setLength(json.length() - 1);
        return json.append("}\n").toString();
    }

    private static String levelName(Level level) {
        return level == Level.WARN ? WARNING : level.toString();
    }

    private static void appendField(StringBuilder json, String name, String value) {
        json.append('"');
        escape(json, name);
        json.append("\":\"");
        escape(json, value);
        json.append("\",");
    }

    private static void escape(StringBuilder json, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> appendPlain(json, c);
            }
        }
    }

    private static void appendPlain(StringBuilder json, char c) {
        if (c < 0x20) {
            json.append(String.format("\\u%04x", (int) c));
        } else {
            json.append(c);
        }
    }
}
