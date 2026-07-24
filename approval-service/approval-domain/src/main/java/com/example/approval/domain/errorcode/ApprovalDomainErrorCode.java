package com.example.approval.domain.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * approval-service 模块错误码定义。
 * <p>
 * 错误码区间 {@code 30001-30099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>5 位纯数字，首位 3 表示业务服务模块，2-3 位 00 表示 approval-service</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 * <p>
 * 码段内部分组：
 * <ul>
 *   <li>30001-30009：审批流配置相关</li>
 *   <li>30011-30019：审批实例相关</li>
 *   <li>30021-30029：审批操作相关</li>
 *   <li>30031-30039：匹配规则相关</li>
 * </ul>
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public enum ApprovalDomainErrorCode implements ErrorDefinition {

    // ==================== 审批流配置相关（30001-30009） ====================
    APPROVAL_FLOW_NOT_FOUND("30001", "审批流不存在"),
    APPROVAL_FLOW_DEPRECATED("30002", "审批流已废弃"),
    APPROVAL_FLOW_VERSION_MISMATCH("30003", "审批流版本不匹配"),
    APPROVAL_FLOW_NODE_INVALID("30004", "审批节点配置无效"),
    APPROVAL_FLOW_MATCH_RULE_INVALID("30005", "匹配规则无效"),

    // ==================== 审批实例相关（30011-30019） ====================
    APPROVAL_INSTANCE_NOT_FOUND("30011", "审批实例不存在"),
    APPROVAL_INSTANCE_ALREADY_COMPLETED("30012", "审批实例已完成"),
    APPROVAL_INSTANCE_ALREADY_WITHDRAWN("30013", "审批实例已撤回"),
    APPROVAL_INSTANCE_NOT_APPROVING("30014", "审批实例不在审批中状态"),
    APPROVAL_INSTANCE_ALREADY_PENDING("30015", "审批实例已在待审批状态"),

    // ==================== 审批操作相关（30021-30029） ====================
    NOT_CURRENT_APPROVER("30021", "不是当前节点的审批人"),
    APPROVER_ALREADY_APPROVED("30022", "审批人已审批"),
    APPROVER_ALREADY_TRANSFERRED("30023", "审批人已转交"),
    TRANSFER_TARGET_NOT_FOUND("30024", "转交目标用户不存在"),
    WITHDRAW_NOT_BY_INITIATOR("30025", "撤回只能由发起人操作"),
    INVALID_REJECT_TARGET("30026", "无效的驳回目标"),
    APPROVAL_NODE_NOT_FOUND("30027", "审批节点不存在"),

    // ==================== 匹配规则相关（30031-30039） ====================
    NO_MATCHING_APPROVAL_FLOW("30031", "未找到匹配的审批流"),
    BUSINESS_APPLICATION_NOT_FOUND("30032", "业务申请不存在"),
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
