package com.example.auth.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataScope 业务对象测试")
class DataScopeTest {

    @Test
    @DisplayName("empty() 返回非全局可见且空集合")
    void emptyReturnsNonGlobalWithEmptySets() {
        DataScope scope = DataScope.empty();
        assertThat(scope.globalVisible()).isFalse();
        assertThat(scope.visiblePlans()).isEmpty();
        assertThat(scope.visibleCustomers()).isEmpty();
        assertThat(scope.excludedPlans()).isEmpty();
        assertThat(scope.excludedCustomers()).isEmpty();
        assertThat(scope.needsFiltering()).isTrue();
    }

    @Test
    @DisplayName("global() 返回全局可见")
    void globalReturnsGlobalVisible() {
        DataScope scope = DataScope.global();
        assertThat(scope.globalVisible()).isTrue();
        assertThat(scope.needsFiltering()).isFalse();
    }

    @Test
    @DisplayName("null 集合被防御性拷贝为空集合")
    void nullSetsAreDefensivelyCopiedToEmpty() {
        DataScope scope = new DataScope(false, null, null, null, null);
        assertThat(scope.visiblePlans()).isEmpty();
        assertThat(scope.visibleCustomers()).isEmpty();
        assertThat(scope.excludedPlans()).isEmpty();
        assertThat(scope.excludedCustomers()).isEmpty();
    }

    @Test
    @DisplayName("传入集合被不可变化保护")
    void inputSetsAreUnmodifiable() {
        Set<String> plans = new java.util.HashSet<>(Set.of("P001"));
        DataScope scope = new DataScope(false, plans, Set.of(), Set.of(), Set.of());
        plans.add("P002");
        assertThat(scope.visiblePlans()).containsExactly("P001");
    }
}
