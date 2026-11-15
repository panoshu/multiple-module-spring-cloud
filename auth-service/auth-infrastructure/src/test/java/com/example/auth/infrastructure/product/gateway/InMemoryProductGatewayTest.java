package com.example.auth.infrastructure.product.gateway;

import com.example.auth.domain.product.PlanSnapshot;
import com.example.core.types.CustomerNo;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 批次 67：可替换内存组织目录网关契约（先红后绿）。 */
@DisplayName("内存组织目录网关（批次 67）")
class InMemoryProductGatewayTest {

  private final InMemoryProductGateway gateway = new InMemoryProductGateway(List.of(
    snapshot("P-1", "P-0"), snapshot("P-0", null)));

  @Test
  @DisplayName("findPlan 返回配置内的计划快照")
  void findPlan_returnsConfiguredSnapshot() {
    assertThat(gateway.findPlan(PlanNo.of("P-1"))).isPresent();
  }

  @Test
  @DisplayName("findPlan 未知计划返回空")
  void findPlan_emptyForUnknown() {
    assertThat(gateway.findPlan(PlanNo.of("P-X"))).isEmpty();
  }

  @Test
  @DisplayName("parentPlanNo 以 Optional 映射，根计划为空")
  void parentPlanNo_mappedToOptional() {
    assertThat(gateway.findPlan(PlanNo.of("P-1")).orElseThrow().parentPlanNo())
      .contains(PlanNo.of("P-0"));
    assertThat(gateway.findPlan(PlanNo.of("P-0")).orElseThrow().parentPlanNo()).isEmpty();
  }

  @Test
  @DisplayName("plansOfCustomer / plansOfProduct 按客户与产品过滤")
  void plansOfCustomerAndProduct_filterByScope() {
    assertThat(gateway.plansOfCustomer(CustomerNo.of("C-1"), false))
      .containsExactlyInAnyOrder(PlanNo.of("P-1"), PlanNo.of("P-0"));
    assertThat(gateway.plansOfProduct(ProductNo.of("PROD-1")))
      .containsExactlyInAnyOrder(PlanNo.of("P-1"), PlanNo.of("P-0"));
  }

  @Test
  @DisplayName("ancestorsOf 返回含自身的链（无外部客户层级数据）")
  void ancestorsOf_containsSelf() {
    assertThat(gateway.ancestorsOf(CustomerNo.of("C-1"))).containsExactly(CustomerNo.of("C-1"));
  }

  private PlanSnapshot snapshot(String planNo, String parentPlanNo) {
    return new PlanSnapshot(PlanNo.of(planNo), ProductNo.of("PROD-1"), CustomerNo.of("C-1"),
      Optional.ofNullable(parentPlanNo).map(PlanNo::of), "示例计划", Instant.EPOCH);
  }
}
