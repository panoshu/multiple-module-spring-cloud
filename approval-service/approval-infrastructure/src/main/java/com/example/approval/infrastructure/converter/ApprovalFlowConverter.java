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
import com.example.shared.primitives.identity.UserNo;
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
    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "flowName", source = "flowName.value")
    @Mapping(target = "matchRules", source = "matchRules", qualifiedByName = "matchRulesToJson")
    @Mapping(target = "flowVersion", source = "flowVersion.value")
    @Mapping(target = "status", source = "status", qualifiedByName = "flowStatusToString")
    @Mapping(target = "createdBy", source = "createdBy.value")
    @Mapping(target = "updatedBy", source = "updatedBy.value")
    @Mapping(target = "version", source = "version.value")
    @Mapping(target = "deleted", constant = "false")
    ApprovalFlowDO toDO(ApprovalFlow flow);

    /**
     * 审批流DO对象转领域对象
     *
     * @param flowDO 审批流DO对象
     * @return 审批流领域对象
     */
    @Mapping(target = "id", source = "id", qualifiedByName = "toApprovalFlowId")
    @Mapping(target = "flowName", source = "flowName", qualifiedByName = "toFlowName")
    @Mapping(target = "matchRules", source = "matchRules", qualifiedByName = "jsonToMatchRules")
    @Mapping(target = "flowVersion", source = "flowVersion", qualifiedByName = "toFlowVersion")
    @Mapping(target = "status", source = "status", qualifiedByName = "stringToFlowStatus")
    @Mapping(target = "createdBy", source = "createdBy", qualifiedByName = "toUserNo")
    @Mapping(target = "updatedBy", source = "updatedBy", qualifiedByName = "toUserNo")
    @Mapping(target = "createdAt", source = "createTime")
    @Mapping(target = "updatedAt", source = "updateTime")
    @Mapping(target = "version", source = "version", qualifiedByName = "toVersion")
    @Mapping(target = "nodes", ignore = true)
    ApprovalFlow toDomain(ApprovalFlowDO flowDO);

    /**
     * 审批节点领域对象转DO对象
     *
     * @param node 审批节点领域对象
     * @return 审批节点DO对象
     */
    @Mapping(target = "id", source = "id.value")
    @Mapping(target = "flowId", ignore = true)
    @Mapping(target = "nodeOrder", source = "nodeOrder.value")
    @Mapping(target = "nodeType", source = "nodeType", qualifiedByName = "nodeTypeToString")
    @Mapping(target = "terminalLevel", source = "terminalLevel", qualifiedByName = "terminalLevelToInteger")
    @Mapping(target = "approverType", source = "approverType", qualifiedByName = "approverTypeToString")
    @Mapping(target = "approverIds", source = "approverIds", qualifiedByName = "userNoListToJson")
    @Mapping(target = "roleIds", source = "roleIds", qualifiedByName = "stringListToJson")
    @Mapping(target = "signMode", source = "signMode", qualifiedByName = "signModeToString")
    @Mapping(target = "createdBy", source = "createdBy.value")
    @Mapping(target = "updatedBy", source = "updatedBy.value")
    @Mapping(target = "version", source = "version.value")
    @Mapping(target = "deleted", constant = "false")
    ApprovalNodeDO toNodeDO(ApprovalNode node);

    /**
     * 审批节点DO对象转领域对象
     *
     * @param nodeDO 审批节点DO对象
     * @return 审批节点领域对象
     */
    @Mapping(target = "id", source = "id", qualifiedByName = "toNodeId")
    @Mapping(target = "nodeOrder", source = "nodeOrder", qualifiedByName = "toNodeOrder")
    @Mapping(target = "nodeType", source = "nodeType", qualifiedByName = "stringToNodeType")
    @Mapping(target = "terminalLevel", source = "terminalLevel", qualifiedByName = "integerToTerminalLevel")
    @Mapping(target = "approverType", source = "approverType", qualifiedByName = "stringToApproverType")
    @Mapping(target = "approverIds", source = "approverIds", qualifiedByName = "jsonToUserNoList")
    @Mapping(target = "roleIds", source = "roleIds", qualifiedByName = "jsonToStringList")
    @Mapping(target = "signMode", source = "signMode", qualifiedByName = "stringToSignMode")
    @Mapping(target = "createdBy", source = "createdBy", qualifiedByName = "toUserNo")
    @Mapping(target = "updatedBy", source = "updatedBy", qualifiedByName = "toUserNo")
    @Mapping(target = "createdAt", source = "createTime")
    @Mapping(target = "updatedAt", source = "updateTime")
    @Mapping(target = "version", source = "version", qualifiedByName = "toVersion")
    ApprovalNode toNodeDomain(ApprovalNodeDO nodeDO);

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
            List<String> values = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
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
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化字符串列表失败", e);
        }
    }
}