package com.example.auth.api;

import com.example.auth.api.dto.RoleUserBatchResponse;
import com.example.auth.api.query.ListRoleUsersRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 批次 58：用户服务「角色→用户」查询 API 契约。
 * 先编译红灯（3 个契约类尚不存在）→ 实现后转绿，锁定协议形状防退化。
 */
@DisplayName("角色用户查询 API 契约（批次 58）")
class RoleUserQueryApiContractTest {

  @Test
  @DisplayName("API 用 @HttpExchange，方法用 @PostExchange + @RequestBody")
  void api_usesHttpExchangeAndPostExchange() throws Exception {
    assertThat(RoleUserQueryApi.class.isAnnotationPresent(HttpExchange.class)).isTrue();
    Method method = RoleUserQueryApi.class.getMethod("listByRoles", ListRoleUsersRequest.class);
    assertThat(method.isAnnotationPresent(PostExchange.class)).isTrue();
    assertThat(method.getParameters()[0].isAnnotationPresent(RequestBody.class)).isTrue();
  }

  @Test
  @DisplayName("请求 CQE 的必填校验齐备")
  void request_hasRequiredValidation() throws Exception {
    assertThat(ListRoleUsersRequest.class.getDeclaredField("roleCodes")
      .isAnnotationPresent(NotEmpty.class)).isTrue();
    assertThat(ListRoleUsersRequest.class.getDeclaredField("planNo")
      .isAnnotationPresent(NotBlank.class)).isTrue();
  }

  @Test
  @DisplayName("响应按角色分组持有用户列表")
  void response_holdsUsersByRole() {
    RoleUserBatchResponse response =
      new RoleUserBatchResponse(Map.of("AGENT_REVIEW", List.of("U-1", "U-2")));

    assertThat(response.usersByRole().get("AGENT_REVIEW")).containsExactly("U-1", "U-2");
  }
}
