package com.pension.permission.application.permission;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.service.EffectivePermissionService;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.channel.valueobject.SessionPermissionCache;
import com.pension.permission.domain.permission.aggregate.PermissionItem;
import com.pension.permission.domain.permission.repository.PermissionItemRepository;
import com.pension.permission.domain.permission.spi.PermissionCacheStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限缓存计算应用服务。
 * <p>负责计算并填充 {@link SessionPermissionCache}：
 * <ul>
 *   <li>{@link #computePlatformPermissions}：遍历 PLATFORM 类别权限点，逐个调用 {@code checkPlatformPermission}</li>
 *   <li>{@link #computeBusinessPermissions}：遍历 BUSINESS 类别权限点，逐个调用 {@code checkPermission}</li>
 *   <li>{@link #computeAndSave}：组合两个集合，写入 PermissionCacheStore</li>
 * </ul>
 * <p>注：逐个调用是为简化实现。后续可优化为批量查询接口，减少 Grant 表访问次数。
 */
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

  private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

  private final EffectivePermissionService effectivePermissionService;
  private final PermissionItemRepository permissionItemRepository;
  private final PermissionCacheStore cacheStore;

  /**
   * 计算平台权限点集合（登录后拉取）。
   */
  public Set<Permission> computePlatformPermissions(UserNo identity) {
    Set<Permission> result = new HashSet<>();
    List<PermissionItem> items = permissionItemRepository.findByCategory(PermissionCategory.PLATFORM);
    LocalDateTime now = LocalDateTime.now();
    for (PermissionItem item : items) {
      Permission perm = new Permission(item.businessCode(), item.actionCode());
      if (effectivePermissionService.checkPlatformPermission(identity, perm, now)) {
        result.add(perm);
      }
    }
    return result;
  }

  /**
   * 计算业务权限点集合（选计划后拉取）。
   */
  public Set<Permission> computeBusinessPermissions(UserNo identity, PlanNo planId) {
    Set<Permission> result = new HashSet<>();
    List<PermissionItem> items = permissionItemRepository.findByCategory(PermissionCategory.BUSINESS);
    LocalDateTime now = LocalDateTime.now();
    for (PermissionItem item : items) {
      Permission perm = new Permission(item.businessCode(), item.actionCode());
      if (effectivePermissionService.checkPermission(identity, planId, perm, now)) {
        result.add(perm);
      }
    }
    return result;
  }

  /**
   * 计算并保存完整缓存（登录时调用，planId 可为 null 表示只拉平台权限）。
   */
  public SessionPermissionCache computeAndSave(UserNo identity, PlanNo planId) {
    Set<Permission> platform = computePlatformPermissions(identity);
    Set<Permission> business = planId != null
      ? computeBusinessPermissions(identity, planId)
      : Set.of();

    LocalDateTime now = LocalDateTime.now();
    SessionPermissionCache cache = new SessionPermissionCache(
      platform, business, planId, now, now.plus(DEFAULT_TTL));
    cacheStore.save(identity, cache);
    return cache;
  }

  /**
   * 仅刷新业务权限区（切换计划时调用，平台权限保持不变）。
   */
  public SessionPermissionCache refreshBusinessPermissions(UserNo identity, PlanNo newPlanId) {
    SessionPermissionCache existing = cacheStore.load(identity).orElse(null);
    Set<Permission> platform = existing != null
      ? existing.platformPermissions()
      : computePlatformPermissions(identity);

    Set<Permission> business = computeBusinessPermissions(identity, newPlanId);
    LocalDateTime now = LocalDateTime.now();
    SessionPermissionCache cache = new SessionPermissionCache(
      platform, business, newPlanId, now, now.plus(DEFAULT_TTL));
    cacheStore.save(identity, cache);
    return cache;
  }
}
