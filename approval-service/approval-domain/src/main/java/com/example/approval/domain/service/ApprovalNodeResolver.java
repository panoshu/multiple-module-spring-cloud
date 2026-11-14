package com.example.approval.domain.service;

import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.root.ApprovalInstance;
import com.example.approval.domain.gateway.RoleUserGateway;
import com.example.approval.types.enums.NodeType;
import com.example.shared.domain.annotation.DomainService;
import com.example.core.types.PlanNo;
import com.example.core.types.UserNo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 审批人解析服务
 * 解析审批节点的实际审批人
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@DomainService
public class ApprovalNodeResolver {

  /**
   * 解析审批节点的实际审批人
   *
   * @param node            审批节点
   * @param instance        审批实例
   * @param roleUserGateway 角色用户网关
   * @return 审批人列表
   */
  public List<UserNo> resolveApprovers(ApprovalNode node, ApprovalInstance instance,
                                       RoleUserGateway roleUserGateway) {
    if (node == null || instance == null || roleUserGateway == null) {
      return List.of();
    }

    // 根据审批人类型解析
    if (node.isSpecifiedUserApproval()) {
      // 指定用户审批
      return node.approverIds();
    }

    if (node.isSpecifiedRoleApproval()) {
      // 指定角色审批：按实例当前计划（PLAN 精确 scope）批量展开角色用户
      String plan = instance.currentPlan();
      if (plan == null || plan.isBlank()) {
        return List.of();
      }
      Map<String, List<UserNo>> byRole =
        roleUserGateway.getUsersByRoles(node.roleIds(), PlanNo.of(plan));
      if (byRole == null || byRole.isEmpty()) {
        return List.of();
      }
      // 按节点配置的角色顺序展开，保证结果确定；跨角色重复用户去重
      List<UserNo> approvers = new ArrayList<>();
      for (String roleId : node.roleIds()) {
        approvers.addAll(byRole.getOrDefault(roleId, List.of()));
      }
      return approvers.stream().distinct().toList();
    }

    // 根据节点类型解析
    NodeType nodeType = node.nodeType();

    if (nodeType == NodeType.SAME_PLAN) {
      // 同计划审批：按发起人计划查同计划成员，并排除发起人本人
      String initiatorPlan = instance.initiatorPlan();
      if (initiatorPlan == null || initiatorPlan.isBlank()) {
        return List.of();
      }
      String initiator = instance.createdBy();
      UserNo excludeUser = (initiator == null || initiator.isBlank())
        ? null : UserNo.of(initiator);
      List<UserNo> users =
        roleUserGateway.getUsersInSamePlan(PlanNo.of(initiatorPlan), excludeUser);
      return users == null ? List.of() : users;
    }

    if (nodeType == NodeType.LEVEL_UP) {
      // 上一级审批：取直接父计划成员；TerminalLevel 多级追溯列为后续，
      // 到顶（无父计划）由网关 fail-closed 返回空，不越级、不回退节点配置
      String currentPlan = instance.currentPlan();
      if (currentPlan == null || currentPlan.isBlank()) {
        return List.of();
      }
      String initiator = instance.createdBy();
      UserNo excludeUser = (initiator == null || initiator.isBlank())
        ? null : UserNo.of(initiator);
      List<UserNo> users =
        roleUserGateway.getUsersInParentPlan(PlanNo.of(currentPlan), excludeUser);
      return users == null ? List.of() : users;
    }

    if (nodeType == NodeType.SPECIFIED_PLAN) {
      // 指定方案审批：直接使用节点配置的用户
      return node.approverIds();
    }

    // 默认返回节点配置的用户列表
    return node.approverIds();
  }
}
