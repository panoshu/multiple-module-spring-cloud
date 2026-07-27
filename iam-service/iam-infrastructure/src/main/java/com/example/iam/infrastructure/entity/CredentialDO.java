package com.example.iam.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 凭据 DO。
 *
 * <p>对应表 {@code t_iam_credential},承载用户登录凭据(密码/UKey/动态令牌)的密文、
 * 盐值、辅助数据与状态。{@code auxData} 以 JSON 字符串存储。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Data
@Table("t_iam_credential")
public class CredentialDO {

    @Id(keyType = KeyType.None)
    private Long id;

    /** 归属类型(如 INTERNET_USER/HQ_USER/BRANCH_USER) */
    private String ownerType;

    /** 归属实体 ID(User.id) */
    private Long ownerId;

    /** 凭据类型:PASSWORD/UKEY/DYNAMIC_TOKEN */
    private String credentialType;

    /** 密文(BCrypt 哈希/RSA 公钥指纹/TOTP seed 等) */
    private String secretHash;

    /** 盐值(可空,BCrypt 内嵌盐时为 null) */
    private String salt;

    /** 辅助数据(JSON 字符串,如 UKey 公钥、动态令牌计数器) */
    private String auxData;

    /** 状态:ACTIVE/EXPIRED/REVOKED */
    private String status;

    /** 过期时间(可空,表示永久凭据) */
    private LocalDateTime expireTime;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
