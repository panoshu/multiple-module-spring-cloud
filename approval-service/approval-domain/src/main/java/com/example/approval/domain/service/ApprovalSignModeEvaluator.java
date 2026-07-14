package com.example.approval.domain.service;

import com.example.approval.domain.aggregate.entity.ApprovalRecord;
import com.example.approval.types.enums.SignMode;
import com.example.shared.domain.annotation.DomainService;

import java.util.List;

/**
 * 签批模式判定服务
 * 根据签批模式判断节点是否完成
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@DomainService
public class ApprovalSignModeEvaluator {

    /**
     * 根据签批模式判断节点是否完成
     *
     * @param signMode 签批模式
     * @param records  审批记录列表
     * @return true 如果节点已完成
     */
    public boolean isNodeCompleted(SignMode signMode, List<ApprovalRecord> records) {
        if (signMode == null || records == null || records.isEmpty()) {
            return false;
        }

        // 获取审批通过的记录
        List<ApprovalRecord> approvedRecords = records.stream()
                .filter(ApprovalRecord::isApproved)
                .toList();

        if (approvedRecords.isEmpty()) {
            return false;
        }

        // 或签模式：只要有一个人审批通过，节点即完成
        if (signMode == SignMode.OR_SIGN) {
            return true;
        }

        // 会签模式：所有审批人都需要审批通过
        // 这里需要外部传入总审批人数，暂时假设会签模式下需要所有记录都是通过
        if (signMode == SignMode.AND_SIGN) {
            // 简化实现：会签模式下，所有记录都是通过状态才完成
            // 实际应该判断是否所有必需的审批人都已审批
            return records.stream().allMatch(ApprovalRecord::isApproved);
        }

        return false;
    }

    /**
     * 判断是否可以继续审批（用于会签模式）
     *
     * @param signMode    签批模式
     * @param totalApprovers 总审批人数
     * @param approvedCount 已审批通过人数
     * @return true 如果可以继续审批
     */
    public boolean canContinueApprove(SignMode signMode, int totalApprovers, int approvedCount) {
        if (signMode == null || totalApprovers <= 0) {
            return false;
        }

        // 或签模式：只要有一个人审批通过，就不能继续审批
        if (signMode == SignMode.OR_SIGN && approvedCount > 0) {
            return false;
        }

        // 会签模式：只要还没全部审批完，就可以继续
        if (signMode == SignMode.AND_SIGN) {
            return approvedCount < totalApprovers;
        }

        return true;
    }
}