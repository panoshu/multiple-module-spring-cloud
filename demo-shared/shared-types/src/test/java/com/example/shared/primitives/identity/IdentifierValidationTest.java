package com.example.shared.primitives.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 所有 {@link Identifier} 实现的校验契约测试。
 * <p>
 * 验证每个 ID 类型（record）的紧凑构造函数对 null/blank value 的防御性校验，
 * 以及工厂方法 {@code of()} 的行为一致性。
 * <p>
 * 标杆实现：{@link CustomerNo}、{@link UserNo}（已有完整校验和 of() 方法）。
 *
 * @author panoshu
 * @since 2026/7/24
 */
@DisplayName("Identifier 校验契约测试")
class IdentifierValidationTest {

    /**
     * 所有 ID 类型的构造函数引用。
     * 用于参数化测试，确保每个 ID 都遵守相同的校验契约。
     */
    static Stream<Arguments> allIdConstructors() {
        return Stream.of(
            Arguments.of("AcceptanceNo", (Function<String, Object>) AcceptanceNo::new),
            Arguments.of("ApplicationId", (Function<String, Object>) ApplicationId::new),
            Arguments.of("BatchId", (Function<String, Object>) BatchId::new),
            Arguments.of("CustomerNo", (Function<String, Object>) CustomerNo::new),
            Arguments.of("EventId", (Function<String, Object>) EventId::new),
            Arguments.of("FileAccessLogId", (Function<String, Object>) FileAccessLogId::new),
            Arguments.of("FileId", (Function<String, Object>) FileId::new),
            Arguments.of("FormId", (Function<String, Object>) FormId::new),
            Arguments.of("PlanNo", (Function<String, Object>) PlanNo::new),
            Arguments.of("ProductNo", (Function<String, Object>) ProductNo::new),
            Arguments.of("SerialNo", (Function<String, Object>) SerialNo::new),
            Arguments.of("TaskId", (Function<String, Object>) TaskId::new),
            Arguments.of("UserNo", (Function<String, Object>) UserNo::new)
        );
    }

    @ParameterizedTest(name = "{0}: null value 应抛 IllegalArgumentException")
    @MethodSource("allIdConstructors")
    void should_throw_when_value_is_null(String name, Function<String, Object> constructor) {
        assertThatThrownBy(() -> constructor.apply(null))
            .as("%s 的 value 为 null 时必须抛出 IllegalArgumentException", name)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}: 空字符串应抛 IllegalArgumentException")
    @MethodSource("allIdConstructors")
    void should_throw_when_value_is_empty(String name, Function<String, Object> constructor) {
        assertThatThrownBy(() -> constructor.apply(""))
            .as("%s 的 value 为空字符串时必须抛出 IllegalArgumentException", name)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}: 纯空白字符串应抛 IllegalArgumentException")
    @MethodSource("allIdConstructors")
    void should_throw_when_value_is_blank(String name, Function<String, Object> constructor) {
        assertThatThrownBy(() -> constructor.apply("   "))
            .as("%s 的 value 为纯空白时必须抛出 IllegalArgumentException", name)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0}: 合法 value 应成功构造")
    @MethodSource("allIdConstructors")
    void should_construct_with_valid_value(String name, Function<String, Object> constructor) {
        Object id = constructor.apply("valid-id-" + name);

        assertThat(id).isNotNull();
    }

    // ==================== 错误消息正确性验证 ====================

    @Test
    @DisplayName("AcceptanceNo 的错误消息应包含自身名称而非 CustomerNo")
    void acceptanceNo_error_message_should_contain_own_name() {
        assertThatThrownBy(() -> new AcceptanceNo(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("AcceptanceNo");
    }

    @Test
    @DisplayName("PlanNo 的错误消息应包含自身名称而非 CustomerNo")
    void planNo_error_message_should_contain_own_name() {
        assertThatThrownBy(() -> new PlanNo(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PlanNo");
    }

    @Test
    @DisplayName("ProductNo 的错误消息应包含自身名称而非 CustomerNo")
    void productNo_error_message_should_contain_own_name() {
        assertThatThrownBy(() -> new ProductNo(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ProductNo");
    }

    // ==================== EventId 特殊行为 ====================

    @Test
    @DisplayName("EventId.generate() 应返回非 null 且 value 非空")
    void eventId_generate_should_return_valid_id() {
        EventId eventId = EventId.generate();

        assertThat(eventId).isNotNull();
        assertThat(eventId.value()).isNotBlank();
    }
}
