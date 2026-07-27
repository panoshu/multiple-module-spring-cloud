package com.example.iam.adapter.controller;

import com.example.iam.api.command.BranchLoginCommand;
import com.example.iam.api.command.InitiateSecondaryAuthCommand;
import com.example.iam.api.command.LogoutCommand;
import com.example.iam.api.dto.LoginResultDTO;
import com.example.iam.api.dto.SecondaryAuthSessionDTO;
import com.example.iam.api.query.GetSecondaryAuthStatusQuery;
import com.example.iam.application.service.BranchAuthService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BranchAuthController} 单元测试。
 *
 * <p>Controller 仅做请求转发,不包含业务逻辑,因此测试重点验证:
 * <ul>
 *   <li>正确委托给 {@link BranchAuthService} 与 {@link SecondaryAuthAppService}</li>
 *   <li>成功路径返回 {@link ApiResult#success(Object)} 包装的结果</li>
 *   <li>异常路径透传应用服务抛出的 {@link BusinessException}</li>
 * </ul>
 *
 * <p>采用纯单元测试方案(方案 C),避免 sa-token 自动配置导致的 Spring 上下文加载复杂度。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BranchAuthController 网点渠道认证")
class BranchAuthControllerTest {

  private static final String TOKEN_VALUE = "token-branch-001";
  private static final Long SESSION_ID = 9001L;

  @Mock
  private BranchAuthService branchAuthService;

  @Mock
  private SecondaryAuthAppService secondaryAuthAppService;

  @InjectMocks
  private BranchAuthController controller;

  private BranchLoginCommand loginCommand;
  private LogoutCommand logoutCommand;

  @BeforeEach
  void setUp() {
    loginCommand = new BranchLoginCommand(
        "T001", "encrypted-pwd", "10.0.0.1", "Mozilla/5.0");
    logoutCommand = new LogoutCommand("BRANCH");
  }

  private static LoginResultDTO successLoginResult() {
    return new LoginResultDTO(
        true, TOKEN_VALUE, "satoken-branch",
        1001L, "BRANCH", null, false);
  }

  @Nested
  @DisplayName("login 网点渠道登录")
  class Login {

    @Test
    @DisplayName("成功路径:委托 BranchAuthService 并以 ApiResult.success 包装返回")
    void success_delegatesAndWrapsAsApiResult() {
      LoginResultDTO result = successLoginResult();
      when(branchAuthService.login(loginCommand)).thenReturn(result);

      ApiResult<LoginResultDTO> apiResult = controller.login(loginCommand);

      assertThat(apiResult).isNotNull();
      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.message()).isEqualTo("success");
      assertThat(apiResult.data()).isSameAs(result);
      assertThat(apiResult.data().tokenValue()).isEqualTo(TOKEN_VALUE);
      verify(branchAuthService).login(loginCommand);
    }

    @Test
    @DisplayName("异常路径:Service 抛 BusinessException 时透传(由 GlobalExceptionHandler 处理)")
    void serviceThrowsBusinessException_propagates() {
      BusinessException ex = new BusinessException(IamAuthErrorCode.LOGIN_NAME_OR_PASSWORD_ERROR)
          .withUserDetail("柜员号或密码错误");
      when(branchAuthService.login(loginCommand)).thenThrow(ex);

      assertThatThrownBy(() -> controller.login(loginCommand))
          .isSameAs(ex)
          .isInstanceOf(BusinessException.class);
      verify(branchAuthService).login(loginCommand);
    }
  }

  @Nested
  @DisplayName("logout 网点渠道登出")
  class Logout {

    @Test
    @DisplayName("成功路径:委托 BranchAuthService 并返回无数据成功结果")
    void success_delegatesAndReturnsVoidSuccess() {
      ApiResult<Void> apiResult = controller.logout(logoutCommand);

      assertThat(apiResult).isNotNull();
      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isNull();
      verify(branchAuthService).logout(logoutCommand);
    }
  }

  @Nested
  @DisplayName("initiateSecondaryAuth 发起二次授权")
  class InitiateSecondaryAuth {

    @Test
    @DisplayName("成功路径:委托 SecondaryAuthAppService 并以 ApiResult.success 包装返回")
    void success_delegatesAndWrapsAsApiResult() {
      InitiateSecondaryAuthCommand command = new InitiateSecondaryAuthCommand(
          "approver01", "C001", "PLAN001", "C002");
      SecondaryAuthSessionDTO session = buildSession(SESSION_ID, "PENDING", null);
      when(secondaryAuthAppService.initiate(command)).thenReturn(session);

      ApiResult<SecondaryAuthSessionDTO> apiResult = controller.initiateSecondaryAuth(command);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isSameAs(session);
      assertThat(apiResult.data().sessionId()).isEqualTo(SESSION_ID);
      verify(secondaryAuthAppService).initiate(command);
    }
  }

  @Nested
  @DisplayName("getSecondaryAuthStatus 查询二次授权状态")
  class GetSecondaryAuthStatus {

    @Test
    @DisplayName("成功路径:委托 SecondaryAuthAppService 并以 ApiResult.success 包装返回")
    void success_delegatesAndWrapsAsApiResult() {
      GetSecondaryAuthStatusQuery query = new GetSecondaryAuthStatusQuery(SESSION_ID);
      SecondaryAuthSessionDTO session = buildSession(SESSION_ID, "AUTHORIZED", LocalDateTime.now());
      when(secondaryAuthAppService.getStatus(query)).thenReturn(session);

      ApiResult<SecondaryAuthSessionDTO> apiResult = controller.getSecondaryAuthStatus(query);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isSameAs(session);
      verify(secondaryAuthAppService).getStatus(query);
    }
  }

  /**
   * 构造二次授权会话 DTO(13 字段 record)。
   */
  private static SecondaryAuthSessionDTO buildSession(Long sessionId, String status, LocalDateTime authorizedAt) {
    return new SecondaryAuthSessionDTO(
        sessionId, 1001L, 2001L, "C001", "PLAN001",
        java.util.Set.of("a.b"), status, LocalDateTime.now(),
        authorizedAt, null, null, LocalDateTime.now(), LocalDateTime.now());
  }
}
