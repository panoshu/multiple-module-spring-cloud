package com.pension.permission.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 开通渠道请求 DTO.
 *
 * @param customerNo 客户编号
 * @param channel    渠道枚举名称（如 NETAPP、TELLER）
 */
public record EnableChannelRequest(
  @NotBlank String customerNo,
  @NotNull String channel
) {}
