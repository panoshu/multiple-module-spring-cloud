package com.pension.permission.application.authorization;

import com.example.auth.api.dto.DataScope;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.service.EffectivePermissionService;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.authorization.valueobject.VisibleScope;
import com.pension.permission.domain.permission.repository.PermissionItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 权限判定查询的应用层入口——前端菜单/按钮的可见性接口、后端网关拦截层，
 * 最终都应该落到这一个方法上，保证前后端用的是同一份判定逻辑。
 * <p>按权限点元数据的 category 分流：
 * <ul>
 *   <li>BUSINESS → 调用 {@code checkPermission(identity, planId, permission, at)}（能力层+主体层）</li>
 *   <li>PLATFORM → 调用 {@code checkPlatformPermission(identity, permission, at)}（仅主体层 GLOBAL 匹配）</li>
 *   <li>未注册权限点 → 默认走 BUSINESS 路径（向后兼容）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public final class PermissionQueryService {

  private final EffectivePermissionService effectivePermissionService;
  private final PermissionItemRepository permissionItemRepository;

  public boolean checkPermission(CheckPermissionQuery query) {
    Permission permission = new Permission(query.businessCode(), query.actionCode());
    PermissionCategory category = resolveCategory(query.businessCode(), query.actionCode());

    return switch (category) {
      case BUSINESS -> effectivePermissionService.checkPermission(
        query.identity(), query.planId(), permission, LocalDateTime.now());
      case PLATFORM -> effectivePermissionService.checkPlatformPermission(
        query.identity(), permission, LocalDateTime.now());
    };
  }

  /**
   * 平台权限判定入口（不依赖 planId）。
   */
  public boolean checkPlatformPermission(UserNo identity, BusinessCode business, ActionCode action) {
    Permission permission = new Permission(business, action);
    return effectivePermissionService.checkPlatformPermission(identity, permission, LocalDateTime.now());
  }

  /**
   * 解析数据可见范围（行级数据过滤用）.
   *
   * <p>委托 {@link EffectivePermissionService#resolveVisibleScope} 聚合 Grant 计算，
   * 返回 auth-api 的 {@link DataScope} 供 shared-permission-starter 使用。
   *
   * <p>对 visiblePlans/visibleCustomers 减去 excludedPlans/excludedCustomers 做防御性收口：
   * 领域层 {@code resolveVisibleScope} 已在聚合阶段完成扣减，此处对不一致输入再次收口，
   * 保证 DENY 优先语义在应用层不会因 VisibleScope 不一致而泄漏到行级过滤条件。
   *
   * @param query 包含用户标识和业务编码
   * @return 数据可见范围
   */
  public DataScope resolveDataScope(ResolveDataScopeQuery query) {
    VisibleScope visible = effectivePermissionService.resolveVisibleScope(
      query.identity(), query.business(), LocalDateTime.now());

    if (visible.globalVisible()) {
      return DataScope.global();
    }
    java.util.Set<String> finalVisiblePlans = new java.util.HashSet<>(visible.visiblePlans());
    finalVisiblePlans.removeAll(visible.excludedPlans());
    java.util.Set<String> finalVisibleCustomers = new java.util.HashSet<>(visible.visibleCustomers());
    finalVisibleCustomers.removeAll(visible.excludedCustomers());
    return new DataScope(
      false,
      finalVisiblePlans,
      finalVisibleCustomers,
      visible.excludedPlans(),
      visible.excludedCustomers());
  }

  private PermissionCategory resolveCategory(BusinessCode business, ActionCode action) {
    Optional<PermissionCategory> category = permissionItemRepository.findCategory(business, action);
    return category.orElse(PermissionCategory.BUSINESS);
  }
}
