package com.example.approval.application.service;

import com.example.approval.api.dto.ApprovalRecordDTO;
import com.example.approval.api.request.GetApprovalHistoryRequest;
import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.entity.ApprovalRecord;
import com.example.approval.domain.aggregate.entity.NodeExecution;
import com.example.approval.domain.aggregate.root.ApprovalFlow;
import com.example.approval.domain.aggregate.root.ApprovalIdGenerator;
import com.example.approval.domain.aggregate.root.ApprovalInstance;
import com.example.approval.domain.repository.ApprovalFlowRepository;
import com.example.approval.domain.repository.ApprovalInstanceRepository;
import com.example.approval.domain.valueobject.ApprovalOpinion;
import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.ApprovalInstanceId;
import com.example.approval.types.RecordId;
import com.example.approval.types.enums.ApprovalAction;
import com.example.approval.types.enums.NodeType;
import com.example.core.types.UserNo;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.contract.IdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 批次 53（P3-1 能力 A）：{@code getApprovalHistory} 从聚合根提取审批记录并组装 DTO。
 * 现状返回空列表；本类先红后绿，覆盖多记录映射、空历史、节点名降级、实例不存在四类。
 */
@DisplayName("审批历史查询（批次 53）")
class ApprovalHistoryServiceTest {

  private static final ApprovalInstanceId INSTANCE_ID = ApprovalInstanceId.of(100L);
  private static final ApprovalFlowId FLOW_ID = ApprovalFlowId.of(1L);
  private static final LocalDateTime OPERATED_AT = LocalDateTime.of(2026, 11, 14, 10, 0);

  private ApprovalInstanceRepository instanceRepository;
  private ApprovalFlowRepository flowRepository;
  private ApprovalInstanceService service;

  @BeforeEach
  void setUp() {
    instanceRepository = mock(ApprovalInstanceRepository.class);
    flowRepository = mock(ApprovalFlowRepository.class);
    service = new ApprovalInstanceService(
      instanceRepository, flowRepository, mock(EventBus.class),
      mock(IdService.class), mock(ApprovalIdGenerator.class),
      mock(com.example.approval.domain.gateway.RoleUserGateway.class));
  }

  @Test
  @DisplayName("多节点多记录全部映射，nodeName 取节点类型且字段完整")
  void getApprovalHistory_mapsRecordsFromAllExecutions() {
    ApprovalRecord approved = record(1L, "U-1", ApprovalAction.APPROVE, "同意");
    ApprovalRecord rejected = record(2L, "U-2", ApprovalAction.REJECT, "驳回");
    NodeExecution first = execution(NodeOrder.of(1), approved);
    NodeExecution second = execution(NodeOrder.of(2), rejected);
    stubInstance(List.of(first, second));
    stubFlowWithNode(NodeType.SAME_PLAN);

    List<ApprovalRecordDTO> result = service.getApprovalHistory(new GetApprovalHistoryRequest(INSTANCE_ID));

    assertThat(result).hasSize(2);
    assertThat(result.get(0).recordId()).isEqualTo(RecordId.of(1L));
    assertThat(result.get(0).instanceId()).isEqualTo(INSTANCE_ID);
    assertThat(result.get(0).nodeName()).isEqualTo("SAME_PLAN");
    assertThat(result.get(0).actionType()).isEqualTo("APPROVE");
    assertThat(result.get(0).operator()).isEqualTo("U-1");
    assertThat(result.get(0).comment()).isEqualTo("同意");
    assertThat(result.get(0).operatedAt()).isEqualTo(OPERATED_AT);
    assertThat(result.get(1).actionType()).isEqualTo("REJECT");
    assertThat(result.get(1).operator()).isEqualTo("U-2");
  }

  @Test
  @DisplayName("无审批记录时返回空列表")
  void getApprovalHistory_emptyWhenNoRecords() {
    stubInstance(List.of(execution(NodeOrder.of(1))));
    stubFlowWithNode(NodeType.SAME_PLAN);

    assertThat(service.getApprovalHistory(new GetApprovalHistoryRequest(INSTANCE_ID))).isEmpty();
  }

  @Test
  @DisplayName("审批流定义缺失时 nodeName 降级为 NODE-<order>")
  void getApprovalHistory_fallsBackWhenFlowMissing() {
    stubInstance(List.of(execution(NodeOrder.of(3), record(9L, "U-9", ApprovalAction.TRANSFER, "转交"))));
    when(flowRepository.load(any())).thenReturn(Optional.empty());

    List<ApprovalRecordDTO> result = service.getApprovalHistory(new GetApprovalHistoryRequest(INSTANCE_ID));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).nodeName()).isEqualTo("NODE-3");
  }

  @Test
  @DisplayName("实例不存在时抛 DomainException")
  void getApprovalHistory_throwsWhenInstanceMissing() {
    when(instanceRepository.load(INSTANCE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getApprovalHistory(new GetApprovalHistoryRequest(INSTANCE_ID)))
      .isInstanceOf(DomainException.class);
  }

  private ApprovalRecord record(long id, String operator, ApprovalAction action, String comment) {
    ApprovalRecord record = mock(ApprovalRecord.class);
    when(record.id()).thenReturn(RecordId.of(id));
    when(record.approverId()).thenReturn(UserNo.of(operator));
    when(record.action()).thenReturn(action);
    when(record.opinion()).thenReturn(ApprovalOpinion.of(comment));
    when(record.operatedAt()).thenReturn(OPERATED_AT);
    return record;
  }

  private NodeExecution execution(NodeOrder order, ApprovalRecord... records) {
    NodeExecution execution = mock(NodeExecution.class);
    when(execution.nodeOrder()).thenReturn(order);
    when(execution.getApprovalRecords()).thenReturn(List.of(records));
    return execution;
  }

  private void stubInstance(List<NodeExecution> executions) {
    ApprovalInstance instance = mock(ApprovalInstance.class);
    when(instance.id()).thenReturn(INSTANCE_ID);
    when(instance.flowId()).thenReturn(FLOW_ID);
    when(instance.getNodeExecutions()).thenReturn(executions);
    when(instanceRepository.load(INSTANCE_ID)).thenReturn(Optional.of(instance));
  }

  private void stubFlowWithNode(NodeType nodeType) {
    ApprovalNode node = mock(ApprovalNode.class);
    when(node.nodeType()).thenReturn(nodeType);
    ApprovalFlow flow = mock(ApprovalFlow.class);
    when(flow.getNode(any())).thenReturn(Optional.of(node));
    when(flowRepository.load(FLOW_ID)).thenReturn(Optional.of(flow));
  }
}
