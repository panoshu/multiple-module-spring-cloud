package com.example.integration.infrastructure.core.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TradeRootResponse 交易响应测试")
class TradeRootResponseTest {

    @Test
    @DisplayName("当 appResponse 为 null 时 isSuccess 不应抛出 NPE")
    void isSuccess_whenAppResponseNull_shouldNotThrowNPE() {
        // given
        TradeRootResponse<String> response = new TradeRootResponse<>(null);

        // when & then
        assertDoesNotThrow(() -> response.isSuccess());
        assertFalse(response.isSuccess());
    }

    @Test
    @DisplayName("当 statusInfo 为 null 时 getErrorCode 不应抛出 NPE")
    void getErrorCode_whenStatusInfoNull_shouldNotThrowNPE() {
        // given
        TradeRootResponse<String> response = new TradeRootResponse<>(null);

        // when & then
        assertDoesNotThrow(() -> response.getErrorCode());
        assertEquals("MISSING", response.getErrorCode());
    }

    @Test
    @DisplayName("当 statusInfo 为 null 时 getErrorMsg 不应抛出 NPE")
    void getErrorMsg_whenStatusInfoNull_shouldNotThrowNPE() {
        // given
        TradeRootResponse<String> response = new TradeRootResponse<>(null);

        // when & then
        assertDoesNotThrow(() -> response.getErrorMsg());
        assertEquals("Unknown error", response.getErrorMsg());
    }
}
