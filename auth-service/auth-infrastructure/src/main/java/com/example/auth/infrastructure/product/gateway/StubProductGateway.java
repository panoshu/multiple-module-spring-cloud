package com.example.auth.infrastructure.product.gateway;

import com.example.core.types.CustomerNo;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.auth.domain.product.CustomerSnapshot;
import com.example.auth.domain.product.PlanSnapshot;
import com.example.auth.domain.product.ProductGateway;
import com.example.auth.domain.product.ProductSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@link ProductGateway} 桩实现.
 *
 * <p>客户/产品/计划的主数据都在外部系统维护，本系统通过防腐层端口查询只读投影。
 * 在外部服务尚未接入前，由本桩实现提供占位，返回空结果并打印 warning 日志。</p>
 *
 * <h3>替换策略</h3>
 * <p>接入真实实现（如 {@code HttpProductGateway}）时：真实实现标注 {@code @Primary}
 * 直接覆盖本桩（注入点优先取 primary），或删除本桩。注意不要在组件扫描类上使用
 * {@code @ConditionalOnMissingBean} 实现退让——扫描期条件评估会自匹配导致桩
 * 被自己跳过、容器装配失败（实测教训）。</p>
 *
 * <h3>当前行为</h3>
 * <ul>
 *   <li>{@code findPlan/findProduct/findCustomer}：返回 {@link Optional#empty()}，
 *       调用方通过 {@code requireXxx()} 会抛出对应的 NotFound 异常</li>
 *   <li>{@code ancestorsOf/descendantsOf/plansOfCustomer/plansOfProduct}：返回空列表</li>
 * </ul>
 *
 * <p><strong>注意</strong>：本桩返回空结果意味着所有依赖 ProductGateway 的领域服务
 * （如 AuthorizationEngine、PlanReachabilityService、IdentityResolutionService）
 * 在未接入真实实现前无法正常工作，仅可用于容器启动验证。</p>
 */
@Slf4j
@Component
public class StubProductGateway implements ProductGateway {

  private static final String STUB_WARNING = "StubProductGateway.{} 桩实现被调用，返回空结果。后续需接入真实外部服务";

  @Override
  public Optional<PlanSnapshot> findPlan(PlanNo planNo) {
    log.warn(STUB_WARNING, "findPlan");
    return Optional.empty();
  }

  @Override
  public Optional<ProductSnapshot> findProduct(ProductNo productNo) {
    log.warn(STUB_WARNING, "findProduct");
    return Optional.empty();
  }

  @Override
  public Optional<CustomerSnapshot> findCustomer(CustomerNo customerNo) {
    log.warn(STUB_WARNING, "findCustomer");
    return Optional.empty();
  }

  @Override
  public List<CustomerNo> ancestorsOf(CustomerNo customerNo) {
    log.warn(STUB_WARNING, "ancestorsOf");
    return List.of();
  }

  @Override
  public List<CustomerNo> descendantsOf(CustomerNo customerNo) {
    log.warn(STUB_WARNING, "descendantsOf");
    return List.of();
  }

  @Override
  public List<PlanNo> plansOfCustomer(CustomerNo customerNo, boolean includeDescendants) {
    log.warn(STUB_WARNING, "plansOfCustomer");
    return List.of();
  }

  @Override
  public List<PlanNo> plansOfProduct(ProductNo productNo) {
    log.warn(STUB_WARNING, "plansOfProduct");
    return List.of();
  }
}
