package com.example.approval.infrastructure.converter;

import com.example.approval.domain.aggregate.entity.ApprovalRecord;
import com.example.approval.domain.aggregate.entity.NodeExecution;
import com.example.approval.domain.aggregate.root.ApprovalInstance;
import com.example.approval.domain.valueobject.ApprovalOpinion;
import com.example.approval.domain.valueobject.FlowVersion;
import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.domain.valueobject.RejectTarget;
import com.example.approval.infrastructure.entity.ApprovalInstanceDO;
import com.example.approval.infrastructure.entity.ApprovalNodeExecutionDO;
import com.example.approval.infrastructure.entity.ApprovalRecordDO;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.ApprovalInstanceId;
import com.example.approval.types.ExecutionId;
import com.example.approval.types.NodeId;
import com.example.approval.types.RecordId;
import com.example.approval.types.enums.ApprovalAction;
import com.example.approval.types.enums.ExecutionStatus;
import com.example.approval.types.enums.InstanceStatus;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.UserNo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDateTime;

/**
 * 审批实例转换器
 * 负责审批实例领域对象与DO对象之间的转换
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@Mapper(componentModel = "spring")
public interface ApprovalInstanceConverter {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 审批实例领域对象转DO对象
     *
     * @param instance 审批实例领域对象
     * @return 审批实例DO对象
     */
    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "flowId", source = "flowId.value")
    @Mapping(target = "flowVersion", source = "flowVersion.value")
    @Mapping(target = "businessApplicationId", source = "businessApplicationId.value")
    @Mapping(target = "currentNodeOrder", source = "currentNodeOrder.value")
    @Mapping(target = "status", source = "status", qualifiedByName = "instanceStatusToString")
    @Mapping(target = "createdBy", source = "createdBy.value")
    @Mapping(target = "updatedBy", source = "updatedBy.value")
    @Mapping(target = "version", source = "version.value")
    @Mapping(target = "deleted", constant = "false")
    ApprovalInstanceDO toDO(ApprovalInstance instance);

    /**
     * 审批实例DO对象转领域对象
     *
     * @param instanceDO 审批实例DO对象
     * @return 审批实例领域对象
     */
    @Mapping(target = "id", source = "id", qualifiedByName = "toApprovalInstanceId")
    @Mapping(target = "flowId", source = "flowId", qualifiedByName = "toApprovalFlowId")
    @Mapping(target = "flowVersion", source = "flowVersion", qualifiedByName = "toFlowVersion")
    @Mapping(target = "businessApplicationId", source = "businessApplicationId", qualifiedByName = "toApplicationId")
    @Mapping(target = "currentNodeOrder", source = "currentNodeOrder", qualifiedByName = "toNodeOrder")
    @Mapping(target = "status", source = "status", qualifiedByName = "stringToInstanceStatus")
    @Mapping(target = "createdBy", source = "createdBy", qualifiedByName = "toUserNo")
    @Mapping(target = "updatedBy", source = "updatedBy", qualifiedByName = "toUserNo")
    @Mapping(target = "createdAt", source = "createTime")
    @Mapping(target = "updatedAt", source = "updateTime")
    @Mapping(target = "version", source = "version", qualifiedByName = "toVersion")
    @Mapping(target = "nodeExecutions", ignore = true)
    ApprovalInstance toDomain(ApprovalInstanceDO instanceDO);

    /**
     * 节点执行记录领域对象转DO对象
     *
     * @param execution 节点执行记录领域对象
     * @return 节点执行记录DO对象
     */
    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "nodeId", source = "nodeId.value")
    @Mapping(target = "nodeOrder", source = "nodeOrder.value")
    @Mapping(target = "status", source = "status", qualifiedByName = "executionStatusToString")
    @Mapping(target = "createdBy", source = "createdBy.value")
    @Mapping(target = "updatedBy", source = "updatedBy.value")
    @Mapping(target = "version", source = "version.value")
    @Mapping(target = "deleted", constant = "false")
    ApprovalNodeExecutionDO toExecutionDO(NodeExecution execution);

    /**
     * 节点执行记录DO对象转领域对象
     *
     * @param executionDO 节点执行记录DO对象
     * @return 节点执行记录领域对象
     */
    @Mapping(target = "id", source = "id", qualifiedByName = "toExecutionId")
    @Mapping(target = "nodeId", source = "nodeId", qualifiedByName = "toNodeId")
    @Mapping(target = "nodeOrder", source = "nodeOrder", qualifiedByName = "toNodeOrder")
    @Mapping(target = "status", source = "status", qualifiedByName = "stringToExecutionStatus")
    @Mapping(target = "approvals", ignore = true)
    @Mapping(target = "createdBy", source = "createdBy", qualifiedByName = "toUserNo")
    @Mapping(target = "updatedBy", source = "updatedBy", qualifiedByName = "toUserNo")
    @Mapping(target = "createdAt", source = "createTime")
    @Mapping(target = "updatedAt", source = "updateTime")
    @Mapping(target = "version", source = "version", qualifiedByName = "toVersion")
    NodeExecution toExecutionDomain(ApprovalNodeExecutionDO executionDO);

    /**
     * 审批记录领域对象转DO对象
     *
     * @param record 审批记录领域对象
     * @return 审批记录DO对象
     */
    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "approverId", source = "approverId.value")
    @Mapping(target = "action", source = "action", qualifiedByName = "approvalActionToString")
    @Mapping(target = "opinion", source = "opinion.value")
    @Mapping(target = "rejectTarget", source = "rejectTarget", qualifiedByName = "rejectTargetToJson")
    @Mapping(target = "transferTo", source = "transferTo.value")
    @Mapping(target = "createdBy", source = "createdBy.value")
    @Mapping(target = "updatedBy", source = "updatedBy.value")
    @Mapping(target = "version", source = "version.value")
    @Mapping(target = "deleted", constant = "false")
    ApprovalRecordDO toRecordDO(ApprovalRecord record);

    /**
     * 审批记录DO对象转领域对象
     *
     * @param recordDO 审批记录DO对象
     * @return 审批记录领域对象
     */
    @Mapping(target = "id", source = "id", qualifiedByName = "toRecordId")
    @Mapping(target = "approverId", source = "approverId", qualifiedByName = "toUserNo")
    @Mapping(target = "action", source = "action", qualifiedByName = "stringToApprovalAction")
    @Mapping(target = "opinion", source = "opinion", qualifiedByName = "toApprovalOpinion")
    @Mapping(target = "rejectTarget", source = "rejectTarget", qualifiedByName = "jsonToRejectTarget")
    @Mapping(target = "transferTo", source = "transferTo", qualifiedByName = "toUserNo")
    @Mapping(target = "createdBy", source = "createdBy", qualifiedByName = "toUserNo")
    @Mapping(target = "updatedBy", source = "updatedBy", qualifiedByName = "toUserNo")
    @Mapping(target = "createdAt", source = "createTime")
    @Mapping(target = "updatedAt", source = "updateTime")
    @Mapping(target = "version", source = "version", qualifiedByName = "toVersion")
    ApprovalRecord toRecordDomain(ApprovalRecordDO recordDO);

    // ========== 类型转换方法 ==========

    @Named("toApprovalInstanceId")
    default ApprovalInstanceId toApprovalInstanceId(String id) {
        return id != null ? ApprovalInstanceId.of(Long.parseLong(id)) : null;
    }

    @Named("toApprovalFlowId")
    default ApprovalFlowId toApprovalFlowId(String id) {
        return id != null ? ApprovalFlowId.of(Long.parseLong(id)) : null;
    }

    @Named("toApplicationId")
    default ApplicationId toApplicationId(String id) {
        return id != null ? ApplicationId.of(id) : null;
    }

    @Named("toFlowVersion")
    default FlowVersion toFlowVersion(Integer flowVersion) {
        return flowVersion != null ? FlowVersion.of(flowVersion) : null;
    }

    @Named("toNodeOrder")
    default NodeOrder toNodeOrder(Integer nodeOrder) {
        return nodeOrder != null ? NodeOrder.of(nodeOrder) : null;
    }

    @Named("toVersion")
    default Version toVersion(Integer version) {
        return version != null ? Version.of(version) : null;
    }

    @Named("toUserNo")
    default UserNo toUserNo(String userNo) {
        return userNo != null ? UserNo.of(userNo) : null;
    }

    @Named("toExecutionId")
    default ExecutionId toExecutionId(String id) {
        return id != null ? ExecutionId.of(Long.parseLong(id)) : null;
    }

    @Named("toNodeId")
    default NodeId toNodeId(String id) {
        return id != null ? NodeId.of(Long.parseLong(id)) : null;
    }

    @Named("toRecordId")
    default RecordId toRecordId(String id) {
        return id != null ? RecordId.of(Long.parseLong(id)) : null;
    }

    @Named("toApprovalOpinion")
    default ApprovalOpinion toApprovalOpinion(String opinion) {
        return opinion != null ? ApprovalOpinion.of(opinion) : ApprovalOpinion.empty();
    }

    @Named("instanceStatusToString")
    default String instanceStatusToString(InstanceStatus status) {
        return status != null ? status.name() : null;
    }

    @Named("stringToInstanceStatus")
    default InstanceStatus stringToInstanceStatus(String status) {
        return status != null ? InstanceStatus.valueOf(status) : null;
    }

    @Named("executionStatusToString")
    default String executionStatusToString(ExecutionStatus status) {
        return status != null ? status.name() : null;
    }

    @Named("stringToExecutionStatus")
    default ExecutionStatus stringToExecutionStatus(String status) {
        return status != null ? ExecutionStatus.valueOf(status) : null;
    }

    @Named("approvalActionToString")
    default String approvalActionToString(ApprovalAction action) {
        return action != null ? action.name() : null;
    }

    @Named("stringToApprovalAction")
    default ApprovalAction stringToApprovalAction(String action) {
        return action != null ? ApprovalAction.valueOf(action) : null;
    }

    @Named("rejectTargetToJson")
    default String rejectTargetToJson(RejectTarget rejectTarget) {
        if (rejectTarget == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(rejectTarget);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化驳回目标失败", e);
        }
    }

    @Named("jsonToRejectTarget")
    default RejectTarget jsonToRejectTarget(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, RejectTarget.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化驳回目标失败", e);
        }
    }
}