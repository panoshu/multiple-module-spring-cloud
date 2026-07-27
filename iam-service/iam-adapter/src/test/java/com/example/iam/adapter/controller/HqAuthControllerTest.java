package com.example.iam.adapter.controller;

import com.example.iam.api.command.HqLoginCommand;
import com.example.iam.api.command.LogoutCommand;
import com.example.iam.api.dto.LoginResultDTO;
import com.example.iam.application.service.HqAuthService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HqAuthController} 单元测试。
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
@DisplayName("HqAuthController 总部渠道认证")
class HqAuthControllerTest {

  private static final String TOKEN_VALUE = "token-hq-001";

  @Mock
  private HqAuthService hqAuthService;

  @InjectMocks
  private HqAuthController controller;

  private HqLoginCommand loginCommand;
  private LogoutCommand logoutCommand;

  @BeforeEach
  void setUp() {
    loginCommand = new HqLoginCommand(
        "E001", "encrypted-pwd", "captcha-001", "10.0.0.2", "Mozilla/5.0");
    logoutCommand = new LogoutCommand("HQ");
  }

  private static LoginResultDTO successLoginResult() {
    return new LoginResultDTO(
        true, TOKEN_VALUE, "satoken-hq",
        2001L, "HQ", null, false);
  }

  @Nested
  @DisplayName("login 总部渠道登录")
  class Login {

    @Test
    @DisplayName("成功路径:委托 HqAuthService 并以 ApiResult.success 包装返回")
    void success_delegatesAndWrapsAsApiResult() {
      LoginResultDTO result = successLoginResult();
      when(hqAuthService.login(loginCommand)).thenReturn(result);

      ApiResult<LoginResultDTO> apiResult = controller.login(loginCommand);

      assertThat(apiResult).isNotNull();
      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.message()).isEqualTo("success");
      assertThat(apiResult.data()).isSameAs(result);
      assertThat(apiResult.data().tokenValue()).isEqualTo(TOKEN_VALUE);
      verify(hqAuthService).login(loginCommand);
    }

    @Test
    @DisplayName("异常路径:Service 抛 BusinessException 时透传")
    void serviceThrowsBusinessException_propagates() {
      BusinessException ex = new BusinessException(IamAuthErrorCode.ACCOUNT_LOCKED)
          .withUserDetail("账号已锁定,请联系管理员");
      when(hqAuthService.login(loginCommand)).thenThrow(ex);

      assertThatThrownBy(() -> controller.login(loginCommand))
          .isSameAs(ex)
          .isInstanceOf(BusinessException.class);
      verify(hqAuthService).login(loginCommand);
    }
  }

  @Nested
  @DisplayName("logout 总部渠道登出")
  class Logout {

    @Test
    @DisplayName("成功路径:委托 HqAuthService 并返回无数据成功结果")
    void success_delegatesAndReturnsVoidSuccess() {
      ApiResult<Void> apiResult = controller.logout(logoutCommand);

      assertThat(apiResult).isNotNull();
      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isNull();
      verify(hqAuthService).logout(logoutCommand);
    }
  }
}
