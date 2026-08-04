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
import com.example.approval.types.*;
import com.example.approval.types.enums.ApprovalAction;
import com.example.approval.types.enums.ExecutionStatus;
import com.example.approval.types.enums.InstanceStatus;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.ApplicationId;
import com.example.shared.identifier.id.UserNo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

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
  @Mapping(target = "id", expression = "java(instance.id() != null ? String.valueOf(instance.id().value()) : null)")
  @Mapping(target = "flowId", expression = "java(instance.flowId() != null ? String.valueOf(instance.flowId().value()) : null)")
  @Mapping(target = "flowVersion", expression = "java(instance.flowVersion() != null ? instance.flowVersion().value() : null)")
  @Mapping(target = "businessApplicationId", expression = "java(instance.businessApplicationId() != null ? instance.businessApplicationId().value() : null)")
  @Mapping(target = "businessNo", expression = "java(instance.businessApplicationId() != null ? instance.businessApplicationId().value() : null)")
  @Mapping(target = "businessType", expression = "java(instance.businessType())")
  @Mapping(target = "currentNodeOrder", expression = "java(instance.currentNodeOrder() != null ? instance.currentNodeOrder().value() : null)")
  @Mapping(target = "status", expression = "java(instanceStatusToString(instance.status()))")
  @Mapping(target = "initiatorPlan", expression = "java(instance.initiatorPlan())")
  @Mapping(target = "currentPlan", expression = "java(instance.currentPlan())")
  // createTime/updateTime 由应用层从领域对象 createdAt()/updatedAt() 映射
  @Mapping(target = "createTime", expression = "java(instance.createdAt())")
  @Mapping(target = "updateTime", expression = "java(instance.updatedAt())")
  @Mapping(target = "createdBy", expression = "java(instance.createdBy() != null ? instance.createdBy().value() : null)")
  @Mapping(target = "updatedBy", expression = "java(instance.updatedBy() != null ? instance.updatedBy().value() : null)")
  @Mapping(target = "version", expression = "java(instance.version() != null ? (int) instance.version().value() : null)")
  @Mapping(target = "deleted", constant = "false")
  ApprovalInstanceDO toDO(ApprovalInstance instance);

  /**
   * 审批实例DO对象转领域对象
   *
   * @param instanceDO 审批实例DO对象
   * @return 审批实例领域对象
   */
  default ApprovalInstance toDomain(ApprovalInstanceDO instanceDO) {
    if (instanceDO == null) {
      return null;
    }
    return ApprovalInstance.reconstitute(
      toApprovalInstanceId(instanceDO.getId()),
      toApprovalFlowId(instanceDO.getFlowId()),
      toFlowVersion(instanceDO.getFlowVersion()),
      toApplicationId(instanceDO.getBusinessApplicationId()),
      instanceDO.getBusinessType(),
      toNodeOrder(instanceDO.getCurrentNodeOrder()),
      stringToInstanceStatus(instanceDO.getStatus()),
      instanceDO.getInitiatorPlan(),
      instanceDO.getCurrentPlan(),
      null,
      toUserNo(instanceDO.getCreatedBy()),
      toUserNo(instanceDO.getUpdatedBy()),
      instanceDO.getCreateTime(),
      instanceDO.getUpdateTime(),
      toVersion(instanceDO.getVersion())
    );
  }

  /**
   * 节点执行记录领域对象转DO对象
   *
   * @param execution 节点执行记录领域对象
   * @return 节点执行记录DO对象
   */
  @Mapping(target = "id", expression = "java(execution.id() != null ? String.valueOf(execution.id().value()) : null)")
  // instanceId 由 RepositoryImpl 在保存时从聚合根上下文设置，领域对象不持有此关联字段
  @Mapping(target = "instanceId", ignore = true)
  @Mapping(target = "nodeId", expression = "java(execution.nodeId() != null ? String.valueOf(execution.nodeId().value()) : null)")
  @Mapping(target = "nodeOrder", expression = "java(execution.nodeOrder() != null ? execution.nodeOrder().value() : null)")
  @Mapping(target = "status", expression = "java(executionStatusToString(execution.status()))")
  @Mapping(target = "startedAt", expression = "java(execution.startedAt())")
  @Mapping(target = "completedAt", expression = "java(execution.completedAt())")
  // createTime/updateTime 由数据库自动管理
  @Mapping(target = "createTime", ignore = true)
  @Mapping(target = "updateTime", ignore = true)
  @Mapping(target = "createdBy", expression = "java(execution.createdBy() != null ? execution.createdBy().value() : null)")
  @Mapping(target = "updatedBy", expression = "java(execution.updatedBy() != null ? execution.updatedBy().value() : null)")
  @Mapping(target = "version", expression = "java(execution.version() != null ? (int) execution.version().value() : null)")
  @Mapping(target = "deleted", constant = "false")
  ApprovalNodeExecutionDO toExecutionDO(NodeExecution execution);

  /**
   * 节点执行记录DO对象转领域对象
   *
   * @param executionDO 节点执行记录DO对象
   * @return 节点执行记录领域对象
   */
  default NodeExecution toExecutionDomain(ApprovalNodeExecutionDO executionDO) {
    if (executionDO == null) {
      return null;
    }
    return NodeExecution.reconstitute(
      toExecutionId(executionDO.getId()),
      toNodeId(executionDO.getNodeId()),
      toNodeOrder(executionDO.getNodeOrder()),
      stringToExecutionStatus(executionDO.getStatus()),
      null,
      executionDO.getStartedAt(),
      executionDO.getCompletedAt(),
      toUserNo(executionDO.getCreatedBy()),
      toUserNo(executionDO.getUpdatedBy()),
      executionDO.getCreateTime(),
      executionDO.getUpdateTime(),
      toVersion(executionDO.getVersion())
    );
  }

  /**
   * 审批记录领域对象转DO对象
   *
   * @param record 审批记录领域对象
   * @return 审批记录DO对象
   */
  @Mapping(target = "id", expression = "java(record.id() != null ? String.valueOf(record.id().value()) : null)")
  // executionId 由 RepositoryImpl 在保存时从聚合根上下文设置，领域对象不持有此关联字段
  @Mapping(target = "executionId", ignore = true)
  @Mapping(target = "approverId", expression = "java(record.approverId() != null ? record.approverId().value() : null)")
  @Mapping(target = "action", expression = "java(approvalActionToString(record.action()))")
  @Mapping(target = "opinion", expression = "java(approvalOpinionToString(record.opinion()))")
  @Mapping(target = "rejectTarget", expression = "java(rejectTargetToJson(record.rejectTarget()))")
  @Mapping(target = "transferTo", expression = "java(record.transferTo() != null ? record.transferTo().value() : null)")
  @Mapping(target = "operatedAt", expression = "java(record.operatedAt())")
  // createTime/updateTime 由应用层从领域对象 createdAt()/updatedAt() 映射
  @Mapping(target = "createTime", expression = "java(record.createdAt())")
  @Mapping(target = "updateTime", expression = "java(record.updatedAt())")
  @Mapping(target = "createdBy", expression = "java(record.createdBy() != null ? record.createdBy().value() : null)")
  @Mapping(target = "updatedBy", expression = "java(record.updatedBy() != null ? record.updatedBy().value() : null)")
  @Mapping(target = "version", expression = "java(record.version() != null ? (int) record.version().value() : null)")
  @Mapping(target = "deleted", constant = "false")
  ApprovalRecordDO toRecordDO(ApprovalRecord record);

  /**
   * 审批记录DO对象转领域对象
   *
   * @param recordDO 审批记录DO对象
   * @return 审批记录领域对象
   */
  default ApprovalRecord toRecordDomain(ApprovalRecordDO recordDO) {
    if (recordDO == null) {
      return null;
    }
    return ApprovalRecord.reconstitute(
      toRecordId(recordDO.getId()),
      toUserNo(recordDO.getApproverId()),
      stringToApprovalAction(recordDO.getAction()),
      toApprovalOpinion(recordDO.getOpinion()),
      jsonToRejectTarget(recordDO.getRejectTarget()),
      toUserNo(recordDO.getTransferTo()),
      recordDO.getOperatedAt(),
      toUserNo(recordDO.getCreatedBy()),
      toUserNo(recordDO.getUpdatedBy()),
      recordDO.getCreateTime(),
      recordDO.getUpdateTime(),
      toVersion(recordDO.getVersion())
    );
  }

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
    return id != null ? new ApplicationId(id) : null;
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

  default String approvalOpinionToString(ApprovalOpinion opinion) {
    return opinion != null ? opinion.value() : null;
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
