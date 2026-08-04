package com.example.approval.domain.aggregate.root;

import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.valueobject.FlowName;
import com.example.approval.domain.valueobject.FlowVersion;
import com.example.approval.domain.valueobject.MatchRules;
import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.NodeId;
import com.example.approval.types.enums.ApproverType;
import com.example.approval.types.enums.SignMode;
import com.example.shared.identifier.id.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("ApprovalFlow 审批流聚合根测试")
class ApprovalFlowTest {

  private static final UserNo OPERATOR = UserNo.of("user-001");

  private ApprovalNode singleNode() {
    return ApprovalNode.createSamePlanNode(
      NodeId.of(1L),
      NodeOrder.first(),
      ApproverType.SPECIFIED_USER,
      List.of(UserNo.of("user-002")),
      List.of(),
      SignMode.OR_SIGN,
      OPERATOR);
  }

  private ApprovalFlow newFlow() {
    return ApprovalFlow.create(
      ApprovalFlowId.of(1L),
      FlowName.of("原始审批流"),
      MatchRules.of("P001", null, null, null, null, null),
      List.of(singleNode()),
      OPERATOR);
  }

  @Test
  @DisplayName("update 传入新 flowName 应更新名称而不是抛出异常")
  void update_withNewFlowName_shouldUpdateName() {
    // given
    ApprovalFlow flow = newFlow();
    FlowName newName = FlowName.of("更新后的审批流");

    // when
    flow.update(newName, null, null, OPERATOR);

    // then
    assertEquals(newName, flow.flowName(),
      "update 后 flowName 应被更新");
  }

  @Test
  @DisplayName("update 传入新 matchRules 应更新匹配规则而不是抛出异常")
  void update_withNewMatchRules_shouldUpdateRules() {
    // given
    ApprovalFlow flow = newFlow();
    MatchRules newRules = MatchRules.of("P002", "C001", null, null, null, null);

    // when
    flow.update(null, newRules, null, OPERATOR);

    // then
    assertEquals(newRules, flow.matchRules(),
      "update 后 matchRules 应被更新");
  }

  @Test
  @DisplayName("update 应递增审批流版本号")
  void update_shouldIncrementFlowVersion() {
    // given
    ApprovalFlow flow = newFlow();
    FlowVersion initialVersion = flow.flowVersion();

    // when
    flow.update(FlowName.of("新名称"), null, null, OPERATOR);

    // then
    assertEquals(initialVersion.increment(), flow.flowVersion(),
      "update 后 flowVersion 应递增");
  }
}
