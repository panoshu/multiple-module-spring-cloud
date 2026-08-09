package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 开通客户渠道请求.
 *
 * @param customerNo  客户编号
 * @param channelType 渠道类型
 */
public record EnableChannelRequest(@NotBlank String customerNo, @NotBlank String channelType) {
}
