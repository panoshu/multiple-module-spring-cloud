package com.example.approval.infrastructure.converter;

import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.root.ApprovalFlow;
import com.example.approval.domain.valueobject.*;
import com.example.approval.infrastructure.entity.ApprovalFlowDO;
import com.example.approval.infrastructure.entity.ApprovalNodeDO;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.NodeId;
import com.example.approval.types.enums.ApproverType;
import com.example.approval.types.enums.FlowStatus;
import com.example.approval.types.enums.NodeType;
import com.example.approval.types.enums.SignMode;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.UserNo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.List;

/**
 * 审批流转换器
 * 负责审批流领域对象与DO对象之间的转换
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@Mapper(componentModel = "spring")
public interface ApprovalFlowConverter {

  ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * 审批流领域对象转DO对象
   *
   * @param flow 审批流领域对象
   * @return 审批流DO对象
   */
  @Mapping(target = "id", expression = "java(flow.id() != null ? String.valueOf(flow.id().value()) : null)")
  @Mapping(target = "flowName", expression = "java(flow.flowName() != null ? flow.flowName().value() : null)")
  // businessType 在领域对象中不存在，DO 保留此字段供未来扩展，暂忽略
  @Mapping(target = "businessType", ignore = true)
  @Mapping(target = "matchRules", expression = "java(matchRulesToJson(flow.matchRules()))")
  @Mapping(target = "flowVersion", expression = "java(flow.flowVersion() != null ? flow.flowVersion().value() : null)")
  @Mapping(target = "status", expression = "java(flowStatusToString(flow.status()))")
  // createTime/updateTime 由数据库 @Column(onInsertValue/onUpdateValue) 自动管理
  @Mapping(target = "createTime", ignore = true)
  @Mapping(target = "updateTime", ignore = true)
  @Mapping(target = "createdBy", expression = "java(flow.createdBy() != null ? flow.createdBy().value() : null)")
  @Mapping(target = "updatedBy", expression = "java(flow.updatedBy() != null ? flow.updatedBy().value() : null)")
  @Mapping(target = "version", expression = "java(flow.version() != null ? (int) flow.version().value() : null)")
  @Mapping(target = "deleted", constant = "false")
  ApprovalFlowDO toDO(ApprovalFlow flow);

  /**
   * 审批流DO对象转领域对象
   *
   * @param flowDO 审批流DO对象
   * @return 审批流领域对象
   */
  default ApprovalFlow toDomain(ApprovalFlowDO flowDO) {
    if (flowDO == null) {
      return null;
    }
    return ApprovalFlow.reconstitute(
      toApprovalFlowId(flowDO.getId()),
      toFlowName(flowDO.getFlowName()),
      jsonToMatchRules(flowDO.getMatchRules()),
      null,
      toFlowVersion(flowDO.getFlowVersion()),
      stringToFlowStatus(flowDO.getStatus()),
      toUserNo(flowDO.getCreatedBy()),
      toUserNo(flowDO.getUpdatedBy()),
      flowDO.getCreateTime(),
      flowDO.getUpdateTime(),
      toVersion(flowDO.getVersion())
    );
  }

  /**
   * 审批节点领域对象转DO对象
   *
   * @param node 审批节点领域对象
   * @return 审批节点DO对象
   */
  @Mapping(target = "id", expression = "java(node.id() != null ? String.valueOf(node.id().value()) : null)")
  @Mapping(target = "flowId", ignore = true)
  @Mapping(target = "nodeOrder", expression = "java(node.nodeOrder() != null ? node.nodeOrder().value() : null)")
  @Mapping(target = "nodeType", expression = "java(nodeTypeToString(node.nodeType()))")
  @Mapping(target = "specifiedPlanId", expression = "java(node.specifiedPlanId().orElse(null))")
  @Mapping(target = "terminalLevel", expression = "java(terminalLevelToInteger(node.terminalLevel().orElse(null)))")
  @Mapping(target = "approverType", expression = "java(approverTypeToString(node.approverType()))")
  @Mapping(target = "approverIds", expression = "java(userNoListToJson(node.approverIds()))")
  @Mapping(target = "roleIds", expression = "java(stringListToJson(node.roleIds()))")
  @Mapping(target = "signMode", expression = "java(signModeToString(node.signMode()))")
  // createTime/updateTime 由应用层从领域对象 createdAt()/updatedAt() 映射
  @Mapping(target = "createTime", expression = "java(node.createdAt())")
  @Mapping(target = "updateTime", expression = "java(node.updatedAt())")
  @Mapping(target = "createdBy", expression = "java(node.createdBy() != null ? node.createdBy().value() : null)")
  @Mapping(target = "updatedBy", expression = "java(node.updatedBy() != null ? node.updatedBy().value() : null)")
  @Mapping(target = "version", expression = "java(node.version() != null ? (int) node.version().value() : null)")
  @Mapping(target = "deleted", constant = "false")
  ApprovalNodeDO toNodeDO(ApprovalNode node);

  /**
   * 审批节点DO对象转领域对象
   *
   * @param nodeDO 审批节点DO对象
   * @return 审批节点领域对象
   */
  default ApprovalNode toNodeDomain(ApprovalNodeDO nodeDO) {
    if (nodeDO == null) {
      return null;
    }
    return ApprovalNode.reconstitute(
      toNodeId(nodeDO.getId()),
      toNodeOrder(nodeDO.getNodeOrder()),
      stringToNodeType(nodeDO.getNodeType()),
      nodeDO.getSpecifiedPlanId(),
      integerToTerminalLevel(nodeDO.getTerminalLevel()),
      stringToApproverType(nodeDO.getApproverType()),
      jsonToUserNoList(nodeDO.getApproverIds()),
      jsonToStringList(nodeDO.getRoleIds()),
      stringToSignMode(nodeDO.getSignMode()),
      toUserNo(nodeDO.getCreatedBy()),
      toUserNo(nodeDO.getUpdatedBy()),
      nodeDO.getCreateTime(),
      nodeDO.getUpdateTime(),
      toVersion(nodeDO.getVersion())
    );
  }

  // ========== 类型转换方法 ==========

  @Named("toApprovalFlowId")
  default ApprovalFlowId toApprovalFlowId(String id) {
    return id != null ? ApprovalFlowId.of(Long.parseLong(id)) : null;
  }

  @Named("toFlowName")
  default FlowName toFlowName(String flowName) {
    return flowName != null ? FlowName.of(flowName) : null;
  }

  @Named("toFlowVersion")
  default FlowVersion toFlowVersion(Integer flowVersion) {
    return flowVersion != null ? FlowVersion.of(flowVersion) : null;
  }

  @Named("toVersion")
  default Version toVersion(Integer version) {
    return version != null ? Version.of(version) : null;
  }

  @Named("toUserNo")
  default UserNo toUserNo(String userNo) {
    return userNo != null ? UserNo.of(userNo) : null;
  }

  @Named("toNodeId")
  default NodeId toNodeId(String id) {
    return id != null ? NodeId.of(Long.parseLong(id)) : null;
  }

  @Named("toNodeOrder")
  default NodeOrder toNodeOrder(Integer nodeOrder) {
    return nodeOrder != null ? NodeOrder.of(nodeOrder) : null;
  }

  @Named("matchRulesToJson")
  default String matchRulesToJson(MatchRules matchRules) {
    if (matchRules == null) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(matchRules);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化匹配规则失败", e);
    }
  }

  @Named("jsonToMatchRules")
  default MatchRules jsonToMatchRules(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.readValue(json, MatchRules.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化匹配规则失败", e);
    }
  }

  @Named("flowStatusToString")
  default String flowStatusToString(FlowStatus status) {
    return status != null ? status.name() : null;
  }

  @Named("stringToFlowStatus")
  default FlowStatus stringToFlowStatus(String status) {
    return status != null ? FlowStatus.valueOf(status) : null;
  }

  @Named("nodeTypeToString")
  default String nodeTypeToString(NodeType nodeType) {
    return nodeType != null ? nodeType.name() : null;
  }

  @Named("stringToNodeType")
  default NodeType stringToNodeType(String nodeType) {
    return nodeType != null ? NodeType.valueOf(nodeType) : null;
  }

  @Named("approverTypeToString")
  default String approverTypeToString(ApproverType approverType) {
    return approverType != null ? approverType.name() : null;
  }

  @Named("stringToApproverType")
  default ApproverType stringToApproverType(String approverType) {
    return approverType != null ? ApproverType.valueOf(approverType) : null;
  }

  @Named("signModeToString")
  default String signModeToString(SignMode signMode) {
    return signMode != null ? signMode.name() : null;
  }

  @Named("stringToSignMode")
  default SignMode stringToSignMode(String signMode) {
    return signMode != null ? SignMode.valueOf(signMode) : null;
  }

  @Named("terminalLevelToInteger")
  default Integer terminalLevelToInteger(TerminalLevel terminalLevel) {
    return terminalLevel != null ? terminalLevel.value() : null;
  }

  @Named("integerToTerminalLevel")
  default TerminalLevel integerToTerminalLevel(Integer value) {
    return value != null ? TerminalLevel.of(value) : null;
  }

  @Named("userNoListToJson")
  default String userNoListToJson(List<UserNo> userNos) {
    if (userNos == null || userNos.isEmpty()) {
      return null;
    }
    try {
      List<String> values = userNos.stream()
        .map(UserNo::value)
        .toList();
      return OBJECT_MAPPER.writeValueAsString(values);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化审批人ID列表失败", e);
    }
  }

  @Named("jsonToUserNoList")
  default List<UserNo> jsonToUserNoList(String json) {
    if (json == null || json.isBlank()) {
      return Collections.emptyList();
    }
    try {
      List<String> values = OBJECT_MAPPER.readValue(json, new TypeReference<>() {
      });
      return values.stream()
        .map(UserNo::of)
        .toList();
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化审批人ID列表失败", e);
    }
  }

  @Named("stringListToJson")
  default String stringListToJson(List<String> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(list);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("序列化字符串列表失败", e);
    }
  }

  @Named("jsonToStringList")
  default List<String> jsonToStringList(String json) {
    if (json == null || json.isBlank()) {
      return Collections.emptyList();
    }
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<>() {
      });
    } catch (JsonProcessingException e) {
      throw new RuntimeException("反序列化字符串列表失败", e);
    }
  }
}
