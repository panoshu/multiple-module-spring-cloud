package com.example.auth.api.dto;

import java.util.Set;

/**
 * 数据可见范围业务对象，承载行级过滤所需信息.
 *
 * <p>由 {@code DataScopeResolver} 解析后放入 {@code DataScopeContext}（ThreadLocal），
 * Repository 通过 {@code DataScopeQueryHelper} 拼接 QueryWrapper 条件。
 *
 * @param globalVisible     是否全局可见（GLOBAL 范围）
 * @param visiblePlans      可见 plan 列表
 * @param visibleCustomers  可见 customer 列表（含继承的子客户）
 * @param excludedPlans     DENY 排除的 plan
 * @param excludedCustomers DENY 排除的 customer
 * @author auth-api
 */
public record DataScope(
    boolean globalVisible,
    Set<String> visiblePlans,
    Set<String> visibleCustomers,
    Set<String> excludedPlans,
    Set<String> excludedCustomers
) {

    public DataScope {
        visiblePlans = visiblePlans != null ? Set.copyOf(visiblePlans) : Set.of();
        visibleCustomers = visibleCustomers != null ? Set.copyOf(visibleCustomers) : Set.of();
        excludedPlans = excludedPlans != null ? Set.copyOf(excludedPlans) : Set.of();
        excludedCustomers = excludedCustomers != null ? Set.copyOf(excludedCustomers) : Set.of();
    }

    public boolean needsFiltering() {
        return !globalVisible;
    }

    public static DataScope empty() {
        return new DataScope(false, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static DataScope global() {
        return new DataScope(true, Set.of(), Set.of(), Set.of(), Set.of());
    }
}
