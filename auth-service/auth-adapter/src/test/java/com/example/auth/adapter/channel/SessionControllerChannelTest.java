package com.example.auth.adapter.channel;

import com.example.auth.api.command.LoginRequest;
import com.example.auth.api.dto.LoginResponse;
import com.example.auth.application.channel.SessionApplicationService;
import com.example.auth.domain.channel.spi.LoginTokenService;
import com.example.auth.types.SessionId;
import com.example.core.types.Channel;
import com.example.core.types.UserNo;
import com.example.shared.web.core.api.ApiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("登录渠道解析（V-05 联调发现的非法 channel NPE）")
class SessionControllerChannelTest {

  private SessionApplicationService sessionApplicationService;
  private LoginTokenService loginTokenService;
  private SessionController controller;

  @BeforeEach
  void setUp() {
    sessionApplicationService = mock(SessionApplicationService.class);
    loginTokenService = mock(LoginTokenService.class);
    controller = new SessionController(sessionApplicationService, loginTokenService);
    when(sessionApplicationService.openSessionWithCredential(any()))
      .thenReturn(new SessionId("token-1"));
    when(loginTokenService.getTokenName(any())).thenReturn("satoken-internet");
    when(loginTokenService.verifyToken("token-1")).thenReturn(Optional.of(UserNo.of("U1001")));
  }

  @Test
  @DisplayName("渠道名 NETAPP 解析为 Channel.NETAPP")
  void acceptsChannelName() {
    ApiResult<LoginResponse> result = controller.login(
      new LoginRequest("PASSWORD", "USER", "U1001", "NETAPP", "pwd", null));

    ArgumentCaptor<com.example.auth.application.channel.OpenSessionWithCredentialCommand> captor =
      ArgumentCaptor.forClass(com.example.auth.application.channel.OpenSessionWithCredentialCommand.class);
    verify(sessionApplicationService).openSessionWithCredential(captor.capture());
    assertThat(captor.getValue().channel()).isEqualTo(Channel.NETAPP);
    assertThat(result.data().channel()).isEqualTo("NETAPP");
  }

  @Test
  @DisplayName("渠道编码 01 解析为 Channel.NETAPP")
  void acceptsChannelCode() {
    controller.login(new LoginRequest("PASSWORD", "USER", "U1001", "01", "pwd", null));

    ArgumentCaptor<com.example.auth.application.channel.OpenSessionWithCredentialCommand> captor =
      ArgumentCaptor.forClass(com.example.auth.application.channel.OpenSessionWithCredentialCommand.class);
    verify(sessionApplicationService).openSessionWithCredential(captor.capture());
    assertThat(captor.getValue().channel()).isEqualTo(Channel.NETAPP);
  }

  @Test
  @DisplayName("未知渠道 fail-fast，不再传入 null 触发领域层 NPE")
  void rejectsUnknownChannel() {
    assertThatThrownBy(() -> controller.login(
      new LoginRequest("PASSWORD", "USER", "U1001", "UNKNOWN", "pwd", null)))
      .isInstanceOf(com.example.shared.exception.BusinessException.class)
      .satisfies(error -> assertThat(
        ((com.example.shared.exception.BusinessException) error).displayMessage())
        .contains("未知渠道"));;
    verifyNoInteractions(sessionApplicationService);
  }
}
