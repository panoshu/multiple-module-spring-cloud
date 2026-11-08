package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 替换客户渠道列表请求.
 *
 * @param customerNo   客户编号
 * @param channelTypes 渠道类型列表
 */
public record ReplaceChannelsRequest(@NotBlank String customerNo, @NotEmpty List<String> channelTypes) {
}
