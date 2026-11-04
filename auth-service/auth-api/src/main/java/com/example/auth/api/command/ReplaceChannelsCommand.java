package com.example.auth.api.command;

import com.example.core.types.Channel;
import com.example.core.types.CustomerNo;
import com.example.core.types.UserNo;

import java.util.Set;

/**
 * 批量替换渠道集合命令.
 *
 * @param customerNo 客户编号
 * @param channels   新的渠道集合
 * @param operator   操作人
 */
public record ReplaceChannelsCommand(
  CustomerNo customerNo,
  Set<Channel> channels,
  UserNo operator
) {
}
