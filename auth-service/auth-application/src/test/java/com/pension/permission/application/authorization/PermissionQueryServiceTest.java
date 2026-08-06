package com.pension.permission.application.authorization;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.service.EffectivePermissionService;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.permission.repository.PermissionItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PermissionQueryService 测试")
@ExtendWith(MockitoExtension.class)
class PermissionQueryServiceTest {

  @Mock
  private EffectivePermissionService effectivePermissionService;

  @Mock
  private PermissionItemRepository permissionItemRepository;

  @InjectMocks
  private PermissionQueryService service;

  @BeforeEach
  void setUp() {
    lenient().when(permissionItemRepository.findCategory(any(), any()))
      .thenReturn(Optional.of(PermissionCategory.BUSINESS));
  }

  private CheckPermissionQuery buildQuery() {
    return new CheckPermissionQuery(
      UserNo.of("user-1"),
      new PlanNo("plan-1"),
      new BusinessCode("PAY"),
      new ActionCode("QUERY"));
  }

  @Nested
  @DisplayName("checkPermission: 权限判定")
  class CheckPermissionTest {

    @Test
    @DisplayName("应返回 EffectivePermissionService 判定通过的结果")
    void shouldReturnTrueWhenPermitted() {
      var query = buildQuery();
      when(effectivePermissionService.checkPermission(
        eq(query.identity()), eq(query.planId()), any(Permission.class), any(LocalDateTime.class)))
        .thenReturn(true);

      boolean result = service.checkPermission(query);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("应返回 EffectivePermissionService 判定拒绝的结果")
    void shouldReturnFalseWhenDenied() {
      var query = buildQuery();
      when(effectivePermissionService.checkPermission(
        eq(query.identity()), eq(query.planId()), any(Permission.class), any(LocalDateTime.class)))
        .thenReturn(false);

      boolean result = service.checkPermission(query);

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("应将 identity、planId 与 Permission(business+action) 透传给 EffectivePermissionService")
    void shouldPassParametersToEffectivePermissionService() {
      var identity = UserNo.of("user-1");
      var planId = new PlanNo("plan-1");
      var businessCode = new BusinessCode("PAY");
      var actionCode = new ActionCode("QUERY");
      var query = new CheckPermissionQuery(identity, planId, businessCode, actionCode);
      when(effectivePermissionService.checkPermission(any(), any(), any(), any()))
        .thenReturn(true);

      service.checkPermission(query);

      verify(effectivePermissionService).checkPermission(
        eq(identity),
        eq(planId),
        eq(new Permission(businessCode, actionCode)),
        any(LocalDateTime.class));
    }
  }

  @Nested
  @DisplayName("checkPlatformPermission: 平台权限判定")
  class CheckPlatformPermissionTest {

    @Test
    @DisplayName("应返回 EffectivePermissionService 判定通过的结果")
    void shouldReturnTrueWhenPermitted() {
      UserNo identity = UserNo.of("user-1");
      BusinessCode business = new BusinessCode("USER_MANAGE");
      ActionCode action = new ActionCode("FREEZE");
      when(effectivePermissionService.checkPlatformPermission(
        eq(identity), any(Permission.class), any(LocalDateTime.class)))
        .thenReturn(true);

      boolean result = service.checkPlatformPermission(identity, business, action);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("应返回 EffectivePermissionService 判定拒绝的结果")
    void shouldReturnFalseWhenDenied() {
      UserNo identity = UserNo.of("user-1");
      BusinessCode business = new BusinessCode("USER_MANAGE");
      ActionCode action = new ActionCode("FREEZE");
      when(effectivePermissionService.checkPlatformPermission(
        eq(identity), any(Permission.class), any(LocalDateTime.class)))
        .thenReturn(false);

      boolean result = service.checkPlatformPermission(identity, business, action);

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("应将 identity 与 Permission(business+action) 透传给 EffectivePermissionService")
    void shouldPassParametersToEffectivePermissionService() {
      UserNo identity = UserNo.of("user-1");
      BusinessCode business = new BusinessCode("USER_MANAGE");
      ActionCode action = new ActionCode("FREEZE");
      when(effectivePermissionService.checkPlatformPermission(any(), any(), any()))
        .thenReturn(true);

      service.checkPlatformPermission(identity, business, action);

      verify(effectivePermissionService).checkPlatformPermission(
        eq(identity),
        eq(new Permission(business, action)),
        any(LocalDateTime.class));
    }
  }
}
