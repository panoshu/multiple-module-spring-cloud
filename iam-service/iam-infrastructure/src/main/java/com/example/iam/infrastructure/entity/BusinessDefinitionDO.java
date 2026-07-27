package com.example.iam.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务定义 DO。
 *
 * <p>对应表 {@code t_iam_business_definition},声明系统支持的某类业务及其支持的动作。
 * {@code supportedActions} 以 JSON 数组字符串存储(如 ["HANDLE","QUERY","AUDIT"])。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Data
@Table("t_iam_business_definition")
public class BusinessDefinitionDO {

    @Id(keyType = KeyType.None)
    private Long id;

    /** 业务编码(全局唯一,如 ANNUITY_ESTABLISH) */
    private String businessCode;

    /** 业务名称(如 "年金计划设立") */
    private String businessName;

    /** 业务描述 */
    private String description;

    /** 支持的动作集合(JSON 数组字符串,如 ["HANDLE","QUERY","AUDIT"]) */
    private String supportedActions;

    /** 是否启用 */
    private Boolean active;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
