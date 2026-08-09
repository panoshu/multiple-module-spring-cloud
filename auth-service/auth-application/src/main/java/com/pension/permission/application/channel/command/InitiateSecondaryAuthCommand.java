package com.pension.permission.application.channel.command;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;

/**
 * 发起二次授权命令.
 *
 * @param tellerAccountId   柜员账号
 * @param credentialOwner   凭证持有者
 * @param approverAccountId 经办人账号
 * @param approverMobile    经办人手机号（应用层从经办人账号查询后传入）
 * @param planId            目标计划ID
 */
public record InitiateSecondaryAuthCommand(
  UserNo tellerAccountId,
  CredentialOwner credentialOwner,
  UserNo approverAccountId,
  Mobile approverMobile,
  PlanNo planId
) {
}
