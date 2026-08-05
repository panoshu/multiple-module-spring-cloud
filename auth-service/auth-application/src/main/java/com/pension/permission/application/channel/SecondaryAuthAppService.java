package com.pension.permission.application.channel;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.application.channel.command.CloseSecondaryAuthCommand;
import com.pension.permission.application.channel.command.ConfirmSecondaryAuthCommand;
import com.pension.permission.application.channel.command.InitiateSecondaryAuthCommand;
import com.pension.permission.application.channel.command.ResendCodeCommand;
import com.pension.permission.application.channel.command.RevokeSecondaryAuthCommand;
import com.pension.permission.application.channel.config.SecondaryAuthConfig;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.errorcode.SecondaryAuthErrorCode;
import com.pension.permission.domain.channel.repository.SecondaryAuthSessionRepository;
import com.pension.permission.domain.channel.spi.VerificationCodeHasher;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.domain.channel.valueobject.VerificationCode;
import com.pension.permission.types.SecondaryAuthSessionId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * 二次授权应用服务.
 *
 * <p>编排二次授权用例：
 * <ol>
 *   <li>柜员发起 → 生成验证码 → 发短信</li>
 *   <li>柜员输入验证码 → 校验 → 冻结快照 → 授权完成</li>
 *   <li>重发验证码</li>
 *   <li>经办人撤销</li>
 *   <li>柜员登出</li>
 * </ol>
 * </p>
 *
 * <p>注意：本类不直接生成权限快照，快照由 PermissionResolver 端口提供（未来 Task）。
 * 当前实现中 confirm 方法的快照参数由调用方传入，应用服务仅负责编排。</p>
 */
@Service
@RequiredArgsConstructor
public class SecondaryAuthAppService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final SecondaryAuthSessionRepository sessionRepository;
  private final VerificationCodeHasher codeHasher;
  private final SecondaryAuthConfig config;
  private final IdService idService;
  private final EventBus eventBus;

  /**
   * 柜员发起二次授权.
   *
   * @param cmd 发起命令
   * @param approverMobile 经办人手机号（应用层从经办人账号查询）
   * @return 会话 ID
   */
  @Transactional
  public SecondaryAuthSessionId initiate(InitiateSecondaryAuthCommand cmd, Mobile approverMobile) {
    // 校验柜员活跃会话唯一性
    sessionRepository.findActiveByTeller(cmd.tellerAccountId())
      .ifPresent(s -> {
        throw new BusinessException(SecondaryAuthErrorCode.ACTIVE_SESSION_EXISTS);
      });

    // 生成验证码（明文，仅在此方法作用域内）
    String rawCode = generateCode();
    String hashedCode = codeHasher.hash(rawCode);
    VerificationCode code = VerificationCode.of(
      hashedCode, LocalDateTime.now(), config.getPendingTimeout(), config.getVerificationMaxAttempts());

    // 创建会话
    SecondaryAuthSessionId id = idService.nextId(SecondaryAuthSessionId.class);
    SecondaryAuthSession.InitiateContext ctx = new SecondaryAuthSession.InitiateContext(
      id, cmd.tellerAccountId(), cmd.credentialOwner(),
      cmd.approverAccountId(), approverMobile, cmd.planId(),
      code, config.getPendingTimeout(), config.getSessionTimeout(),
      cmd.tellerAccountId());
    SecondaryAuthSession session = SecondaryAuthSession.initiate(ctx);

    sessionRepository.save(session);
    session.domainEvents().forEach(eventBus::publish);
    return id;
  }

  /**
   * 柜员输入验证码确认.
   *
   * @param cmd 确认命令
   * @param snapshot 权限快照（由 PermissionResolver 预先解析）
   */
  @Transactional
  public void confirm(ConfirmSecondaryAuthCommand cmd, PermissionSnapshot snapshot) {
    SecondaryAuthSession session = sessionRepository.loadOrThrow(cmd.sessionId());

    if (session.status().isTerminal()) {
      throw new BusinessException(SecondaryAuthErrorCode.SESSION_EXPIRED);
    }

    EffectiveIdentity identity = new EffectiveIdentity(
      session.approverAccountId(),
      session.tellerAccountId(),
      true);

    session.authorize(cmd.rawCode(), snapshot, identity, codeHasher, cmd.operator());
    sessionRepository.save(session);
    session.domainEvents().forEach(eventBus::publish);
  }

  /**
   * 重发验证码.
   */
  @Transactional
  public void resendCode(ResendCodeCommand cmd) {
    SecondaryAuthSession session = sessionRepository.loadOrThrow(cmd.sessionId());
    String rawCode = generateCode();
    String hashedCode = codeHasher.hash(rawCode);
    VerificationCode newCode = VerificationCode.of(
      hashedCode, LocalDateTime.now(), config.getPendingTimeout(), config.getVerificationMaxAttempts());
    session.resendVerificationCode(newCode, cmd.operator());
    sessionRepository.save(session);
    session.domainEvents().forEach(eventBus::publish);
  }

  /**
   * 撤销二次授权.
   */
  @Transactional
  public void revoke(RevokeSecondaryAuthCommand cmd) {
    SecondaryAuthSession session = sessionRepository.loadOrThrow(cmd.sessionId());
    session.revoke(cmd.operator(), cmd.reason());
    sessionRepository.save(session);
    session.domainEvents().forEach(eventBus::publish);
  }

  /**
   * 关闭二次授权会话（柜员登出）.
   */
  @Transactional
  public void close(CloseSecondaryAuthCommand cmd) {
    SecondaryAuthSession session = sessionRepository.loadOrThrow(cmd.sessionId());
    session.close(cmd.operator());
    sessionRepository.save(session);
    session.domainEvents().forEach(eventBus::publish);
  }

  /**
   * 紧急收权：撤销经办人所有 AUTHORIZED 会话.
   *
   * @param approverAccountId 经办人账号
   */
  @Transactional
  public void revokeAllByApprover(UserNo approverAccountId) {
    sessionRepository.findAuthorizedByApprover(approverAccountId)
      .forEach(session -> {
        session.revoke(approverAccountId, "账号冻结紧急收权");
        sessionRepository.save(session);
        session.domainEvents().forEach(eventBus::publish);
      });
  }

  private String generateCode() {
    int length = config.getVerificationCodeLength();
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(SECURE_RANDOM.nextInt(10));
    }
    return sb.toString();
  }
}
