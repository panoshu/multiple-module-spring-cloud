package com.example.core.types;

import com.example.shared.identifier.contract.Identifier;
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
 * business-core-types 中所有 {@link Identifier} 实现的校验契约测试。
 *
 * @author hupan
 * @since 2026/11/02
 */
@DisplayName("business-core-types Identifier 校验契约测试")
class CoreTypesIdentifierValidationTest {

  static Stream<Arguments> allIdConstructors() {
    return Stream.of(
      Arguments.of("ApplicationId", (Function<String, Object>) ApplicationId::new),
      Arguments.of("BatchId", (Function<String, Object>) BatchId::new),
      Arguments.of("FormId", (Function<String, Object>) FormId::new),
      Arguments.of("PlanNo", (Function<String, Object>) PlanNo::new),
      Arguments.of("ProductNo", (Function<String, Object>) ProductNo::new)
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
  @DisplayName("PlanNo 的错误消息应包含自身名称")
  void planNo_error_message_should_contain_own_name() {
    assertThatThrownBy(() -> new PlanNo(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("PlanNo");
  }

  @Test
  @DisplayName("ProductNo 的错误消息应包含自身名称")
  void productNo_error_message_should_contain_own_name() {
    assertThatThrownBy(() -> new ProductNo(null))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("ProductNo");
  }
}
