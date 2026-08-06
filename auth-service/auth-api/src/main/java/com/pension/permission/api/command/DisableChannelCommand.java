package com.pension.permission.api.command;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.UserNo;

/**
 * 关闭渠道命令.
 *
 * @param customerNo 客户编号
 * @param channel    待关闭渠道
 * @param operator   操作人
 */
public record DisableChannelCommand(
  CustomerNo customerNo,
  AnnuityChannel channel,
  UserNo operator
) {}
