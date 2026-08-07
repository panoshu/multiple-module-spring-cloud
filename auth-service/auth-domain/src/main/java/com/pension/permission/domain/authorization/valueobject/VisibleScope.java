package com.pension.permission.domain.authorization.valueobject;

import java.util.Set;

/**
 * 可见范围领域内聚合值对象.
 *
 * <p>由 {@code EffectivePermissionService.resolveVisibleScope} 聚合 Grant 计算得出，
 * 由 {@code PermissionQueryService} 转换为 auth-api 的 {@code DataScope} 返回。
 *
 * <p>不直接复用 auth-api 的 DataScope：避免 domain 层依赖 api 层（DDD 依赖倒置）。
 *
 * @param globalVisible     是否全局可见
 * @param visiblePlans      可见 plan 列表
 * @param visibleCustomers  可见 customer 列表（含继承的子客户）
 * @param excludedPlans     DENY 排除的 plan
 * @param excludedCustomers DENY 排除的 customer
 * @author auth-domain
 */
public record VisibleScope(
    boolean globalVisible,
    Set<String> visiblePlans,
    Set<String> visibleCustomers,
    Set<String> excludedPlans,
    Set<String> excludedCustomers) {

    public VisibleScope {
        visiblePlans = visiblePlans != null ? Set.copyOf(visiblePlans) : Set.of();
        visibleCustomers = visibleCustomers != null ? Set.copyOf(visibleCustomers) : Set.of();
        excludedPlans = excludedPlans != null ? Set.copyOf(excludedPlans) : Set.of();
        excludedCustomers = excludedCustomers != null ? Set.copyOf(excludedCustomers) : Set.of();
    }

    public static VisibleScope empty() {
        return new VisibleScope(false, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static VisibleScope global() {
        return new VisibleScope(true, Set.of(), Set.of(), Set.of(), Set.of());
    }
}
