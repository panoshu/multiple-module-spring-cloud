package com.example.approval.domain.aggregate.root;

import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.errorcode.ApprovalDomainErrorCode;
import com.example.approval.domain.valueobject.FlowName;
import com.example.approval.domain.valueobject.FlowVersion;
import com.example.approval.domain.valueobject.MatchRules;
import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.enums.FlowStatus;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 审批流聚合根
 * 定义审批流程的配置信息，包含匹配规则和审批节点
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public class ApprovalFlow extends AggregateRoot<ApprovalFlowId> {

    /**
     * 审批流名称
     */
    private final FlowName flowName;

    /**
     * 匹配规则
     */
    private final MatchRules matchRules;

    /**
     * 审批节点列表
     */
    private final List<ApprovalNode> nodes;

    /**
     * 版本号
     */
    private FlowVersion flowVersion;

    /**
     * 状态
     */
    private FlowStatus status;

    /**
     * 场景1: 业务创建
     */
    private ApprovalFlow(ApprovalFlowId id, FlowName flowName, MatchRules matchRules,
                         List<ApprovalNode> nodes, UserNo operator) {
        super(id, operator);
        this.flowName = flowName;
        this.matchRules = matchRules;
        this.nodes = nodes != null ? new ArrayList<>(nodes) : new ArrayList<>();
        this.flowVersion = FlowVersion.initial();
        this.status = FlowStatus.ACTIVE;
        validateNodesOrder();
    }

    /**
     * 场景2: 从数据库重建
     */
    private ApprovalFlow(ApprovalFlowId id, FlowName flowName, MatchRules matchRules,
                         List<ApprovalNode> nodes, FlowVersion flowVersion, FlowStatus status,
                         UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.flowName = flowName;
        this.matchRules = matchRules;
        this.nodes = nodes != null ? new ArrayList<>(nodes) : new ArrayList<>();
        this.flowVersion = flowVersion;
        this.status = status;
    }

    /**
     * 静态工厂方法 - 创建审批流
     *
     * @param id         审批流ID
     * @param flowName   审批流名称
     * @param matchRules 匹配规则
     * @param nodes      审批节点列表
     * @param operator   操作人
     * @return ApprovalFlow 实例
     */
    public static ApprovalFlow create(ApprovalFlowId id, FlowName flowName, MatchRules matchRules,
                                       List<ApprovalNode> nodes, UserNo operator) {
        if (flowName == null) {
            throw new IllegalArgumentException("审批流名称不能为空");
        }
        if (matchRules == null) {
            throw new IllegalArgumentException("匹配规则不能为空");
        }
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("审批节点列表不能为空");
        }
        if (operator == null) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        return new ApprovalFlow(id, flowName, matchRules, nodes, operator);
    }

    /**
     * 静态工厂方法 - 从数据库重建
     */
    public static ApprovalFlow reconstitute(ApprovalFlowId id, FlowName flowName, MatchRules matchRules,
                                             List<ApprovalNode> nodes, FlowVersion flowVersion, FlowStatus status,
                                             UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new ApprovalFlow(id, flowName, matchRules, nodes, flowVersion, status,
                createdBy, updatedBy, createdAt, updatedAt, version);
    }

    /**
     * 更新审批流
     *
     * @param flowName   审批流名称
     * @param matchRules 匹配规则
     * @param nodes      审批节点列表
     * @param operator   操作人
     */
    public void update(FlowName flowName, MatchRules matchRules, List<ApprovalNode> nodes, UserNo operator) {
        if (this.status == FlowStatus.DEPRECATED) {
            throw new DomainException(ApprovalDomainErrorCode.APPROVAL_FLOW_DEPRECATED)
                    .withLogDetail("废弃状态的审批流不能更新, ApprovalFlowId: %s".formatted(this.id()));
        }
        if (flowName != null) {
            // flowName 是 final 的，这里需要通过重建来更新，但在聚合根中通常使用可变字段
            throw new DomainException(ApprovalDomainErrorCode.APPROVAL_FLOW_NODE_INVALID)
                    .withLogDetail("审批流名称不可修改");
        }
        if (matchRules != null) {
            // matchRules 是 final 的，同样的问题
            throw new DomainException(ApprovalDomainErrorCode.APPROVAL_FLOW_NODE_INVALID)
                    .withLogDetail("匹配规则不可修改");
        }
        if (nodes != null && !nodes.isEmpty()) {
            this.nodes.clear();
            this.nodes.addAll(nodes);
            validateNodesOrder();
        }
        this.flowVersion = this.flowVersion.increment();
        this.markUpdated(operator);
    }

    /**
     * 废弃审批流
     *
     * @param operator 操作人
     */
    public void deprecate(UserNo operator) {
        if (this.status == FlowStatus.DEPRECATED) {
            throw new DomainException(ApprovalDomainErrorCode.APPROVAL_FLOW_DEPRECATED)
                    .withLogDetail("审批流已废弃, ApprovalFlowId: %s".formatted(this.id()));
        }
        this.status = FlowStatus.DEPRECATED;
        this.markUpdated(operator);
    }

    /**
     * 根据节点顺序获取节点
     *
     * @param nodeOrder 节点顺序
     * @return 审批节点（可能为空）
     */
    public Optional<ApprovalNode> getNode(NodeOrder nodeOrder) {
        if (nodeOrder == null) {
            return Optional.empty();
        }
        return nodes.stream()
                .filter(node -> node.nodeOrder().equals(nodeOrder))
                .findFirst();
    }

    /**
     * 获取下一个节点
     *
     * @param currentOrder 当前节点顺序
     * @return 下一个节点（可能为空）
     */
    public Optional<ApprovalNode> getNextNode(NodeOrder currentOrder) {
        if (currentOrder == null) {
            return Optional.empty();
        }
        NodeOrder nextOrder = currentOrder.next();
        return getNode(nextOrder);
    }

    /**
     * 判断是否有下一个节点
     *
     * @param currentOrder 当前节点顺序
     * @return true 如果有下一个节点
     */
    public boolean hasNextNode(NodeOrder currentOrder) {
        return getNextNode(currentOrder).isPresent();
    }

    /**
     * 获取最后一个节点的顺序号
     *
     * @return 最后一个节点的顺序号
     */
    public NodeOrder getLastNodeOrder() {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("审批流没有节点");
        }
        return nodes.stream()
                .map(ApprovalNode::nodeOrder)
                .max(Comparator.comparingInt(NodeOrder::value))
                .orElse(NodeOrder.first());
    }

    /**
     * 判断审批流是否处于激活状态
     *
     * @return true 如果处于激活状态
     */
    public boolean isActive() {
        return status == FlowStatus.ACTIVE;
    }

    /**
     * 判断审批流是否已废弃
     *
     * @return true 如果已废弃
     */
    public boolean isDeprecated() {
        return status == FlowStatus.DEPRECATED;
    }

    /**
     * 获取所有节点
     *
     * @return 节点列表
     */
    public List<ApprovalNode> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    /**
     * 校验节点顺序必须连续
     */
    private void validateNodesOrder() {
        if (nodes.isEmpty()) {
            return;
        }
        
        // 按节点顺序排序
        List<ApprovalNode> sortedNodes = nodes.stream()
                .sorted(Comparator.comparing(ApprovalNode::nodeOrder))
                .toList();
        
        // 校验顺序必须从1开始且连续
        int expectedOrder = 1;
        for (ApprovalNode node : sortedNodes) {
            if (node.nodeOrder().value() != expectedOrder) {
                throw new DomainException(ApprovalDomainErrorCode.APPROVAL_FLOW_NODE_INVALID)
                        .withLogDetail("节点顺序必须从1开始且连续, 当前期望顺序: %d, 实际顺序: %d"
                                .formatted(expectedOrder, node.nodeOrder().value()));
            }
            expectedOrder++;
        }
    }

    public FlowName flowName() {
        return flowName;
    }

    public MatchRules matchRules() {
        return matchRules;
    }

    public FlowVersion flowVersion() {
        return flowVersion;
    }

    public FlowStatus status() {
        return status;
    }

    @Override
    protected void validateInvariants() {
        // 校验名称
        if (flowName == null) {
            throw new IllegalStateException("审批流名称不能为空");
        }

        // 校验匹配规则
        if (matchRules == null) {
            throw new IllegalStateException("匹配规则不能为空");
        }

        // 校验节点列表
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalStateException("审批节点列表不能为空");
        }

        // 校验版本号
        if (flowVersion == null) {
            throw new IllegalStateException("版本号不能为空");
        }

        // 校验状态
        if (status == null) {
            throw new IllegalStateException("状态不能为空");
        }

        // 校验节点顺序连续性
        validateNodesOrder();
    }
}