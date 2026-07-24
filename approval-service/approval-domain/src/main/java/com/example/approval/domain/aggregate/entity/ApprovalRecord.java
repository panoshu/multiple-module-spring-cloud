package com.example.approval.domain.aggregate.entity;

import com.example.approval.domain.valueobject.ApprovalOpinion;
import com.example.approval.domain.valueobject.RejectTarget;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.NodeId;
import com.example.approval.types.RecordId;
import com.example.approval.types.enums.ApprovalAction;
import com.example.shared.domain.aggregate.entity.Entity;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 审批记录实体
 * 记录一次具体的审批操作
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public class ApprovalRecord extends Entity<RecordId> {

    /**
     * 审批人ID
     */
    private final UserNo approverId;

    /**
     * 审批动作
     */
    private final ApprovalAction action;

    /**
     * 审批意见
     */
    private final ApprovalOpinion opinion;

    /**
     * 驳回目标（仅当 action 为 REJECT 时有效）
     */
    private final RejectTarget rejectTarget;

    /**
     * 转交目标用户（仅当 action 为 TRANSFER 时有效）
     */
    private final UserNo transferTo;

    /**
     * 操作时间
     */
    private final LocalDateTime operatedAt;

    /**
     * 场景1: 业务创建 - 通过
     */
    private ApprovalRecord(RecordId id, UserNo approverId, ApprovalOpinion opinion, UserNo operator) {
        super(id, operator);
        this.approverId = approverId;
        this.action = ApprovalAction.APPROVE;
        this.opinion = opinion;
        this.rejectTarget = null;
        this.transferTo = null;
        this.operatedAt = LocalDateTime.now();
        this.validateInvariants();
    }

    /**
     * 场景1: 业务创建 - 拒绝
     */
    private ApprovalRecord(RecordId id, UserNo approverId, ApprovalOpinion opinion, RejectTarget rejectTarget, UserNo operator) {
        super(id, operator);
        this.approverId = approverId;
        this.action = ApprovalAction.REJECT;
        this.opinion = opinion;
        this.rejectTarget = rejectTarget;
        this.transferTo = null;
        this.operatedAt = LocalDateTime.now();
        this.validateInvariants();
    }

    /**
     * 场景1: 业务创建 - 转交
     */
    private ApprovalRecord(RecordId id, UserNo approverId, ApprovalOpinion opinion, UserNo transferTo, UserNo operator) {
        super(id, operator);
        this.approverId = approverId;
        this.action = ApprovalAction.TRANSFER;
        this.opinion = opinion;
        this.rejectTarget = null;
        this.transferTo = transferTo;
        this.operatedAt = LocalDateTime.now();
        this.validateInvariants();
    }

    /**
     * 场景2: 从数据库重建
     */
    private ApprovalRecord(RecordId id, UserNo approverId, ApprovalAction action, ApprovalOpinion opinion,
                           RejectTarget rejectTarget, UserNo transferTo, LocalDateTime operatedAt,
                           UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.approverId = approverId;
        this.action = action;
        this.opinion = opinion;
        this.rejectTarget = rejectTarget;
        this.transferTo = transferTo;
        this.operatedAt = operatedAt;
        this.validateInvariants();
    }

    /**
     * 静态工厂方法 - 通过
     *
     * @param id        审批记录ID
     * @param approverId 审批人ID
     * @param opinion   审批意见
     * @param operator  操作人
     * @return ApprovalRecord 实例
     */
    public static ApprovalRecord approve(RecordId id, UserNo approverId, ApprovalOpinion opinion, UserNo operator) {
        if (approverId == null) {
            throw new IllegalArgumentException("审批人ID不能为空");
        }
        if (operator == null) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        return new ApprovalRecord(id, approverId, opinion != null ? opinion : ApprovalOpinion.empty(), operator);
    }

    /**
     * 静态工厂方法 - 拒绝
     *
     * @param id          审批记录ID
     * @param approverId  审批人ID
     * @param opinion     审批意见
     * @param rejectTarget 驳回目标
     * @param operator    操作人
     * @return ApprovalRecord 实例
     */
    public static ApprovalRecord reject(RecordId id, UserNo approverId, ApprovalOpinion opinion, RejectTarget rejectTarget, UserNo operator) {
        if (approverId == null) {
            throw new IllegalArgumentException("审批人ID不能为空");
        }
        if (rejectTarget == null) {
            throw new IllegalArgumentException("驳回目标不能为空");
        }
        if (operator == null) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        return new ApprovalRecord(id, approverId, opinion != null ? opinion : ApprovalOpinion.empty(), rejectTarget, operator);
    }

    /**
     * 静态工厂方法 - 转交
     *
     * @param id         审批记录ID
     * @param approverId 审批人ID
     * @param opinion    审批意见
     * @param transferTo 转交目标用户
     * @param operator   操作人
     * @return ApprovalRecord 实例
     */
    public static ApprovalRecord transfer(RecordId id, UserNo approverId, ApprovalOpinion opinion, UserNo transferTo, UserNo operator) {
        if (approverId == null) {
            throw new IllegalArgumentException("审批人ID不能为空");
        }
        if (transferTo == null) {
            throw new IllegalArgumentException("转交目标用户不能为空");
        }
        if (operator == null) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        return new ApprovalRecord(id, approverId, opinion != null ? opinion : ApprovalOpinion.empty(), transferTo, operator);
    }

    /**
     * 静态工厂方法 - 从数据库重建
     */
    public static ApprovalRecord reconstitute(RecordId id, UserNo approverId, ApprovalAction action, ApprovalOpinion opinion,
                                              RejectTarget rejectTarget, UserNo transferTo, LocalDateTime operatedAt,
                                              UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new ApprovalRecord(id, approverId, action, opinion, rejectTarget, transferTo, operatedAt, createdBy, updatedBy, createdAt, updatedAt, version);
    }

    public UserNo approverId() {
        return approverId;
    }

    public ApprovalAction action() {
        return action;
    }

    public ApprovalOpinion opinion() {
        return opinion;
    }

    public RejectTarget rejectTarget() {
        return rejectTarget;
    }

    public UserNo transferTo() {
        return transferTo;
    }

    public LocalDateTime operatedAt() {
        return operatedAt;
    }

    /**
     * 是否为通过
     */
    public boolean isApproved() {
        return action == ApprovalAction.APPROVE;
    }

    /**
     * 是否为拒绝
     */
    public boolean isRejected() {
        return action == ApprovalAction.REJECT;
    }

    /**
     * 是否为转交
     */
    public boolean isTransferred() {
        return action == ApprovalAction.TRANSFER;
    }

    @Override
    protected void validateInvariants() {
        // 校验审批人ID
        if (approverId == null) {
            throw new IllegalStateException("审批人ID不能为空");
        }

        // 校验操作时间
        if (operatedAt == null) {
            throw new IllegalStateException("操作时间不能为空");
        }

        // 校验动作与参数的匹配
        if (action == ApprovalAction.REJECT && rejectTarget == null) {
            throw new IllegalStateException("拒绝操作必须指定驳回目标");
        }

        if (action == ApprovalAction.TRANSFER && transferTo == null) {
            throw new IllegalStateException("转交操作必须指定转交目标用户");
        }

        if (action != ApprovalAction.REJECT && rejectTarget != null) {
            throw new IllegalStateException("只有拒绝操作才能有驳回目标");
        }

        if (action != ApprovalAction.TRANSFER && transferTo != null) {
            throw new IllegalStateException("只有转交操作才能有转交目标");
        }
    }
}