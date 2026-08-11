package com.afkgame.env.logging;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * 層をまたぐ呼び出し（Web ↔ Domain・Domain ↔ Repository）の START / END を出す。
 *
 * <p>仕様: docs/process/coding_standards_backend/logging/application.md §3・§3.1。
 * 適用は {@code AspectJExpressionPointcutAdvisor} を境界ごとに1本ずつ Bean 定義して行う
 * （本クラス自体はポイントカットを持たない。{@code AfkgameDomainConfig} を参照）。
 *
 * <p>引数・戻り値のマスクは本クラスが完結させる（呼び出し側に書かせない）。
 * パラメータ名が機密名（固定表）に一致する値・{@link MaskReturnValue} を付けたメソッドの戻り値は
 * {@code ****} に、コレクション・配列・{@link Map} は件数のみ、1値200文字で打ち切る。
 */
public class LayerLoggingInterceptor implements MethodInterceptor {

    private static final AppLogger logger = AppLogger.of(LoggerName.LAYER);

    /** 名前一致で伏せるパラメータ名（application.md §3.1 規約1の固定表）。 */
    private static final Set<String> MASKED_PARAM_NAMES = Set.of(
            "password", "rawPassword", "newPassword", "passwordHash", "token", "accessToken",
            "refreshToken", "googleAuthCode", "secret", "credential");

    private static final String EMAIL_PARAM_NAME = "email";

    private static final String FULL_MASK = "****";

    private static final int MAX_FIELD_LENGTH = 200;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        String signature = signature(invocation, method);

        logger.info("START")
                .with(LogKey.SIGNATURE, signature)
                .with(LogKey.ARGS, formatArgs(method, invocation.getArguments()))
                .log();

        long startedAt = System.nanoTime();
        try {
            Object result = invocation.proceed();
            logger.info("END")
                    .with(LogKey.SIGNATURE, signature)
                    .with(LogKey.RESULT, formatResult(method, result))
                    .with(LogKey.DURATION_MS, elapsedMs(startedAt))
                    .log();
            return result;
        } catch (Throwable t) {
            logger.info("END")
                    .with(LogKey.SIGNATURE, signature)
                    .reason(LogReason.EXCEPTION)
                    .with(LogKey.DURATION_MS, elapsedMs(startedAt))
                    .log();
            throw t;
        }
    }

    /** MyBatis の Mapper プロキシは実クラス名を持たないため、その場合だけ宣言インタフェース名を使う。 */
    private static String signature(MethodInvocation invocation, Method method) {
        Class<?> targetClass = invocation.getThis().getClass();
        String className = Proxy.isProxyClass(targetClass)
                ? method.getDeclaringClass().getSimpleName()
                : targetClass.getSimpleName();
        return className + "#" + method.getName();
    }

    private static String formatArgs(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        return "[" + IntStream.range(0, args.length)
                .mapToObj(i -> formatArg(parameters[i].getName(), args[i]))
                .collect(Collectors.joining(", ")) + "]";
    }

    private static String formatArg(String paramName, Object value) {
        if (MASKED_PARAM_NAMES.contains(paramName)) {
            return FULL_MASK;
        }
        if (EMAIL_PARAM_NAME.equals(paramName) && value instanceof String email) {
            return LogKey.EMAIL.masked(email);
        }
        return formatValue(value);
    }

    private static String formatResult(Method method, Object result) {
        if (method.getReturnType() == void.class) {
            return null;
        }
        if (isMaskedReturn(method)) {
            return FULL_MASK;
        }
        return formatValue(result);
    }

    /** CGLIB では実装メソッドが渡るため、インタフェース側の宣言も探す（注釈は実装へ継承されない）。 */
    private static boolean isMaskedReturn(Method method) {
        if (method.isAnnotationPresent(MaskReturnValue.class)) {
            return true;
        }
        for (Class<?> type : method.getDeclaringClass().getInterfaces()) {
            try {
                if (type.getMethod(method.getName(), method.getParameterTypes())
                        .isAnnotationPresent(MaskReturnValue.class)) {
                    return true;
                }
            } catch (NoSuchMethodException e) {
                // このインタフェースは同じシグネチャを宣言していない
            }
        }
        return false;
    }

    /** コレクション・配列・{@link Map} は件数のみ、それ以外は200文字で打ち切る（{@link Optional} は中身へ適用）。 */
    private static String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Optional<?> optional) {
            return optional.isPresent() ? formatValue(optional.get()) : "Optional.empty";
        }
        if (value instanceof Collection<?> collection) {
            return collection.getClass().getSimpleName() + "(size=" + collection.size() + ")";
        }
        if (value instanceof Map<?, ?> map) {
            return map.getClass().getSimpleName() + "(size=" + map.size() + ")";
        }
        if (value.getClass().isArray()) {
            return value.getClass().getSimpleName() + "(size=" + java.lang.reflect.Array.getLength(value) + ")";
        }
        return truncate(String.valueOf(value));
    }

    private static String truncate(String value) {
        return value.length() <= MAX_FIELD_LENGTH ? value : value.substring(0, MAX_FIELD_LENGTH) + "...";
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
