package com.example.iam.api.command;

import java.time.LocalDateTime;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建权限规则命令
 *
 * <p>创建一条权限规则,用于控制指定主体(Customer/OperationMode/Product/Plan/AccountManager)
 * 在特定业务上的可执行动作集合,支持向子节点继承以及 ADD/REMOVE 覆盖模式。
 *
 * @author iam-service
 */
public record CreatePermissionRuleCommand(
    /**
     * 规则编号(业务唯一)
     */
    @NotBlank(message = "规则编号不能为空")
    String ruleCode,
    /**
     * 规则名称
     */
    @NotBlank(message = "规则名称不能为空")
    String ruleName,
    /**
     * 主体类型(CUSTOMER/OPERATION_MODE/PRODUCT/PLAN/ACCOUNT_MANAGER)
     */
    @NotBlank(message = "主体类型不能为空")
    String subjectType,
    /**
     * 主体标识
     */
    @NotBlank(message = "主体标识不能为空")
    String subjectId,
    /**
     * 业务编码
     */
    @NotBlank(message = "业务编码不能为空")
    String businessCode,
    /**
     * 允许的动作集合(如 HANDLE/QUERY/AUDIT)
     */
    @NotNull(message = "允许动作集合不能为空")
    @Size(min = 1, message = "允许动作集合至少包含一个动作")
    Set<String> allowedActions,
    /**
     * 是否向子节点继承(默认 false)
     */
    boolean inheritToChildren,
    /**
     * 覆盖模式(ADD/REMOVE)
     */
    @NotBlank(message = "覆盖模式不能为空")
    String overrideMode,
    /**
     * 优先级(可空,数字越小优先级越高)
     */
    Integer priority,
    /**
     * 生效时间(可空,为空则立即生效)
     */
    LocalDateTime effectiveAt,
    /**
     * 失效时间(可空,为空则长期有效)
     */
    LocalDateTime expireAt,
    /**
     * 操作人 UserNo(审计用)
     */
    @NotBlank(message = "操作人不能为空")
    String operator
) {
}
