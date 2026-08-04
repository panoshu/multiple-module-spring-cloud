package com.pension.permission.domain.product;

import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.ProductNo;

import java.util.List;
import java.util.Optional;

/**
 * 组织与计划域的防腐层端口(Anti-Corruption Layer Port)。
 * 客户/产品/计划的主数据都在外部系统维护，本系统只通过这层接口查询只读投影，
 * 具体实现(调用外部API+本地缓存/同步)属于基础设施层，不在领域层范围内。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/8/3 11:08
 */
public interface ProductGateway {

  Optional<PlanSnapshot> findPlan(PlanNo PlanNo);

  Optional<ProductSnapshot> findProduct(ProductNo ProductNo);

  Optional<CustomerSnapshot> findCustomer(CustomerNo CustomerNo);

  /**
   * 获取计划快照，计划必须存在。
   * 找不到时由 Gateway 统一抛出 PlanNotFoundException，调用方无需手动处理。
   */
  default PlanSnapshot requirePlan(PlanNo planNo) {
    return findPlan(planNo)
      .orElseThrow(() -> new DomainException(ProductError.PLAN_NOT_FOUND).withContext("planNo", planNo.value()));
  }

  default ProductSnapshot requireProduct(ProductNo productNo) {
    return findProduct(productNo)
      .orElseThrow(() -> new DomainException(ProductError.PRODUCT_NOT_FOUND).withContext("productNo", productNo.value()));
  }

  default CustomerSnapshot requireCustomer(CustomerNo customerNo) {
    return findCustomer(customerNo)
      .orElseThrow(() -> new DomainException(ProductError.CUSTOMER_NOT_FOUND).withContext("customerNo", customerNo.value()));
  }

  /**
   * 返回该客户从自身到最顶层的整条上级链(含自身)，用于客户维度可继承(inheritable)规则的匹配。
   * 顺序：[自身, 父级, 祖父级, ...]
   */
  List<CustomerNo> ancestorsOf(CustomerNo CustomerNo);

  /**
   * 返回该客户所有下级客户(不含自身)
   */
  List<CustomerNo> descendantsOf(CustomerNo CustomerNo);

  /**
   * 某客户名下的计划集合。includeDescendants=true 时同时展开其所有下级客户的计划
   * (对应"设置权限时可选择其下属企业是否继承")。
   */
  List<PlanNo> plansOfCustomer(CustomerNo CustomerNo, boolean includeDescendants);

  /**
   * 某产品下的全部计划
   */
  List<PlanNo> plansOfProduct(ProductNo ProductNo);
}
