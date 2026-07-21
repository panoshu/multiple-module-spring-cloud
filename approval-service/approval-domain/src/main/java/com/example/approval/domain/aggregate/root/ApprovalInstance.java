package com.example.approval.domain.aggregate.root;

import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.entity.ApprovalRecord;
import com.example.approval.domain.aggregate.entity.NodeExecution;
import com.example.approval.domain.errorcode.ApprovalDomainErrorCode;
import com.example.approval.domain.event.ApprovalInstanceApproved;
import com.example.approval.domain.event.ApprovalInstanceCreated;
import com.example.approval.domain.event.ApprovalInstanceRejected;
import com.example.approval.domain.event.ApprovalInstanceWithdrawn;
import com.example.approval.domain.valueobject.ApprovalOpinion;
import com.example.approval.domain.valueobject.FlowVersion;
import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.domain.valueobject.RejectTarget;
import com.example.approval.types.*;
import com.example.approval.types.enums.InstanceStatus;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 审批实例聚合根
 * 代表一次具体的审批流程执行实例
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public class ApprovalInstance extends AggregateRoot<ApprovalInstanceId> {

    /**
     * 审批流ID
     */
    private final ApprovalFlowId flowId;

    /**
     * 审批流版本
     */
    private final FlowVersion flowVersion;

    /**
     * 业务申请ID
     */
    private final ApplicationId businessApplicationId;

    /**
     * 业务类型
     */
    private final String businessType;

    /**
     * 当前节点顺序
     */
    private NodeOrder currentNodeOrder;

    /**
     * 实例状态
     */
    private InstanceStatus status;

    /**
     * 发起人方案
     */
    private final String initiatorPlan;

    /**
     * 当前方案
     */
    private String currentPlan;

    /**
     * 节点执行记录列表
     */
    private final List<NodeExecution> nodeExecutions;

    /**
     * 场景1: 业务创建
     */
    private ApprovalInstance(ApprovalInstanceId id, ApprovalFlowId flowId, FlowVersion flowVersion,
                              ApplicationId businessApplicationId, String businessType,
                              String initiatorPlan, UserNo operator) {
        super(id, operator);
        this.flowId = flowId;
        this.flowVersion = flowVersion;
        this.businessApplicationId = businessApplicationId;
        this.businessType = businessType;
        this.initiatorPlan = initiatorPlan;
        this.currentPlan = initiatorPlan;
        this.currentNodeOrder = NodeOrder.first();
        this.status = InstanceStatus.PENDING;
        this.nodeExecutions = new ArrayList<>();
    }

    /**
     * 场景2: 从数据库重建
     */
    private ApprovalInstance(ApprovalInstanceId id, ApprovalFlowId flowId, FlowVersion flowVersion,
                              ApplicationId businessApplicationId, String businessType, NodeOrder currentNodeOrder, InstanceStatus status,
                              String initiatorPlan, String currentPlan, List<NodeExecution> nodeExecutions,
                              UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.flowId = flowId;
        this.flowVersion = flowVersion;
        this.businessApplicationId = businessApplicationId;
        this.businessType = businessType;
        this.currentNodeOrder = currentNodeOrder;
        this.status = status;
        this.initiatorPlan = initiatorPlan;
        this.currentPlan = currentPlan;
        this.nodeExecutions = nodeExecutions != null ? new ArrayList<>(nodeExecutions) : new ArrayList<>();
    }

    /**
     * 静态工厂方法 - 创建审批实例
     *
     * @param id                    审批实例ID
     * @param flowId                审批流ID
     * @param flowVersion           审批流版本
     * @param businessApplicationId 业务申请ID
     * @param businessType          业务类型
     * @param initiatorPlan         发起人方案
     * @param operator              操作人
     * @return ApprovalInstance 实例
     */
    public static ApprovalInstance create(ApprovalInstanceId id, ApprovalFlowId flowId, FlowVersion flowVersion,
                                           ApplicationId businessApplicationId, String businessType,
                                           String initiatorPlan, UserNo operator) {
        if (flowId == null) {
            throw new IllegalArgumentException("审批流ID不能为空");
        }
        if (flowVersion == null) {
            throw new IllegalArgumentException("审批流版本不能为空");
        }
        if (businessApplicationId == null) {
            throw new IllegalArgumentException("业务申请ID不能为空");
        }
        if (operator == null) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        ApprovalInstance instance = new ApprovalInstance(id, flowId, flowVersion, businessApplicationId,
                businessType, initiatorPlan, operator);
        instance.registerDomainEvent(
                ApprovalInstanceCreated.of(id, businessApplicationId.value(), businessType));
        return instance;
    }

    /**
     * 静态工厂方法 - 从数据库重建
     */
    public static ApprovalInstance reconstitute(ApprovalInstanceId id, ApprovalFlowId flowId, FlowVersion flowVersion,
                                                 ApplicationId businessApplicationId, String businessType, NodeOrder currentNodeOrder, InstanceStatus status,
                                                 String initiatorPlan, String currentPlan, List<NodeExecution> nodeExecutions,
                                                 UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new ApprovalInstance(id, flowId, flowVersion, businessApplicationId, businessType, currentNodeOrder, status,
                initiatorPlan, currentPlan, nodeExecutions, createdBy, updatedBy, createdAt, updatedAt, version);
    }

    /**
     * 启动审批流程
     *
     * @param operator 操作人
     */
    public void start(UserNo operator) {
        if (this.status != InstanceStatus.PENDING) {
            throw new DomainException(ApprovalDomainErrorCode.APPROVAL_INSTANCE_ALREADY_PENDING)
                    .withLogDetail("只有待审批状态的实例才能启动, ApprovalInstanceId: %s, status: %s".formatted(this.id(), this.status));
        }
        this.status = InstanceStatus.APPROVING;
        this.markUpdated(operator);
    }

    /**
     * 通过审批
     *
     * @param node         审批节点
     * @param approverId   审批人ID
     * @param opinion      审批意见
     * @param operator     操作人
     */
    public void approve(ApprovalNode node, UserNo approverId, ApprovalOpinion opinion, UserNo operator) {
        validateCanApprove(approverId);

        // 创建审批记录
        RecordId recordId = RecordId.of(System.currentTimeMillis()); // 简单生成ID，实际应由Repository生成
        ApprovalRecord record = ApprovalRecord.approve(recordId, approverId, opinion, operator);

        // 获取或创建当前节点的执行记录
        NodeExecution execution = getOrCreateCurrentExecution(node, operator);
        execution.addApprovalRecord(record, operator);

        // 根据签批模式决定是否标记节点完成
        if (node.isOrSign() || allApproversApproved(node, execution)) {
            execution.markApproved(operator);
            moveToNextNode(node, operator);
        }

        // 当状态变为 APPROVED 时派发领域事件
        if (this.status == InstanceStatus.APPROVED) {
            registerDomainEvent(
                    ApprovalInstanceApproved.of(id(), businessApplicationId.value(), businessType));
        }
    }

    /**
     * 拒绝审批
     *
     * @param node         审批节点
     * @param approverId   审批人ID
     * @param opinion      审批意见
     * @param rejectTarget 驳回目标
     * @param operator     操作人
     */
    public void reject(ApprovalNode node, UserNo approverId, ApprovalOpinion opinion,
                       RejectTarget rejectTarget, UserNo operator) {
        validateCanApprove(approverId);

        // 创建审批记录
        RecordId recordId = RecordId.of(System.currentTimeMillis());
        ApprovalRecord record = ApprovalRecord.reject(recordId, approverId, opinion, rejectTarget, operator);

        // 获取或创建当前节点的执行记录
        NodeExecution execution = getOrCreateCurrentExecution(node, operator);
        execution.addApprovalRecord(record, operator);
        execution.markRejected(operator);

        // 处理驳回逻辑
        handleRejection(rejectTarget, operator);

        // 当状态变为 REJECTED 时派发领域事件
        if (this.status == InstanceStatus.REJECTED) {
            registerDomainEvent(
                    ApprovalInstanceRejected.of(id(), businessApplicationId.value(), businessType));
        }
    }

    /**
     * 转交审批
     *
     * @param node       审批节点
     * @param approverId 审批人ID
     * @param opinion    审批意见
     * @param transferTo 转交目标用户
     * @param operator   操作人
     */
    public void transfer(ApprovalNode node, UserNo approverId, ApprovalOpinion opinion,
                          UserNo transferTo, UserNo operator) {
        validateCanApprove(approverId);

        // 创建审批记录
        RecordId recordId = RecordId.of(System.currentTimeMillis());
        ApprovalRecord record = ApprovalRecord.transfer(recordId, approverId, opinion, transferTo, operator);

        // 获取或创建当前节点的执行记录
        NodeExecution execution = getOrCreateCurrentExecution(node, operator);
        execution.addApprovalRecord(record, operator);
        // 转交不改变节点状态，等待被转交人审批
    }

    /**
     * 撤回审批实例
     *
     * @param operator 操作人
     */
    public void withdraw(UserNo operator) {
        // 只有发起人可以撤回
        if (!this.createdBy().equals(operator)) {
            throw new DomainException(ApprovalDomainErrorCode.WITHDRAW_NOT_BY_INITIATOR)
                    .withLogDetail("只有发起人可以撤回审批实例, ApprovalInstanceId: %s".formatted(this.id()));
        }

        if (this.status != InstanceStatus.APPROVING) {
            throw new DomainException(ApprovalDomainErrorCode.APPROVAL_INSTANCE_NOT_APPROVING)
                    .withLogDetail("只有审批中的实例才能撤回, ApprovalInstanceId: %s, status: %s".formatted(this.id(), this.status));
        }

        this.status = InstanceStatus.WITHDRAWN;
        this.markUpdated(operator);

        // 派发领域事件
        registerDomainEvent(
                ApprovalInstanceWithdrawn.of(id(), businessApplicationId.value(), businessType));
    }

    /**
     * 移动到下一个节点
     *
     * @param currentNode 当前节点
     * @param operator    操作人
     */
    private void moveToNextNode(ApprovalNode currentNode, UserNo operator) {
        NodeOrder nextOrder = currentNodeOrder.next();
        this.currentNodeOrder = nextOrder;

        // 如果没有下一个节点，标记为已通过
        if (this.currentNodeOrder.value() > getMaxNodeOrder()) {
            this.status = InstanceStatus.APPROVED;
        }

        this.markUpdated(operator);
    }

    /**
     * 处理驳回逻辑
     *
     * @param rejectTarget 驳回目标
     * @param operator     操作人
     */
    private void handleRejection(RejectTarget rejectTarget, UserNo operator) {
        if (rejectTarget.isTerminate()) {
            // 终止流程
            this.status = InstanceStatus.REJECTED;
        } else if (rejectTarget.isToInitiator()) {
            // 驳回到发起人，重新开始
            this.currentNodeOrder = NodeOrder.first();
            this.status = InstanceStatus.PENDING;
        } else if (rejectTarget.isToNode()) {
            // 驳回到指定节点
            this.currentNodeOrder = rejectTarget.targetNodeOrder();
            this.status = InstanceStatus.APPROVING;
        }
        this.markUpdated(operator);
    }

    /**
     * 校验是否可以审批
     *
     * @param approverId 审批人ID
     */
    private void validateCanApprove(UserNo approverId) {
        if (this.status != InstanceStatus.APPROVING) {
            throw new DomainException(ApprovalDomainErrorCode.APPROVAL_INSTANCE_NOT_APPROVING)
                    .withLogDetail("只有审批中的实例才能操作, ApprovalInstanceId: %s, status: %s".formatted(this.id(), this.status));
        }
        if (approverId == null) {
            throw new DomainException(ApprovalDomainErrorCode.NOT_CURRENT_APPROVER)
                    .withLogDetail("审批人ID不能为空");
        }
    }

    /**
     * 获取或创建当前节点的执行记录
     *
     * @param node     审批节点
     * @param operator 操作人
     * @return 节点执行记录
     */
    private NodeExecution getOrCreateCurrentExecution(ApprovalNode node, UserNo operator) {
        return nodeExecutions.stream()
                .filter(e -> e.nodeOrder().equals(currentNodeOrder))
                .findFirst()
                .orElseGet(() -> {
                    ExecutionId executionId = ExecutionId.of(System.currentTimeMillis());
                    NodeExecution execution = NodeExecution.create(executionId, node.id(), currentNodeOrder, operator);
                    nodeExecutions.add(execution);
                    return execution;
                });
    }

    /**
     * 判断所有审批人是否都已审批
     * <p>
     * 对于指定用户审批节点（SPECIFIED_USER），approverIds 非空，按列表逐一校验。
     * 对于指定角色审批节点（SPECIFIED_ROLE），approverIds 为空、roleIds 非空：
     * 运行时无法在领域层确定角色对应的全部用户，因此保守返回 false，
     * 避免 AND_SIGN 角色审批节点在第一次审批时即被错误标记完成。
     * 仅当既无 approverIds 也无 roleIds（配置异常）时才返回 true。
     *
     * @param node      审批节点
     * @param execution 节点执行记录
     * @return true 如果所有审批人都已审批
     */
    private boolean allApproversApproved(ApprovalNode node, NodeExecution execution) {
        List<UserNo> approverIds = node.approverIds();
        if (approverIds.isEmpty()) {
            return node.roleIds().isEmpty();
        }
        return approverIds.stream()
                .allMatch(execution::hasApprovedBy);
    }

    /**
     * 获取最大节点顺序号
     *
     * @return 最大节点顺序号
     */
    private int getMaxNodeOrder() {
        return nodeExecutions.stream()
                .mapToInt(e -> e.nodeOrder().value())
                .max()
                .orElse(1);
    }

    /**
     * 获取当前节点执行记录
     *
     * @return 当前节点执行记录（可能为空）
     */
    public Optional<NodeExecution> getCurrentExecution() {
        return nodeExecutions.stream()
                .filter(e -> e.nodeOrder().equals(currentNodeOrder))
                .findFirst();
    }

    /**
     * 获取所有节点执行记录
     *
     * @return 节点执行记录列表
     */
    public List<NodeExecution> getNodeExecutions() {
        return Collections.unmodifiableList(nodeExecutions);
    }

    /**
     * 判断是否已完成
     *
     * @return true 如果已完成
     */
    public boolean isCompleted() {
        return status == InstanceStatus.APPROVED ||
               status == InstanceStatus.REJECTED ||
               status == InstanceStatus.WITHDRAWN;
    }

    /**
     * 判断是否已通过
     */
    public boolean isApproved() {
        return status == InstanceStatus.APPROVED;
    }

    /**
     * 判断是否已拒绝
     */
    public boolean isRejected() {
        return status == InstanceStatus.REJECTED;
    }

    /**
     * 判断是否已撤回
     */
    public boolean isWithdrawn() {
        return status == InstanceStatus.WITHDRAWN;
    }

    /**
     * 判断是否审批中
     */
    public boolean isApproving() {
        return status == InstanceStatus.APPROVING;
    }

    /**
     * 判断是否待审批
     */
    public boolean isPending() {
        return status == InstanceStatus.PENDING;
    }

    public ApprovalFlowId flowId() {
        return flowId;
    }

    public FlowVersion flowVersion() {
        return flowVersion;
    }

    public ApplicationId businessApplicationId() {
        return businessApplicationId;
    }

    public String businessType() {
        return businessType;
    }

    public NodeOrder currentNodeOrder() {
        return currentNodeOrder;
    }

    public InstanceStatus status() {
        return status;
    }

    public String initiatorPlan() {
        return initiatorPlan;
    }

    public String currentPlan() {
        return currentPlan;
    }

    @Override
    protected void validateInvariants() {
        // 校验审批流ID
        if (flowId == null) {
            throw new IllegalStateException("审批流ID不能为空");
        }

        // 校验审批流版本
        if (flowVersion == null) {
            throw new IllegalStateException("审批流版本不能为空");
        }

        // 校验业务申请ID
        if (businessApplicationId == null) {
            throw new IllegalStateException("业务申请ID不能为空");
        }

        // 校验业务类型
        if (businessType == null || businessType.isBlank()) {
            throw new IllegalStateException("业务类型不能为空");
        }

        // 校验当前节点顺序
        if (currentNodeOrder == null) {
            throw new IllegalStateException("当前节点顺序不能为空");
        }

        // 校验状态
        if (status == null) {
            throw new IllegalStateException("实例状态不能为空");
        }
    }
}
