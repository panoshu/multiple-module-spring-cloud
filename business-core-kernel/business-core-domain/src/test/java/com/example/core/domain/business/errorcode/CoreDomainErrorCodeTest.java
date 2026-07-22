package com.example.core.domain.business.errorcode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CoreDomainErrorCode 错误码测试")
class CoreDomainErrorCodeTest {

    @Test
    @DisplayName("message() 应返回构造函数传入的消息，而非空串")
    void message_shouldReturnActualMessage_notEmptyString() {
        // given
        CoreDomainErrorCode errorCode = CoreDomainErrorCode.INVALID_STATUS;

        // when
        String message = errorCode.message();

        // then
        assertEquals("[状态有误]{}", message);
        assertFalse(message.isEmpty(), "错误码消息不应为空");
    }

    @Test
    @DisplayName("code() 应返回构造函数传入的编码")
    void code_shouldReturnActualCode() {
        assertEquals("200001", CoreDomainErrorCode.INVALID_STATUS.code());
        assertEquals("200002", CoreDomainErrorCode.INVALID_DATA.code());
        assertEquals("200003", CoreDomainErrorCode.INVALID_OPERATION.code());
    }

    @Test
    @DisplayName("所有错误码的 message 都不应为空串")
    void allErrorCodes_messageShouldNotBeEmpty() {
        for (CoreDomainErrorCode errorCode : CoreDomainErrorCode.values()) {
            assertFalse(errorCode.message().isEmpty(),
                "错误码 " + errorCode.name() + " 的消息不应为空串");
        }
    }
}
