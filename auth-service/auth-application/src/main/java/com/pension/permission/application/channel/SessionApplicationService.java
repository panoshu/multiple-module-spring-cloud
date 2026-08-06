package com.pension.permission.application.channel;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.spi.LoginTokenService;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.errorcode.SecondaryAuthErrorCode;
import com.pension.permission.domain.channel.event.SecondaryAuthCompleted;
import com.pension.permission.domain.channel.event.SecondaryAuthRevoked;
import com.pension.permission.domain.channel.repository.SessionRepository;
import com.pension.permission.domain.channel.service.ChannelAccessPolicy;
import com.pension.permission.domain.channel.service.IdentityResolutionService;
import com.pension.permission.domain.channel.service.PlanSelectionStrategy;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.EnumeratedPlans;
import com.pension.permission.domain.channel.valueobject.SelectablePlanScope;
import com.pension.permission.types.SessionId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 会话用例的编排入口：开会话(直接给AccountId / 用凭证登录)、网点二次授权(身份提升)、
 * 选计划、查可选计划范围、登出。
 * <p>
 * 凭证校验+身份定位全部委托给IdentityResolutionService(网上/总部/网点柜员登录、
 * 网点二次授权四个场景共用同一个方法)，这一层不重复任何校验逻辑，只负责把结果
 * 包装成Session或EffectiveIdentity，以及事务边界。
 * <p>
 * Session的id直接就是LoginTokenService签发的token值本身——两者合一，
 * 这样"给一个token找到对应会话"不需要额外的映射表：
 * sessionRepository.findById(new SessionId(token)) 就是全部逻辑。
 */
@Service
@RequiredArgsConstructor
public class SessionApplicationService {

  private final SessionRepository sessionRepository;
  private final IdentityResolutionService identityResolutionService;
  private final LoginTokenService loginTokenService;
  private final Map<AnnuityChannel, PlanSelectionStrategy> strategiesByChannel;
  private final ChannelAccessPolicy channelAccessPolicy;
  private final EventBus eventBus;

  /**
   * AccountId已经确定的场景下直接建会话(比如身份已经由别的前置步骤确认过)
   */
  @Transactional
  public SessionId openSession(OpenSessionCommand command) {
    return buildSession(command.accountId(), command.channel());
  }

  /**
   * 用凭证登录：账号级凭证(密码/个人UKey)校验通过就是账号本身；
   * 客户/计划级凭证(企业UKey)校验通过后还要靠手机号定位到具体经办。
   * 网上渠道经办登录、网点柜员登录都走这个方法。
   */
  @Transactional
  public SessionId openSessionWithCredential(OpenSessionWithCredentialCommand command) {
    UserNo accountId = identityResolutionService.resolve(
        command.credentialOwner(), command.channel(), command.proof(), command.phoneNumber())
      .orElseThrow(() -> new SecurityException("登录失败：凭证校验不通过，或无法定位到有效经办"));
    return buildSession(accountId, command.channel());
  }

  private SessionId buildSession(UserNo accountId, AnnuityChannel channel) {
    String token = loginTokenService.issueToken(accountId, channel);
    // Session.id 直接等于 Sa-Token 签发的 tokenValue，二者合一，
    // 使得"给定 token 找会话"无需额外映射表，sessionRepository.load(new SessionId(token)) 即可。
    SessionId sessionId = new SessionId(token);
    Session session = Session.create(
      sessionId,
      accountId,
      accountId,
      channel,
      EffectiveIdentity.direct(accountId),
      Duration.ofHours(8)
    );
    sessionRepository.save(session);
    session.domainEvents().forEach(eventBus::publish);

    return session.id();
  }

  @Transactional
  public void logout(LogoutCommand command) {
    Session session = requireSession(command.sessionId());
    session.close(command.operator());
    sessionRepository.save(session);
    session.domainEvents().forEach(eventBus::publish);

    loginTokenService.invalidateToken(command.sessionId().value());
    // Session记录本身可以留给自然过期/定期清理，不强制在这里同步删除，
    // 避免把"登出"这个轻量操作也绑进数据库事务。
  }

  public SelectablePlanScope listSelectablePlans(SessionId sessionId) {
    Session session = requireSession(sessionId);
    PlanSelectionStrategy strategy = strategiesByChannel.get(session.channel());
    if (strategy == null) {
      throw new IllegalStateException("该渠道未注册对应的计划选择策略: " + session.channel());
    }
    SelectablePlanScope scope = strategy.listSelectablePlans(session.effectiveIdentity());
    return filterByCustomerChannel(scope, session.channel());
  }

  /**
   * 按客户渠道开通记录过滤可选计划.
   *
   * <p>仅对 {@link EnumeratedPlans}（网上渠道/网点二次授权后）生效：保留所属客户
   * 已开通当前登录渠道的计划，剔除未开通的计划。{@link com.pension.permission.domain.channel.valueobject.AllPlans}
   * （总部渠道）不做过滤——总部可办理任意计划，准入由 AuthorizationEngine 二层校验。</p>
   *
   * @param scope   策略返回的可选计划范围
   * @param channel 当前会话渠道
   * @return 过滤后的可选计划范围
   */
  private SelectablePlanScope filterByCustomerChannel(SelectablePlanScope scope, AnnuityChannel channel) {
    if (!(scope instanceof EnumeratedPlans enumerated)) {
      return scope;
    }
    List<PlanNo> filtered = channelAccessPolicy.filterPlansByChannel(enumerated.plans(), channel);
    return new EnumeratedPlans(filtered);
  }

  @Transactional
  public void selectPlan(SelectPlanCommand command) {
    Session session = requireSession(command.sessionId());
    session.selectPlan(command.planId(), command.operator());
    sessionRepository.save(session);
    session.domainEvents().forEach(eventBus::publish);
  }

  private Session requireSession(SessionId id) {
    return sessionRepository.loadOrThrow(id);
  }

  /**
   * 二次授权完成事件监听.
   *
   * <p>在 {@code SecondaryAuthAppService#confirm} 提交事务后触发，将柜员的渠道会话与
   * 二次授权会话绑定，使其有效身份提升为经办身份。</p>
   *
   * <p>注意：本监听依赖 {@link EventBus} 通过 {@code SpringEventDispatcher} 委派给
   * Spring 的 {@code ApplicationEventPublisher}；事件发布侧（{@code SecondaryAuthAppService}
   * 或其 Repository 实现）需在事务内调用 {@code eventBus.publish(...)} 才能触发本监听。</p>
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional
  public void onSecondaryAuthCompleted(SecondaryAuthCompleted event) {
    Session session = sessionRepository.findActiveByPrimaryAccountIdAndChannel(
        event.tellerAccountId(), AnnuityChannel.BANK_BRANCH)
      .orElseThrow(() -> new BusinessException(SecondaryAuthErrorCode.SESSION_NOT_FOUND));
    session.applySecondaryAuth(event.sessionId(), event.effectiveIdentity(), event.tellerAccountId());
    sessionRepository.save(session);
    session.domainEvents().forEach(eventBus::publish);
  }

  /**
   * 二次授权撤销事件监听.
   *
   * <p>在 {@code SecondaryAuthAppService#revoke} 提交事务后触发，清除柜员渠道会话上的
   * 二次授权绑定，恢复为柜员直接身份。柜员无活跃会话时静默跳过。</p>
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional
  public void onSecondaryAuthRevoked(SecondaryAuthRevoked event) {
    sessionRepository.findActiveByPrimaryAccountIdAndChannel(
        event.tellerAccountId(), AnnuityChannel.BANK_BRANCH)
      .ifPresent(session -> {
        session.clearSecondaryAuth(event.createdBy());
        sessionRepository.save(session);
        session.domainEvents().forEach(eventBus::publish);
      });
  }
}
