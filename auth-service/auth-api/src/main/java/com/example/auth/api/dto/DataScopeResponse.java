package com.example.auth.api.dto;

import java.util.Set;

/**
 * 解析数据可见范围响应.
 *
 * @param globalVisible     是否全局可见
 * @param visiblePlans      可见 plan 列表
 * @param visibleCustomers  可见 customer 列表
 * @param excludedPlans     DENY 排除的 plan
 * @param excludedCustomers DENY 排除的 customer
 * @author auth-api
 */
public record DataScopeResponse(
    boolean globalVisible,
    Set<String> visiblePlans,
    Set<String> visibleCustomers,
    Set<String> excludedPlans,
    Set<String> excludedCustomers) {}
