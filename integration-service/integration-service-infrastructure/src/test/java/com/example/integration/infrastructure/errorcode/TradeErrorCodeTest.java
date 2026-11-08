package com.example.integration.infrastructure.errorcode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TradeErrorCode} 错误码契约测试。
 * <p>
 * 验证错误码符合 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式 SERVICE.INTEGRATION.XXXX</li>
 *   <li>码段 SERVICE.INTEGRATION.0001-SERVICE.INTEGRATION.0099（integration-service）</li>
 *   <li>消息禁止 {} 占位符和方括号前缀</li>
 *   <li>各枚举的 code 唯一</li>
 * </ul>
 *
 * @author Trae
 * @since 2026/07/24
 */
@DisplayName("TradeErrorCode 错误码契约测试")
class TradeErrorCodeTest {

  @ParameterizedTest(name = "{0} 的 code 必须匹配层级字符串格式 SERVICE.INTEGRATION.XXXX")
  @EnumSource(TradeErrorCode.class)
  @DisplayName("所有错误码必须匹配层级字符串格式 SERVICE.INTEGRATION.XXXX")
  void codeShouldBeFiveDigits(TradeErrorCode error) {
    assertThat(error.getCode())
      .as("%s 的 code 必须匹配层级字符串格式 SERVICE.INTEGRATION.XXXX", error.name())
      .matches("^SERVICE\\.INTEGRATION\\.\\d{4}$");
  }

  @ParameterizedTest(name = "{0} 的 code 应以 SERVICE.INTEGRATION. 为前缀")
  @EnumSource(TradeErrorCode.class)
  @DisplayName("所有 code 应落在 integration-service 码段 SERVICE.INTEGRATION.XXXX")
  void codeShouldBeInIntegrationSegment(TradeErrorCode error) {
    assertThat(error.getCode())
      .as("%s 的 code 应以 SERVICE.INTEGRATION. 为前缀", error.name())
      .startsWith("SERVICE.INTEGRATION.");
  }

  @ParameterizedTest(name = "{0} 的 message 禁止使用占位符和方括号前缀")
  @EnumSource(TradeErrorCode.class)
  @DisplayName("所有消息禁止使用 {} 占位符和方括号前缀")
  void messageShouldNotContainPlaceholderOrBracket(TradeErrorCode error) {
    assertThat(error.getMessage()).doesNotContain("{}");
    assertThat(error.getMessage()).doesNotStartWith("[");
  }

  @ParameterizedTest(name = "{0} 的 message 不应为空串")
  @EnumSource(TradeErrorCode.class)
  @DisplayName("所有错误码的 message 都不应为空串")
  void messageShouldNotBeEmpty(TradeErrorCode error) {
    assertThat(error.getMessage()).isNotBlank();
  }

  @Test
  @DisplayName("各枚举的 code 应唯一")
  void codeShouldBeUnique() {
    long distinctCount = Arrays.stream(TradeErrorCode.values())
      .map(TradeErrorCode::getCode)
      .distinct()
      .count();
    assertThat(distinctCount).isEqualTo(TradeErrorCode.values().length);
  }
}
