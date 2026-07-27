package com.example.iam.adapter.controller;

import com.example.iam.api.dto.PermissionSnapshotDTO;
import com.example.iam.api.query.ResolvePermissionsQuery;
import com.example.iam.application.service.PermissionResolverAppService;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.shared.exception.BusinessException;
import com.example.shared.web.core.api.ApiResult;
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
 * {@link PermissionResolverController} 单元测试。
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
@DisplayName("PermissionResolverController 权限解析")
class PermissionResolverControllerTest {

  private static final Long USER_ID = 1001L;

  @Mock
  private PermissionResolverAppService permissionResolverAppService;

  @InjectMocks
  private PermissionResolverController controller;

  private static PermissionSnapshotDTO buildSnapshot() {
    return new PermissionSnapshotDTO(
        USER_ID, "PLAN001",
        Set.of("business1.handle", "business2.query"),
        LocalDateTime.now());
  }

  @Nested
  @DisplayName("resolve 解析权限")
  class Resolve {

    @Test
    @DisplayName("成功路径:委托 PermissionResolverAppService 并以 ApiResult.success 包装返回")
    void success_delegatesAndWrapsAsApiResult() {
      ResolvePermissionsQuery query = new ResolvePermissionsQuery(USER_ID, "PLAN001");
      PermissionSnapshotDTO snapshot = buildSnapshot();
      when(permissionResolverAppService.resolve(query)).thenReturn(snapshot);

      ApiResult<PermissionSnapshotDTO> apiResult = controller.resolve(query);

      assertThat(apiResult).isNotNull();
      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.message()).isEqualTo("success");
      assertThat(apiResult.data()).isSameAs(snapshot);
      assertThat(apiResult.data().userId()).isEqualTo(USER_ID);
      assertThat(apiResult.data().permissions()).hasSize(2);
      verify(permissionResolverAppService).resolve(query);
    }

    @Test
    @DisplayName("异常路径:Service 抛 BusinessException(USER_NOT_FOUND)时透传")
    void serviceThrowsBusinessException_propagates() {
      ResolvePermissionsQuery query = new ResolvePermissionsQuery(USER_ID, "PLAN001");
      BusinessException ex = new BusinessException(IamAuthErrorCode.USER_NOT_FOUND)
          .withUserDetail("用户不存在");
      when(permissionResolverAppService.resolve(query)).thenThrow(ex);

      assertThatThrownBy(() -> controller.resolve(query))
          .isSameAs(ex)
          .isInstanceOf(BusinessException.class);
      verify(permissionResolverAppService).resolve(query);
    }
  }
}
