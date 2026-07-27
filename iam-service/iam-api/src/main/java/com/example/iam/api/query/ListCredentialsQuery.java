package com.example.iam.api.query;

import com.example.shared.web.core.dto.PageQuery;
import jakarta.validation.constraints.NotNull;

/**
 * 凭据列表查询
 *
 * <p>支持按主体、凭据类型、状态等条件分页查询凭据列表。
 *
 * @author iam-service
 */
public record ListCredentialsQuery(
    /**
     * 凭据主体 ID(可选,如用户 ID)
     */
    Long ownerId,
    /**
     * 凭据主体类型(可选,如 INTERNET_USER / HQ_USER / BRANCH_TELLER)
     */
    String ownerType,
    /**
     * 凭据类型(可选,如 PASSWORD/UKEY/DYNAMIC_TOKEN)
     */
    String credentialType,
    /**
     * 凭据状态(可选,如 ACTIVE/REVOKED/EXPIRED)
     */
    String status,
    /**
     * 分页参数
     */
    @NotNull(message = "分页参数不能为空")
    PageQuery pageQuery
) {
}
