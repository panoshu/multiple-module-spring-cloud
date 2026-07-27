package com.example.iam.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务动作明细 DO。
 *
 * <p>对应表 {@code t_iam_business_action},作为 {@link BusinessDefinitionDO} 的子表,
 * 通过 {@code definitionId} 关联。每条记录声明某业务支持的一个动作及其描述。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Data
@Table("t_iam_business_action")
public class BusinessActionDO {

    @Id(keyType = KeyType.None)
    private Long id;

    /** 业务定义 ID(FK to t_iam_business_definition.id) */
    private Long definitionId;

    /** 业务动作:HANDLE/QUERY/AUDIT */
    private String action;

    /** 动作描述 */
    private String description;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
