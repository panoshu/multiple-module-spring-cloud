package com.example.iam.application.service;

import com.example.iam.api.command.ConfirmSecondaryAuthCommand;
import com.example.iam.api.command.InitiateSecondaryAuthCommand;
import com.example.iam.api.command.RejectSecondaryAuthCommand;
import com.example.iam.api.command.RevokeSecondaryAuthCommand;
import com.example.iam.api.dto.SecondaryAuthSessionDTO;
import com.example.iam.api.query.GetSecondaryAuthStatusQuery;
import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.root.User;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.iam.domain.authentication.repository.CredentialRepository;
import com.example.iam.domain.authentication.repository.SecondaryAuthSessionRepository;
import com.example.iam.domain.authentication.repository.UserRepository;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionSnapshot;
import com.example.iam.domain.authorization.service.PermissionResolver;
import com.example.iam.application.port.ChannelSessionPort;
import com.example.iam.application.port.PasswordEncryptorPort;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.iam.types.UserId;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.primitives.identity.IdService;
import com.example.shared.primitives.identity.UserNo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 二次授权应用服务(网点渠道专属)。
 *
 * <p>负责网点柜员借用经办人权限的二次授权会话编排,包括:
 * <ul>
 *   <li>柜员发起二次授权请求({@link #initiate})</li>
 *   <li>经办人确认授权并冻结权限快照({@link #confirm})</li>
 *   <li>经办人拒绝授权({@link #reject})</li>
 *   <li>撤销已生效的授权会话({@link #revoke})</li>
 *   <li>查询授权会话状态({@link #getStatus})</li>
 * </ul>
 *
 * <p>授权完成时通过 {@link PermissionResolver} 计算经办人当前权限快照,
 * 冻结后即使经办人后续权限变更,本会话期间柜员仍使用冻结的权限。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecondaryAuthAppService {

  /** 二次授权会话默认有效期(小时) */
  private static final long DEFAULT_SESSION_HOURS = 8L;

  private final SecondaryAuthSessionRepository sessionRepository;
  private final UserRepository userRepository;
  private final CredentialRepository credentialRepository;
  private final PermissionResolver permissionResolver;
  private final ChannelSessionPort channelSessionPort;
  private final EventBus eventBus;
  private final IdService idService;
  private final PasswordEncryptorPort passwordEncryptorPort;

  /**
   * 柜员发起二次授权请求。
   *
   * <p>流程:
   * <ol>
   *   <li>从渠道上下文获取当前柜员 ID</li>
   *   <li>按登录名查找经办人(BRANCH 渠道)</li>
   *   <li>创建 PENDING 状态会话</li>
   *   <li>保存会话并发布事件</li>
   * </ol>
   *
   * @param command 发起命令
   * @return 会话 DTO
   */
  @Transactional
  public SecondaryAuthSessionDTO initiate(InitiateSecondaryAuthCommand command) {
    Long tellerId = channelSessionPort.currentUserId();
    User approver = userRepository.findByLoginName(command.approverLoginName(), ChannelType.BRANCH)
        .orElseThrow(() -> new BusinessException(IamAuthErrorCode.USER_NOT_FOUND)
            .withUserDetail("经办人不存在")
            .withContext("approverLoginName", command.approverLoginName()));

    SecondaryAuthSessionId sessionId = idService.nextLongId(SecondaryAuthSessionId.class, "IAM_2ND_AUTH");
    UserNo operator = UserNo.of(String.valueOf(tellerId));
    SecondaryAuthSession session = SecondaryAuthSession.initiate(
        sessionId, tellerId, approver.id().value(),
        command.customerNo(), command.planId(), operator);

    sessionRepository.save(session);
    publishEvents(session);

    log.info("二次授权请求发起: sessionId={}, tellerId={}, approverId={}",
        sessionId.value(), tellerId, approver.id().value());
    return toDTO(session);
  }

  /**
   * 经办人确认二次授权。
   *
   * <p>流程:
   * <ol>
   *   <li>加载会话(必须为 PENDING 状态)</li>
   *   <li>校验经办人密码(再次身份验证)</li>
   *   <li>调用 {@link PermissionResolver} 计算经办人权限快照</li>
   *   <li>调用 {@link SecondaryAuthSession#authorize} 冻结快照</li>
   *   <li>通过 {@link ChannelSessionPort} 同步会话信息(柜员可借用权限)</li>
   * </ol>
   *
   * @param command 确认命令
   * @return 会话 DTO
   */
  @Transactional
  public SecondaryAuthSessionDTO confirm(ConfirmSecondaryAuthCommand command) {
    SecondaryAuthSession session = loadSessionOrThrow(command.sessionId());
    Long approverId = session.approverId();

    User approver = userRepository.load(UserId.of(approverId))
        .orElseThrow(() -> new BusinessException(IamAuthErrorCode.USER_NOT_FOUND)
            .withUserDetail("经办人不存在")
            .withContext("approverId", approverId));
    if (!approver.status().isActive()) {
      throw new BusinessException(IamAuthErrorCode.ACCOUNT_DISABLED)
          .withUserDetail("经办人账号状态不允许此操作")
          .withContext("approverStatus", approver.status());
    }

    Credential credential = credentialRepository
        .findActive(approverId, "BRANCH_TELLER", CredentialType.PASSWORD)
        .orElseThrow(() -> new BusinessException(IamAuthErrorCode.CREDENTIAL_NOT_FOUND)
            .withUserDetail("经办人密码凭据不存在")
            .withContext("approverId", approverId));
    if (!passwordEncryptorPort.matches(command.approverPassword(), credential.secretHash())) {
      throw new BusinessException(IamAuthErrorCode.LOGIN_NAME_OR_PASSWORD_ERROR)
          .withUserDetail("经办人密码校验失败");
    }

    PermissionSnapshot snapshot = permissionResolver.resolve(
        UserId.of(approverId), session.planId());
    LocalDateTime expireAt = LocalDateTime.now().plusHours(DEFAULT_SESSION_HOURS);
    UserNo approverNo = UserNo.of(String.valueOf(approverId));
    session.authorize(snapshot, expireAt, approverNo);

    sessionRepository.save(session);
    publishEvents(session);

    Set<String> permissionStrings = snapshot.permissions().stream()
        .map(pc -> pc.value())
        .collect(Collectors.toSet());
    channelSessionPort.setSecondaryAuthSession(
        session.id().value(), approverId, session.planId(), permissionStrings);

    log.info("二次授权确认: sessionId={}, approverId={}, permissionCount={}",
        session.id().value(), approverId, permissionStrings.size());
    return toDTO(session);
  }

  /**
   * 经办人拒绝二次授权请求。
   *
   * @param command 拒绝命令
   */
  @Transactional
  public void reject(RejectSecondaryAuthCommand command) {
    SecondaryAuthSession session = loadSessionOrThrow(command.sessionId());
    Long approverId = session.approverId();
    UserNo approverNo = UserNo.of(String.valueOf(approverId));
    session.reject(approverNo);
    sessionRepository.save(session);
    publishEvents(session);
    log.info("二次授权拒绝: sessionId={}, reason={}", command.sessionId(), command.reason());
  }

  /**
   * 撤销二次授权会话(柜员或经办人主动撤销)。
   *
   * @param command 撤销命令
   */
  @Transactional
  public void revoke(RevokeSecondaryAuthCommand command) {
    SecondaryAuthSession session = loadSessionOrThrow(command.sessionId());
    Long operatorId = channelSessionPort.currentUserId();
    UserNo operator = UserNo.of(String.valueOf(operatorId));
    session.revoke(operator, command.reason());
    sessionRepository.save(session);
    publishEvents(session);
    channelSessionPort.clearSecondaryAuthSession();
    log.info("二次授权撤销: sessionId={}, reason={}", command.sessionId(), command.reason());
  }

  /**
   * 查询二次授权会话状态。
   *
   * @param query 状态查询
   * @return 会话 DTO
   */
  @Transactional(readOnly = true)
  public SecondaryAuthSessionDTO getStatus(GetSecondaryAuthStatusQuery query) {
    SecondaryAuthSession session = loadSessionOrThrow(query.sessionId());
    return toDTO(session);
  }

  /**
   * 加载会话或抛出业务异常。
   */
  private SecondaryAuthSession loadSessionOrThrow(Long sessionId) {
    return sessionRepository.load(SecondaryAuthSessionId.of(sessionId))
        .orElseThrow(() -> new BusinessException(IamAuthErrorCode.SECONDARY_AUTH_SESSION_NOT_FOUND)
            .withUserDetail("二次授权会话不存在")
            .withContext("sessionId", sessionId));
  }

  /**
   * 发布聚合根的领域事件并清理。
   */
  private void publishEvents(SecondaryAuthSession session) {
    session.getDomainEvents().forEach(eventBus::publish);
    session.clearDomainEvents();
  }

  /**
   * 领域对象转 DTO。
   */
  private SecondaryAuthSessionDTO toDTO(SecondaryAuthSession session) {
    Set<String> permissionStrings = session.permissionSnapshot() == null
        ? Set.of()
        : session.permissionSnapshot().stream().map(pc -> pc.value()).collect(Collectors.toSet());
    return new SecondaryAuthSessionDTO(
        session.id().value(),
        session.tellerId(),
        session.approverId(),
        session.customerNo(),
        session.planId(),
        permissionStrings,
        session.status() != null ? session.status().name() : null,
        session.initiatedAt(),
        session.authorizedAt(),
        session.expireAt(),
        session.revokeReason(),
        session.createdAt(),
        session.updatedAt()
    );
  }
}
