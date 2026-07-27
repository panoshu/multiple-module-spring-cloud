package com.example.iam.adapter.controller;

import com.example.iam.api.command.ConfirmSecondaryAuthCommand;
import com.example.iam.api.command.InternetLoginCommand;
import com.example.iam.api.command.LogoutCommand;
import com.example.iam.api.command.RejectSecondaryAuthCommand;
import com.example.iam.api.command.RevokeSecondaryAuthCommand;
import com.example.iam.api.dto.LoginResultDTO;
import com.example.iam.api.dto.SecondaryAuthSessionDTO;
import com.example.iam.application.service.InternetAuthService;
import com.example.iam.application.service.SecondaryAuthAppService;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.shared.exception.BusinessException;
import com.example.shared.web.core.api.ApiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InternetAuthController} 单元测试。
 *
 * <p>Controller 仅做请求转发,测试重点验证委托关系与 {@link ApiResult} 包装。
 *
 * <p>采用纯单元测试方案(方案 C),避免 sa-token 自动配置导致的 Spring 上下文加载复杂度。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InternetAuthController 网上渠道认证")
class InternetAuthControllerTest {

  private static final String TOKEN_VALUE = "token-internet-001";
  private static final Long SESSION_ID = 9002L;

  @Mock
  private InternetAuthService internetAuthService;

  @Mock
  private SecondaryAuthAppService secondaryAuthAppService;

  @InjectMocks
  private InternetAuthController controller;

  private InternetLoginCommand loginCommand;
  private LogoutCommand logoutCommand;

  @BeforeEach
  void setUp() {
    loginCommand = new InternetLoginCommand(
        "user01", "encrypted-pwd", "captcha-002", "10.0.0.3", "Mozilla/5.0");
    logoutCommand = new LogoutCommand("INTERNET");
  }

  private static LoginResultDTO successLoginResult() {
    return new LoginResultDTO(
        true, TOKEN_VALUE, "satoken-internet",
        3001L, "INTERNET", null, false);
  }

  private static SecondaryAuthSessionDTO buildSession(Long sessionId, String status) {
    return new SecondaryAuthSessionDTO(
        sessionId, 3001L, 4001L, "C001", "PLAN001",
        Set.of("a.b"), status, LocalDateTime.now(),
        null, null, null, LocalDateTime.now(), LocalDateTime.now());
  }

  @Nested
  @DisplayName("login 网上渠道登录")
  class Login {

    @Test
    @DisplayName("成功路径:委托 InternetAuthService 并以 ApiResult.success 包装返回")
    void success_delegatesAndWrapsAsApiResult() {
      LoginResultDTO result = successLoginResult();
      when(internetAuthService.login(loginCommand)).thenReturn(result);

      ApiResult<LoginResultDTO> apiResult = controller.login(loginCommand);

      assertThat(apiResult).isNotNull();
      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.message()).isEqualTo("success");
      assertThat(apiResult.data()).isSameAs(result);
      assertThat(apiResult.data().tokenValue()).isEqualTo(TOKEN_VALUE);
      verify(internetAuthService).login(loginCommand);
    }

    @Test
    @DisplayName("异常路径:Service 抛 BusinessException 时透传")
    void serviceThrowsBusinessException_propagates() {
      BusinessException ex = new BusinessException(IamAuthErrorCode.LOGIN_NAME_OR_PASSWORD_ERROR)
          .withUserDetail("登录名或密码错误");
      when(internetAuthService.login(loginCommand)).thenThrow(ex);

      assertThatThrownBy(() -> controller.login(loginCommand))
          .isSameAs(ex)
          .isInstanceOf(BusinessException.class);
      verify(internetAuthService).login(loginCommand);
    }
  }

  @Nested
  @DisplayName("logout 网上渠道登出")
  class Logout {

    @Test
    @DisplayName("成功路径:委托 InternetAuthService 并返回无数据成功结果")
    void success_delegatesAndReturnsVoidSuccess() {
      ApiResult<Void> apiResult = controller.logout(logoutCommand);

      assertThat(apiResult).isNotNull();
      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isNull();
      verify(internetAuthService).logout(logoutCommand);
    }
  }

  @Nested
  @DisplayName("confirmSecondaryAuth 确认二次授权")
  class ConfirmSecondaryAuth {

    @Test
    @DisplayName("成功路径:委托 SecondaryAuthAppService 并以 ApiResult.success 包装返回")
    void success_delegatesAndWrapsAsApiResult() {
      ConfirmSecondaryAuthCommand command = new ConfirmSecondaryAuthCommand(
          SESSION_ID, "approver-pwd");
      SecondaryAuthSessionDTO session = buildSession(SESSION_ID, "AUTHORIZED");
      when(secondaryAuthAppService.confirm(command)).thenReturn(session);

      ApiResult<SecondaryAuthSessionDTO> apiResult = controller.confirmSecondaryAuth(command);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isSameAs(session);
      verify(secondaryAuthAppService).confirm(command);
    }
  }

  @Nested
  @DisplayName("rejectSecondaryAuth 拒绝二次授权")
  class RejectSecondaryAuth {

    @Test
    @DisplayName("成功路径:委托 SecondaryAuthAppService 并返回无数据成功结果")
    void success_delegatesAndReturnsVoidSuccess() {
      RejectSecondaryAuthCommand command = new RejectSecondaryAuthCommand(
          SESSION_ID, "拒绝原因");
      ApiResult<Void> apiResult = controller.rejectSecondaryAuth(command);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isNull();
      verify(secondaryAuthAppService).reject(command);
    }
  }

  @Nested
  @DisplayName("revokeSecondaryAuth 撤销二次授权")
  class RevokeSecondaryAuth {

    @Test
    @DisplayName("成功路径:委托 SecondaryAuthAppService 并返回无数据成功结果")
    void success_delegatesAndReturnsVoidSuccess() {
      RevokeSecondaryAuthCommand command = new RevokeSecondaryAuthCommand(
          SESSION_ID, "撤销原因");
      ApiResult<Void> apiResult = controller.revokeSecondaryAuth(command);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isNull();
      verify(secondaryAuthAppService).revoke(command);
    }
  }
}
