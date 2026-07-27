package com.example.iam.application.event;

import com.example.iam.application.port.ChannelSessionPort;
import com.example.iam.application.port.PermissionCachePort;
import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.root.User;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.event.CredentialChangedEvent;
import com.example.iam.domain.authentication.event.CredentialCreatedEvent;
import com.example.iam.domain.authentication.event.SecondaryAuthCompletedEvent;
import com.example.iam.domain.authentication.event.SecondaryAuthRevokedEvent;
import com.example.iam.domain.authentication.event.UserCreatedEvent;
import com.example.iam.domain.authentication.event.UserDisabledEvent;
import com.example.iam.domain.authentication.event.UserEnabledEvent;
import com.example.iam.domain.authentication.repository.CredentialRepository;
import com.example.iam.domain.authentication.repository.SecondaryAuthSessionRepository;
import com.example.iam.domain.authentication.repository.UserRepository;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.event.PermissionRuleCreatedEvent;
import com.example.iam.domain.authorization.event.PermissionRuleDisabledEvent;
import com.example.iam.domain.authorization.event.PermissionRuleEnabledEvent;
import com.example.iam.domain.authorization.event.PlanDelegationActivatedEvent;
import com.example.iam.domain.authorization.event.PlanDelegationCreatedEvent;
import com.example.iam.domain.authorization.event.PlanDelegationRevokedEvent;
import com.example.iam.types.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IAM 领域事件订阅者。
 *
 * <p>订阅 iam-domain 各聚合根发布的领域事件,执行跨聚合/跨上下文的后续处理:
 * <ul>
 *   <li>用户禁用 → 撤销凭据 + 撤销二次授权会话 + 踢人下线 + 清缓存</li>
 *   <li>凭据变更 → 踢人下线 + 清缓存</li>
 *   <li>二次授权完成 → 写入柜员 Token-Session</li>
 *   <li>二次授权撤销 → 清除 Token-Session + 踢柜员下线</li>
 *   <li>权限规则变更 → 清除权限缓存</li>
 *   <li>计划代办变更 → 清除权限缓存</li>
 * </ul>
 *
 * <p>所有 AFTER_COMMIT 监听器使用 {@code @Async} 异步执行,异常仅记录日志不抛出,
 * 避免影响其他订阅者和事务提交。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IamDomainEventListener {

  private static final String OWNER_TYPE_SUFFIX = "_USER";
  private static final String USER_DISABLED_REVOKE_REASON = "用户已禁用";

  private final UserRepository userRepository;
  private final CredentialRepository credentialRepository;
  private final SecondaryAuthSessionRepository secondaryAuthSessionRepository;
  private final ChannelSessionPort channelSessionPort;
  private final PermissionCachePort permissionCachePort;

  /**
   * 用户已禁用 - 连锁反应:撤销凭据 + 撤销二次授权会话 + 踢人下线 + 清缓存。
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserDisabled(UserDisabledEvent event) {
    try {
      Long userId = event.userId().value();
      log.info("处理用户禁用事件, userId: {}, reason: {}", userId, event.reason());

      User user = userRepository.load(event.userId())
          .orElseThrow(() -> new IllegalStateException("用户不存在: " + userId));
      String ownerType = ownerTypeOf(user.channelType());

      List<Credential> credentials = credentialRepository.findAllByOwner(userId, ownerType);
      for (Credential credential : credentials) {
        credential.markRevoked(event.operator());
        credentialRepository.save(credential);
      }
      log.info("已撤销用户 {} 的 {} 条凭据", userId, credentials.size());

      secondaryAuthSessionRepository.findEffectiveByTeller(userId)
          .ifPresent(session -> {
            session.revoke(event.operator(), USER_DISABLED_REVOKE_REASON + ": " + event.reason());
            secondaryAuthSessionRepository.save(session);
            log.info("已撤销柜员 {} 的二次授权会话 {}", userId, session.id().value());
          });

      channelSessionPort.kickout(userId, user.channelType());
      permissionCachePort.evictByUser(userId);
    } catch (Exception e) {
      log.error("处理用户禁用事件失败, eventId: {}, userId: {}",
          event.eventId().value(), event.userId().value(), e);
    }
  }

  /**
   * 用户已启用 - 当前无需特别处理,仅记录日志。
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserEnabled(UserEnabledEvent event) {
    log.info("用户已启用, userId: {}, operator: {}",
        event.userId().value(), event.operator().value());
  }

  /**
   * 用户已创建 - 跨上下文初始化(后续补充默认凭据创建等逻辑)。
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserCreated(UserCreatedEvent event) {
    log.info("用户已创建, userId: {}, loginName: {}, channelType: {}",
        event.userId().value(), event.loginName(), event.channelType());
  }

  /**
   * 凭据已创建 - 当前无需特别处理,仅记录日志。
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCredentialCreated(CredentialCreatedEvent event) {
    log.info("凭据已创建, credentialId: {}, ownerId: {}, credentialType: {}",
        event.credentialId().value(), event.ownerId(), event.credentialType());
  }

  /**
   * 凭据已变更 - 踢人下线 + 清缓存。
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCredentialChanged(CredentialChangedEvent event) {
    try {
      Long ownerId = event.ownerId();
      log.info("处理凭据变更事件, credentialId: {}, ownerId: {}",
          event.credentialId().value(), ownerId);

      ChannelType channelType = userRepository.load(UserId.of(ownerId))
          .map(User::channelType)
          .orElseThrow(() -> new IllegalStateException("用户不存在: " + ownerId));

      channelSessionPort.kickout(ownerId, channelType);
      permissionCachePort.evictByUser(ownerId);
    } catch (Exception e) {
      log.error("处理凭据变更事件失败, eventId: {}, credentialId: {}",
          event.eventId().value(), event.credentialId().value(), e);
    }
  }

  /**
   * 二次授权完成 - 异步更新柜员 Token-Session 中的权限快照。
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onSecondaryAuthCompleted(SecondaryAuthCompletedEvent event) {
    try {
      log.info("处理二次授权完成事件, sessionId: {}, tellerId: {}",
          event.sessionId().value(), event.tellerId());

      Set<String> permissions = secondaryAuthSessionRepository.load(event.sessionId())
          .map(session -> session.permissionSnapshot() == null
              ? Set.<String>of()
              : session.permissionSnapshot().stream()
                  .map(PermissionCode::value)
                  .collect(Collectors.toSet()))
          .orElse(Set.of());

      channelSessionPort.setSecondaryAuthSession(
          event.sessionId().value(), event.approverId(), event.planId(), permissions);
    } catch (Exception e) {
      log.error("处理二次授权完成事件失败, eventId: {}, sessionId: {}",
          event.eventId().value(), event.sessionId().value(), e);
    }
  }

  /**
   * 二次授权撤销 - 清除 Token-Session + 踢柜员下线。
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onSecondaryAuthRevoked(SecondaryAuthRevokedEvent event) {
    try {
      log.info("处理二次授权撤销事件, sessionId: {}, tellerId: {}",
          event.sessionId().value(), event.tellerId());

      channelSessionPort.clearSecondaryAuthSession();
      channelSessionPort.kickout(event.tellerId(), ChannelType.BRANCH);
    } catch (Exception e) {
      log.error("处理二次授权撤销事件失败, eventId: {}, sessionId: {}",
          event.eventId().value(), event.sessionId().value(), e);
    }
  }

  /**
   * 权限规则已创建 - 清除权限缓存。
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPermissionRuleCreated(PermissionRuleCreatedEvent event) {
    try {
      log.info("处理权限规则创建事件, ruleId: {}, ruleCode: {}", event.ruleId().value(), event.ruleCode());
      permissionCachePort.evictAll();
    } catch (Exception e) {
      log.error("处理权限规则创建事件失败, eventId: {}, ruleId: {}",
          event.eventId().value(), event.ruleId().value(), e);
    }
  }

  /**
   * 权限规则已禁用 - 清除权限缓存。
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPermissionRuleDisabled(PermissionRuleDisabledEvent event) {
    try {
      log.info("处理权限规则禁用事件, ruleId: {}, ruleCode: {}", event.ruleId().value(), event.ruleCode());
      permissionCachePort.evictAll();
    } catch (Exception e) {
      log.error("处理权限规则禁用事件失败, eventId: {}, ruleId: {}",
          event.eventId().value(), event.ruleId().value(), e);
    }
  }

  /**
   * 权限规则已启用 - 清除权限缓存。
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPermissionRuleEnabled(PermissionRuleEnabledEvent event) {
    try {
      log.info("处理权限规则启用事件, ruleId: {}, ruleCode: {}", event.ruleId().value(), event.ruleCode());
      permissionCachePort.evictAll();
    } catch (Exception e) {
      log.error("处理权限规则启用事件失败, eventId: {}, ruleId: {}",
          event.eventId().value(), event.ruleId().value(), e);
    }
  }

  /**
   * 计划代办关系已创建 - 清除授权方与被授权方计划的权限缓存。
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPlanDelegationCreated(PlanDelegationCreatedEvent event) {
    try {
      log.info("处理计划代办创建事件, delegationId: {}, delegatorPlanNo: {}, delegateePlanNo: {}",
          event.delegationId().value(), event.delegatorPlanNo(), event.delegateePlanNo());
      permissionCachePort.evictByPlan(event.delegatorPlanNo());
      permissionCachePort.evictByPlan(event.delegateePlanNo());
    } catch (Exception e) {
      log.error("处理计划代办创建事件失败, eventId: {}, delegationId: {}",
          event.eventId().value(), event.delegationId().value(), e);
    }
  }

  /**
   * 计划代办关系已激活 - 清除被授权方计划的权限缓存。
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPlanDelegationActivated(PlanDelegationActivatedEvent event) {
    try {
      log.info("处理计划代办激活事件, delegationId: {}, delegateePlanNo: {}",
          event.delegationId().value(), event.delegateePlanNo());
      permissionCachePort.evictByPlan(event.delegateePlanNo());
    } catch (Exception e) {
      log.error("处理计划代办激活事件失败, eventId: {}, delegationId: {}",
          event.eventId().value(), event.delegationId().value(), e);
    }
  }

  /**
   * 计划代办关系已撤销 - 清除被授权方计划的权限缓存。
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPlanDelegationRevoked(PlanDelegationRevokedEvent event) {
    try {
      log.info("处理计划代办撤销事件, delegationId: {}, delegateePlanNo: {}",
          event.delegationId().value(), event.delegateePlanNo());
      permissionCachePort.evictByPlan(event.delegateePlanNo());
    } catch (Exception e) {
      log.error("处理计划代办撤销事件失败, eventId: {}, delegationId: {}",
          event.eventId().value(), event.delegationId().value(), e);
    }
  }

  /**
   * 根据渠道类型推导凭据归属类型。
   *
   * <p>约定: {@code channelType.name() + "_USER"},如 {@code INTERNET_USER}、{@code BRANCH_USER}。
   */
  private static String ownerTypeOf(ChannelType channelType) {
    return channelType.name() + OWNER_TYPE_SUFFIX;
  }
}
