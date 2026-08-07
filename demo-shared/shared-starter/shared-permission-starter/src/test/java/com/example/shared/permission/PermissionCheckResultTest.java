package com.example.shared.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PermissionCheckResult 工厂方法测试")
class PermissionCheckResultTest {

    @Test
    @DisplayName("allow() 返回 allowed=true 且 reason=null")
    void allowReturnsAllowedTrueWithNullReason() {
        PermissionCheckResult result = PermissionCheckResult.allow();
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    @DisplayName("deny(reason) 返回 allowed=false 且 reason=指定值")
    void denyReturnsAllowedFalseWithReason() {
        PermissionCheckResult result = PermissionCheckResult.deny("权限不足");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo("权限不足");
    }
}
