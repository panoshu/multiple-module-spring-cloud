package com.pension.permission.api.dto;

import java.util.Set;

/**
 * 客户渠道开通记录响应 DTO.
 *
 * @param entitlementId    开通记录 ID
 * @param customerNo       客户编号
 * @param enabledChannels  已开通的渠道集合（渠道枚举名称）
 */
public record CustomerChannelEntitlementResponse(
  String entitlementId,
  String customerNo,
  Set<String> enabledChannels
) {}
