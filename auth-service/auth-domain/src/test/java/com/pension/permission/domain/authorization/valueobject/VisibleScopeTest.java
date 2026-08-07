package com.pension.permission.domain.authorization.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VisibleScope 值对象测试")
class VisibleScopeTest {

    @Test
    @DisplayName("empty() 返回非全局可见且空集合")
    void emptyReturnsNonGlobalWithEmptySets() {
        VisibleScope scope = VisibleScope.empty();
        assertThat(scope.globalVisible()).isFalse();
        assertThat(scope.visiblePlans()).isEmpty();
    }

    @Test
    @DisplayName("global() 返回全局可见")
    void globalReturnsGlobalVisible() {
        assertThat(VisibleScope.global().globalVisible()).isTrue();
    }

    @Test
    @DisplayName("null 集合被防御性拷贝为空集合")
    void nullSetsAreDefensivelyCopied() {
        VisibleScope scope = new VisibleScope(false, null, null, null, null);
        assertThat(scope.visiblePlans()).isEmpty();
        assertThat(scope.visibleCustomers()).isEmpty();
        assertThat(scope.excludedPlans()).isEmpty();
        assertThat(scope.excludedCustomers()).isEmpty();
    }

    @Test
    @DisplayName("集合不可变")
    void setsAreUnmodifiable() {
        VisibleScope scope = new VisibleScope(false, Set.of("P001"), Set.of(), Set.of(), Set.of());
        assertThatThrownBy(() -> scope.visiblePlans().add("P002"));
    }

    private static void assertThatThrownBy(java.lang.Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // pass
        }
    }
}
