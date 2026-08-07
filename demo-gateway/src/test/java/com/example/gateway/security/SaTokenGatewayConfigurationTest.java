package com.example.gateway.security;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import com.example.shared.web.core.api.ApiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link SaTokenGatewayConfiguration} sa-token 网关配置单元测试。
 *
 * <p>覆盖 saReactorFilter Bean 创建、handleError 异常映射、initDefaultStpLogic 初始化。
 *
 * <p>handleError 为 private 方法,通过反射调用以测试其可观察行为,不修改被测类。
 * handleError 依赖 SaHolder.getResponse(),使用 Mockito.mockStatic 模拟。
 *
 * <p>注:saReactorFilter() 的 setAuth 回调依赖 SaHolder 运行时上下文,本测试仅验证 Bean 创建,
 * 不验证回调内部逻辑。
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
  private GatewayProperties gatewayProperties;

  private SaTokenGatewayConfiguration configuration;

  @BeforeEach
  void setUp() {
    configuration = new SaTokenGatewayConfiguration(channelAwareSaRouter, gatewayProperties);
  }

  private Object invokeHandleError(Throwable e) throws Exception {
    Method method = SaTokenGatewayConfiguration.class.getDeclaredMethod("handleError", Throwable.class);
    method.setAccessible(true);
    return method.invoke(configuration, e);
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
  @DisplayName("initDefaultStpLogic 初始化")
  class InitDefaultStpLogic {

    @Test
    @DisplayName("initDefaultStpLogic() 调用 channelAwareSaRouter.configureDefaultStpLogic()")
    void invokesConfigureDefaultStpLogic() {
      configuration.initDefaultStpLogic();
      verify(channelAwareSaRouter).configureDefaultStpLogic();
    }

    @Test
    @DisplayName("initDefaultStpLogic() 不调用 gatewayProperties")
    void doesNotInvokeGatewayProperties() {
      configuration.initDefaultStpLogic();
      verifyNoInteractions(gatewayProperties);
    }
  }

  @Nested
  @DisplayName("handleError 异常处理")
  class HandleError {

    @Test
    @DisplayName("NotLoginException → 401 + COMMON.0002")
    void notLoginExceptionReturns401() throws Exception {
      NotLoginException exception = Mockito.mock(NotLoginException.class);

      try (var mockedSaHolder = Mockito.mockStatic(SaHolder.class)) {
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

      try (var mockedSaHolder = Mockito.mockStatic(SaHolder.class)) {
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

      try (var mockedSaHolder = Mockito.mockStatic(SaHolder.class)) {
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

      try (var mockedSaHolder = Mockito.mockStatic(SaHolder.class)) {
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
}
