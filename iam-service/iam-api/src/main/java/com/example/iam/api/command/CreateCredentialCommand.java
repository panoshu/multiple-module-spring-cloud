package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 创建凭据命令
 *
 * <p>为指定主体(用户/设备等)创建登录凭据,凭据类型包括密码/UKey/动态令牌等。
 *
 * @author iam-service
 */
public record CreateCredentialCommand(
    /**
     * 凭据主体类型(如 INTERNET_USER / HQ_USER / BRANCH_TELLER)
     */
    @NotBlank(message = "凭据主体类型不能为空")
    String ownerType,
    /**
     * 凭据主体 ID(用户 ID)
     */
    @NotNull(message = "凭据主体ID不能为空")
    Long ownerId,
    /**
     * 凭据类型(PASSWORD/UKEY/DYNAMIC_TOKEN)
     */
    @NotBlank(message = "凭据类型不能为空")
    String credentialType,
    /**
     * 凭据密文哈希(由前端加密后传输)
     */
    @NotBlank(message = "凭据密文不能为空")
    String secretHash,
    /**
     * 盐值(可选,部分加密算法使用)
     */
    String salt,
    /**
     * 辅助数据(可选,如 UKey 序列号、动态令牌种子等)
     */
    Map<String, String> auxData,
    /**
     * 凭据过期时间(可选,为空表示永不过期)
     */
    LocalDateTime expireTime,
    /**
     * 操作人 UserNo(审计用)
     */
    @NotBlank(message = "操作人不能为空")
    String operator
) {
}
