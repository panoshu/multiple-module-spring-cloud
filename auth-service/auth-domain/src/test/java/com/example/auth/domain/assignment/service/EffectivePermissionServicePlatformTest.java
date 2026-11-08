package com.example.auth.domain.assignment.service;

import com.example.shared.identifier.contract.IdService;
import com.example.core.types.UserNo;
import com.example.auth.domain.assignment.repository.AssignmentRepository;
import com.example.auth.domain.authorization.aggregate.Grant;
import com.example.auth.domain.authorization.repository.GrantRepository;
import com.example.auth.domain.authorization.service.AuthorizationEngine;
import com.example.auth.domain.authorization.spi.PlanMembershipLookup;
import com.example.auth.domain.authorization.valueobject.Permission;
import com.example.auth.domain.fixture.AuthorizationFixtures;
import com.example.auth.domain.product.ProductGateway;
import com.example.auth.domain.role.service.RoleTemplateResolver;
import com.example.auth.types.GrantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EffectivePermissionServicePlatformTest {

  private EffectivePermissionService service;
  private GrantRepository grantRepository;
  private AssignmentRepository assignmentRepository;

  @BeforeEach
  void setUp() {
    ProductGateway productGateway = AuthorizationFixtures.mockProductGateway();
    grantRepository = AuthorizationFixtures.mockGrantRepository();
    assignmentRepository = mock(AssignmentRepository.class);
    RoleTemplateResolver roleTemplateResolver = mock(RoleTemplateResolver.class);
    PlanMembershipLookup membershipLookup = AuthorizationFixtures.mockMembershipLookup();
    AuthorizationEngine engine = mock(AuthorizationEngine.class);
    IdService idService = mock(IdService.class);
    when(idService.nextId(GrantId.class)).thenReturn(new GrantId("g-virtual-1"));

    service = new EffectivePermissionService(
      productGateway, grantRepository, assignmentRepository, roleTemplateResolver,
      membershipLookup, engine, idService);
  }

  @Test
  void checkPlatformPermission_should_return_true_when_global_allow_grant_exists() {
    Permission perm = AuthorizationFixtures.permission("USER_MANAGE", "FREEZE");
    when(grantRepository.findCandidateSubjectGrants(eq(UserNo.of("U-001")), any(LocalDateTime.class)))
      .thenReturn(List.of(AuthorizationFixtures.effectiveGlobalAllowGrant("USER_MANAGE", "FREEZE")));
    when(assignmentRepository.findActiveByAccount(any()))
      .thenReturn(List.of());

    boolean result = service.checkPlatformPermission(UserNo.of("U-001"), perm, LocalDateTime.now());

    assertThat(result).isTrue();
  }

  @Test
  void checkPlatformPermission_should_return_false_when_no_grant() {
    Permission perm = AuthorizationFixtures.permission("USER_MANAGE", "FREEZE");
    when(grantRepository.findCandidateSubjectGrants(any(), any(LocalDateTime.class)))
      .thenReturn(List.of());
    when(assignmentRepository.findActiveByAccount(any()))
      .thenReturn(List.of());

    boolean result = service.checkPlatformPermission(UserNo.of("U-001"), perm, LocalDateTime.now());

    assertThat(result).isFalse();
  }

  @Test
  void checkPlatformPermission_should_return_false_when_deny_overrides_allow() {
    Permission perm = AuthorizationFixtures.permission("USER_MANAGE", "FREEZE");
    when(grantRepository.findCandidateSubjectGrants(any(), any(LocalDateTime.class)))
      .thenReturn(List.of(
        AuthorizationFixtures.effectiveGlobalAllowGrant("USER_MANAGE", "FREEZE"),
        AuthorizationFixtures.effectiveGlobalDenyGrant("USER_MANAGE", "FREEZE")));
    when(assignmentRepository.findActiveByAccount(any()))
      .thenReturn(List.of());

    boolean result = service.checkPlatformPermission(UserNo.of("U-001"), perm, LocalDateTime.now());

    assertThat(result).isFalse();
  }

  @Test
  void checkPlatformPermission_should_skip_non_global_grants() {
    Permission perm = AuthorizationFixtures.permission("USER_MANAGE", "FREEZE");
    Grant businessGrant = AuthorizationFixtures.effectiveAllowGrant();
    when(grantRepository.findCandidateSubjectGrants(any(), any(LocalDateTime.class)))
      .thenReturn(List.of(businessGrant));
    when(assignmentRepository.findActiveByAccount(any()))
      .thenReturn(List.of());

    boolean result = service.checkPlatformPermission(UserNo.of("U-001"), perm, LocalDateTime.now());

    assertThat(result).isFalse();
  }
}
