package com.pension.permission.infrastructure.authorization.spi;

import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.ProductNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;
import com.pension.permission.domain.product.ProductGateway;
import com.pension.permission.types.AssignmentScopeDimension;
import com.pension.permission.types.RoleCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 {@link AgentIdentityAssignment} 实现的 {@link PlanMembershipLookup}.
 *
 * <p>本实现是 authorization 域与 assignment 域之间的接线器：authorization 域通过 SPI 端口
 * 查询"某账号是否是某计划的成员 / 是否在某计划有某角色"，由本类调用 assignment 域的
 * {@link AssignmentRepository} 完成实际查询，避免 authorization 反向依赖 assignment 包。</p>
 *
 * <h3>匹配规则</h3>
 * <ul>
 *   <li>{@link AssignmentScopeDimension#PLAN}：scopeValue 直接等于 planNo.value()，
 *       即该账号被分配到此计划下</li>
 *   <li>{@link AssignmentScopeDimension#CUSTOMER}：通过 {@link ProductGateway#plansOfCustomer}
 *       反查该客户名下的计划集合，判断 planNo 是否在其中；inheritable=true 时同时展开
 *       下级客户的计划</li>
 *   <li>{@link AssignmentScopeDimension#PRODUCT}：通过 {@link ProductGateway#plansOfProduct}
 *       反查该产品下的计划集合</li>
 *   <li>{@link AssignmentScopeDimension#GLOBAL}：所有计划都视为成员</li>
 * </ul>
 *
 * <h3>性能考虑</h3>
 * <p>每次调用会触发一次 {@link AssignmentRepository#findActiveByAccount}，结果在
 * {@link #isMemberOf} 与 {@link #hasRole} 之间不共享缓存。若成为热点，可考虑在
 * 应用层加 Caffeine 本地缓存（按 userNo + planNo 维度）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentBasedPlanMembershipLookup implements PlanMembershipLookup {

  private final AssignmentRepository assignmentRepository;
  private final ProductGateway productGateway;

  @Override
  public boolean isMemberOf(UserNo userNo, PlanNo planNo) {
    if (userNo == null || planNo == null) {
      return false;
    }

    List<AgentIdentityAssignment> assignments = assignmentRepository.findActiveByAccount(userNo);
    return assignments.stream().anyMatch(a -> coversPlan(a, planNo));
  }

  @Override
  public boolean hasRole(UserNo userNo, PlanNo planNo, RoleCode roleCode) {
    if (userNo == null || planNo == null || roleCode == null) {
      return false;
    }

    List<AgentIdentityAssignment> assignments = assignmentRepository.findActiveByAccount(userNo);
    return assignments.stream()
      .anyMatch(a -> coversPlan(a, planNo) && roleCode.equals(a.roleCode()));
  }

  /**
   * 判断身份分配是否覆盖指定计划.
   *
   * <p>按 scopeDimension 分派：</p>
   * <ul>
   *   <li>PLAN：scopeValue 直接匹配</li>
   *   <li>GLOBAL：恒为 true</li>
   *   <li>CUSTOMER：通过 ProductGateway 反查客户名下计划集合</li>
   *   <li>PRODUCT：通过 ProductGateway 反查产品名下计划集合</li>
   * </ul>
   */
  private boolean coversPlan(AgentIdentityAssignment assignment, PlanNo planNo) {
    AssignmentScopeDimension dimension = assignment.scopeDimension();
    String scopeValue = assignment.scopeValue();

    return switch (dimension) {
      case PLAN -> planNo.value().equals(scopeValue);
      case GLOBAL -> true;
      case CUSTOMER -> coversPlanUnderCustomer(scopeValue, assignment.isInheritable(), planNo);
      case PRODUCT -> coversPlanUnderProduct(scopeValue, planNo);
    };
  }

  /**
   * CUSTOMER 维度匹配：判断 planNo 是否在该客户名下的计划集合中.
   *
   * @param customerNoValue 客户编号
   * @param inheritable     是否级联下级客户
   * @param planNo          目标计划
   */
  private boolean coversPlanUnderCustomer(String customerNoValue, boolean inheritable, PlanNo planNo) {
    CustomerNo customerNo = CustomerNo.of(customerNoValue);
    List<PlanNo> plans = productGateway.plansOfCustomer(customerNo, inheritable);
    return plans.contains(planNo);
  }

  /**
   * PRODUCT 维度匹配：判断 planNo 是否在该产品名下的计划集合中.
   *
   * @param productNoValue 产品编号
   * @param planNo         目标计划
   */
  private boolean coversPlanUnderProduct(String productNoValue, PlanNo planNo) {
    ProductNo productNo = ProductNo.of(productNoValue);
    List<PlanNo> plans = productGateway.plansOfProduct(productNo);
    return plans.contains(planNo);
  }
}
