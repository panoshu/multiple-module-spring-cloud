package com.example.iam.adapter.controller;

import com.example.iam.api.dto.LoginLogDTO;
import com.example.iam.api.query.ListLoginLogsQuery;
import com.example.iam.application.service.LoginLogAppService;
import com.example.shared.exception.BusinessException;
import com.example.shared.exception.SystemException;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import com.example.shared.web.core.dto.PageQuery;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LoginLogController} 单元测试。
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
@DisplayName("LoginLogController 登录日志查询")
class LoginLogControllerTest {

  @Mock
  private LoginLogAppService loginLogAppService;

  @InjectMocks
  private LoginLogController controller;

  private static LoginLogDTO buildLogDTO() {
    return new LoginLogDTO(
        8001L, 1001L, "user01", "INTERNET",
        true, LocalDateTime.now(), "10.0.0.1",
        "Mozilla/5.0", List.of());
  }

  @Nested
  @DisplayName("list 查询登录日志列表")
  class ListQuery {

    @Test
    @DisplayName("成功路径:委托 LoginLogAppService 并以 ApiResult.success 包装返回分页结果")
    void success_delegatesAndWrapsAsApiResult() {
      ListLoginLogsQuery query = new ListLoginLogsQuery(
          1001L, "user01", "INTERNET",
          LocalDateTime.now().minusDays(1), LocalDateTime.now(),
          Boolean.TRUE, PageQuery.firstPage(10));
      PageData<LoginLogDTO> pageData = new PageData<>(
          1, 0, 1, false, List.of(buildLogDTO()));
      when(loginLogAppService.list(query)).thenReturn(pageData);

      ApiResult<PageData<LoginLogDTO>> apiResult = controller.list(query);

      assertThat(apiResult).isNotNull();
      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.message()).isEqualTo("success");
      assertThat(apiResult.data()).isSameAs(pageData);
      assertThat(apiResult.data().items()).hasSize(1);
      verify(loginLogAppService).list(query);
    }

    @Test
    @DisplayName("异常路径:Service 抛 SystemException 时透传(由 GlobalExceptionHandler 映射为 500)")
    void serviceThrowsSystemException_propagates() {
      ListLoginLogsQuery query = new ListLoginLogsQuery(
          null, null, null, null, null, null, PageQuery.firstPage(10));
      SystemException ex = new SystemException(
          com.example.shared.exception.CommonError.INTERNAL_SERVER_ERROR);
      when(loginLogAppService.list(query)).thenThrow(ex);

      assertThatThrownBy(() -> controller.list(query))
          .isSameAs(ex)
          .isInstanceOf(SystemException.class);
      verify(loginLogAppService).list(query);
    }
  }
}
