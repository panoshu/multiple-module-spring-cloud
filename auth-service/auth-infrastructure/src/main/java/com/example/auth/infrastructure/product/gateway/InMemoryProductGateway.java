package com.example.auth.infrastructure.product.gateway;

import com.example.auth.domain.product.CustomerSnapshot;
import com.example.auth.domain.product.PlanSnapshot;
import com.example.auth.domain.product.ProductGateway;
import com.example.auth.domain.product.ProductSnapshot;
import com.example.core.types.CustomerNo;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * 可替换的内存 {@link ProductGateway} 实现（立项批次 67）。
 * <p>
 * 从配置加载计划层级快照，让 SAME_PLAN / LEVEL_UP 在示例环境可工作；真实外部
 * 主数据系统接入时，用标 {@code @Primary} 的 {@code HttpProductGateway} 覆盖即可。
 * 本实现只在 {@code auth.org-directory.enabled=true} 时由配置类注册，默认不启用，
 * {@link StubProductGateway} 保持唯一实现、零行为改变。
 *
 * <p>客户层级不可得，{@code ancestorsOf} 返回含自身的单元素链；产品 / 客户主数据
 * 返回空，仅计划维度数据由配置提供。</p>
 */
@Slf4j
public class InMemoryProductGateway implements ProductGateway {

  private final List<PlanSnapshot> plans;

  public InMemoryProductGateway(List<PlanSnapshot> plans) {
    this.plans = List.copyOf(plans);
  }

  @Override
  public Optional<PlanSnapshot> findPlan(PlanNo planNo) {
    return plans.stream().filter(plan -> plan.planNo().equals(planNo)).findFirst();
  }

  @Override
  public Optional<ProductSnapshot> findProduct(ProductNo productNo) {
    log.warn("InMemoryProductGateway.findProduct 未提供产品主数据，返回空");
    return Optional.empty();
  }

  @Override
  public Optional<CustomerSnapshot> findCustomer(CustomerNo customerNo) {
    log.warn("InMemoryProductGateway.findCustomer 未提供客户主数据，返回空");
    return Optional.empty();
  }

  @Override
  public List<CustomerNo> ancestorsOf(CustomerNo customerNo) {
    return List.of(customerNo);
  }

  @Override
  public List<CustomerNo> descendantsOf(CustomerNo customerNo) {
    return List.of();
  }

  @Override
  public List<PlanNo> plansOfCustomer(CustomerNo customerNo, boolean includeDescendants) {
    return plans.stream()
      .filter(plan -> plan.customerNo().equals(customerNo))
      .map(PlanSnapshot::planNo)
      .toList();
  }

  @Override
  public List<PlanNo> plansOfProduct(ProductNo productNo) {
    return plans.stream()
      .filter(plan -> plan.productNo().equals(productNo))
      .map(PlanSnapshot::planNo)
      .toList();
  }
}
