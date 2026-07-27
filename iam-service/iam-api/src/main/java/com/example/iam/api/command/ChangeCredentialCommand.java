package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 修改凭据密文命令
 *
 * <p>更新指定凭据的密文哈希、盐值及辅助数据(如修改密码、轮换 UKey 等)。
 *
 * @author iam-service
 */
public record ChangeCredentialCommand(
    /**
     * 凭据 ID
     */
    @NotNull(message = "凭据ID不能为空")
    Long credentialId,
    /**
     * 新密文哈希(由前端加密后传输)
     */
    @NotBlank(message = "新密文不能为空")
    String newSecretHash,
    /**
     * 新盐值(可选,部分加密算法使用)
     */
    String newSalt,
    /**
     * 新辅助数据(可选)
     */
    Map<String, String> newAuxData,
    /**
     * 操作人 UserNo(审计用)
     */
    @NotBlank(message = "操作人不能为空")
    String operator
) {
}
