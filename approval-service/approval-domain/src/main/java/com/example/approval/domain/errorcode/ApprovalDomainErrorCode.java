package com.example.approval.domain.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * approval-service 模块错误码定义。
 * <p>
 * 错误码区间 {@code SERVICE.APPROVAL.0001-SERVICE.APPROVAL.0099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式：SERVICE.APPROVAL.XXXX（业务服务模块 - approval-service）</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 * <p>
 * 码段内部分组：
 * <ul>
 *   <li>SERVICE.APPROVAL.0001-0005：审批流配置相关</li>
 *   <li>SERVICE.APPROVAL.0006-0010：审批实例相关</li>
 *   <li>SERVICE.APPROVAL.0011-0017：审批操作相关</li>
 *   <li>SERVICE.APPROVAL.0018-0019：匹配规则相关</li>
 * </ul>
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public enum ApprovalDomainErrorCode implements ErrorDefinition {

    // ==================== 审批流配置相关（SERVICE.APPROVAL.0001-0005） ====================
    APPROVAL_FLOW_NOT_FOUND("SERVICE.APPROVAL.0001", "审批流不存在"),
    APPROVAL_FLOW_DEPRECATED("SERVICE.APPROVAL.0002", "审批流已废弃"),
    APPROVAL_FLOW_VERSION_MISMATCH("SERVICE.APPROVAL.0003", "审批流版本不匹配"),
    APPROVAL_FLOW_NODE_INVALID("SERVICE.APPROVAL.0004", "审批节点配置无效"),
    APPROVAL_FLOW_MATCH_RULE_INVALID("SERVICE.APPROVAL.0005", "匹配规则无效"),

    // ==================== 审批实例相关（SERVICE.APPROVAL.0006-0010） ====================
    APPROVAL_INSTANCE_NOT_FOUND("SERVICE.APPROVAL.0006", "审批实例不存在"),
    APPROVAL_INSTANCE_ALREADY_COMPLETED("SERVICE.APPROVAL.0007", "审批实例已完成"),
    APPROVAL_INSTANCE_ALREADY_WITHDRAWN("SERVICE.APPROVAL.0008", "审批实例已撤回"),
    APPROVAL_INSTANCE_NOT_APPROVING("SERVICE.APPROVAL.0009", "审批实例不在审批中状态"),
    APPROVAL_INSTANCE_ALREADY_PENDING("SERVICE.APPROVAL.0010", "审批实例已在待审批状态"),

    // ==================== 审批操作相关（SERVICE.APPROVAL.0011-0017） ====================
    NOT_CURRENT_APPROVER("SERVICE.APPROVAL.0011", "不是当前节点的审批人"),
    APPROVER_ALREADY_APPROVED("SERVICE.APPROVAL.0012", "审批人已审批"),
    APPROVER_ALREADY_TRANSFERRED("SERVICE.APPROVAL.0013", "审批人已转交"),
    TRANSFER_TARGET_NOT_FOUND("SERVICE.APPROVAL.0014", "转交目标用户不存在"),
    WITHDRAW_NOT_BY_INITIATOR("SERVICE.APPROVAL.0015", "撤回只能由发起人操作"),
    INVALID_REJECT_TARGET("SERVICE.APPROVAL.0016", "无效的驳回目标"),
    APPROVAL_NODE_NOT_FOUND("SERVICE.APPROVAL.0017", "审批节点不存在"),

    // ==================== 匹配规则相关（SERVICE.APPROVAL.0018-0019） ====================
    NO_MATCHING_APPROVAL_FLOW("SERVICE.APPROVAL.0018", "未找到匹配的审批流"),
    BUSINESS_APPLICATION_NOT_FOUND("SERVICE.APPROVAL.0019", "业务申请不存在"),
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
