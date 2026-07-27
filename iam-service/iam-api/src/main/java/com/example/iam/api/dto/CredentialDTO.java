package com.example.iam.api.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 凭据DTO
 *
 * <p>对应凭据聚合根(Credential)的展示视图。出于安全考虑,不返回 secretHash 和 salt 字段。
 *
 * @author iam-service
 */
public record CredentialDTO(
    /**
     * 凭据ID
     */
    Long credentialId,
    /**
     * 归属类型(如 INTERNET_USER/HQ_USER/BRANCH_USER)
     */
    String ownerType,
    /**
     * 归属实体ID(User.id)
     */
    Long ownerId,
    /**
     * 凭据类型(PASSWORD/uKEY/DYNAMIC_TOKEN)
     */
    String credentialType,
    /**
     * 凭据状态(ACTIVE/EXPIRED/REVOKED)
     */
    String status,
    /**
     * 过期时间(可空,表示永久凭据)
     */
    LocalDateTime expireTime,
    /**
     * 辅助数据(如UKey公钥、动态令牌计数器等)
     */
    Map<String, String> auxData,
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
