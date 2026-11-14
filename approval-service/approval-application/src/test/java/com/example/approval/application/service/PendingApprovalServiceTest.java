package com.example.approval.application.service;

import com.example.approval.api.dto.PendingApprovalDTO;
import com.example.approval.api.request.ListMyPendingApprovalsRequest;
import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.root.ApprovalFlow;
import com.example.approval.domain.aggregate.root.ApprovalIdGenerator;
import com.example.approval.domain.aggregate.root.ApprovalInstance;
import com.example.approval.domain.gateway.RoleUserGateway;
import com.example.approval.domain.repository.ApprovalFlowRepository;
import com.example.approval.domain.repository.ApprovalInstanceRepository;
import com.example.approval.domain.valueobject.FlowName;
import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.ApprovalInstanceId;
import com.example.approval.types.enums.NodeType;
import com.example.core.types.ApplicationId;
import com.example.core.types.PlanNo;
import com.example.core.types.UserNo;
import com.example.shared.domain.event.EventBus;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.page.Pagination;
import com.example.shared.web.core.dto.PageData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批次 55/61（P3-1 能力 C）：{@code listMyPendingApprovals} 指定用户 + 角色节点过滤、
 * 内存分页与 DTO 组装；批次 61 补 {@code SPECIFIED_ROLE} 节点按 planNo 批量展开。
 */
@DisplayName("待审批列表查询（批次 55/61）")
class PendingApprovalServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0);
  private static final NodeOrder CURRENT_NODE = NodeOrder.of(1);

  private ApprovalInstanceRepository instanceRepository;
  private ApprovalFlowRepository flowRepository;
  private RoleUserGateway roleUserGateway;
  private ApprovalInstanceService service;
  private final List<ApprovalInstance> candidates = new ArrayList<>();

  @BeforeEach
  void setUp() {
    candidates.clear();
    instanceRepository = mock(ApprovalInstanceRepository.class);
    flowRepository = mock(ApprovalFlowRepository.class);
    roleUserGateway = mock(RoleUserGateway.class);
    service = new ApprovalInstanceService(
      instanceRepository, flowRepository, mock(EventBus.class),
      mock(IdService.class), mock(ApprovalIdGenerator.class), roleUserGateway,
      mock(com.example.approval.domain.service.ApprovalNodeResolver.class));
    when(instanceRepository.findByStatuses(any())).thenAnswer(inv -> List.copyOf(candidates));
  }

  @Test
  @DisplayName("指定用户节点命中，组装 DTO 与分页元数据")
  void listMyPendingApprovals_matchesSpecifiedUserAndMapsDto() {
    candidate(1L, 1L, true, List.of("U-1", "U-9"));
    candidate(2L, 2L, true, List.of("U-1"));

    PageData<PendingApprovalDTO> result = service.listMyPendingApprovals(
      new ListMyPendingApprovalsRequest("U-1", Pagination.of(0, 10)));

    assertThat(result.totalCount()).isEqualTo(2);
    assertThat(result.currentStart()).isZero();
    assertThat(result.returnedCount()).isEqualTo(2);
    assertThat(result.hasMore()).isFalse();
    assertThat(result.items()).hasSize(2);
    PendingApprovalDTO first = result.items().get(0);
    assertThat(first.instanceId()).isEqualTo(ApprovalInstanceId.of(1L));
    assertThat(first.businessNo()).isEqualTo("APP-1");
    assertThat(first.businessType()).isEqualTo("ANNUITY");
    assertThat(first.flowName()).isEqualTo("测试流");
    assertThat(first.currentNodeName()).isEqualTo("SPECIFIED_PLAN");
    assertThat(first.initiator()).isEqualTo("INITIATOR");
    assertThat(first.createdAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("角色审批节点（网关无提供方）与审批人不匹配的实例被排除")
  void listMyPendingApprovals_excludesRoleNodesAndOtherApprovers() {
    candidate(1L, 1L, false, List.of());
    candidate(2L, 2L, true, List.of("U-2"));

    PageData<PendingApprovalDTO> result = service.listMyPendingApprovals(
      new ListMyPendingApprovalsRequest("U-1", Pagination.of(0, 10)));

    assertThat(result.isEmpty()).isTrue();
    assertThat(result.totalCount()).isZero();
    assertThat(result.hasMore()).isFalse();
  }

  @Test
  @DisplayName("无候选实例时返回空 PageData")
  void listMyPendingApprovals_emptyCandidates() {
    PageData<PendingApprovalDTO> result = service.listMyPendingApprovals(
      new ListMyPendingApprovalsRequest("U-1", Pagination.of(0, 10)));

    assertThat(result.isEmpty()).isTrue();
    assertThat(result.currentStart()).isZero();
  }

  @Test
  @DisplayName("第二页按 startPos 切片，hasMore 按剩余量计算")
  void listMyPendingApprovals_secondPage() {
    for (long i = 1; i <= 7; i++) {
      candidate(i, i, true, List.of("U-1"));
    }

    PageData<PendingApprovalDTO> result = service.listMyPendingApprovals(
      new ListMyPendingApprovalsRequest("U-1", Pagination.of(5, 5)));

    assertThat(result.totalCount()).isEqualTo(7);
    assertThat(result.currentStart()).isEqualTo(5);
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).instanceId()).isEqualTo(ApprovalInstanceId.of(6L));
    assertThat(result.hasMore()).isFalse();
  }

  @Test
  @DisplayName("角色节点命中：按当前计划展开角色用户后判断审批人")
  void listMyPendingApprovals_roleNodeHitsExpandedApprover() {
    roleCandidate(1L, 1L, "P-1", List.of("R1"));
    when(roleUserGateway.getUsersByRoles(List.of("R1"), PlanNo.of("P-1")))
      .thenReturn(Map.of("R1", List.of(UserNo.of("U-1"))));

    PageData<PendingApprovalDTO> result = service.listMyPendingApprovals(
      new ListMyPendingApprovalsRequest("U-1", Pagination.of(0, 10)));

    assertThat(result.totalCount()).isEqualTo(1);
    assertThat(result.items().get(0).instanceId()).isEqualTo(ApprovalInstanceId.of(1L));
    assertThat(result.items().get(0).currentNodeName()).isEqualTo("SPECIFIED_PLAN");
  }

  @Test
  @DisplayName("同一计划的多角色合并为一次批量调用，规避 N+1")
  void listMyPendingApprovals_batchesRoleIdsPerPlan() {
    roleCandidate(1L, 1L, "P-1", List.of("R1"));
    roleCandidate(2L, 2L, "P-1", List.of("R2"));
    when(roleUserGateway.getUsersByRoles(any(), any()))
      .thenReturn(Map.of("R1", List.of(UserNo.of("U-1")), "R2", List.of()));

    service.listMyPendingApprovals(
      new ListMyPendingApprovalsRequest("U-1", Pagination.of(0, 10)));

    verify(roleUserGateway).getUsersByRoles(List.of("R1", "R2"), PlanNo.of("P-1"));
  }

  @Test
  @DisplayName("auth 不可用（网关返回空）时角色节点 fail-closed 不命中")
  void listMyPendingApprovals_roleNodeFailsClosedWhenGatewayEmpty() {
    roleCandidate(1L, 1L, "P-1", List.of("R1"));
    when(roleUserGateway.getUsersByRoles(any(), any())).thenReturn(Map.of());

    PageData<PendingApprovalDTO> result = service.listMyPendingApprovals(
      new ListMyPendingApprovalsRequest("U-1", Pagination.of(0, 10)));

    assertThat(result.isEmpty()).isTrue();
  }

  @Test
  @DisplayName("角色节点缺少 currentPlan 时跳过且不调用网关")
  void listMyPendingApprovals_roleNodeWithoutPlanSkipped() {
    roleCandidate(1L, 1L, null, List.of("R1"));

    PageData<PendingApprovalDTO> result = service.listMyPendingApprovals(
      new ListMyPendingApprovalsRequest("U-1", Pagination.of(0, 10)));

    assertThat(result.isEmpty()).isTrue();
    verify(roleUserGateway, never()).getUsersByRoles(any(), any());
  }

  /** 造一个候选实例及其审批流/当前节点定义；{@code specifiedUser=false} 模拟角色审批节点。 */
  private void candidate(long instanceId, long flowId, boolean specifiedUser, List<String> approverIds) {
    ApprovalInstance instance = mock(ApprovalInstance.class);
    when(instance.id()).thenReturn(ApprovalInstanceId.of(instanceId));
    when(instance.flowId()).thenReturn(ApprovalFlowId.of(flowId));
    when(instance.currentNodeOrder()).thenReturn(CURRENT_NODE);
    when(instance.businessApplicationId()).thenReturn(new ApplicationId("APP-" + instanceId));
    when(instance.businessType()).thenReturn("ANNUITY");
    when(instance.createdBy()).thenReturn("INITIATOR");
    when(instance.createdAt()).thenReturn(NOW);
    candidates.add(instance);

    ApprovalNode node = mock(ApprovalNode.class);
    when(node.isSpecifiedUserApproval()).thenReturn(specifiedUser);
    when(node.approverIds()).thenReturn(approverIds.stream().map(UserNo::of).toList());
    when(node.nodeType()).thenReturn(NodeType.SPECIFIED_PLAN);

    ApprovalFlow flow = mock(ApprovalFlow.class);
    when(flow.flowName()).thenReturn(FlowName.of("测试流"));
    when(flow.getNode(CURRENT_NODE)).thenReturn(Optional.of(node));
    when(flowRepository.load(ApprovalFlowId.of(flowId))).thenReturn(Optional.of(flow));
  }

  /** 造一个 SPECIFIED_ROLE 节点候选；currentPlan 为 null 模拟无 PLAN scope。 */
  private void roleCandidate(long instanceId, long flowId, String currentPlan, List<String> roleIds) {
    ApprovalInstance instance = mock(ApprovalInstance.class);
    when(instance.id()).thenReturn(ApprovalInstanceId.of(instanceId));
    when(instance.flowId()).thenReturn(ApprovalFlowId.of(flowId));
    when(instance.currentNodeOrder()).thenReturn(CURRENT_NODE);
    when(instance.currentPlan()).thenReturn(currentPlan);
    when(instance.businessApplicationId()).thenReturn(new ApplicationId("APP-" + instanceId));
    when(instance.businessType()).thenReturn("ANNUITY");
    when(instance.createdBy()).thenReturn("INITIATOR");
    when(instance.createdAt()).thenReturn(NOW);
    candidates.add(instance);

    ApprovalNode node = mock(ApprovalNode.class);
    when(node.isSpecifiedUserApproval()).thenReturn(false);
    when(node.isSpecifiedRoleApproval()).thenReturn(true);
    when(node.approverIds()).thenReturn(List.of());
    when(node.roleIds()).thenReturn(roleIds);
    when(node.nodeType()).thenReturn(NodeType.SPECIFIED_PLAN);

    ApprovalFlow flow = mock(ApprovalFlow.class);
    when(flow.flowName()).thenReturn(FlowName.of("测试流"));
    when(flow.getNode(CURRENT_NODE)).thenReturn(Optional.of(node));
    when(flowRepository.load(ApprovalFlowId.of(flowId))).thenReturn(Optional.of(flow));
  }
}
