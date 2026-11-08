package com.example.auth.api.dto;

import java.util.List;

/**
 * 客户渠道开通响应.
 *
 * @param customerNo   客户编号
 * @param channelTypes 已开通渠道类型列表
 * @param status       状态
 */
public record CustomerChannelEntitlementResponse(String customerNo, List<String> channelTypes, String status) {
}
