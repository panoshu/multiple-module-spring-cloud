package com.example.iam.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户渠道专属档案 DO。
 *
 * <p>对应表 {@code t_iam_user_profile},与 {@link UserDO} 1:1 关联(共享主键)。
 * 渠道差异化字段由此表承载,扩展字段以 JSON 字符串形式存储。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Data
@Table("t_iam_user_profile")
public class UserProfileDO {

    /** 用户 ID(同时也是外键到 t_iam_user.id,共享主键) */
    @Id(keyType = KeyType.None)
    private Long userId;

    /** 渠道类型 */
    private String channelType;

    private String email;

    private String phone;

    private String organization;

    private String position;

    /** 网点渠道必填 */
    private String branchId;

    private String employeeNo;

    /** 扩展属性(JSON 字符串) */
    private String extraAttributes;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
