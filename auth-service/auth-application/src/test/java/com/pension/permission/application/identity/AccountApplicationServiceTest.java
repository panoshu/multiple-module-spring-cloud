package com.pension.permission.application.identity;

import com.example.shared.domain.event.EventBus;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.spi.LoginTokenService;
import com.pension.permission.domain.user.aggregate.UserAggregate;
import com.pension.permission.domain.user.event.UserFrozen;
import com.pension.permission.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AccountApplicationService 测试")
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceTest {

  @Mock
  private UserRepository accountRepository;

  @Mock
  private LoginTokenService loginTokenService;

  @Mock
  private EventBus eventBus;

  @InjectMocks
  private AccountApplicationService service;

  @Nested
  @DisplayName("freeze: 冻结账号")
  class FreezeTest {

    @Test
    @DisplayName("应加载账号、冻结、保存、发布事件并踢下线")
    void shouldFreezeAccountAndInvalidateTokens() {
      var account = mock(UserAggregate.class);
      when(accountRepository.loadOrThrow(UserNo.of("user-1"))).thenReturn(account);
      when(account.domainEvents()).thenReturn(java.util.List.of(mock(UserFrozen.class)));
      var command = new FreezeAccountCommand(UserNo.of("user-1"), UserNo.of("admin-1"));

      service.freeze(command);

      verify(account).freeze(UserNo.of("admin-1"));
      verify(accountRepository).save(account);
      verify(eventBus, atLeastOnce()).publish(any());
      verify(loginTokenService).invalidateAllTokensOf(UserNo.of("user-1"));
    }
  }

  @Nested
  @DisplayName("activate: 激活账号")
  class ActivateTest {

    @Test
    @DisplayName("应加载账号、激活、保存并发布事件")
    void shouldActivateAccount() {
      var account = mock(UserAggregate.class);
      when(accountRepository.loadOrThrow(UserNo.of("user-1"))).thenReturn(account);
      when(account.domainEvents()).thenReturn(java.util.List.of());
      var command = new ActivateAccountCommand(UserNo.of("user-1"), UserNo.of("admin-1"));

      service.activate(command);

      verify(account).activate(UserNo.of("admin-1"));
      verify(accountRepository).save(account);
      verify(loginTokenService, never()).invalidateAllTokensOf(any());
    }
  }
}
