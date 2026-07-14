package com.example.approval.domain.aggregate.entity;

import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.domain.valueobject.TerminalLevel;
import com.example.approval.types.NodeId;
import com.example.approval.types.enums.ApproverType;
import com.example.approval.types.enums.NodeType;
import com.example.approval.types.enums.SignMode;
import com.example.shared.domain.aggregate.entity.Entity;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 审批节点实体
 * 定义审批流中的一个审批节点
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public class ApprovalNode extends Entity<NodeId> {

    /**
     * 节点顺序
     */
    private final NodeOrder nodeOrder;

    /**
     * 节点类型
     */
    private final NodeType nodeType;

    /**
     * 指定方案ID（仅当 nodeType 为 SPECIFIED_PLAN 时有效）
     */
    private final String specifiedPlanId;

    /**
     * 终止级别（仅当 nodeType 为 LEVEL_UP 时有效）
     */
    private final TerminalLevel terminalLevel;

    /**
     * 审批人类型
     */
    private final ApproverType approverType;

    /**
     * 审批人ID列表
     */
    private final List<UserNo> approverIds;

    /**
     * 角色ID列表
     */
    private final List<String> roleIds;

    /**
     * 签批模式
     */
    private final SignMode signMode;

    /**
     * 场景1: 业务创建
     */
    private ApprovalNode(NodeId id, NodeOrder nodeOrder, NodeType nodeType, String specifiedPlanId,
                         TerminalLevel terminalLevel, ApproverType approverType, List<UserNo> approverIds,
                         List<String> roleIds, SignMode signMode, UserNo operator) {
        super(id, operator);
        this.nodeOrder = nodeOrder;
        this.nodeType = nodeType;
        this.specifiedPlanId = specifiedPlanId;
        this.terminalLevel = terminalLevel;
        this.approverType = approverType;
        this.approverIds = approverIds != null ? List.copyOf(approverIds) : List.of();
        this.roleIds = roleIds != null ? List.copyOf(roleIds) : List.of();
        this.signMode = signMode;
    }

    /**
     * 场景2: 从数据库重建
     */
    private ApprovalNode(NodeId id, NodeOrder nodeOrder, NodeType nodeType, String specifiedPlanId,
                         TerminalLevel terminalLevel, ApproverType approverType, List<UserNo> approverIds,
                         List<String> roleIds, SignMode signMode,
                         UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.nodeOrder = nodeOrder;
        this.nodeType = nodeType;
        this.specifiedPlanId = specifiedPlanId;
        this.terminalLevel = terminalLevel;
        this.approverType = approverType;
        this.approverIds = approverIds != null ? List.copyOf(approverIds) : List.of();
        this.roleIds = roleIds != null ? List.copyOf(roleIds) : List.of();
        this.signMode = signMode;
    }

    /**
     * 静态工厂方法 - 创建指定方案节点
     *
     * @param id             节点ID
     * @param nodeOrder      节点顺序
     * @param specifiedPlanId 指定方案ID
     * @param approverType   审批人类型
     * @param approverIds    审批人ID列表
     * @param roleIds        角色ID列表
     * @param signMode       签批模式
     * @param operator       操作人
     * @return ApprovalNode 实例
     */
    public static ApprovalNode createSpecifiedPlanNode(NodeId id, NodeOrder nodeOrder, String specifiedPlanId,
                                                        ApproverType approverType, List<UserNo> approverIds,
                                                        List<String> roleIds, SignMode signMode, UserNo operator) {
        if (specifiedPlanId == null || specifiedPlanId.isBlank()) {
            throw new IllegalArgumentException("指定方案节点必须提供 specifiedPlanId");
        }
        return new ApprovalNode(id, nodeOrder, NodeType.SPECIFIED_PLAN, specifiedPlanId, null,
                approverType, approverIds, roleIds, signMode, operator);
    }

    /**
     * 静态工厂方法 - 创建同方案节点
     *
     * @param id           节点ID
     * @param nodeOrder    节点顺序
     * @param approverType 审批人类型
     * @param approverIds  审批人ID列表
     * @param roleIds      角色ID列表
     * @param signMode     签批模式
     * @param operator     操作人
     * @return ApprovalNode 实例
     */
    public static ApprovalNode createSamePlanNode(NodeId id, NodeOrder nodeOrder,
                                                   ApproverType approverType, List<UserNo> approverIds,
                                                   List<String> roleIds, SignMode signMode, UserNo operator) {
        return new ApprovalNode(id, nodeOrder, NodeType.SAME_PLAN, null, null,
                approverType, approverIds, roleIds, signMode, operator);
    }

    /**
     * 静态工厂方法 - 创建上一级节点
     *
     * @param id            节点ID
     * @param nodeOrder     节点顺序
     * @param terminalLevel 终止级别
     * @param approverType  审批人类型
     * @param approverIds   审批人ID列表
     * @param roleIds       角色ID列表
     * @param signMode      签批模式
     * @param operator      操作人
     * @return ApprovalNode 实例
     */
    public static ApprovalNode createLevelUpNode(NodeId id, NodeOrder nodeOrder, TerminalLevel terminalLevel,
                                                  ApproverType approverType, List<UserNo> approverIds,
                                                  List<String> roleIds, SignMode signMode, UserNo operator) {
        if (terminalLevel == null) {
            throw new IllegalArgumentException("上一级节点必须提供 terminalLevel");
        }
        return new ApprovalNode(id, nodeOrder, NodeType.LEVEL_UP, null, terminalLevel,
                approverType, approverIds, roleIds, signMode, operator);
    }

    /**
     * 静态工厂方法 - 从数据库重建
     */
    public static ApprovalNode reconstitute(NodeId id, NodeOrder nodeOrder, NodeType nodeType, String specifiedPlanId,
                                            TerminalLevel terminalLevel, ApproverType approverType, List<UserNo> approverIds,
                                            List<String> roleIds, SignMode signMode,
                                            UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new ApprovalNode(id, nodeOrder, nodeType, specifiedPlanId, terminalLevel, approverType,
                approverIds, roleIds, signMode, createdBy, updatedBy, createdAt, updatedAt, version);
    }

    public NodeOrder nodeOrder() {
        return nodeOrder;
    }

    public NodeType nodeType() {
        return nodeType;
    }

    public Optional<String> specifiedPlanId() {
        return Optional.ofNullable(specifiedPlanId);
    }

    public Optional<TerminalLevel> terminalLevel() {
        return Optional.ofNullable(terminalLevel);
    }

    public ApproverType approverType() {
        return approverType;
    }

    public List<UserNo> approverIds() {
        return Collections.unmodifiableList(approverIds);
    }

    public List<String> roleIds() {
        return Collections.unmodifiableList(roleIds);
    }

    public SignMode signMode() {
        return signMode;
    }

    /**
     * 是否为会签模式
     */
    public boolean isAndSign() {
        return signMode == SignMode.AND_SIGN;
    }

    /**
     * 是否为或签模式
     */
    public boolean isOrSign() {
        return signMode == SignMode.OR_SIGN;
    }

    /**
     * 是否为指定用户审批
     */
    public boolean isSpecifiedUserApproval() {
        return approverType == ApproverType.SPECIFIED_USER;
    }

    /**
     * 是否为指定角色审批
     */
    public boolean isSpecifiedRoleApproval() {
        return approverType == ApproverType.SPECIFIED_ROLE;
    }

    @Override
    protected void validateInvariants() {
        // 校验节点顺序
        if (nodeOrder == null) {
            throw new IllegalStateException("节点顺序不能为空");
        }

        // 校验节点类型
        if (nodeType == null) {
            throw new IllegalStateException("节点类型不能为空");
        }

        // 校验节点类型与参数的匹配
        if (nodeType == NodeType.SPECIFIED_PLAN && (specifiedPlanId == null || specifiedPlanId.isBlank())) {
            throw new IllegalStateException("指定方案节点必须提供 specifiedPlanId");
        }

        if (nodeType == NodeType.LEVEL_UP && terminalLevel == null) {
            throw new IllegalStateException("上一级节点必须提供 terminalLevel");
        }

        if (nodeType == NodeType.SAME_PLAN && specifiedPlanId != null) {
            throw new IllegalStateException("同方案节点不应有 specifiedPlanId");
        }

        if (nodeType != NodeType.LEVEL_UP && terminalLevel != null) {
            throw new IllegalStateException("只有上一级节点才能有 terminalLevel");
        }

        // 校验审批人类型
        if (approverType == null) {
            throw new IllegalStateException("审批人类型不能为空");
        }

        // 校验签批模式
        if (signMode == null) {
            throw new IllegalStateException("签批模式不能为空");
        }

        // 校验审批人配置
        if (approverType == ApproverType.SPECIFIED_USER && approverIds.isEmpty()) {
            throw new IllegalStateException("指定用户审批必须提供审批人ID列表");
        }

        if (approverType == ApproverType.SPECIFIED_ROLE && roleIds.isEmpty()) {
            throw new IllegalStateException("指定角色审批必须提供角色ID列表");
        }
    }
}