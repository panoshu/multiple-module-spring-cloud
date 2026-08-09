package com.example.auth.api.command;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.UserNo;

/**
 * 开通渠道命令.
 *
 * @param customerNo 客户编号
 * @param channel    待开通渠道
 * @param operator   操作人
 */
public record EnableChannelCommand(
  CustomerNo customerNo,
  AnnuityChannel channel,
  UserNo operator
) {
}
