package com.example.shared.id.errorcode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IdErrorCode} 错误码契约测试。
 * <p>
 * 验证错误码符合 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式 SHARED.ID.XXXX</li>
 *   <li>码段 SHARED.ID.0001-SHARED.ID.0099（shared-id-starter）</li>
 *   <li>消息禁止 {} 占位符和方括号前缀</li>
 *   <li>message() 返回实际消息而非空串</li>
 *   <li>各枚举的 code 唯一（修复历史 99971 重复问题）</li>
 * </ul>
 *
 * @author Trae
 * @since 2026/07/24
 */
@DisplayName("IdErrorCode 错误码契约测试")
class IdErrorCodeTest {

  @ParameterizedTest(name = "{0} 的 code 必须匹配层级字符串格式 SHARED.ID.XXXX")
  @EnumSource(IdErrorCode.class)
  @DisplayName("所有错误码必须匹配层级字符串格式 SHARED.ID.XXXX")
  void codeShouldBeFiveDigits(IdErrorCode error) {
    assertThat(error.getCode())
      .as("%s 的 code 必须匹配层级字符串格式 SHARED.ID.XXXX", error.name())
      .matches("^SHARED\\.ID\\.\\d{4}$");
  }

  @ParameterizedTest(name = "{0} 的 code 应以 SHARED.ID. 为前缀")
  @EnumSource(IdErrorCode.class)
  @DisplayName("所有 code 应落在 shared-id-starter 码段 SHARED.ID.XXXX")
  void codeShouldBeInIdSegment(IdErrorCode error) {
    assertThat(error.getCode())
      .as("%s 的 code 应以 SHARED.ID. 为前缀", error.name())
      .startsWith("SHARED.ID.");
  }

  @ParameterizedTest(name = "{0} 的 message 禁止使用占位符和方括号前缀")
  @EnumSource(IdErrorCode.class)
  @DisplayName("所有消息禁止使用 {} 占位符和方括号前缀")
  void messageShouldNotContainPlaceholderOrBracket(IdErrorCode error) {
    assertThat(error.getMessage())
      .as("%s 的 message 禁止包含 {} 占位符", error.name())
      .doesNotContain("{}");
    assertThat(error.getMessage())
      .as("%s 的 message 禁止以方括号开头", error.name())
      .doesNotStartWith("[");
  }

  @ParameterizedTest(name = "{0} 的 message 不应为空串")
  @EnumSource(IdErrorCode.class)
  @DisplayName("所有错误码的 message 都不应为空串")
  void messageShouldNotBeEmpty(IdErrorCode error) {
    assertThat(error.getMessage())
      .as("%s 的 message 不应为空", error.name())
      .isNotBlank();
  }

  @Test
  @DisplayName("各枚举的 code 应唯一（修复历史 99971 重复问题）")
  void codeShouldBeUnique() {
    long distinctCount = Arrays.stream(IdErrorCode.values())
      .map(IdErrorCode::getCode)
      .distinct()
      .count();
    assertThat(distinctCount)
      .as("所有 IdErrorCode 的 code 必须唯一，历史版本存在 99971 重复问题")
      .isEqualTo(IdErrorCode.values().length);
  }

  @Test
  @DisplayName("ID_GEN_ERROR 码值应为 SHARED.ID.0001")
  void idGenErrorShouldHaveCorrectCode() {
    assertThat(IdErrorCode.ID_GEN_ERROR.getCode()).isEqualTo("SHARED.ID.0001");
    assertThat(IdErrorCode.ID_GEN_ERROR.getMessage()).isEqualTo("ID生成异常");
  }
}
