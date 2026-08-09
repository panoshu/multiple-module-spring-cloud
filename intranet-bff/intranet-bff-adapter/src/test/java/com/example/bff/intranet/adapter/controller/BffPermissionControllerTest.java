package com.example.bff.intranet.adapter.controller;

import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.auth.api.query.ListPermissionItemsRequest;
import com.example.auth.api.query.PermissionCheckRequest;
import com.example.bff.intranet.application.service.PermissionManagementService;
import com.example.shared.web.core.api.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 权限管理 Controller 测试
 *
 * <p>展示 check 和 listItems 两个方法，其余 5 个方法同模式。
 *
 * @author bff
 */
@WebMvcTest(BffPermissionController.class)
class BffPermissionControllerTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private PermissionManagementService permissionManagementService;

  @Test
  @DisplayName("POST /management/permissions/check 透明转发到 auth-service")
  void check_forwardsToAuthService() throws Exception {
    when(permissionManagementService.check(any()))
      .thenReturn(ApiResult.success(new PermissionCheckResponse(true)));

    PermissionCheckRequest request = new PermissionCheckRequest(
      "user-001", null, "ACC_PLAN_CREATE", "PERM_CODE");

    mockMvc.perform(post("/management/permissions/check")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("COMMON.0000"));
  }

  @Test
  @DisplayName("POST /management/permissions/metadata/items 透明转发到 auth-service")
  void listItems_forwardsToAuthService() throws Exception {
    when(permissionManagementService.listItems(any()))
      .thenReturn(ApiResult.success(List.of()));

    ListPermissionItemsRequest request = new ListPermissionItemsRequest(null);

    mockMvc.perform(post("/management/permissions/metadata/items")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("COMMON.0000"));
  }
}
