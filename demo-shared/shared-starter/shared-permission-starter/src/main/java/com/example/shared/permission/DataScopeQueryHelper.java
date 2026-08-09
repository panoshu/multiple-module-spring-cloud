package com.example.shared.permission;

import com.example.auth.api.dto.DataScope;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;

/**
 * Repository 行级数据过滤条件拼接工具.
 *
 * <p>业务服务 Repository 在拼接 {@link QueryWrapper} 时调用，根据当前
 * {@link DataScopeContext} 自动添加 plan_no / customer_no 维度的 IN 过滤。
 *
 * <p>规则：
 * <ul>
 *   <li>全局可见（globalVisible=true）→ 不拼接条件</li>
 *   <li>空集（visiblePlans 为空）→ 拼接 {@code 1=0}，查不到任何数据</li>
 *   <li>非空集合 → 拼接 IN 子句</li>
 * </ul>
 *
 * @author shared-permission-starter
 */
public final class DataScopeQueryHelper {

  private DataScopeQueryHelper() {
  }

  /**
   * 应用 plan_no 维度的过滤条件.
   *
   * @param wrapper      MyBatis-Flex QueryWrapper
   * @param planNoColumn plan_no 列定义（如 BATCH_DO.PLAN_NO）
   */
  public static void applyPlanScope(QueryWrapper wrapper, QueryColumn planNoColumn) {
    DataScope scope = DataScopeContext.get();
    if (!scope.needsFiltering()) {
      return;
    }
    if (scope.visiblePlans().isEmpty()) {
      wrapper.and("1 = 0");
      return;
    }
    wrapper.and(planNoColumn.in(scope.visiblePlans()));
  }

  /**
   * 应用 customer_no 维度的过滤条件.
   *
   * @param wrapper          MyBatis-Flex QueryWrapper
   * @param customerNoColumn customer_no 列定义
   */
  public static void applyCustomerScope(QueryWrapper wrapper, QueryColumn customerNoColumn) {
    DataScope scope = DataScopeContext.get();
    if (!scope.needsFiltering()) {
      return;
    }
    if (scope.visibleCustomers().isEmpty()) {
      wrapper.and("1 = 0");
      return;
    }
    wrapper.and(customerNoColumn.in(scope.visibleCustomers()));
  }
}
