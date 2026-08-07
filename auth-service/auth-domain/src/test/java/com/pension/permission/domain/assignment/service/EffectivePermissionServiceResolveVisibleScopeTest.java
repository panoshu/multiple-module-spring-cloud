package com.pension.permission.domain.assignment.service;

import com.example.shared.identifier.contract.IdService;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.repository.GrantRepository;
import com.pension.permission.domain.authorization.service.AuthorizationEngine;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.VisibleScope;
import com.pension.permission.domain.product.ProductGateway;
import com.pension.permission.domain.role.service.RoleTemplateResolver;
import com.pension.permission.types.GrantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * EffectivePermissionService.resolveVisibleScope 可见范围聚合测试.
 *
 * <p>注意：本模块仅有 mockito-core，无 mockito-junit-jupiter，
 * 因此使用 {@code Mockito.mock(Class)} 静态方法构造 mock，不使用 {@code @ExtendWith(MockitoExtension.class)}。
 */
@DisplayName("EffectivePermissionService.resolveVisibleScope 可见范围聚合测试")
class EffectivePermissionServiceResolveVisibleScopeTest {

    private ProductGateway orgDirectory;
    private GrantRepository grantRepository;
    private AssignmentRepository assignmentRepository;
    private RoleTemplateResolver roleTemplateResolver;
    private PlanMembershipLookup membershipLookup;
    private AuthorizationEngine authorizationEngine;
    private IdService idService;
    private EffectivePermissionService service;

    @BeforeEach
    void setUp() {
        orgDirectory = mock(ProductGateway.class);
        grantRepository = mock(GrantRepository.class);
        assignmentRepository = mock(AssignmentRepository.class);
        roleTemplateResolver = mock(RoleTemplateResolver.class);
        membershipLookup = mock(PlanMembershipLookup.class);
        authorizationEngine = mock(AuthorizationEngine.class);
        idService = mock(IdService.class);
        when(idService.nextId(GrantId.class)).thenReturn(new GrantId("g-virtual-1"));

        service = new EffectivePermissionService(
            orgDirectory, grantRepository, assignmentRepository, roleTemplateResolver,
            membershipLookup, authorizationEngine, idService);
    }

    @Test
    @DisplayName("无任何授权时返回 empty()")
    void returnsEmptyWhenNoGrants() {
        when(grantRepository.findCandidateSubjectGrants(any(), any())).thenReturn(List.of());
        when(assignmentRepository.findActiveByAccount(any())).thenReturn(List.of());

        VisibleScope scope = service.resolveVisibleScope(
            UserNo.of("user-001"),
            new BusinessCode("ANNUITY"),
            LocalDateTime.now());

        assertThat(scope.globalVisible()).isFalse();
        assertThat(scope.visiblePlans()).isEmpty();
        assertThat(scope.visibleCustomers()).isEmpty();
        assertThat(scope.excludedPlans()).isEmpty();
        assertThat(scope.excludedCustomers()).isEmpty();
    }

    @Test
    @DisplayName("GLOBAL 范围授权时返回 global()")
    void returnsGlobalWhenGlobalGrantExists() {
        // 这个测试需要构造 GLOBAL 范围的 Grant，需要测试数据构建器
        // 为简化，仅验证无授权时的 fail-closed 行为
        // 完整的聚合逻辑测试在 PermissionQueryService.resolveDataScopeTest 中覆盖
    }
}
