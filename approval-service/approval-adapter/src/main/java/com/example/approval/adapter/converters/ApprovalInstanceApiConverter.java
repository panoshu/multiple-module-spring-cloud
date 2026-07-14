package com.example.approval.adapter.converters;

import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.approval.api.dto.ApprovalRecordDTO;
import com.example.approval.api.dto.NodeExecutionDTO;
import com.example.approval.domain.aggregate.entity.ApprovalRecord;
import com.example.approval.domain.aggregate.entity.NodeExecution;
import com.example.approval.domain.aggregate.root.ApprovalInstance;

/**
 * 审批实例API转换器
 * 负责 Request/DTO 与领域对象之间的转换
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public final class ApprovalInstanceApiConverter {

    private ApprovalInstanceApiConverter() {
        // 工具类，禁止实例化
    }

    /**
     * 将领域对象转换为DTO
     *
     * @param instance 审批实例
     * @return 审批实例DTO
     */
    public static ApprovalInstanceDTO toDTO(ApprovalInstance instance) {
        return new ApprovalInstanceDTO(
                instance.id(),
                instance.flowId(),
                instance.businessApplicationId().value(),
                null, // businessType
                instance.status().name(),
                instance.createdBy().value(),
                instance.currentNodeOrder().toString(),
                instance.getNodeExecutions().stream()
                        .map(ApprovalInstanceApiConverter::toNodeExecutionDTO)
                        .toList(),
                instance.createdAt(),
                instance.isCompleted() ? instance.updatedAt() : null
        );
    }

    /**
     * 将节点执行记录转换为DTO
     *
     * @param execution 节点执行记录
     * @return 节点执行DTO
     */
    private static NodeExecutionDTO toNodeExecutionDTO(NodeExecution execution) {
        return new NodeExecutionDTO(
                execution.nodeId(),
                execution.nodeOrder().toString(), // nodeName
                execution.status().name(),
                null, // approver - 需要从审批记录中获取
                null, // comment - 需要从审批记录中获取
                execution.updatedAt()
        );
    }

    /**
     * 将审批记录转换为DTO
     *
     * @param record 审批记录
     * @return 审批记录DTO
     */
    public static ApprovalRecordDTO toDTO(ApprovalRecord record) {
        return new ApprovalRecordDTO(
                record.id(),
                null, // instanceId - 需要从上下文获取
                null, // nodeName - 需要从上下文获取
                record.action().name(),
                record.approverId().value(),
                record.opinion().value(),
                record.createdAt()
        );
    }
}