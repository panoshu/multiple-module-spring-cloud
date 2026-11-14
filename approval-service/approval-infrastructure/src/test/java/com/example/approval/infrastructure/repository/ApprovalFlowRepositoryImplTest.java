package com.example.approval.infrastructure.repository;

import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.root.ApprovalFlow;
import com.example.approval.domain.valueobject.ApprovalFlowPage;
import com.example.approval.domain.valueobject.FlowName;
import com.example.approval.domain.valueobject.FlowVersion;
import com.example.approval.domain.valueobject.MatchRules;
import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.infrastructure.converter.ApprovalFlowConverter;
import com.example.approval.infrastructure.entity.ApprovalFlowDO;
import com.example.approval.infrastructure.entity.ApprovalNodeDO;
import com.example.approval.infrastructure.mapper.ApprovalFlowMapper;
import com.example.approval.infrastructure.mapper.ApprovalNodeMapper;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.NodeId;
import com.example.approval.types.enums.ApproverType;
import com.example.approval.types.enums.FlowStatus;
import com.example.approval.types.enums.SignMode;
import com.example.core.types.UserNo;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批次 54（P3-1 能力 B）：{@code findByCondition} 的条件过滤、DB 计数与 limit 分页。
 */
@DisplayName("审批流条件分页仓储（批次 54）")
class ApprovalFlowRepositoryImplTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0);

  private ApprovalFlowMapper flowMapper;
  private ApprovalNodeMapper nodeMapper;
  private ApprovalFlowConverter converter;
  private ApprovalFlowRepositoryImpl repository;

  @BeforeEach
  void setUp() {
    flowMapper = mock(ApprovalFlowMapper.class);
    nodeMapper = mock(ApprovalNodeMapper.class);
    converter = mock(ApprovalFlowConverter.class);
    repository = new ApprovalFlowRepositoryImpl(flowMapper, nodeMapper, converter);
  }

  @Test
  @DisplayName("count=0 时短路，不查询列表")
  void findByCondition_emptyCountSkipsListQuery() {
    when(flowMapper.selectCountByQuery(any())).thenReturn(0L);

    ApprovalFlowPage page = repository.findByCondition("ANNUITY", "ACTIVE", 0, 10);

    assertThat(page.totalCount()).isZero();
    assertThat(page.items()).isEmpty();
    verify(flowMapper, never()).selectListByQuery(any());
  }

  @Test
  @DisplayName("条件与 limit 落到 QueryWrapper，返回的一页转换为领域对象")
  void findByCondition_appliesConditionsAndLimit() {
    when(flowMapper.selectCountByQuery(any())).thenReturn(2L);
    ApprovalFlowDO flowDO = new ApprovalFlowDO();
    flowDO.setId("1");
    when(flowMapper.selectListByQuery(any())).thenReturn(List.of(flowDO));
    ApprovalNodeDO nodeDO = new ApprovalNodeDO();
    nodeDO.setId("1");
    when(nodeMapper.selectListByQuery(any())).thenReturn(List.of(nodeDO));
    when(converter.toDomain(any())).thenReturn(flow());
    when(converter.toNodeDomain(any())).thenReturn(node());

    ApprovalFlowPage page = repository.findByCondition("ANNUITY", "ACTIVE", 5, 10);

    assertThat(page.totalCount()).isEqualTo(2);
    assertThat(page.items()).hasSize(1);
    ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
    verify(flowMapper).selectListByQuery(captor.capture());
    String sql = captor.getValue().toSQL().toLowerCase();
    assertThat(sql).contains("business_type", "status", "limit");
  }

  private ApprovalFlow flow() {
    return ApprovalFlow.reconstitute(
      ApprovalFlowId.of(1L),
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
