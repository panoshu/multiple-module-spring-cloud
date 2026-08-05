package com.pension.permission.application.channel.command;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;

/**
 * 发起二次授权命令.
 */
public record InitiateSecondaryAuthCommand(
  UserNo tellerAccountId,
  CredentialOwner credentialOwner,
  UserNo approverAccountId,
  PlanNo planId
) {}
