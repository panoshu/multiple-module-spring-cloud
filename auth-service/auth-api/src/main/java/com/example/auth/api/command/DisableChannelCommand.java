package com.example.auth.api.command;

import com.example.core.types.Channel;
import com.example.core.types.CustomerNo;
import com.example.core.types.UserNo;

/**
 * 关闭渠道命令.
 *
 * @param customerNo 客户编号
 * @param channel    待关闭渠道
 * @param operator   操作人
 */
public record DisableChannelCommand(
  CustomerNo customerNo,
  Channel channel,
  UserNo operator
) {
}
