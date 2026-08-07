package com.example.shared.permission;

import com.example.auth.api.dto.DataScope;
import com.example.shared.exception.BusinessException;
import com.example.shared.permission.errorcode.PermissionErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DataScopeContext ThreadLocal 行为测试")
class DataScopeContextTest {

    @AfterEach
    void clearContext() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("未设置时 get() 返回 empty()")
    void getReturnsEmptyWhenNotSet() {
        DataScope scope = DataScopeContext.get();
        assertThat(scope.needsFiltering()).isTrue();
        assertThat(scope.visiblePlans()).isEmpty();
    }

    @Test
    @DisplayName("set() 后 get() 返回设置的对象")
    void setAndGetReturnsSameScope() {
        DataScope expected = new DataScope(false, Set.of("P001"), Set.of(), Set.of(), Set.of());
        DataScopeContext.set(expected);
        assertThat(DataScopeContext.get()).isEqualTo(expected);
    }

    @Test
    @DisplayName("clear() 后 get() 返回 empty()")
    void clearResetsToEmpty() {
        DataScopeContext.set(DataScope.global());
        DataScopeContext.clear();
        assertThat(DataScopeContext.get().needsFiltering()).isTrue();
    }

    @Test
    @DisplayName("未设置时 require() 抛 BusinessException(SESSION_CONTEXT_MISSING)")
    void requireThrowsWhenNotSet() {
        assertThatThrownBy(DataScopeContext::require)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(PermissionErrorCode.SESSION_CONTEXT_MISSING.getMessage());
    }

    @Test
    @DisplayName("设置后 require() 返回设置的对象")
    void requireReturnsScopeWhenSet() {
        DataScope expected = DataScope.global();
        DataScopeContext.set(expected);
        assertThat(DataScopeContext.require()).isEqualTo(expected);
    }
}
