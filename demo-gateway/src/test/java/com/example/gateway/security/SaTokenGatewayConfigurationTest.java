package com.example.gateway.security;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.stp.StpLogic;
import com.example.shared.web.core.api.ApiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * {@link SaTokenGatewayConfiguration} sa-token 网关配置单元测试。
 *
 * <p>覆盖 saReactorFilter Bean 创建、handleError 异常映射、checkChannel 渠道校验、
 * applyRuleCheck 规则分派(LOGIN/PERMISSION/ROLE/CHANNEL/SKIP/未知)。
 *
 * <p>handleError/checkChannel/applyRuleCheck 为 private 方法,通过反射调用以测试其可观察行为,
 * 不修改被测类。handleError 依赖 SaHolder.getResponse(),使用 Mockito.mockStatic 模拟。
 *
 * <p>注:saReactorFilter() 的 setAuth 回调依赖 SaHolder 运行时上下文,本测试仅验证 Bean 创建,
 * 不验证回调内部逻辑(回调逻辑通过 applyRuleCheck / checkChannel 单独测试覆盖)。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SaTokenGatewayConfiguration sa-token 网关配置测试")
class SaTokenGatewayConfigurationTest {

  private static final String CODE_NOT_LOGIN = "COMMON.0002";
  private static final String CODE_NO_PERMISSION = "COMMON.0003";
  private static final String CODE_INTERNAL_ERROR = "COMMON.0050";

  @Mock
  private ChannelAwareSaRouter channelAwareSaRouter;

  @Mock
  private RouteRuleLoader routeRuleLoader;

  @Mock
  private StpLogic stpLogic;

  private SaTokenGatewayConfiguration configuration;

  @BeforeEach
  void setUp() {
    configuration = new SaTokenGatewayConfiguration(channelAwareSaRouter, routeRuleLoader);
  }

  private Object invokeHandleError(Throwable e) throws Exception {
    Method method = SaTokenGatewayConfiguration.class.getDeclaredMethod("handleError", Throwable.class);
    method.setAccessible(true);
    return method.invoke(configuration, e);
  }

  private void invokeApplyRuleCheck(RouteRule rule, ChannelType channel, StpLogic logic) throws Exception {
    Method method = SaTokenGatewayConfiguration.class.getDeclaredMethod(
      "applyRuleCheck", RouteRule.class, ChannelType.class, StpLogic.class);
    method.setAccessible(true);
    method.invoke(configuration, rule, channel, logic);
  }

  private void invokeCheckChannel(ChannelType current, String checkValue) throws Exception {
    Method method = SaTokenGatewayConfiguration.class.getDeclaredMethod(
      "checkChannel", ChannelType.class, String.class);
    method.setAccessible(true);
    method.invoke(configuration, current, checkValue);
  }

  @Nested
  @DisplayName("saReactorFilter Bean 创建")
  class SaReactorFilterBean {

    @Test
    @DisplayName("saReactorFilter() 返回非 null SaReactorFilter 实例")
    void returnsNonNullFilter() {
      SaReactorFilter filter = configuration.saReactorFilter();
      assertThat(filter).isNotNull();
    }
  }

  @Nested
  @DisplayName("handleError 异常处理")
  class HandleError {

    @Test
    @DisplayName("NotLoginException → 401 + COMMON.0002")
    void notLoginExceptionReturns401() throws Exception {
      NotLoginException exception = Mockito.mock(NotLoginException.class);

      try (MockedStatic<SaHolder> mockedSaHolder = Mockito.mockStatic(SaHolder.class)) {
        SaResponse mockResponse = Mockito.mock(SaResponse.class);
        mockedSaHolder.when(SaHolder::getResponse).thenReturn(mockResponse);

        Object result = invokeHandleError(exception);

        assertThat(result).isInstanceOf(ApiResult.class);
        @SuppressWarnings("unchecked")
        ApiResult<Object> apiResult = (ApiResult<Object>) result;
        assertThat(apiResult.code()).isEqualTo(CODE_NOT_LOGIN);
        assertThat(apiResult.message()).isEqualTo("未登录或登录已过期");
        verify(mockResponse).setStatus(401);
      }
    }

    @Test
    @DisplayName("NotPermissionException → 403 + COMMON.0003")
    void notPermissionExceptionReturns403() throws Exception {
      NotPermissionException exception = Mockito.mock(NotPermissionException.class);

      try (MockedStatic<SaHolder> mockedSaHolder = Mockito.mockStatic(SaHolder.class)) {
        SaResponse mockResponse = Mockito.mock(SaResponse.class);
        mockedSaHolder.when(SaHolder::getResponse).thenReturn(mockResponse);

        Object result = invokeHandleError(exception);

        assertThat(result).isInstanceOf(ApiResult.class);
        @SuppressWarnings("unchecked")
        ApiResult<Object> apiResult = (ApiResult<Object>) result;
        assertThat(apiResult.code()).isEqualTo(CODE_NO_PERMISSION);
        assertThat(apiResult.message()).isEqualTo("无权限访问");
        verify(mockResponse).setStatus(403);
      }
    }

    @Test
    @DisplayName("NotRoleException → 403 + COMMON.0003")
    void notRoleExceptionReturns403() throws Exception {
      NotRoleException exception = Mockito.mock(NotRoleException.class);

      try (MockedStatic<SaHolder> mockedSaHolder = Mockito.mockStatic(SaHolder.class)) {
        SaResponse mockResponse = Mockito.mock(SaResponse.class);
        mockedSaHolder.when(SaHolder::getResponse).thenReturn(mockResponse);

        Object result = invokeHandleError(exception);

        assertThat(result).isInstanceOf(ApiResult.class);
        @SuppressWarnings("unchecked")
        ApiResult<Object> apiResult = (ApiResult<Object>) result;
        assertThat(apiResult.code()).isEqualTo(CODE_NO_PERMISSION);
        assertThat(apiResult.message()).isEqualTo("无权限访问");
        verify(mockResponse).setStatus(403);
      }
    }

    @Test
    @DisplayName("其他异常 → 500 + COMMON.0050")
    void otherExceptionReturns500() throws Exception {
      RuntimeException exception = new RuntimeException("unexpected");

      try (MockedStatic<SaHolder> mockedSaHolder = Mockito.mockStatic(SaHolder.class)) {
        SaResponse mockResponse = Mockito.mock(SaResponse.class);
        mockedSaHolder.when(SaHolder::getResponse).thenReturn(mockResponse);

        Object result = invokeHandleError(exception);

        assertThat(result).isInstanceOf(ApiResult.class);
        @SuppressWarnings("unchecked")
        ApiResult<Object> apiResult = (ApiResult<Object>) result;
        assertThat(apiResult.code()).isEqualTo(CODE_INTERNAL_ERROR);
        assertThat(apiResult.message()).isEqualTo("系统内部错误");
        verify(mockResponse).setStatus(500);
      }
    }
  }

  @Nested
  @DisplayName("checkChannel 渠道校验")
  class CheckChannel {

    @Test
    @DisplayName("匹配渠道: 不抛异常")
    void matchingChannelDoesNotThrow() throws Exception {
      invokeCheckChannel(ChannelType.INTERNET, "INTERNET");
    }

    @Test
    @DisplayName("匹配渠道(大小写不敏感): 不抛异常")
    void matchingChannelCaseInsensitive() throws Exception {
      invokeCheckChannel(ChannelType.HQ, "hq");
    }

    @Test
    @DisplayName("不匹配渠道: 抛 NotPermissionException")
    void mismatchingChannelThrows() {
      when(channelAwareSaRouter.getStpLogic(ChannelType.INTERNET)).thenReturn(stpLogic);
      when(stpLogic.getLoginType()).thenReturn("internet");

      assertThatThrownBy(() -> invokeCheckChannel(ChannelType.INTERNET, "HQ"))
        .isInstanceOf(InvocationTargetException.class)
        .hasCauseInstanceOf(NotPermissionException.class);
    }

    @Test
    @DisplayName("null checkValue: 不抛异常")
    void nullCheckValueDoesNotThrow() throws Exception {
      invokeCheckChannel(ChannelType.INTERNET, null);
    }

    @Test
    @DisplayName("空白 checkValue: 不抛异常")
    void blankCheckValueDoesNotThrow() throws Exception {
      invokeCheckChannel(ChannelType.INTERNET, "   ");
    }
  }

  @Nested
  @DisplayName("applyRuleCheck 规则校验分派")
  class ApplyRuleCheck {

    @Test
    @DisplayName("LOGIN: 已在 matchAndCheckLogin 阶段校验,不调用 stpLogic")
    void loginTypeDoesNotCallStpLogic() throws Exception {
      RouteRule rule = new RouteRule("/internet/**", "LOGIN", "", 100);
      invokeApplyRuleCheck(rule, ChannelType.INTERNET, stpLogic);
      verifyNoInteractions(stpLogic);
    }

    @Test
    @DisplayName("PERMISSION: 调用 stpLogic.checkPermission(checkValue)")
    void permissionTypeCallsCheckPermission() throws Exception {
      RouteRule rule = new RouteRule("/internet/**", "PERMISSION", "biz:handle", 100);
      invokeApplyRuleCheck(rule, ChannelType.INTERNET, stpLogic);
      verify(stpLogic).checkPermission("biz:handle");
    }

    @Test
    @DisplayName("ROLE: 调用 stpLogic.checkRole(checkValue)")
    void roleTypeCallsCheckRole() throws Exception {
      RouteRule rule = new RouteRule("/internet/**", "ROLE", "admin", 100);
      invokeApplyRuleCheck(rule, ChannelType.INTERNET, stpLogic);
      verify(stpLogic).checkRole("admin");
    }

    @Test
    @DisplayName("CHANNEL 匹配: 不抛异常,不调用 stpLogic")
    void channelTypeMatchingDoesNotThrow() throws Exception {
      RouteRule rule = new RouteRule("/internet/**", "CHANNEL", "INTERNET", 100);
      invokeApplyRuleCheck(rule, ChannelType.INTERNET, stpLogic);
      verifyNoInteractions(stpLogic);
    }

    @Test
    @DisplayName("CHANNEL 不匹配: 抛 NotPermissionException")
    void channelTypeMismatchThrows() {
      when(channelAwareSaRouter.getStpLogic(ChannelType.INTERNET)).thenReturn(stpLogic);
      when(stpLogic.getLoginType()).thenReturn("internet");

      RouteRule rule = new RouteRule("/internet/**", "CHANNEL", "HQ", 100);

      assertThatThrownBy(() -> invokeApplyRuleCheck(rule, ChannelType.INTERNET, stpLogic))
        .isInstanceOf(InvocationTargetException.class)
        .hasCauseInstanceOf(NotPermissionException.class);
    }

    @Test
    @DisplayName("SKIP: 白名单,不调用 stpLogic")
    void skipTypeDoesNotCallStpLogic() throws Exception {
      RouteRule rule = new RouteRule("/internet/**", "SKIP", "", 100);
      invokeApplyRuleCheck(rule, ChannelType.INTERNET, stpLogic);
      verifyNoInteractions(stpLogic);
    }

    @Test
    @DisplayName("未知 checkType: 不抛异常,不调用 stpLogic")
    void unknownCheckTypeDoesNotThrow() throws Exception {
      RouteRule rule = new RouteRule("/internet/**", "UNKNOWN", "value", 100);
      invokeApplyRuleCheck(rule, ChannelType.INTERNET, stpLogic);
      verifyNoInteractions(stpLogic);
    }
  }
}
