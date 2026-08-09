package com.afkgame.env.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * {@link LayerLoggingInterceptor} の単体テスト。
 *
 * <p>仕様: docs/process/coding_standards_backend/logging/application.md §3・§3.1。
 * START / END の対出力、例外時の {@code reason=exception}、引数・戻り値のマスクと整形
 * （名前一致のマスク・{@link MaskReturnValue}・コレクション類の件数表示・200文字打ち切り・
 * {@link Optional} の展開）を検証する。
 *
 * <p>骨格構築の横断基盤であり詳細設計の分岐一覧を持たないため、分岐マーカーは付けない。
 */
@Tag("unit")
class LayerLoggingInterceptorTest {

    /** テスト対象のインタフェース。実クラス名 / インタフェース名の出し分けを両方検証するため用意する。 */
    interface Sample {

        Object identity(Object value);

        void noop();

        String password(String password);

        String email(String email);

        int emailWrongType(int email);

        @MaskReturnValue
        String secret();

        String boom();

        /** 固定表8語（application.md §3.1 規約1）をパラメータ名で1度に通すためのメソッド。 */
        String maskedNames(String password, String rawPassword, String newPassword, String token,
                String accessToken, String refreshToken, String secret, String credential);
    }

    static class SampleImpl implements Sample {

        @Override
        public Object identity(Object value) {
            return value;
        }

        @Override
        public void noop() {
        }

        @Override
        public String password(String password) {
            return "ignored";
        }

        @Override
        public String email(String email) {
            return "ignored";
        }

        @Override
        public int emailWrongType(int email) {
            return email;
        }

        @Override
        public String secret() {
            return "raw-secret-value";
        }

        @Override
        public String boom() {
            throw new IllegalStateException("失敗");
        }

        @Override
        public String maskedNames(String password, String rawPassword, String newPassword, String token,
                String accessToken, String refreshToken, String secret, String credential) {
            return "ignored";
        }

        /** {@link Sample} が宣言していない public メソッド（インタフェース探索の空振り経路用）。 */
        public String implOnly() {
            return "impl-only";
        }
    }

    private final LayerLoggingInterceptor interceptor = new LayerLoggingInterceptor();

    /** ログ出力そのものを検証するための受け皿（coding_standards_backend/test.md §5「新規実装から適用する」1）。 */
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    private ch.qos.logback.classic.Logger layerLogger;

    @BeforeEach
    void setUp() {
        logs.start();
        layerLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(LoggerName.LAYER.loggerName());
        layerLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        layerLogger.detachAppender(logs);
        logs.stop();
    }

    private Sample proxy(Object target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        return (Sample) factory.getProxy();
    }

    /** MyBatis Mapper と同じ JDK 動的プロキシを模す（実クラス名を持たない）。 */
    private Sample mapperLikeTarget() {
        SampleImpl delegate = new SampleImpl();
        return (Sample) Proxy.newProxyInstance(
                Sample.class.getClassLoader(),
                new Class<?>[] {Sample.class},
                (target, method, args) -> method.invoke(delegate, args));
    }

    /**
     * 実装クラスの {@link Method} をそのまま渡す呼び出しを組み立てる。
     *
     * <p>CGLIB プロキシは実装メソッドを {@code getMethod()} で渡すため、その状況を
     * プロキシ方式に依存せず再現する（{@link ProxyFactory} は既定で JDK 動的プロキシを選ぶ）。
     */
    private static MethodInvocation invocationOf(Object target, Method method, Object... args) {
        return new MethodInvocation() {

            @Override
            public Method getMethod() {
                return method;
            }

            @Override
            public Object[] getArguments() {
                return args;
            }

            @Override
            public Object proceed() throws Throwable {
                return method.invoke(target, args);
            }

            @Override
            public Object getThis() {
                return target;
            }

            @Override
            public AccessibleObject getStaticPart() {
                return method;
            }
        };
    }

    @Nested
    @DisplayName("START / END の対出力")
    class TestStartEnd {

        @Test
        void test_正常終了でSTART_ENDを対で出す() {
            proxy(new SampleImpl()).identity("x");

            assertThat(logs.list).hasSize(2);
            assertThat(logs.list.get(0).getFormattedMessage()).isEqualTo("START");
            assertThat(logs.list.get(1).getFormattedMessage()).isEqualTo("END");
            assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.INFO);
            assertThat(logs.list.get(0).getMDCPropertyMap())
                    .containsEntry("signature", "SampleImpl#identity");
            assertThat(logs.list.get(1).getMDCPropertyMap()).containsKeys("duration_ms");
        }

        @Test
        void test_実クラスを持つ対象はクラス名で組み立てる() {
            proxy(new SampleImpl()).identity("x");

            assertThat(logs.list.get(0).getMDCPropertyMap())
                    .containsEntry("signature", "SampleImpl#identity");
        }

        @Test
        void test_MyBatisのMapperプロキシはインタフェース名で組み立てる() {
            proxy(mapperLikeTarget()).identity("x");

            assertThat(logs.list.get(0).getMDCPropertyMap())
                    .containsEntry("signature", "Sample#identity");
        }

        @Test
        void test_例外で抜けてもENDを出しreasonにexceptionを積む() {
            Sample sample = proxy(new SampleImpl());

            assertThatThrownBy(sample::boom).isInstanceOf(IllegalStateException.class);

            assertThat(logs.list).hasSize(2);
            assertThat(logs.list.get(1).getFormattedMessage()).isEqualTo("END");
            assertThat(logs.list.get(1).getMDCPropertyMap()).containsEntry("reason", "exception");
        }

        @Test
        void test_voidの戻り値はresultを出さない() {
            proxy(new SampleImpl()).noop();

            assertThat(logs.list.get(1).getMDCPropertyMap()).doesNotContainKey("result");
        }
    }

    @Nested
    @DisplayName("引数・戻り値のマスク")
    class TestMasking {

        @Test
        void test_パラメータ名が機密名に一致したら伏せる() {
            proxy(new SampleImpl()).password("hunter2");

            assertThat(logs.list.get(0).getMDCPropertyMap()).containsEntry("args", "[****]");
        }

        @Test
        void test_emailという名前の文字列引数はメールマスクを適用する() {
            proxy(new SampleImpl()).email("abcdef@example.com");

            assertThat(logs.list.get(0).getMDCPropertyMap()).containsEntry("args", "[ab***@example.com]");
        }

        @Test
        void test_emailという名前でも文字列でなければそのまま出す() {
            proxy(new SampleImpl()).emailWrongType(42);

            assertThat(logs.list.get(0).getMDCPropertyMap()).containsEntry("args", "[42]");
        }

        @Test
        void test_固定表の機密名はすべて伏せる() {
            proxy(new SampleImpl()).maskedNames("p", "rp", "np", "t", "at", "rt", "s", "c");

            assertThat(logs.list.get(0).getMDCPropertyMap())
                    .containsEntry("args", "[****, ****, ****, ****, ****, ****, ****, ****]");
        }

        @Test
        void test_MaskReturnValueを付けた戻り値は伏せる() {
            proxy(new SampleImpl()).secret();

            assertThat(logs.list.get(1).getMDCPropertyMap()).containsEntry("result", "****");
        }

        @Test
        void test_実装メソッドが渡ってもインタフェース側のMaskReturnValueで伏せる() throws Throwable {
            interceptor.invoke(invocationOf(new SampleImpl(), SampleImpl.class.getMethod("secret")));

            assertThat(logs.list.get(1).getMDCPropertyMap()).containsEntry("result", "****");
        }

        @Test
        void test_インタフェース側にも注釈が無ければ実装メソッドでも伏せない() throws Throwable {
            interceptor.invoke(invocationOf(new SampleImpl(),
                    SampleImpl.class.getMethod("identity", Object.class), "x"));

            assertThat(logs.list.get(1).getMDCPropertyMap()).containsEntry("result", "x");
        }

        @Test
        void test_インタフェースが同じシグネチャを宣言していなければ伏せない() throws Throwable {
            interceptor.invoke(invocationOf(new SampleImpl(), SampleImpl.class.getMethod("implOnly")));

            assertThat(logs.list.get(1).getMDCPropertyMap()).containsEntry("result", "impl-only");
        }
    }

    @Nested
    @DisplayName("値の整形")
    class TestFormatting {

        @Test
        void test_nullはnullという文字列にする() {
            proxy(new SampleImpl()).identity(null);

            assertThat(logs.list.get(0).getMDCPropertyMap()).containsEntry("args", "[null]");
        }

        @Test
        void test_コレクションは件数だけ出す() {
            proxy(new SampleImpl()).identity(List.of("a", "b", "c"));

            assertThat(logs.list.get(1).getMDCPropertyMap().get("result")).contains("size=3");
        }

        @Test
        void test_Mapは件数だけ出す() {
            proxy(new SampleImpl()).identity(Map.of("k", "v"));

            assertThat(logs.list.get(1).getMDCPropertyMap().get("result")).contains("size=1");
        }

        @Test
        void test_配列は件数だけ出す() {
            proxy(new SampleImpl()).identity(new String[] {"a", "b"});

            assertThat(logs.list.get(1).getMDCPropertyMap().get("result")).contains("size=2");
        }

        @Test
        void test_Optionalは中身へ規則を適用する() {
            proxy(new SampleImpl()).identity(Optional.of("abcdef@example.com"));

            assertThat(logs.list.get(1).getMDCPropertyMap()).containsEntry("result", "abcdef@example.com");
        }

        @Test
        void test_空のOptionalはOptional_emptyにする() {
            proxy(new SampleImpl()).identity(Optional.empty());

            assertThat(logs.list.get(1).getMDCPropertyMap()).containsEntry("result", "Optional.empty");
        }

        @Test
        void test_200文字を超える値は末尾を打ち切る() {
            String longValue = "a".repeat(250);

            proxy(new SampleImpl()).identity(longValue);

            String result = logs.list.get(1).getMDCPropertyMap().get("result");
            assertThat(result).hasSize(203).endsWith("...");
        }

        @Test
        void test_200文字以下の値は打ち切らない() {
            String shortValue = "a".repeat(10);

            proxy(new SampleImpl()).identity(shortValue);

            assertThat(logs.list.get(1).getMDCPropertyMap()).containsEntry("result", shortValue);
        }
    }
}
