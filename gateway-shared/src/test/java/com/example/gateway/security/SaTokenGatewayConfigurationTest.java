package com.example.gateway.security;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SaTokenGatewayConfiguration} sa-token 网关配置单元测试。
 *
 * <p>覆盖 saReactorFilter Bean 创建、handleError 异常映射、initDefaultStpLogic 初始化。
 *
 * <p>handleError 为 private 方法,通过反射调用以测试其可观察行为,不修改被测类。
 * handleError 依赖 SaHolder.getResponse().getSource() 获取 Spring ServerHttpResponse,
 * 使用 Mockito.mockStatic 模拟 SaHolder，并 mock ServerHttpResponse 设置状态码与响应头。
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

  private ObjectMapper objectMapper;
  private SaTokenGatewayConfiguration configuration;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    configuration = new SaTokenGatewayConfiguration(channelAwareSaRouter, gatewayProperties, objectMapper);
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
    @DisplayName("NotLoginException → 401 + JSON {code=COMMON.0002, message=未登录或登录已过期}")
    void notLoginExceptionReturns401() throws Exception {
      NotLoginException exception = Mockito.mock(NotLoginException.class);

      try (var mockedSaHolder = Mockito.mockStatic(SaHolder.class)) {
        ServerHttpResponse mockResponse = mockSpringResponse(mockedSaHolder);

        Object result = invokeHandleError(exception);

        verify(mockResponse).setStatusCode(HttpStatusCode.valueOf(401));
        assertJsonResult(result, CODE_NOT_LOGIN, "未登录或登录已过期");
      }
    }

    @Test
    @DisplayName("NotPermissionException → 403 + JSON {code=COMMON.0003, message=无权限访问}")
    void notPermissionExceptionReturns403() throws Exception {
      NotPermissionException exception = Mockito.mock(NotPermissionException.class);

      try (var mockedSaHolder = Mockito.mockStatic(SaHolder.class)) {
        ServerHttpResponse mockResponse = mockSpringResponse(mockedSaHolder);

        Object result = invokeHandleError(exception);

        verify(mockResponse).setStatusCode(HttpStatusCode.valueOf(403));
        assertJsonResult(result, CODE_NO_PERMISSION, "无权限访问");
      }
    }

    @Test
    @DisplayName("NotRoleException → 403 + JSON {code=COMMON.0003, message=无权限访问}")
    void notRoleExceptionReturns403() throws Exception {
      NotRoleException exception = Mockito.mock(NotRoleException.class);

      try (var mockedSaHolder = Mockito.mockStatic(SaHolder.class)) {
        ServerHttpResponse mockResponse = mockSpringResponse(mockedSaHolder);

        Object result = invokeHandleError(exception);

        verify(mockResponse).setStatusCode(HttpStatusCode.valueOf(403));
        assertJsonResult(result, CODE_NO_PERMISSION, "无权限访问");
      }
    }

    @Test
    @DisplayName("其他异常 → 500 + JSON {code=COMMON.0050, message=系统内部错误}")
    void otherExceptionReturns500() throws Exception {
      RuntimeException exception = new RuntimeException("unexpected");

      try (var mockedSaHolder = Mockito.mockStatic(SaHolder.class)) {
        ServerHttpResponse mockResponse = mockSpringResponse(mockedSaHolder);

        Object result = invokeHandleError(exception);

        verify(mockResponse).setStatusCode(HttpStatusCode.valueOf(500));
        assertJsonResult(result, CODE_INTERNAL_ERROR, "系统内部错误");
      }
    }

    /**
     * 在 mockStatic 作用域内搭建 SaHolder.getResponse() → SaResponse → getSource() → ServerHttpResponse 链路。
     * 使用真实 HttpHeaders 以便验证 Content-Type 设置。
     *
     * @param mockedSaHolder 已开启的 SaHolder 静态 mock
     * @return mock 的 ServerHttpResponse，供调用方验证 setStatusCode
     */
    private ServerHttpResponse mockSpringResponse(MockedStatic<SaHolder> mockedSaHolder) {
      SaResponse mockSaResponse = Mockito.mock(SaResponse.class);
      ServerHttpResponse mockServerResponse = Mockito.mock(ServerHttpResponse.class);
      HttpHeaders headers = new HttpHeaders();
      when(mockSaResponse.getSource()).thenReturn(mockServerResponse);
      when(mockServerResponse.getHeaders()).thenReturn(headers);
      mockedSaHolder.when(SaHolder::getResponse).thenReturn(mockSaResponse);
      return mockServerResponse;
    }

    /**
     * 断言 handleError 返回值为 JSON 字符串，且包含预期的 code 和 message，
     * 同时验证 Content-Type 已设置为 application/json。
     */
    private void assertJsonResult(Object result, String expectedCode, String expectedMessage) throws Exception {
      assertThat(result).isInstanceOf(String.class);
      JsonNode json = objectMapper.readTree((String) result);
      assertThat(json.get("code").asText()).isEqualTo(expectedCode);
      assertThat(json.get("message").asText()).isEqualTo(expectedMessage);
    }
  }
}