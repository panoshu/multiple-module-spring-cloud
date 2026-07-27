package com.example.iam.api.dto;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 业务定义DTO
 *
 * <p>对应业务定义聚合根(BusinessDefinition)的展示视图,声明系统支持的某类业务及其支持的动作。
 *
 * @author iam-service
 */
public record BusinessDefinitionDTO(
    /**
     * 业务定义ID
     */
    Long definitionId,
    /**
     * 业务编码(全局唯一,如 ANNUITY_ESTABLISH)
     */
    String businessCode,
    /**
     * 业务名称(如 "年金计划设立")
     */
    String businessName,
    /**
     * 业务描述(可空)
     */
    String description,
    /**
     * 支持的动作集合(如 HANDLE/QUERY/AUDIT)
     */
    Set<BusinessActionDTO> supportedActions,
    /**
     * 是否启用
     */
    boolean active,
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
