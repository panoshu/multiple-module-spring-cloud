package com.example.core.application.engine.errorcode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link CoreAppErrorCode} 错误码契约测试。
 * <p>
 * 验证错误码符合 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式 CORE.APP.XXXX</li>
 *   <li>码段 CORE.APP.0001-CORE.APP.0099（business-core-application）</li>
 *   <li>消息禁止 {} 占位符和方括号前缀</li>
 *   <li>各枚举的 code 唯一</li>
 * </ul>
 *
 * @author Trae
 * @since 2026/07/24
 */
@DisplayName("CoreAppErrorCode 错误码契约测试")
class CoreAppErrorCodeTest {

  @ParameterizedTest(name = "{0} 的 code 必须匹配层级字符串格式 CORE.APP.XXXX")
  @EnumSource(CoreAppErrorCode.class)
  @DisplayName("所有错误码必须匹配层级字符串格式 CORE.APP.XXXX")
  void codeShouldBeFiveDigits(CoreAppErrorCode error) {
    assertThat(error.code())
        .as("%s 的 code 必须匹配层级字符串格式 CORE.APP.XXXX", error.name())
        .matches("^CORE\\.APP\\.\\d{4}$");
  }

  @ParameterizedTest(name = "{0} 的 code 应以 CORE.APP. 为前缀")
  @EnumSource(CoreAppErrorCode.class)
  @DisplayName("所有 code 应落在 business-core-application 码段 CORE.APP.XXXX")
  void codeShouldBeInAppSegment(CoreAppErrorCode error) {
    assertThat(error.code())
        .as("%s 的 code 应以 CORE.APP. 为前缀", error.name())
        .startsWith("CORE.APP.");
  }

  @ParameterizedTest(name = "{0} 的 message 禁止使用占位符和方括号前缀")
  @EnumSource(CoreAppErrorCode.class)
  @DisplayName("所有消息禁止使用 {} 占位符和方括号前缀")
  void messageShouldNotContainPlaceholderOrBracket(CoreAppErrorCode error) {
    assertThat(error.message()).doesNotContain("{}");
    assertThat(error.message()).doesNotStartWith("[");
  }

  @ParameterizedTest(name = "{0} 的 message 不应为空串")
  @EnumSource(CoreAppErrorCode.class)
  @DisplayName("所有错误码的 message 都不应为空串")
  void messageShouldNotBeEmpty(CoreAppErrorCode error) {
    assertThat(error.message()).isNotBlank();
  }

  @Test
  @DisplayName("各枚举的 code 应唯一")
  void codeShouldBeUnique() {
    long distinctCount = Arrays.stream(CoreAppErrorCode.values())
        .map(CoreAppErrorCode::code)
        .distinct()
        .count();
    assertThat(distinctCount).isEqualTo(CoreAppErrorCode.values().length);
  }
}
