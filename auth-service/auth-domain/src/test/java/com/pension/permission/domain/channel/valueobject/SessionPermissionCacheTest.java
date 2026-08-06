package com.pension.permission.domain.channel.valueobject;

import com.example.shared.identifier.id.PlanNo;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.fixture.AuthorizationFixtures;
import com.pension.permission.domain.authorization.valueobject.Permission;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SessionPermissionCacheTest {

  @Test
  void contains_platform_permission_should_check_platform_set() {
    Permission perm = AuthorizationFixtures.permission("USER_MANAGE", "FREEZE");
    SessionPermissionCache cache = new SessionPermissionCache(
      Set.of(perm), Set.of(), null,
      LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));

    assertThat(cache.contains(perm, PermissionCategory.PLATFORM)).isTrue();
    assertThat(cache.contains(perm, PermissionCategory.BUSINESS)).isFalse();
  }

  @Test
  void contains_business_permission_should_require_plan_id() {
    Permission perm = AuthorizationFixtures.permission("BIZ-001", "VIEW");
    SessionPermissionCache cacheWithPlan = new SessionPermissionCache(
      Set.of(), Set.of(perm), PlanNo.of("PLAN-001"),
      LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));
    SessionPermissionCache cacheWithoutPlan = new SessionPermissionCache(
      Set.of(), Set.of(perm), null,
      LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));

    assertThat(cacheWithPlan.contains(perm, PermissionCategory.BUSINESS)).isTrue();
    assertThat(cacheWithoutPlan.contains(perm, PermissionCategory.BUSINESS)).isFalse();
  }

  @Test
  void isExpired_should_return_true_after_expires_at() {
    SessionPermissionCache expired = new SessionPermissionCache(
      Set.of(), Set.of(), null,
      LocalDateTime.now().minusMinutes(10), LocalDateTime.now().minusMinutes(5));

    assertThat(expired.isExpired()).isTrue();
  }

  @Test
  void isExpired_should_return_false_before_expires_at() {
    SessionPermissionCache fresh = new SessionPermissionCache(
      Set.of(), Set.of(), null,
      LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));

    assertThat(fresh.isExpired()).isFalse();
  }
}
