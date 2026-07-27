package com.example.iam.api.dto;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 权限规则DTO
 *
 * <p>对应权限规则聚合根(PermissionRule)的展示视图,声明"在某主体维度下,对某业务的某些动作是否允许"。
 *
 * @author iam-service
 */
public record PermissionRuleDTO(
    /**
     * 规则ID
     */
    Long ruleId,
    /**
     * 规则编码(全局唯一)
     */
    String ruleCode,
    /**
     * 规则名称(展示用)
     */
    String ruleName,
    /**
     * 主体维度(CUSTOMER/OPERATION_MODE/PRODUCT/PLAN/ACCOUNT_MANAGER)
     */
    String subjectType,
    /**
     * 主体标识(对应维度的具体值,如客户编号/计划编号等)
     */
    String subjectId,
    /**
     * 业务编码(关联BusinessDefinition)
     */
    String businessCode,
    /**
     * 授权动作集合(HANDLE/QUERY/AUDIT 等)
     */
    Set<String> allowedActions,
    /**
     * 是否继承给下属企业(仅 CUSTOMER 级有意义)
     */
    boolean inheritToChildren,
    /**
     * 覆盖模式(ADD 扩展/REMOVE 收紧)
     */
    String overrideMode,
    /**
     * 优先级(可空,空则使用 SubjectType.priority)
     */
    Integer priority,
    /**
     * 规则状态(ACTIVE/DISABLED)
     */
    String status,
    /**
     * 生效时间
     */
    LocalDateTime effectiveAt,
    /**
     * 失效时间(可空,表示永久)
     */
    LocalDateTime expireAt,
    /**
     * 创建时间
     */
    LocalDateTime createdAt,
    /**
     * 更新时间
     */
    LocalDateTime updatedAt,
    /**
     * 乐观锁版本号
     */
    Long version
) {
}
