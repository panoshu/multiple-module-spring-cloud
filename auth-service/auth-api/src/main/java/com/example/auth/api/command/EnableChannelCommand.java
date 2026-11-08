package com.example.auth.api.command;

import com.example.core.types.Channel;
import com.example.core.types.CustomerNo;
import com.example.core.types.UserNo;

/**
 * 开通渠道命令.
 *
 * @param customerNo 客户编号
 * @param channel    待开通渠道
 * @param operator   操作人
 */
public record EnableChannelCommand(
  CustomerNo customerNo,
  Channel channel,
  UserNo operator
) {
}
