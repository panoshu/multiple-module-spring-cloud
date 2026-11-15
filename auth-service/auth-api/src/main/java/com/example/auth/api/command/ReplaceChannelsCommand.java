package com.example.auth.api.command;

import java.util.Set;

/**
 * 批量替换渠道集合命令.
 *
 * @param customerNo 客户编号
 * @param channels   新的渠道集合（ChannelType 名称或年金渠道 code）
 * @param operator   操作人
 */
public record ReplaceChannelsCommand(
  String customerNo,
  Set<String> channels,
  String operator
) {
}
