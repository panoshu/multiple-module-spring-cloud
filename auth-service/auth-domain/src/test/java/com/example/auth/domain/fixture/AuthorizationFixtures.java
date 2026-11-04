package com.example.auth.domain.fixture;

import com.example.core.types.PlanNo;
import com.example.core.types.UserNo;
import com.example.core.types.temporal.ValidityPeriod;
import com.example.auth.domain.authorization.aggregate.Grant;
import com.example.auth.domain.authorization.enumeration.*;
import com.example.auth.domain.authorization.repository.GrantRepository;
import com.example.auth.domain.authorization.spi.PlanMembershipLookup;
import com.example.auth.domain.authorization.valueobject.ActionCode;
import com.example.auth.domain.authorization.valueobject.BusinessCode;
import com.example.auth.domain.authorization.valueobject.Permission;
import com.example.auth.domain.authorization.valueobject.ScopeRule;
import com.example.auth.domain.authorization.valueobject.subject.CapabilitySubject;
import com.example.auth.domain.authorization.valueobject.subject.PlanAllMembersSubject;
import com.example.auth.domain.authorization.valueobject.subject.PlanRoleSubject;
import com.example.auth.domain.authorization.valueobject.subject.UserListSubject;
import com.example.auth.domain.product.PlanSnapshot;
import com.example.auth.domain.product.ProductGateway;
import com.example.auth.types.GrantId;
import com.example.auth.types.RoleCode;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.mock;

/**
 * authorization 域测试数据工厂。
 * 所有方法返回完整可测试对象，调用方可按需修改字段。
 */
public final class AuthorizationFixtures {

  private AuthorizationFixtures() {
  }

  // ===== 基础值对象 =====

  public static BusinessCode businessCode(String value) {
    return new BusinessCode(value);
  }

  public static ActionCode actionCode(String value) {
    return new ActionCode(value);
  }

  public static Permission permission(String business, String action) {
    return new Permission(businessCode(business), actionCode(action));
  }

  public static Permission wholeBusiness(String business) {
    return Permission.wholeBusiness(businessCode(business));
  }

  public static ScopeRule planScopeRule(String planValue) {
    return ScopeRule.of(ScopeDimension.PLAN, planValue);
  }

  // ===== Subject 系列 =====

  public static CapabilitySubject capabilitySubject() {
    return new CapabilitySubject();
  }

  public static PlanAllMembersSubject planAllMembersSubject(String planNo) {
    return new PlanAllMembersSubject(PlanNo.of(planNo));
  }

  public static PlanRoleSubject planRoleSubject(String planNo, String roleCode) {
    return new PlanRoleSubject(PlanNo.of(planNo), new RoleCode(roleCode));
  }

  public static UserListSubject userListSubject(String... userNos) {
    Set<UserNo> users = java.util.Arrays.stream(userNos).map(UserNo::of).collect(java.util.stream.Collectors.toSet());
    return new UserListSubject(users);
  }

  // ===== Grant 聚合根 =====

  /**
   * 创建一个 PENDING_APPROVAL 状态的 ALLOW 授权（能力层，PLAN 维度）。
   */
  public static Grant pendingAllowGrant() {
    return Grant.create(
      new GrantId("g-pending-1"),
      UserNo.of("creator-1"),
      capabilitySubject(),
      List.of(planScopeRule("PLAN-001")),
      Set.of(permission("BIZ-001", "ACT-VIEW")),
      GrantType.BASE,
      GrantOrigin.HQ_CONFIG,
      Effect.ALLOW,
      GrantStatus.PENDING_APPROVAL,
      ValidityPeriod.infinite(),
      PlanNo.of("PLAN-001"),
      PlanNo.of("PLAN-001"));
  }

  /**
   * 创建一个 EFFECTIVE 状态的 ALLOW 授权。
   */
  public static Grant effectiveAllowGrant() {
    return Grant.create(
      new GrantId("g-allow-1"),
      UserNo.of("creator-1"),
      capabilitySubject(),
      List.of(planScopeRule("PLAN-001")),
      Set.of(permission("BIZ-001", "ACT-VIEW")),
      GrantType.BASE,
      GrantOrigin.HQ_CONFIG,
      Effect.ALLOW,
      GrantStatus.EFFECTIVE,
      ValidityPeriod.infinite(),
      PlanNo.of("PLAN-001"),
      PlanNo.of("PLAN-001"));
  }

  /**
   * 创建一个 EFFECTIVE 状态的 DENY 授权。
   */
  public static Grant effectiveDenyGrant() {
    return Grant.create(
      new GrantId("g-deny-1"),
      UserNo.of("creator-1"),
      capabilitySubject(),
      List.of(planScopeRule("PLAN-001")),
      Set.of(permission("BIZ-001", "ACT-VIEW")),
      GrantType.BASE,
      GrantOrigin.HQ_CONFIG,
      Effect.DENY,
      GrantStatus.EFFECTIVE,
      ValidityPeriod.infinite(),
      PlanNo.of("PLAN-001"),
      PlanNo.of("PLAN-001"));
  }

  // ===== 快照 =====

  public static PlanSnapshot planSnapshot(String planNo) {
    return new PlanSnapshot(
      PlanNo.of(planNo),
      com.example.core.types.ProductNo.of("PROD-001"),
      com.example.core.types.CustomerNo.of("CUST-001"),
      Optional.empty(),
      "测试计划",
      java.time.Instant.now());
  }

  // ===== Mock 工厂 =====

  public static GrantRepository mockGrantRepository() {
    return mock(GrantRepository.class);
  }

  public static ProductGateway mockProductGateway() {
    return mock(ProductGateway.class);
  }

  public static PlanMembershipLookup mockMembershipLookup() {
    return mock(PlanMembershipLookup.class);
  }

  // ===== GLOBAL / 平台权限 =====

  /**
   * 创建一个 GLOBAL 范围的 EFFECTIVE ALLOW 授权（平台管理权限）。
   */
  public static Grant effectiveGlobalAllowGrant(String business, String action) {
    return Grant.create(
      new GrantId("g-global-allow-1"),
      UserNo.of("creator-1"),
      userListSubject("U-001"),
      List.of(),
      Set.of(permission(business, action)),
      GrantType.BASE,
      GrantOrigin.HQ_CONFIG,
      Effect.ALLOW,
      GrantStatus.EFFECTIVE,
      ValidityPeriod.infinite(),
      null,
      null);
  }

  /**
   * 创建一个 GLOBAL 范围的 EFFECTIVE DENY 授权（紧急收权）。
   */
  public static Grant effectiveGlobalDenyGrant(String business, String action) {
    return Grant.create(
      new GrantId("g-global-deny-1"),
      UserNo.of("creator-1"),
      userListSubject("U-001"),
      List.of(),
      Set.of(permission(business, action)),
      GrantType.BASE,
      GrantOrigin.HQ_CONFIG,
      Effect.DENY,
      GrantStatus.EFFECTIVE,
      ValidityPeriod.infinite(),
      null,
      null);
  }
}
