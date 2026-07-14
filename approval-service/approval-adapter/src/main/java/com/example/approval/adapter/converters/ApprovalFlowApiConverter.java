package com.example.approval.adapter.converters;

import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.dto.ApprovalNodeDTO;
import com.example.approval.api.dto.MatchRulesDTO;
import com.example.approval.api.request.CreateApprovalFlowRequest;
import com.example.approval.api.request.UpdateApprovalFlowRequest;
import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.root.ApprovalFlow;
import com.example.approval.domain.valueobject.FlowName;
import com.example.approval.domain.valueobject.MatchRules;
import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.types.NodeId;
import com.example.shared.primitives.identity.UserNo;

import java.util.List;

/**
 * 审批流API转换器
 * 负责 Request/DTO 与领域对象之间的转换
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public final class ApprovalFlowApiConverter {

    private ApprovalFlowApiConverter() {
        // 工具类，禁止实例化
    }

    /**
     * 将创建请求转换为领域对象所需的参数
     *
     * @param request 创建请求
     * @return 匹配规则
     */
    public static MatchRules toMatchRules(CreateApprovalFlowRequest request) {
        MatchRulesDTO dto = request.matchRules();
        return MatchRules.of(
                null, // productNo
                null, // customerNo
                dto.accountManagerCodes() != null && !dto.accountManagerCodes().isEmpty()
                        ? dto.accountManagerCodes().get(0) : null,
                null, // operationMode
                dto.businessTypes() != null && !dto.businessTypes().isEmpty()
                        ? dto.businessTypes().get(0) : null,
                null  // annuityChannel
        );
    }

    /**
     * 将更新请求转换为领域对象所需的参数
     *
     * @param request 更新请求
     * @return 匹配规则
     */
    public static MatchRules toMatchRules(UpdateApprovalFlowRequest request) {
        MatchRulesDTO dto = request.matchRules();
        return MatchRules.of(
                null, // productNo
                null, // customerNo
                dto.accountManagerCodes() != null && !dto.accountManagerCodes().isEmpty()
                        ? dto.accountManagerCodes().get(0) : null,
                null, // operationMode
                dto.businessTypes() != null && !dto.businessTypes().isEmpty()
                        ? dto.businessTypes().get(0) : null,
                null  // annuityChannel
        );
    }

    /**
     * 将节点DTO列表转换为领域对象列表
     *
     * @param nodeDTOs 节点DTO列表
     * @return 审批节点列表
     */
    public static List<ApprovalNode> toNodes(List<ApprovalNodeDTO> nodeDTOs) {
        if (nodeDTOs == null || nodeDTOs.isEmpty()) {
            return List.of();
        }

        return nodeDTOs.stream()
                .map(ApprovalFlowApiConverter::toNode)
                .toList();
    }

    /**
     * 将节点DTO转换为领域对象
     *
     * @param dto 节点DTO
     * @return 审批节点
     */
    private static ApprovalNode toNode(ApprovalNodeDTO dto) {
        return ApprovalNode.createSamePlanNode(
                dto.nodeId() != null ? dto.nodeId() : NodeId.of(System.currentTimeMillis()),
                NodeOrder.of(dto.order()),
                com.example.approval.types.enums.ApproverType.valueOf(dto.nodeType()),
                dto.approvalUsers() != null
                        ? dto.approvalUsers().stream().map(UserNo::of).toList()
                        : List.of(),
                null, // roleIds
                com.example.approval.types.enums.SignMode.OR_SIGN,
                UserNo.of("SYSTEM")
        );
    }

    /**
     * 将领域对象转换为DTO
     *
     * @param flow 审批流
     * @return 审批流DTO
     */
    public static ApprovalFlowDTO toDTO(ApprovalFlow flow) {
        return new ApprovalFlowDTO(
                flow.id(),
                flow.flowName().value(),
                flow.matchRules().businessType(),
                flow.status().name(),
                flow.flowVersion().value(),
                toMatchRulesDTO(flow.matchRules()),
                flow.getNodes().stream().map(ApprovalFlowApiConverter::toNodeDTO).toList(),
                flow.createdBy().value(),
                flow.createdAt(),
                flow.updatedAt()
        );
    }

    /**
     * 将匹配规则转换为DTO
     *
     * @param rules 匹配规则
     * @return 匹配规则DTO
     */
    private static MatchRulesDTO toMatchRulesDTO(MatchRules rules) {
        if (rules == null) {
            return null;
        }
        return new MatchRulesDTO(
                rules.accountManager() != null ? List.of(rules.accountManager()) : null,
                rules.businessType() != null ? List.of(rules.businessType()) : null,
                null, // amountMin
                null  // amountMax
        );
    }

    /**
     * 将审批节点转换为DTO
     *
     * @param node 审批节点
     * @return 审批节点DTO
     */
    private static ApprovalNodeDTO toNodeDTO(ApprovalNode node) {
        return new ApprovalNodeDTO(
                node.id(),
                node.nodeType().name(),
                node.nodeType().name(),
                node.isSpecifiedRoleApproval() && !node.roleIds().isEmpty()
                        ? node.roleIds().get(0) : null,
                node.approverIds().stream().map(UserNo::value).toList(),
                node.nodeOrder().value(),
                true
        );
    }
}