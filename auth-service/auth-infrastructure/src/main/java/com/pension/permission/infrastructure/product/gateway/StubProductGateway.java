package com.pension.permission.infrastructure.product.gateway;

import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.ProductNo;
import com.pension.permission.domain.product.ProductGateway;
import com.pension.permission.domain.product.CustomerSnapshot;
import com.pension.permission.domain.product.PlanSnapshot;
import com.pension.permission.domain.product.ProductSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 * <p>本桩使用 {@link ConditionalOnMissingBean} 注册，当未来真实实现
 * （如 {@code HttpProductGateway}）被注册为 Spring Bean 时，本桩自动退让，
 * 无需修改任何代码或配置。</p>
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
@ConditionalOnMissingBean(ProductGateway.class)
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
