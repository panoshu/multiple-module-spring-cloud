package com.example.iam.api.event;

/**
 * IAM 集成事件类型常量。
 * <p>
 * 用于 MQ topic 路由、集成事件落库标识,与 iam-domain 领域事件一一对应。
 *
 * @author iam-service
 */
public final class IntegrationEventTypes {

    public static final String USER_DISABLED = "iam.user.disabled";
    public static final String USER_ENABLED = "iam.user.enabled";
    public static final String SECONDARY_AUTH_COMPLETED = "iam.secondary-auth.completed";
    public static final String SECONDARY_AUTH_REVOKED = "iam.secondary-auth.revoked";
    public static final String PERMISSION_RULE_CREATED = "iam.permission.rule.created";
    public static final String PERMISSION_RULE_DISABLED = "iam.permission.rule.disabled";
    public static final String PERMISSION_RULE_ENABLED = "iam.permission.rule.enabled";
    public static final String PLAN_DELEGATION_CREATED = "iam.plan-delegation.created";
    public static final String PLAN_DELEGATION_ACTIVATED = "iam.plan-delegation.activated";
    public static final String PLAN_DELEGATION_REVOKED = "iam.plan-delegation.revoked";

    private IntegrationEventTypes() {}
}
