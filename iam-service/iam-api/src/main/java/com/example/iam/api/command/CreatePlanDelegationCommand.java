package com.example.iam.api.command;

import java.time.LocalDateTime;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建计划代办关系命令
 *
 * <p>建立授权方计划与被授权方计划之间的代办关系,授权方操作员可代为办理被授权方计划下的业务。
 * 支持全部操作员代办或指定操作员代办,并为每项业务配置代办动作范围。
 *
 * @author iam-service
 */
public record CreatePlanDelegationCommand(
    /**
     * 代办关系编号(业务唯一)
     */
    @NotBlank(message = "代办关系编号不能为空")
    String delegationCode,
    /**
     * 授权方计划编号
     */
    @NotBlank(message = "授权方计划编号不能为空")
    String delegatorPlanNo,
    /**
     * 被授权方计划编号
     */
    @NotBlank(message = "被授权方计划编号不能为空")
    String delegateePlanNo,
    /**
     * 代办类型(ALL_OPERATORS/SPECIFIC_OPERATORS)
     */
    @NotBlank(message = "代办类型不能为空")
    String delegationType,
    /**
     * 指定操作员 ID 集合(可空,仅 delegationType=SPECIFIC_OPERATORS 时必填)
     */
    Set<Long> designatedOperators,
    /**
     * 代办权限列表(每项定义业务编码与允许动作)
     */
    @NotNull(message = "代办权限列表不能为空")
    @Size(min = 1, message = "代办权限列表至少包含一项")
    @Valid
    Set<DelegationPermissionItem> delegationPermissions,
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
    /**
     * 代办权限项
     *
     * <p>描述单个业务下被授权方操作员可执行的代办动作集合。
     *
     * @author iam-service
     */
    public record DelegationPermissionItem(
        /**
         * 业务编码
         */
        @NotBlank(message = "业务编码不能为空")
        String businessCode,
        /**
         * 允许的代办动作集合(如 HANDLE/QUERY/AUDIT)
         */
        @NotNull(message = "代办动作集合不能为空")
        @Size(min = 1, message = "代办动作集合至少包含一个动作")
        Set<String> actions
    ) {
    }
}
