package com.pension.permission.application.authorization;

import com.example.auth.api.dto.DataScope;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.service.EffectivePermissionService;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.VisibleScope;
import com.pension.permission.domain.permission.repository.PermissionItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionQueryService.resolveDataScope 测试")
class PermissionQueryServiceResolveDataScopeTest {

    @Mock private EffectivePermissionService effectivePermissionService;
    @Mock private PermissionItemRepository permissionItemRepository;

    private PermissionQueryService service;

    @BeforeEach
    void setUp() {
        service = new PermissionQueryService(effectivePermissionService, permissionItemRepository);
    }

    @Test
    @DisplayName("GLOBAL 范围授权时返回 DataScope.global()")
    void returnsGlobalWhenGlobalScope() {
        when(effectivePermissionService.resolveVisibleScope(any(), any(), any()))
            .thenReturn(VisibleScope.global());

        DataScope scope = service.resolveDataScope(new ResolveDataScopeQuery(
            UserNo.of("user-001"), new BusinessCode("ANNUITY")));

        assertThat(scope.globalVisible()).isTrue();
        assertThat(scope.needsFiltering()).isFalse();
    }

    @Test
    @DisplayName("无授权时返回 empty()")
    void returnsEmptyWhenNoGrants() {
        when(effectivePermissionService.resolveVisibleScope(any(), any(), any()))
            .thenReturn(VisibleScope.empty());

        DataScope scope = service.resolveDataScope(new ResolveDataScopeQuery(
            UserNo.of("user-001"), new BusinessCode("ANNUITY")));

        assertThat(scope.globalVisible()).isFalse();
        assertThat(scope.visiblePlans()).isEmpty();
        assertThat(scope.needsFiltering()).isTrue();
    }

    @Test
    @DisplayName("PLAN 维度授权时返回可见 plans 集合")
    void returnsVisiblePlansWhenPlanScope() {
        when(effectivePermissionService.resolveVisibleScope(any(), any(), any()))
            .thenReturn(new VisibleScope(false, Set.of("P001", "P002"), Set.of(), Set.of(), Set.of()));

        DataScope scope = service.resolveDataScope(new ResolveDataScopeQuery(
            UserNo.of("user-001"), new BusinessCode("ANNUITY")));

        assertThat(scope.visiblePlans()).containsExactlyInAnyOrder("P001", "P002");
    }

    @Test
    @DisplayName("PLAN 维度 ALLOW + DENY 时 DENY 被排除")
    void deniedPlansAreExcluded() {
        when(effectivePermissionService.resolveVisibleScope(any(), any(), any()))
            .thenReturn(new VisibleScope(false, Set.of("P001"), Set.of(), Set.of("P001"), Set.of()));

        DataScope scope = service.resolveDataScope(new ResolveDataScopeQuery(
            UserNo.of("user-001"), new BusinessCode("ANNUITY")));

        assertThat(scope.visiblePlans()).isEmpty();
    }
}
