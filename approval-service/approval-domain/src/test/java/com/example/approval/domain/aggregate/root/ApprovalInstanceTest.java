package com.example.approval.domain.aggregate.root;

import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.valueobject.ApprovalOpinion;
import com.example.approval.domain.valueobject.FlowVersion;
import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.ApprovalInstanceId;
import com.example.approval.types.NodeId;
import com.example.approval.types.enums.ApproverType;
import com.example.approval.types.enums.InstanceStatus;
import com.example.approval.types.enums.SignMode;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApprovalInstance 审批实例聚合根测试")
class ApprovalInstanceTest {

    private static final UserNo INITIATOR = UserNo.of("user-initiator");
    private static final UserNo APPROVER_1 = UserNo.of("user-001");
    private static final UserNo APPROVER_2 = UserNo.of("user-002");
    private static final ApplicationId BUSINESS_APP_ID = new ApplicationId("app-001");

    private ApprovalInstance newApprovingInstance() {
        ApprovalInstance instance = ApprovalInstance.create(
                ApprovalInstanceId.of(1L),
                ApprovalFlowId.of(1L),
                FlowVersion.initial(),
                BUSINESS_APP_ID,
                "initiator-plan",
                INITIATOR);
        instance.start(INITIATOR);
        return instance;
    }

    @Test
    @DisplayName("AND_SIGN + SPECIFIED_ROLE 角色审批节点：单人审批不应立即完成节点")
    void approve_andSignSpecifiedRole_firstApprovalShouldNotCompleteNode() {
        // given: 角色审批节点，approverIds 为空，roleIds 非空
        ApprovalNode roleNode = ApprovalNode.createSamePlanNode(
                NodeId.of(1L),
                NodeOrder.first(),
                ApproverType.SPECIFIED_ROLE,
                List.of(),
                List.of("ROLE_MANAGER"),
                SignMode.AND_SIGN,
                INITIATOR);
        ApprovalInstance instance = newApprovingInstance();

        // when: 第一个用户审批
        instance.approve(roleNode, APPROVER_1, ApprovalOpinion.of("同意"), APPROVER_1);

        // then: 节点不应完成，实例应仍在审批中
        assertEquals(InstanceStatus.APPROVING, instance.status(),
                "AND_SIGN 角色审批节点第一次审批后实例不应已完成");
        assertEquals(NodeOrder.first(), instance.currentNodeOrder(),
                "AND_SIGN 角色审批节点第一次审批后不应推进到下一节点");
        assertTrue(instance.getCurrentExecution().isPresent(),
                "应存在当前节点执行记录");
        assertFalse(instance.getCurrentExecution().get().isApproved(),
                "AND_SIGN 角色审批节点第一次审批不应标记为已通过");
    }

    @Test
    @DisplayName("AND_SIGN + SPECIFIED_USER 用户审批节点：需全部审批人通过后才完成节点")
    void approve_andSignSpecifiedUser_shouldCompleteOnlyWhenAllApproversApproved() {
        // given: 用户审批节点，2 个审批人
        ApprovalNode userNode = ApprovalNode.createSamePlanNode(
                NodeId.of(1L),
                NodeOrder.first(),
                ApproverType.SPECIFIED_USER,
                List.of(APPROVER_1, APPROVER_2),
                List.of(),
                SignMode.AND_SIGN,
                INITIATOR);
        ApprovalInstance instance = newApprovingInstance();

        // when: 第一个审批人通过
        instance.approve(userNode, APPROVER_1, ApprovalOpinion.of("同意"), APPROVER_1);

        // then: 节点不应完成
        assertEquals(InstanceStatus.APPROVING, instance.status(),
                "AND_SIGN 用户审批节点单人通过后实例不应已完成");
        assertFalse(instance.getCurrentExecution().get().isApproved(),
                "AND_SIGN 用户审批节点单人通过后不应标记为已通过");

        // when: 第二个审批人通过
        instance.approve(userNode, APPROVER_2, ApprovalOpinion.of("同意"), APPROVER_2);

        // then: 节点应完成，实例应已通过
        assertEquals(InstanceStatus.APPROVED, instance.status(),
                "AND_SIGN 用户审批节点全部通过后实例应已通过");
    }

    @Test
    @DisplayName("OR_SIGN + SPECIFIED_ROLE 角色审批节点：单人审批即完成节点")
    void approve_orSignSpecifiedRole_firstApprovalShouldCompleteNode() {
        // given: 或签角色审批节点
        ApprovalNode orSignRoleNode = ApprovalNode.createSamePlanNode(
                NodeId.of(1L),
                NodeOrder.first(),
                ApproverType.SPECIFIED_ROLE,
                List.of(),
                List.of("ROLE_MANAGER"),
                SignMode.OR_SIGN,
                INITIATOR);
        ApprovalInstance instance = newApprovingInstance();

        // when: 第一个用户审批
        instance.approve(orSignRoleNode, APPROVER_1, ApprovalOpinion.of("同意"), APPROVER_1);

        // then: 或签模式下节点应完成
        assertEquals(InstanceStatus.APPROVED, instance.status(),
                "OR_SIGN 角色审批节点单人通过后实例应已通过");
    }
}
