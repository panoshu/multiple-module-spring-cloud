package com.example.auth.api.command;

/**
 * 关闭渠道命令.
 *
 * @param customerNo 客户编号
 * @param channel    待关闭渠道（ChannelType 名称或年金渠道 code）
 * @param operator   操作人
 */
public record DisableChannelCommand(
  String customerNo,
  String channel,
  String operator
) {
}
