package com.pension.permission.domain.channel.valueobject;

import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PermissionSnapshot 值对象测试")
class PermissionSnapshotTest {

  private Permission permission(String business, String action) {
    return new Permission(new BusinessCode(business), new ActionCode(action));
  }

  @Nested
  @DisplayName("创建快照")
  class CreateTest {

    @Test
    @DisplayName("应当用权限集合和 TTL 创建快照")
    void should_create_when_valid_input() {
      LocalDateTime now = LocalDateTime.now();
      Set<Permission> permissions = Set.of(
        permission("ANNUITY_CONTRIBUTION", "HANDLE"),
        permission("ANNUITY_PAYMENT", "QUERY"));
      PermissionSnapshot snapshot = PermissionSnapshot.of(permissions, now, Duration.ofSeconds(30));
      assertThat(snapshot.permissions()).hasSize(2);
      assertThat(snapshot.frozenAt()).isEqualTo(now);
      assertThat(snapshot.expiresAt()).isEqualTo(now.plusSeconds(30));
    }

    @Test
    @DisplayName("权限集合为空时应当抛异常")
    void should_throw_when_permissions_empty() {
      assertThatThrownBy(() ->
        PermissionSnapshot.of(Set.of(), LocalDateTime.now(), Duration.ofSeconds(30)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("permissions");
    }

    @Test
    @DisplayName("frozenAt 为 null 时应当抛异常")
    void should_throw_when_frozen_at_null() {
      assertThatThrownBy(() ->
        PermissionSnapshot.of(Set.of(permission("B", "A")), null, Duration.ofSeconds(30)))
        .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("过期判断")
  class IsExpiredTest {

    @Test
    @DisplayName("当前时间在过期前应当未过期")
    void should_not_expired_before_expires_at() {
      LocalDateTime now = LocalDateTime.now();
      PermissionSnapshot snapshot = PermissionSnapshot.of(
        Set.of(permission("B", "A")), now, Duration.ofSeconds(30));
      assertThat(snapshot.isExpired(now.plusSeconds(29))).isFalse();
    }

    @Test
    @DisplayName("当前时间到达过期时间应当已过期")
    void should_expired_when_reaches_expires_at() {
      LocalDateTime now = LocalDateTime.now();
      PermissionSnapshot snapshot = PermissionSnapshot.of(
        Set.of(permission("B", "A")), now, Duration.ofSeconds(30));
      assertThat(snapshot.isExpired(now.plusSeconds(30))).isTrue();
    }
  }

  @Nested
  @DisplayName("权限包含判断")
  class ContainsTest {

    @Test
    @DisplayName("快照中包含的权限应当返回 true")
    void should_return_true_when_permission_in_snapshot() {
      LocalDateTime now = LocalDateTime.now();
      Permission p = permission("ANNUITY_CONTRIBUTION", "HANDLE");
      PermissionSnapshot snapshot = PermissionSnapshot.of(Set.of(p), now, Duration.ofSeconds(30));
      assertThat(snapshot.contains(p)).isTrue();
    }

    @Test
    @DisplayName("快照中不包含的权限应当返回 false")
    void should_return_false_when_permission_not_in_snapshot() {
      LocalDateTime now = LocalDateTime.now();
      Permission p1 = permission("ANNUITY_CONTRIBUTION", "HANDLE");
      Permission p2 = permission("ANNUITY_PAYMENT", "QUERY");
      PermissionSnapshot snapshot = PermissionSnapshot.of(Set.of(p1), now, Duration.ofSeconds(30));
      assertThat(snapshot.contains(p2)).isFalse();
    }
  }
}
