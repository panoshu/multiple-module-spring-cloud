package com.pension.permission.domain.channel.service;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.domain.channel.valueobject.VerificationCode;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.types.SecondaryAuthSessionId;

import java.time.Duration;

/**
 * 二次授权策略 SPI.
 *
 * <p>不同策略对应不同的授权方式（短信验证码、人脸识别、UKey 签名等）。
 * 默认实现为 SmsCodeSecondaryAuthStrategy（短信验证码）。</p>
 */
public interface SecondaryAuthStrategy {

  /**
   * 策略标识.
   */
  String supports();

  /**
   * 发起授权.
   *
   * <p>本接口方法保留用于未来策略扩展。当前实现统一使用 SecondaryAuthSession.initiate() 静态工厂方法。
   * 策略实现可以在此方法中封装特定于策略的发起逻辑（如生成不同长度的验证码）。</p>
   */
  SecondaryAuthSession initiate(SecondaryAuthContext context);

  /**
   * 完成授权.
   *
   * <p>校验验证码、冻结快照、设置 EffectiveIdentity。
   * 策略实现可以在此方法中封装特定于策略的校验逻辑。</p>
   */
  SecondaryAuthSession authorize(
    SecondaryAuthSession session,
    AuthorizeCommand command);

  /**
   * 发起上下文.
   */
  record SecondaryAuthContext(
    SecondaryAuthSessionId id,
    UserNo tellerAccountId,
    CredentialOwner credentialOwner,
    UserNo approverAccountId,
    Mobile approverMobile,
    PlanNo planId,
    VerificationCode verificationCode,
    Duration pendingTimeout,
    Duration sessionTimeout,
    UserNo operator
  ) {}

  /**
   * 授权命令.
   */
  record AuthorizeCommand(
    String rawCode,
    PermissionSnapshot snapshot,
    EffectiveIdentity identity,
    UserNo operator
  ) {}
}
