package com.pension.permission.domain.channel.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.pension.permission.domain.authorization.valueobject.Permission;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 权限快照值对象.
 *
 * <p>二次授权确认瞬间冻结的经办人权限集合，用于授权后业务办理时的快速判定。
 * 快照不可变，TTL 过期后需要重新发起授权。</p>
 */
public record PermissionSnapshot(
  Set<Permission> permissions,
  LocalDateTime frozenAt,
  LocalDateTime expiresAt
) implements ValueObject {

  public PermissionSnapshot {
    Objects.requireNonNull(permissions, "permissions");
    Objects.requireNonNull(frozenAt, "frozenAt");
    Objects.requireNonNull(expiresAt, "expiresAt");
    if (permissions.isEmpty()) {
      throw new IllegalArgumentException("permissions must not be empty");
    }
    if (expiresAt.isBefore(frozenAt)) {
      throw new IllegalArgumentException("expiresAt must not be before frozenAt");
    }
    permissions = Collections.unmodifiableSet(new HashSet<>(permissions));
  }

  /**
   * 创建权限快照.
   *
   * @param permissions 冻结的权限集合
   * @param frozenAt    冻结时间
   * @param ttl         存活时间（从 frozenAt 开始计算）
   * @return PermissionSnapshot 实例
   */
  public static PermissionSnapshot of(Set<Permission> permissions, LocalDateTime frozenAt, Duration ttl) {
    Objects.requireNonNull(ttl, "ttl");
    return new PermissionSnapshot(permissions, frozenAt, frozenAt.plus(ttl));
  }

  /**
   * 校验快照是否已过期.
   */
  public boolean isExpired(LocalDateTime now) {
    return !now.isBefore(expiresAt);
  }

  /**
   * 校验快照是否包含指定权限.
   */
  public boolean contains(Permission permission) {
    return permissions.contains(permission);
  }
}
