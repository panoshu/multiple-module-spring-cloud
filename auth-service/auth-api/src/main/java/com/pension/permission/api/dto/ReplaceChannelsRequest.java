package com.pension.permission.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * 批量替换渠道集合请求 DTO.
 *
 * @param customerNo 客户编号
 * @param channels   渠道枚举名称集合（如 NETAPP、TELLER）
 */
public record ReplaceChannelsRequest(
  @NotBlank String customerNo,
  @NotNull Set<String> channels
) {}
