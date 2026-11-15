package com.example.approval.application.service;

import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.request.ListApprovalFlowsRequest;
import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.root.ApprovalFlow;
import com.example.approval.domain.repository.ApprovalFlowRepository;
import com.example.approval.domain.service.ApprovalFlowMatcher;
import com.example.approval.domain.valueobject.ApprovalFlowPage;
import com.example.approval.domain.valueobject.FlowName;
import com.example.approval.domain.valueobject.FlowVersion;
import com.example.approval.domain.valueobject.MatchRules;
import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.NodeId;
import com.example.approval.types.enums.ApproverType;
import com.example.approval.types.enums.FlowStatus;
import com.example.approval.types.enums.SignMode;
import com.example.core.types.UserNo;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.domain.event.EventBus;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.page.Pagination;
import com.example.shared.web.core.dto.PageData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批次 54（P3-1 能力 B）：{@code listApprovalFlows} 条件分页与 PageData 组装。
 * 现状抛 {@code UnsupportedOperationException}；本类先红后绿。
 */
@DisplayName("审批流列表查询（批次 54）")
class ApprovalFlowQueryServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0);

  private ApprovalFlowRepository flowRepository;
  private ApprovalFlowService service;

  @BeforeEach
  void setUp() {
    flowRepository = mock(ApprovalFlowRepository.class);
    service = new ApprovalFlowService(
      flowRepository, mock(ApprovalFlowMatcher.class), mock(EventBus.class), mock(IdService.class));
  }

  @Test
  @DisplayName("按条件查一页，DTO 与分页元数据完整")
  void listApprovalFlows_mapsItemsAndPageMeta() {
    when(flowRepository.findByCondition("ANNUITY", "ACTIVE", 10, 10))
      .thenReturn(new ApprovalFlowPage(25, List.of(flow(1L), flow(2L))));

    PageData<ApprovalFlowDTO> result = service.listApprovalFlows(
      new ListApprovalFlowsRequest("ANNUITY", "ACTIVE", Pagination.of(10, 10)));

    assertThat(result.totalCount()).isEqualTo(25);
    assertThat(result.currentStart()).isEqualTo(10);
    assertThat(result.returnedCount()).isEqualTo(2);
    assertThat(result.hasMore()).isTrue();
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).flowName()).isEqualTo("测试流");
    assertThat(result.items().get(0).businessType()).isEqualTo("ANNUITY");
    assertThat(result.items().get(1).flowId()).isEqualTo(ApprovalFlowId.of(2L));
  }

  @Test
  @DisplayName("可选条件为空时原样透传给仓储")
  void listApprovalFlows_passesNullableConditions() {
    when(flowRepository.findByCondition(null, null, 0, 5)).thenReturn(ApprovalFlowPage.empty());

    service.listApprovalFlows(new ListApprovalFlowsRequest(null, null, Pagination.of(0, 5)));

    verify(flowRepository).findByCondition(null, null, 0, 5);
  }

  @Test
  @DisplayName("空结果返回 hasMore=false 的空 PageData")
  void listApprovalFlows_emptyPage() {
    when(flowRepository.findByCondition(any(), any(), anyInt(), anyInt()))
      .thenReturn(ApprovalFlowPage.empty());

    PageData<ApprovalFlowDTO> result = service.listApprovalFlows(
      new ListApprovalFlowsRequest("ANNUITY", "ACTIVE", Pagination.of(0, 10)));

    assertThat(result.isEmpty()).isTrue();
    assertThat(result.hasMore()).isFalse();
    assertThat(result.totalCount()).isZero();
    assertThat(result.currentStart()).isZero();
  }

  @Test
  @DisplayName("末页 startPos + 返回数达到总数时 hasMore=false")
  void listApprovalFlows_lastPageHasNoMore() {
    when(flowRepository.findByCondition("ANNUITY", "ACTIVE", 10, 10))
      .thenReturn(new ApprovalFlowPage(12, List.of(flow(1L), flow(2L))));

    PageData<ApprovalFlowDTO> result = service.listApprovalFlows(
      new ListApprovalFlowsRequest("ANNUITY", "ACTIVE", Pagination.of(10, 10)));

    assertThat(result.hasMore()).isFalse();
    assertThat(result.returnedCount()).isEqualTo(2);
  }

  private ApprovalFlow flow(long id) {
    return ApprovalFlow.reconstitute(
      ApprovalFlowId.of(id),
      FlowName.of("测试流"),
      MatchRules.of(null, null, null, null, "ANNUITY", null),
      List.of(node()),
      FlowVersion.of(1),
      FlowStatus.ACTIVE,
      "SYSTEM", "SYSTEM", NOW, NOW, Version.of(1L));
  }

  private ApprovalNode node() {
    return ApprovalNode.createSamePlanNode(
      NodeId.of(1L), NodeOrder.of(1),
      ApproverType.SPECIFIED_USER, List.of(UserNo.of("U-1")), List.of(),
      SignMode.OR_SIGN, "SYSTEM");
  }
}
