package com.example.integration.infrastructure.errorcode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link TradeErrorCode} 错误码契约测试。
 * <p>
 * 验证错误码符合 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>5 位纯数字</li>
 *   <li>码段 32001-32099（integration-service）</li>
 *   <li>消息禁止 {} 占位符和方括号前缀</li>
 *   <li>各枚举的 code 唯一</li>
 * </ul>
 *
 * @author Trae
 * @since 2026/07/24
 */
@DisplayName("TradeErrorCode 错误码契约测试")
class TradeErrorCodeTest {

  @ParameterizedTest(name = "{0} 的 code 必须为 5 位纯数字")
  @EnumSource(TradeErrorCode.class)
  @DisplayName("所有错误码必须为 5 位纯数字")
  void codeShouldBeFiveDigits(TradeErrorCode error) {
    assertThat(error.code())
        .as("%s 的 code 必须为 5 位纯数字", error.name())
        .matches("\\d{5}");
  }

  @ParameterizedTest(name = "{0} 的 code 应在 32001-32099 码段")
  @EnumSource(TradeErrorCode.class)
  @DisplayName("所有 code 应落在 integration-service 码段 32001-32099")
  void codeShouldBeInIntegrationSegment(TradeErrorCode error) {
    int code = Integer.parseInt(error.code());
    assertThat(code)
        .as("%s 的 code 应在 32001-32099 区间", error.name())
        .isBetween(32001, 32099);
  }

  @ParameterizedTest(name = "{0} 的 message 禁止使用占位符和方括号前缀")
  @EnumSource(TradeErrorCode.class)
  @DisplayName("所有消息禁止使用 {} 占位符和方括号前缀")
  void messageShouldNotContainPlaceholderOrBracket(TradeErrorCode error) {
    assertThat(error.message()).doesNotContain("{}");
    assertThat(error.message()).doesNotStartWith("[");
  }

  @ParameterizedTest(name = "{0} 的 message 不应为空串")
  @EnumSource(TradeErrorCode.class)
  @DisplayName("所有错误码的 message 都不应为空串")
  void messageShouldNotBeEmpty(TradeErrorCode error) {
    assertThat(error.message()).isNotBlank();
  }

  @Test
  @DisplayName("各枚举的 code 应唯一")
  void codeShouldBeUnique() {
    long distinctCount = Arrays.stream(TradeErrorCode.values())
        .map(TradeErrorCode::code)
        .distinct()
        .count();
    assertThat(distinctCount).isEqualTo(TradeErrorCode.values().length);
  }
}
