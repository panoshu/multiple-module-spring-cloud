package com.example.approval.domain.event;

import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.root.ApprovalInstance;
import com.example.approval.domain.valueobject.ApprovalOpinion;
import com.example.approval.domain.valueobject.FlowVersion;
import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.domain.valueobject.RejectTarget;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.ApprovalInstanceId;
import com.example.approval.types.NodeId;
import com.example.approval.types.enums.ApproverType;
import com.example.approval.types.enums.InstanceStatus;
import com.example.approval.types.enums.SignMode;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.ApplicationId;
import com.example.shared.identifier.id.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 审批实例领域事件派发测试
 * 验证聚合根在状态变更时正确派发领域事件
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@DisplayName("ApprovalInstance 领域事件派发测试")
class ApprovalInstanceEventTest {

  private static final UserNo INITIATOR = UserNo.of("user-initiator");
  private static final UserNo APPROVER = UserNo.of("user-001");
  private static final ApplicationId BUSINESS_APP_ID = new ApplicationId("app-001");
  private static final String BUSINESS_TYPE = "ANNUITY";
  private static final String BUSINESS_NO = "app-001";

  private ApprovalInstance newApprovingInstance() {
    ApprovalInstance instance = ApprovalInstance.create(
      ApprovalInstanceId.of(1L),
      ApprovalFlowId.of(1L),
      FlowVersion.initial(),
      BUSINESS_APP_ID,
      BUSINESS_TYPE,
      "initiator-plan",
      INITIATOR);
    instance.start(INITIATOR);
    return instance;
  }

  private ApprovalNode singleOrSignUserNode() {
    return ApprovalNode.createSamePlanNode(
      NodeId.of(1L),
      NodeOrder.first(),
      ApproverType.SPECIFIED_USER,
      List.of(APPROVER),
      List.of(),
      SignMode.OR_SIGN,
      INITIATOR);
  }

  @Test
  @DisplayName("create 后应派发 ApprovalInstanceCreated 事件，携带正确的 businessNo 和 businessType")
  void create_shouldDispatchCreatedEventWithBusinessFields() {
    // when
    ApprovalInstance instance = ApprovalInstance.create(
      ApprovalInstanceId.of(100L),
      ApprovalFlowId.of(1L),
      FlowVersion.initial(),
      BUSINESS_APP_ID,
      BUSINESS_TYPE,
      "initiator-plan",
      INITIATOR);

    // then
    List<DomainEvent> events = instance.domainEvents();
    assertEquals(1, events.size(),
      "create 后应注册 1 个领域事件");

    ApprovalInstanceCreated created = (ApprovalInstanceCreated) events.get(0);
    assertEquals(ApprovalInstanceId.of(100L), created.instanceId(),
      "事件 instanceId 应与聚合根ID一致");
    assertEquals(BUSINESS_NO, created.businessNo(),
      "事件 businessNo 应为业务申请ID的值");
    assertEquals(BUSINESS_TYPE, created.businessType(),
      "事件 businessType 应为业务类型");
  }

  @Test
  @DisplayName("approve 使状态变为 APPROVED 时应派发 ApprovalInstanceApproved 事件")
  void approve_shouldDispatchApprovedEventWhenStatusBecomesApproved() {
    // given
    ApprovalInstance instance = newApprovingInstance();
    ApprovalNode node = singleOrSignUserNode();

    // when
    instance.approve(node, APPROVER, ApprovalOpinion.of("同意"), APPROVER);

    // then
    assertThat(instance.status()).isEqualTo(InstanceStatus.APPROVED);

    List<DomainEvent> events = instance.domainEvents().stream()
      .filter(e -> e instanceof ApprovalInstanceApproved)
      .toList();
    assertEquals(1, events.size(),
      "approve 后应派发 1 个 ApprovalInstanceApproved 事件");

    ApprovalInstanceApproved approved = (ApprovalInstanceApproved) events.get(0);
    assertEquals(BUSINESS_NO, approved.businessNo(),
      "Approved 事件 businessNo 应正确");
    assertEquals(BUSINESS_TYPE, approved.businessType(),
      "Approved 事件 businessType 应正确");
  }

  @Test
  @DisplayName("reject 终止流程使状态变为 REJECTED 时应派发 ApprovalInstanceRejected 事件")
  void reject_shouldDispatchRejectedEventWhenStatusBecomesRejected() {
    // given
    ApprovalInstance instance = newApprovingInstance();
    ApprovalNode node = singleOrSignUserNode();

    // when
    instance.reject(node, APPROVER, ApprovalOpinion.of("不同意"),
      RejectTarget.terminate(), APPROVER);

    // then
    assertThat(instance.status()).isEqualTo(InstanceStatus.REJECTED);

    List<DomainEvent> events = instance.domainEvents().stream()
      .filter(e -> e instanceof ApprovalInstanceRejected)
      .toList();
    assertEquals(1, events.size(),
      "reject 后应派发 1 个 ApprovalInstanceRejected 事件");

    ApprovalInstanceRejected rejected = (ApprovalInstanceRejected) events.get(0);
    assertEquals(BUSINESS_NO, rejected.businessNo(),
      "Rejected 事件 businessNo 应正确");
    assertEquals(BUSINESS_TYPE, rejected.businessType(),
      "Rejected 事件 businessType 应正确");
  }

  @Test
  @DisplayName("withdraw 使状态变为 WITHDRAWN 时应派发 ApprovalInstanceWithdrawn 事件")
  void withdraw_shouldDispatchWithdrawnEvent() {
    // given
    ApprovalInstance instance = newApprovingInstance();

    // when
    instance.withdraw(INITIATOR);

    // then
    assertThat(instance.status()).isEqualTo(InstanceStatus.WITHDRAWN);

    List<DomainEvent> events = instance.domainEvents().stream()
      .filter(e -> e instanceof ApprovalInstanceWithdrawn)
      .toList();
    assertEquals(1, events.size(),
      "withdraw 后应派发 1 个 ApprovalInstanceWithdrawn 事件");

    ApprovalInstanceWithdrawn withdrawn = (ApprovalInstanceWithdrawn) events.get(0);
    assertEquals(BUSINESS_NO, withdrawn.businessNo(),
      "Withdrawn 事件 businessNo 应正确");
    assertEquals(BUSINESS_TYPE, withdrawn.businessType(),
      "Withdrawn 事件 businessType 应正确");
  }
}
