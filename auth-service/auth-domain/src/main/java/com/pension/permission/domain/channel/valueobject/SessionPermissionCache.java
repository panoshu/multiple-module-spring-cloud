package com.pension.permission.domain.channel.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.identifier.id.PlanNo;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.authorization.valueobject.Permission;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Session 级权限缓存值对象。
 * <p>分两区存储：
 * <ul>
 *   <li>{@code platformPermissions}：平台管理权限，登录后拉取，不随计划切换变化</li>
 *   <li>{@code businessPermissions}：当前计划下的业务权限，选计划后拉取</li>
 * </ul>
 * <p>仅用于前端可见性判定（菜单/按钮显不显示），后端 API 实际安全校验始终实时查 Grant。
 */
public record SessionPermissionCache(
  Set<Permission> platformPermissions,
  Set<Permission> businessPermissions,
  PlanNo selectedPlanId,
  LocalDateTime cachedAt,
  LocalDateTime expiresAt
) implements ValueObject {

  public SessionPermissionCache {
    Objects.requireNonNull(platformPermissions, "platformPermissions");
    Objects.requireNonNull(businessPermissions, "businessPermissions");
    Objects.requireNonNull(cachedAt, "cachedAt");
    Objects.requireNonNull(expiresAt, "expiresAt");
    platformPermissions = Collections.unmodifiableSet(new HashSet<>(platformPermissions));
    businessPermissions = Collections.unmodifiableSet(new HashSet<>(businessPermissions));
  }

  public boolean contains(Permission permission, PermissionCategory category) {
    return switch (category) {
      case PLATFORM -> platformPermissions.contains(permission);
      case BUSINESS -> selectedPlanId != null && businessPermissions.contains(permission);
    };
  }

  public boolean isExpired() {
    return LocalDateTime.now().isAfter(expiresAt);
  }
}
