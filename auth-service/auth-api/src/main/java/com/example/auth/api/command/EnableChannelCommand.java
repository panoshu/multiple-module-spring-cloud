package com.example.auth.api.command;

/**
 * 开通渠道命令.
 *
 * @param customerNo 客户编号
 * @param channel    待开通渠道（ChannelType 名称或年金渠道 code）
 * @param operator   操作人
 */
public record EnableChannelCommand(
  String customerNo,
  String channel,
  String operator
) {
}
