package com.example.core.domain.business.errorcode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CoreDomainErrorCode} 错误码契约测试。
 * <p>
 * 验证错误码符合 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式 CORE.DOMAIN.XXXX</li>
 *   <li>码段 CORE.DOMAIN.0001-CORE.DOMAIN.0099</li>
 *   <li>消息禁止 {} 占位符和方括号前缀</li>
 *   <li>message() 返回实际消息而非空串</li>
 * </ul>
 *
 * @author Trae
 * @since 2026/07/24
 */
@DisplayName("CoreDomainErrorCode 错误码契约测试")
class CoreDomainErrorCodeTest {

  @Test
  @DisplayName("message() 应返回构造函数传入的消息，而非空串")
  void message_shouldReturnActualMessage_notEmptyString() {
    CoreDomainErrorCode errorCode = CoreDomainErrorCode.INVALID_STATUS;
    assertEquals("状态有误", errorCode.getMessage());
  }

  @Test
  @DisplayName("code() 应返回符合规范的层级字符串编码")
  void code_shouldReturnActualCode() {
    assertEquals("CORE.DOMAIN.0001", CoreDomainErrorCode.INVALID_STATUS.getCode());
    assertEquals("CORE.DOMAIN.0002", CoreDomainErrorCode.INVALID_DATA.getCode());
    assertEquals("CORE.DOMAIN.0003", CoreDomainErrorCode.INVALID_OPERATION.getCode());
  }

  @ParameterizedTest(name = "{0} 的 code 必须匹配层级字符串格式 CORE.DOMAIN.XXXX")
  @EnumSource(CoreDomainErrorCode.class)
  @DisplayName("所有错误码必须匹配层级字符串格式 CORE.DOMAIN.XXXX")
  void codeShouldBeFiveDigits(CoreDomainErrorCode errorCode) {
    assertTrue(errorCode.getCode().matches("^CORE\\.DOMAIN\\.\\d{4}$"),
      "错误码 " + errorCode.name() + " 必须匹配层级字符串格式 CORE.DOMAIN.XXXX");
  }

  @ParameterizedTest(name = "{0} 的 message 禁止使用占位符和方括号前缀")
  @EnumSource(CoreDomainErrorCode.class)
  @DisplayName("所有消息禁止使用 {} 占位符和方括号前缀")
  void messageShouldNotContainPlaceholderOrBracket(CoreDomainErrorCode errorCode) {
    String message = errorCode.getMessage();
    assertFalse(message.contains("{}"),
      "错误码 " + errorCode.name() + " 的消息禁止包含 {} 占位符");
    assertFalse(message.startsWith("["),
      "错误码 " + errorCode.name() + " 的消息禁止以方括号开头");
  }

  @ParameterizedTest(name = "{0} 的 message 不应为空串")
  @EnumSource(CoreDomainErrorCode.class)
  @DisplayName("所有错误码的 message 都不应为空串")
  void allErrorCodes_messageShouldNotBeEmpty(CoreDomainErrorCode errorCode) {
    assertFalse(errorCode.getMessage().isEmpty(),
      "错误码 " + errorCode.name() + " 的消息不应为空串");
  }
}
