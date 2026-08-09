package com.example.shared.permission;

import com.example.auth.api.dto.DataScope;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.example.shared.permission.MockQueryColumns.PLAN_NO;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataScopeQueryHelper 条件拼接测试")
class DataScopeQueryHelperTest {

  @AfterEach
  void clearContext() {
    DataScopeContext.clear();
  }

  @Test
  @DisplayName("全局可见时不拼接条件")
  void applyPlanScopeGlobalVisibleSkips() {
    DataScopeContext.set(DataScope.global());
    QueryWrapper wrapper = QueryWrapper.create();
    DataScopeQueryHelper.applyPlanScope(wrapper, PLAN_NO);
    assertThat(wrapper.toSQL()).doesNotContain("IN");
  }

  @Test
  @DisplayName("空集时拼接 1 = 0")
  void applyPlanScopeEmptySetAppendsOneEqualsZero() {
    DataScopeContext.set(DataScope.empty());
    QueryWrapper wrapper = QueryWrapper.create();
    DataScopeQueryHelper.applyPlanScope(wrapper, PLAN_NO);
    assertThat(wrapper.toSQL()).contains("1 = 0");
  }

  @Test
  @DisplayName("非空集合时拼接 IN 子句")
  void applyPlanScopeNonEmptyAppendsIn() {
    DataScopeContext.set(new DataScope(false, Set.of("P001", "P002"), Set.of(), Set.of(), Set.of()));
    QueryWrapper wrapper = QueryWrapper.create();
    DataScopeQueryHelper.applyPlanScope(wrapper, PLAN_NO);
    String sql = wrapper.toSQL();
    assertThat(sql).contains("IN");
    assertThat(sql).contains("P001");
    assertThat(sql).contains("P002");
  }
}
