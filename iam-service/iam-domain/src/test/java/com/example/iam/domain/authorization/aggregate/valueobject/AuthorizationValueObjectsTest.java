package com.example.iam.domain.authorization.aggregate.valueobject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.iam.types.UserId;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * authorization 限界上下文值对象契约测试。
 *
 * <p>验证 7 个枚举值对象 + 4 个 record 值对象满足 {@code 03-领域模型约束.md} 与设计文档 3.5 节约束:
 * <ul>
 *   <li>枚举常量数量符合设计</li>
 *   <li>SubjectType 优先级数值正确</li>
 *   <li>PermissionCode/PermissionSnapshot 的 null/blank 校验</li>
 *   <li>状态转换方法语义正确(DelegationStatus/RuleStatus)</li>
 *   <li>PermissionSnapshot 的 permissions 集合不可变</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
class AuthorizationValueObjectsTest {

  // ==================== 枚举常量数量 ====================

  @Test
  void subjectTypeShouldHave5Constants() {
    assertEquals(5, SubjectType.values().length);
  }

  @Test
  void overrideModeShouldHave2Constants() {
    assertEquals(2, OverrideMode.values().length);
  }

  @Test
  void actionShouldHave3Constants() {
    assertEquals(3, Action.values().length);
  }

  @Test
  void operationModeShouldHave3Constants() {
    assertEquals(3, OperationMode.values().length);
  }

  @Test
  void delegationTypeShouldHave2Constants() {
    assertEquals(2, DelegationType.values().length);
  }

  @Test
  void delegationStatusShouldHave3Constants() {
    assertEquals(3, DelegationStatus.values().length);
  }

  @Test
  void ruleStatusShouldHave2Constants() {
    assertEquals(2, RuleStatus.values().length);
  }

  // ==================== SubjectType 优先级 ====================

  @Test
  void subjectTypePriorityShouldMatchDesign() {
    assertEquals(1, SubjectType.CUSTOMER.priority());
    assertEquals(2, SubjectType.OPERATION_MODE.priority());
    assertEquals(3, SubjectType.PRODUCT.priority());
    assertEquals(4, SubjectType.PLAN.priority());
    assertEquals(5, SubjectType.ACCOUNT_MANAGER.priority());
  }

  @Test
  void accountManagerShouldHaveHigherPriorityThanCustomer() {
    assertTrue(SubjectType.ACCOUNT_MANAGER.priority() > SubjectType.CUSTOMER.priority());
  }

  // ==================== PermissionCode 校验 ====================

  @Test
  void permissionCodeOfShouldReturnCode() {
    PermissionCode code = PermissionCode.of("business1.handle");
    assertEquals("business1.handle", code.value());
  }

  @Test
  void permissionCodeShouldRejectNull() {
    assertThrows(NullPointerException.class, () -> new PermissionCode(null));
  }

  @Test
  void permissionCodeShouldRejectBlank() {
    assertThrows(IllegalArgumentException.class, () -> new PermissionCode(""));
    assertThrows(IllegalArgumentException.class, () -> new PermissionCode("   "));
  }

  // ==================== PermissionSnapshot 校验 ====================

  @Test
  void permissionSnapshotShouldRejectNullUserId() {
    assertThrows(NullPointerException.class, () -> new PermissionSnapshot(
        null, "PLAN001", Set.of(PermissionCode.of("biz.handle")), LocalDateTime.now()));
  }

  @Test
  void permissionSnapshotShouldRejectNullPlanId() {
    assertThrows(NullPointerException.class, () -> new PermissionSnapshot(
        UserId.of(1L), null, Set.of(PermissionCode.of("biz.handle")), LocalDateTime.now()));
  }

  @Test
  void permissionSnapshotShouldRejectNullPermissions() {
    assertThrows(NullPointerException.class, () -> new PermissionSnapshot(
        UserId.of(1L), "PLAN001", null, LocalDateTime.now()));
  }

  @Test
  void permissionSnapshotShouldRejectNullCalculatedAt() {
    assertThrows(NullPointerException.class, () -> new PermissionSnapshot(
        UserId.of(1L), "PLAN001", Set.of(PermissionCode.of("biz.handle")), null));
  }

  @Test
  void permissionSnapshotPermissionsShouldBeImmutable() {
    Set<PermissionCode> mutable = new HashSet<>();
    mutable.add(PermissionCode.of("biz.handle"));
    PermissionSnapshot snapshot = new PermissionSnapshot(
        UserId.of(1L), "PLAN001", mutable, LocalDateTime.now());

    // 修改原始集合不应影响 snapshot
    mutable.add(PermissionCode.of("biz.query"));
    assertEquals(1, snapshot.permissions().size());

    // snapshot 内部集合不可修改
    assertThrows(UnsupportedOperationException.class, () ->
        snapshot.permissions().add(PermissionCode.of("biz.query")));
  }

  // ==================== DelegationStatus 状态转换 ====================

  @Test
  void delegationStatusActiveShouldBeActive() {
    assertTrue(DelegationStatus.ACTIVE.isActive());
    assertFalse(DelegationStatus.REVOKED.isActive());
    assertFalse(DelegationStatus.EXPIRED.isActive());
  }

  @Test
  void delegationStatusCanRevokeOnlyFromActive() {
    assertTrue(DelegationStatus.ACTIVE.canRevoke());
    assertFalse(DelegationStatus.REVOKED.canRevoke());
    assertFalse(DelegationStatus.EXPIRED.canRevoke());
  }

  @Test
  void delegationStatusCanExpireOnlyFromActive() {
    assertTrue(DelegationStatus.ACTIVE.canExpire());
    assertFalse(DelegationStatus.REVOKED.canExpire());
    assertFalse(DelegationStatus.EXPIRED.canExpire());
  }

  // ==================== RuleStatus 状态转换 ====================

  @Test
  void ruleStatusActiveShouldBeActive() {
    assertTrue(RuleStatus.ACTIVE.isActive());
    assertFalse(RuleStatus.DISABLED.isActive());
  }

  @Test
  void ruleStatusCanDisableOnlyFromActive() {
    assertTrue(RuleStatus.ACTIVE.canDisable());
    assertFalse(RuleStatus.DISABLED.canDisable());
  }

  @Test
  void ruleStatusCanEnableOnlyFromDisabled() {
    assertTrue(RuleStatus.DISABLED.canEnable());
    assertFalse(RuleStatus.ACTIVE.canEnable());
  }
}
