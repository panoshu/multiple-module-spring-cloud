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
 *   <li>5 位纯数字</li>
 *   <li>码段 20001-20099</li>
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
        assertEquals("状态有误", errorCode.message());
    }

    @Test
    @DisplayName("code() 应返回符合规范的 5 位数字编码")
    void code_shouldReturnActualCode() {
        assertEquals("20001", CoreDomainErrorCode.INVALID_STATUS.code());
        assertEquals("20002", CoreDomainErrorCode.INVALID_DATA.code());
        assertEquals("20003", CoreDomainErrorCode.INVALID_OPERATION.code());
    }

    @ParameterizedTest(name = "{0} 的 code 必须为 5 位纯数字")
    @EnumSource(CoreDomainErrorCode.class)
    @DisplayName("所有错误码必须为 5 位纯数字")
    void codeShouldBeFiveDigits(CoreDomainErrorCode errorCode) {
        assertTrue(errorCode.code().matches("^\\d{5}$"),
            "错误码 " + errorCode.name() + " 必须为 5 位纯数字");
    }

    @ParameterizedTest(name = "{0} 的 message 禁止使用占位符和方括号前缀")
    @EnumSource(CoreDomainErrorCode.class)
    @DisplayName("所有消息禁止使用 {} 占位符和方括号前缀")
    void messageShouldNotContainPlaceholderOrBracket(CoreDomainErrorCode errorCode) {
        String message = errorCode.message();
        assertFalse(message.contains("{}"),
            "错误码 " + errorCode.name() + " 的消息禁止包含 {} 占位符");
        assertFalse(message.startsWith("["),
            "错误码 " + errorCode.name() + " 的消息禁止以方括号开头");
    }

    @ParameterizedTest(name = "{0} 的 message 不应为空串")
    @EnumSource(CoreDomainErrorCode.class)
    @DisplayName("所有错误码的 message 都不应为空串")
    void allErrorCodes_messageShouldNotBeEmpty(CoreDomainErrorCode errorCode) {
        assertFalse(errorCode.message().isEmpty(),
            "错误码 " + errorCode.name() + " 的消息不应为空串");
    }
}
