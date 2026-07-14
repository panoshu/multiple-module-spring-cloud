package com.example.approval.domain.aggregate.entity;

import com.example.approval.domain.errorcode.ApprovalDomainErrorCode;
import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.types.ExecutionId;
import com.example.approval.types.NodeId;
import com.example.approval.types.RecordId;
import com.example.approval.types.enums.ExecutionStatus;
import com.example.shared.domain.aggregate.entity.Entity;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 节点执行实体
 * 记录审批实例中某个节点的执行情况
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public class NodeExecution extends Entity<ExecutionId> {

    /**
     * 节点ID
     */
    private final NodeId nodeId;

    /**
     * 节点顺序
     */
    private final NodeOrder nodeOrder;

    /**
     * 执行状态
     */
    private ExecutionStatus status;

    /**
     * 审批记录列表
     */
    private final List<ApprovalRecord> approvals;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 场景1: 业务创建
     */
    private NodeExecution(ExecutionId id, NodeId nodeId, NodeOrder nodeOrder, UserNo operator) {
        super(id, operator);
        this.nodeId = nodeId;
        this.nodeOrder = nodeOrder;
        this.status = ExecutionStatus.PENDING;
        this.approvals = new ArrayList<>();
        this.startedAt = null;
        this.completedAt = null;
    }

    /**
     * 场景2: 从数据库重建
     */
    private NodeExecution(ExecutionId id, NodeId nodeId, NodeOrder nodeOrder, ExecutionStatus status,
                          List<ApprovalRecord> approvals, LocalDateTime startedAt, LocalDateTime completedAt,
                          UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.nodeId = nodeId;
        this.nodeOrder = nodeOrder;
        this.status = status;
        this.approvals = approvals != null ? new ArrayList<>(approvals) : new ArrayList<>();
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    /**
     * 静态工厂方法 - 创建待执行节点
     *
     * @param id        执行ID
     * @param nodeId    节点ID
     * @param nodeOrder 节点顺序
     * @param operator  操作人
     * @return NodeExecution 实例
     */
    public static NodeExecution create(ExecutionId id, NodeId nodeId, NodeOrder nodeOrder, UserNo operator) {
        if (nodeId == null) {
            throw new IllegalArgumentException("节点ID不能为空");
        }
        if (nodeOrder == null) {
            throw new IllegalArgumentException("节点顺序不能为空");
        }
        if (operator == null) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        return new NodeExecution(id, nodeId, nodeOrder, operator);
    }

    /**
     * 静态工厂方法 - 从数据库重建
     */
    public static NodeExecution reconstitute(ExecutionId id, NodeId nodeId, NodeOrder nodeOrder, ExecutionStatus status,
                                              List<ApprovalRecord> approvals, LocalDateTime startedAt, LocalDateTime completedAt,
                                              UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new NodeExecution(id, nodeId, nodeOrder, status, approvals, startedAt, completedAt,
                createdBy, updatedBy, createdAt, updatedAt, version);
    }

    /**
     * 开始执行
     *
     * @param operator 操作人
     */
    public void start(UserNo operator) {
        if (this.status != ExecutionStatus.PENDING) {
            throw new DomainException(ApprovalDomainErrorCode.APPROVAL_INSTANCE_NOT_APPROVING)
                    .withLogDetail("只有待执行状态的节点才能开始执行, ExecutionId: %s, status: %s".formatted(this.id(), this.status));
        }
        this.status = ExecutionStatus.PENDING; // 保持PENDING，等待审批
        this.startedAt = LocalDateTime.now();
        this.markUpdated(operator);
    }

    /**
     * 添加审批记录
     *
     * @param record   审批记录
     * @param operator 操作人
     */
    public void addApprovalRecord(ApprovalRecord record, UserNo operator) {
        if (this.isCompleted()) {
            throw new DomainException(ApprovalDomainErrorCode.APPROVAL_INSTANCE_ALREADY_COMPLETED)
                    .withLogDetail("已完成的节点不能再添加审批记录, ExecutionId: %s".formatted(this.id()));
        }
        if (record == null) {
            throw new IllegalArgumentException("审批记录不能为空");
        }
        // 首次添加审批记录时，设置开始时间
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
        this.approvals.add(record);
        this.markUpdated(operator);
    }

    /**
     * 标记为已通过
     *
     * @param operator 操作人
     */
    public void markApproved(UserNo operator) {
        if (this.isCompleted()) {
            throw new DomainException(ApprovalDomainErrorCode.APPROVAL_INSTANCE_ALREADY_COMPLETED)
                    .withLogDetail("节点已完成，不能再次标记, ExecutionId: %s, status: %s".formatted(this.id(), this.status));
        }
        this.status = ExecutionStatus.APPROVED;
        this.completedAt = LocalDateTime.now();
        this.markUpdated(operator);
    }

    /**
     * 标记为已拒绝
     *
     * @param operator 操作人
     */
    public void markRejected(UserNo operator) {
        if (this.isCompleted()) {
            throw new DomainException(ApprovalDomainErrorCode.APPROVAL_INSTANCE_ALREADY_COMPLETED)
                    .withLogDetail("节点已完成，不能再次标记, ExecutionId: %s, status: %s".formatted(this.id(), this.status));
        }
        this.status = ExecutionStatus.REJECTED;
        this.completedAt = LocalDateTime.now();
        this.markUpdated(operator);
    }

    /**
     * 标记为已跳过
     *
     * @param operator 操作人
     */
    public void markSkipped(UserNo operator) {
        if (this.isCompleted()) {
            throw new DomainException(ApprovalDomainErrorCode.APPROVAL_INSTANCE_ALREADY_COMPLETED)
                    .withLogDetail("节点已完成，不能再次标记, ExecutionId: %s, status: %s".formatted(this.id(), this.status));
        }
        this.status = ExecutionStatus.SKIPPED;
        this.completedAt = LocalDateTime.now();
        this.markUpdated(operator);
    }

    /**
     * 判断某个用户是否已审批
     *
     * @param approverId 审批人ID
     * @return true 如果已审批
     */
    public boolean hasApprovedBy(UserNo approverId) {
        if (approverId == null) {
            return false;
        }
        return approvals.stream()
                .anyMatch(record -> record.approverId().equals(approverId) && record.isApproved());
    }

    /**
     * 判断节点是否已完成
     *
     * @return true 如果已完成
     */
    public boolean isCompleted() {
        return status == ExecutionStatus.APPROVED ||
               status == ExecutionStatus.REJECTED ||
               status == ExecutionStatus.SKIPPED;
    }

    /**
     * 判断节点是否已通过
     */
    public boolean isApproved() {
        return status == ExecutionStatus.APPROVED;
    }

    /**
     * 判断节点是否已拒绝
     */
    public boolean isRejected() {
        return status == ExecutionStatus.REJECTED;
    }

    /**
     * 判断节点是否已跳过
     */
    public boolean isSkipped() {
        return status == ExecutionStatus.SKIPPED;
    }

    /**
     * 判断节点是否待执行
     */
    public boolean isPending() {
        return status == ExecutionStatus.PENDING;
    }

    /**
     * 获取最后一条审批记录
     *
     * @return 最后一条审批记录（可能为空）
     */
    public Optional<ApprovalRecord> getLastApprovalRecord() {
        if (approvals.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(approvals.get(approvals.size() - 1));
    }

    /**
     * 获取所有审批记录
     *
     * @return 审批记录列表
     */
    public List<ApprovalRecord> getApprovalRecords() {
        return Collections.unmodifiableList(approvals);
    }

    public NodeId nodeId() {
        return nodeId;
    }

    public NodeOrder nodeOrder() {
        return nodeOrder;
    }

    public ExecutionStatus status() {
        return status;
    }

    public LocalDateTime startedAt() {
        return startedAt;
    }

    public LocalDateTime completedAt() {
        return completedAt;
    }

    @Override
    protected void validateInvariants() {
        // 校验节点ID
        if (nodeId == null) {
            throw new IllegalStateException("节点ID不能为空");
        }

        // 校验节点顺序
        if (nodeOrder == null) {
            throw new IllegalStateException("节点顺序不能为空");
        }

        // 校验状态
        if (status == null) {
            throw new IllegalStateException("执行状态不能为空");
        }

        // 校验时间一致性
        if (isCompleted() && completedAt == null) {
            throw new IllegalStateException("已完成节点必须有完成时间");
        }

        // 校验审批记录非空时必须有开始时间
        if (!approvals.isEmpty() && startedAt == null) {
            throw new IllegalStateException("有审批记录时必须有开始时间");
        }
    }
}