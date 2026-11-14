package com.example.auth.application.assignment;

import com.example.auth.domain.assignment.service.PlanReachabilityService;
import com.example.auth.domain.product.PlanSnapshot;
import com.example.auth.domain.product.ProductGateway;
import com.example.core.types.PlanNo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 组织目录查询应用服务（立项批次 67）。
 * <p>
 * 只做编排与编号提取，不含权限判断：计划成员复用 {@link PlanReachabilityService}
 * 的 PLAN / PRODUCT / CUSTOMER 继承匹配，父计划取 {@link PlanSnapshot#parentPlanNo()}。
 * 组织目录无该计划数据时 fail-closed 返回空 / {@code null}，避免把未知计划的
 * 全表分配误当作同计划成员或越级审批人。
 *
 * @author auth-application
 */
@Service
@RequiredArgsConstructor
public class PlanDirectoryQueryService {

  private final PlanReachabilityService planReachabilityService;
  private final ProductGateway productGateway;

  /**
   * 查询计划范围内的有效用户编号（可排除发起人）。
   *
   * @param planNo       计划号
   * @param excludeUserNo 需排除的用户编号（可空）
   * @return 去重后的用户编号列表；计划未知时为空列表（fail-closed）
   */
  public List<String> listMembers(String planNo, String excludeUserNo) {
    PlanNo plan = PlanNo.of(planNo);
    if (productGateway.findPlan(plan).isEmpty()) {
      // 组织目录无该计划的层级数据：fail-closed 返回空，避免把全表分配误当作同计划成员
      return List.of();
    }

    return planReachabilityService.listAssignmentsForPlan(plan).stream()
      .map(assignment -> assignment.userNo().value())
      .filter(userNo -> !userNo.equals(excludeUserNo))
      .distinct()
      .toList();
  }

  /**
   * 查询计划的直接父计划号。
   *
   * @param planNo 计划号
   * @return 父计划号；根计划、无层级数据或计划未知时返回 {@code null}
   *         （契约约定的可空值，非异常情况，消费侧须 fail-closed 回退）
   */
  public String getParentPlan(String planNo) {
    return productGateway.findPlan(PlanNo.of(planNo))
      .flatMap(PlanSnapshot::parentPlanNo)
      .map(PlanNo::value)
      .orElse(null);
  }
}
