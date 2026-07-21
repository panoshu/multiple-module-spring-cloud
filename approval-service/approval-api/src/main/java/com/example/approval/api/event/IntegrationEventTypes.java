package com.example.approval.api.event;

/**
 * 审批集成事件类型常量。
 * <p>
 * 用于 MQ topic 路由、集成事件落库标识，与 approval-domain 领域事件一一对应。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public final class IntegrationEventTypes {

    public static final String APPROVAL_INSTANCE_CREATED = "ApprovalInstanceCreatedEvent";
    public static final String APPROVAL_INSTANCE_APPROVED = "ApprovalInstanceApprovedEvent";
    public static final String APPROVAL_INSTANCE_REJECTED = "ApprovalInstanceRejectedEvent";
    public static final String APPROVAL_INSTANCE_WITHDRAWN = "ApprovalInstanceWithdrawnEvent";

    private IntegrationEventTypes() {}
}
