package com.example.iam.api.command;

import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建业务定义命令
 *
 * <p>创建一项业务定义,声明该业务支持的动作集合,供权限规则与代办权限引用。
 *
 * @author iam-service
 */
public record CreateBusinessDefinitionCommand(
    /**
     * 业务编码(业务唯一)
     */
    @NotBlank(message = "业务编码不能为空")
    String businessCode,
    /**
     * 业务名称
     */
    @NotBlank(message = "业务名称不能为空")
    String businessName,
    /**
     * 业务描述(可空)
     */
    String description,
    /**
     * 支持的动作集合
     */
    @NotNull(message = "支持动作集合不能为空")
    @Size(min = 1, message = "支持动作集合至少包含一个动作")
    @Valid
    Set<BusinessActionItem> supportedActions,
    /**
     * 操作人 UserNo(审计用)
     */
    @NotBlank(message = "操作人不能为空")
    String operator
) {
    /**
     * 业务动作项
     *
     * <p>描述业务支持的单个动作及其说明。
     *
     * @author iam-service
     */
    public record BusinessActionItem(
        /**
         * 动作编码(如 HANDLE/QUERY/AUDIT)
         */
        @NotBlank(message = "动作编码不能为空")
        String action,
        /**
         * 动作描述
         */
        @NotBlank(message = "动作描述不能为空")
        String description
    ) {
    }
}
