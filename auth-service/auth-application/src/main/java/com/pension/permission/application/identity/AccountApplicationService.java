package com.pension.permission.application.identity;

import com.example.shared.domain.event.EventBus;
import com.pension.permission.domain.channel.spi.LoginTokenService;
import com.pension.permission.domain.user.aggregate.UserAggregate;
import com.pension.permission.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号冻结是这套模型里对"紧急收权"要求最高的场景之一，且要求两件事同步发生：
 * 1) 权限层面：freeze()登记的AccountFrozen事件，驱动该身份的有效权限快照立即失效
 * 2) 登录态层面：联动调用LoginTokenService把这个账号名下所有登录态踢下线
 * 只做一半会出现"权限已经收了，但这个人还能拿着旧token继续操作直到token自然过期"的窗口期。
 */
@Service
@RequiredArgsConstructor
public class AccountApplicationService {

  private final UserRepository accountRepository;
  private final LoginTokenService loginTokenService;
  private final EventBus eventBus;

  @Transactional
  public void freeze(FreezeAccountCommand command) {
    UserAggregate account = accountRepository.loadOrThrow(command.accountId());
    account.freeze(command.operator());
    accountRepository.save(account);
    account.domainEvents().forEach(eventBus::publish);
    // 登录态失效放在事务外：Sa-Token的踢人操作不参与我们自己的数据库事务，
    // 且即使这一步异常，也不应该回滚已经落库的冻结状态——冻结本身要优先生效，
    // 踢人失败可以重试或者靠token自然过期兜底。
    loginTokenService.invalidateAllTokensOf(command.accountId());
  }

  @Transactional
  public void activate(ActivateAccountCommand command) {
    UserAggregate account = accountRepository.loadOrThrow(command.accountId());
    account.activate(command.operator());
    accountRepository.save(account);
    account.domainEvents().forEach(eventBus::publish);
  }
}
