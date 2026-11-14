package com.example.approval.domain.service;

import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.root.ApprovalInstance;
import com.example.approval.domain.gateway.RoleUserGateway;
import com.example.approval.types.enums.NodeType;
import com.example.core.types.PlanNo;
import com.example.core.types.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 批次 62/68：审批节点「实际审批人」解析（框架能力，当前无业务调用方）。 */
@DisplayName("审批节点实际审批人解析（批次 62/68）")
class ApprovalNodeResolverTest {

  private RoleUserGateway roleUserGateway;
  private ApprovalNodeResolver resolver;

  @BeforeEach
  void setUp() {
    roleUserGateway = mock(RoleUserGateway.class);
    resolver = new ApprovalNodeResolver();
  }

  @Test
  @DisplayName("指定用户节点直接返回配置的审批人列表")
  void resolveApprovers_specifiedUserReturnsConfiguredApprovers() {
    ApprovalNode node = mock(ApprovalNode.class);
    when(node.isSpecifiedUserApproval()).thenReturn(true);
    when(node.approverIds()).thenReturn(List.of(UserNo.of("U-1"), UserNo.of("U-2")));

    assertThat(resolver.resolveApprovers(node, mock(ApprovalInstance.class), roleUserGateway))
      .containsExactly(UserNo.of("U-1"), UserNo.of("U-2"));
  }

  @Test
  @DisplayName("角色节点带 currentPlan 时按 PLAN 展开并去重")
  void resolveApprovers_roleExpandsByPlanAndDedupes() {
    ApprovalNode node = mock(ApprovalNode.class);
    when(node.isSpecifiedRoleApproval()).thenReturn(true);
    when(node.roleIds()).thenReturn(List.of("R1", "R2"));
    ApprovalInstance instance = mock(ApprovalInstance.class);
    when(instance.currentPlan()).thenReturn("P-1");
    when(roleUserGateway.getUsersByRoles(List.of("R1", "R2"),
      com.example.core.types.PlanNo.of("P-1")))
      .thenReturn(Map.of(
        "R1", List.of(UserNo.of("U-1"), UserNo.of("U-2")),
        "R2", List.of(UserNo.of("U-2"), UserNo.of("U-3"))));

    assertThat(resolver.resolveApprovers(node, instance, roleUserGateway))
      .containsExactly(UserNo.of("U-1"), UserNo.of("U-2"), UserNo.of("U-3"));
  }

  @Test
  @DisplayName("角色节点缺 currentPlan 时 fail-closed 返回空列表")
  void resolveApprovers_roleWithoutPlanReturnsEmpty() {
    ApprovalNode node = mock(ApprovalNode.class);
    when(node.isSpecifiedRoleApproval()).thenReturn(true);
    when(node.roleIds()).thenReturn(List.of("R1"));
    ApprovalInstance instance = mock(ApprovalInstance.class);
    when(instance.currentPlan()).thenReturn(null);

    assertThat(resolver.resolveApprovers(node, instance, roleUserGateway)).isEmpty();
  }

  @Test
  @DisplayName("节点或实例为空时返回空列表")
  void resolveApprovers_nullInputsReturnEmpty() {
    assertThat(resolver.resolveApprovers(null, mock(ApprovalInstance.class), roleUserGateway)).isEmpty();
    assertThat(resolver.resolveApprovers(mock(ApprovalNode.class), null, roleUserGateway)).isEmpty();
  }

  @Test
  @DisplayName("SAME_PLAN 节点命中同计划用户并排除发起人")
  void resolveApprovers_samePlanHitsSamePlanUsers() {
    ApprovalNode node = mock(ApprovalNode.class);
    when(node.nodeType()).thenReturn(NodeType.SAME_PLAN);
    ApprovalInstance instance = mock(ApprovalInstance.class);
    when(instance.initiatorPlan()).thenReturn("P-1");
    when(instance.createdBy()).thenReturn("U-INIT");
    when(roleUserGateway.getUsersInSamePlan(PlanNo.of("P-1"), UserNo.of("U-INIT")))
      .thenReturn(List.of(UserNo.of("U-1"), UserNo.of("U-2")));

    assertThat(resolver.resolveApprovers(node, instance, roleUserGateway))
      .containsExactly(UserNo.of("U-1"), UserNo.of("U-2"));
  }

  @Test
  @DisplayName("SAME_PLAN 节点缺发起人计划时 fail-closed 返回空，不回退节点配置")
  void resolveApprovers_samePlanWithoutPlanFailsClosed() {
    ApprovalNode node = mock(ApprovalNode.class);
    when(node.nodeType()).thenReturn(NodeType.SAME_PLAN);
    when(node.approverIds()).thenReturn(List.of(UserNo.of("U-FALLBACK")));
    ApprovalInstance instance = mock(ApprovalInstance.class);
    when(instance.initiatorPlan()).thenReturn(null);

    assertThat(resolver.resolveApprovers(node, instance, roleUserGateway)).isEmpty();
  }
}
