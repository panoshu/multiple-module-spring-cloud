package com.example.approval.domain.service;

import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.root.ApprovalInstance;
import com.example.approval.domain.gateway.RoleUserGateway;
import com.example.approval.types.enums.NodeType;
import com.example.shared.domain.annotation.DomainService;
import com.example.shared.identifier.id.UserNo;

import java.util.ArrayList;
import java.util.List;

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
      // 指定角色审批：从角色中获取用户
      List<UserNo> approvers = new ArrayList<>();
      for (String roleId : node.roleIds()) {
        List<UserNo> users = roleUserGateway.getUsersByRole(roleId);
        approvers.addAll(users);
      }
      return approvers;
    }

    // 根据节点类型解析
    NodeType nodeType = node.nodeType();

    if (nodeType == NodeType.SAME_PLAN) {
      // 同方案审批：获取同一计划下的用户
      // 这里需要从实例中获取发起人方案
      String initiatorPlan = instance.initiatorPlan();
      if (initiatorPlan != null) {
        // TODO: 需要根据方案ID查询用户
        // 暂时返回节点配置的用户列表
        return node.approverIds();
      }
    }

    if (nodeType == NodeType.LEVEL_UP) {
      // 上一级审批：需要通过计划层级网关获取上级计划的用户
      // TODO: 实现层级审批逻辑
      return node.approverIds();
    }

    if (nodeType == NodeType.SPECIFIED_PLAN) {
      // 指定方案审批：直接使用节点配置的用户
      return node.approverIds();
    }

    // 默认返回节点配置的用户列表
    return node.approverIds();
  }
}
