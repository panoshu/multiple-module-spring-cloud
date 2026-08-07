package com.example.auth.api.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询客户渠道开通记录请求.
 *
 * @param customerNo 客户编号
 */
public record GetEntitlementRequest(@NotBlank String customerNo) {}
