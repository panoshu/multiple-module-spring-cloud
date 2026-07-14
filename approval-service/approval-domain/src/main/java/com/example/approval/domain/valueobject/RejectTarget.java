package com.example.approval.domain.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 驳回目标值对象
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public record RejectTarget(RejectType type, NodeOrder targetNodeOrder) implements ValueObject {

    public RejectTarget {
        if (type == null) {
            throw new IllegalArgumentException("驳回类型不能为空");
        }
        if (type == RejectType.NODE && targetNodeOrder == null) {
            throw new IllegalArgumentException("驳回到指定节点时，目标节点顺序不能为空");
        }
        if (type != RejectType.NODE && targetNodeOrder != null) {
            throw new IllegalArgumentException("只有驳回到指定节点时才需要指定目标节点顺序");
        }
    }

    /**
     * 驳回类型枚举
     */
    public enum RejectType {
        /** 终止流程 */
        TERMINATE,
        /** 驳回到发起人 */
        INITIATOR,
        /** 驳回到指定节点 */
        NODE
    }

    /**
     * 驳回到终止
     *
     * @return RejectTarget 实例
     */
    public static RejectTarget terminate() {
        return new RejectTarget(RejectType.TERMINATE, null);
    }

    /**
     * 驳回到发起人
     *
     * @return RejectTarget 实例
     */
    public static RejectTarget toInitiator() {
        return new RejectTarget(RejectType.INITIATOR, null);
    }

    /**
     * 驳回到指定节点
     *
     * @param nodeOrder 目标节点顺序
     * @return RejectTarget 实例
     */
    public static RejectTarget toNode(NodeOrder nodeOrder) {
        return new RejectTarget(RejectType.NODE, nodeOrder);
    }

    /**
     * 是否为终止
     *
     * @return true 如果是终止
     */
    public boolean isTerminate() {
        return type == RejectType.TERMINATE;
    }

    /**
     * 是否为驳回到发起人
     *
     * @return true 如果是驳回到发起人
     */
    public boolean isToInitiator() {
        return type == RejectType.INITIATOR;
    }

    /**
     * 是否为驳回到指定节点
     *
     * @return true 如果是驳回到指定节点
     */
    public boolean isToNode() {
        return type == RejectType.NODE;
    }
}