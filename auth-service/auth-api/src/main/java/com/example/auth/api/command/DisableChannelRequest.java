package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 关闭客户渠道请求.
 *
 * @param customerNo   客户编号
 * @param channelType 渠道类型
 */
public record DisableChannelRequest(@NotBlank String customerNo, @NotBlank String channelType) {}
