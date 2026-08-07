package com.example.auth.api.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 解析数据可见范围请求.
 *
 * @param accountId    账号 ID
 * @param businessCode 业务编码
 * @author auth-api
 */
public record DataScopeRequest(
    @NotBlank String accountId,
    @NotBlank String businessCode) {}
