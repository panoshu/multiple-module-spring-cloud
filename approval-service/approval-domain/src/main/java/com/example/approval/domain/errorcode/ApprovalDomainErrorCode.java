package com.example.approval.domain.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * 审批领域错误码定义
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public enum ApprovalDomainErrorCode implements ErrorDefinition {

    // 审批流配置相关 (AP001-AP009)
    APPROVAL_FLOW_NOT_FOUND("AP001", "[审批流不存在]{}"),
    APPROVAL_FLOW_DEPRECATED("AP002", "[审批流已废弃]{}"),
    APPROVAL_FLOW_VERSION_MISMATCH("AP003", "[审批流版本不匹配]{}"),
    APPROVAL_FLOW_NODE_INVALID("AP004", "[审批节点配置无效]{}"),
    APPROVAL_FLOW_MATCH_RULE_INVALID("AP005", "[匹配规则无效]{}"),

    // 审批实例相关 (AP010-AP019)
    APPROVAL_INSTANCE_NOT_FOUND("AP010", "[审批实例不存在]{}"),
    APPROVAL_INSTANCE_ALREADY_COMPLETED("AP011", "[审批实例已完成]{}"),
    APPROVAL_INSTANCE_ALREADY_WITHDRAWN("AP012", "[审批实例已撤回]{}"),
    APPROVAL_INSTANCE_NOT_APPROVING("AP013", "[审批实例不在审批中状态]{}"),
    APPROVAL_INSTANCE_ALREADY_PENDING("AP014", "[审批实例已在待审批状态]{}"),

    // 审批操作相关 (AP020-AP029)
    NOT_CURRENT_APPROVER("AP020", "[不是当前节点的审批人]{}"),
    APPROVER_ALREADY_APPROVED("AP021", "[审批人已审批]{}"),
    APPROVER_ALREADY_TRANSFERRED("AP022", "[审批人已转交]{}"),
    TRANSFER_TARGET_NOT_FOUND("AP023", "[转交目标用户不存在]{}"),
    WITHDRAW_NOT_BY_INITIATOR("AP024", "[撤回只能由发起人操作]{}"),
    INVALID_REJECT_TARGET("AP025", "[无效的驳回目标]{}"),
    APPROVAL_NODE_NOT_FOUND("AP026", "[审批节点不存在]{}"),

    // 匹配规则相关 (AP030-AP039)
    NO_MATCHING_APPROVAL_FLOW("AP030", "[未找到匹配的审批流]{}"),
    BUSINESS_APPLICATION_NOT_FOUND("AP031", "[业务申请不存在]{}"),
    ;

    private final String code;
    private final String message;

    ApprovalDomainErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return this.code;
    }

    @Override
    public String message() {
        return this.message;
    }
}