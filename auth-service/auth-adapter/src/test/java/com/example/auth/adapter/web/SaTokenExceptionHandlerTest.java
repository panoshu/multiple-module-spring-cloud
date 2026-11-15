package com.example.auth.adapter.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.example.shared.exception.CommonError;
import com.example.shared.web.core.api.ApiResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * S-04：sa-token 异常映射已从 shared-web-core 迁到 auth-adapter。
 */
@DisplayName("SaTokenExceptionHandler sa-token 异常映射")
class SaTokenExceptionHandlerTest {

  private final SaTokenExceptionHandler handler = new SaTokenExceptionHandler();
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/test");

  @Test
  @DisplayName("NotLoginException → 统一 200 + UNAUTHORIZED 业务码")
  void notLogin_mapsToUnauthorized() {
    ApiResult<Void> result = handler.handleNotLogin(mock(NotLoginException.class), request);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.code()).isEqualTo(CommonError.UNAUTHORIZED.getCode());
    assertThat(result.message()).isEqualTo(CommonError.UNAUTHORIZED.getMessage());
  }

  @Test
  @DisplayName("NotPermissionException → 统一 200 + FORBIDDEN 业务码")
  void notPermission_mapsToForbidden() {
    ApiResult<Void> result = handler.handleNotPermission(mock(NotPermissionException.class), request);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.code()).isEqualTo(CommonError.FORBIDDEN.getCode());
    assertThat(result.message()).isEqualTo(CommonError.FORBIDDEN.getMessage());
  }
}
