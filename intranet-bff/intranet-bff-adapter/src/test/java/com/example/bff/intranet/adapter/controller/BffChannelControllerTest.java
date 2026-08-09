package com.example.bff.intranet.adapter.controller;

import com.example.auth.api.command.DisableChannelRequest;
import com.example.auth.api.command.EnableChannelRequest;
import com.example.auth.api.command.ReplaceChannelsRequest;
import com.example.auth.api.dto.CustomerChannelEntitlementResponse;
import com.example.auth.api.query.GetEntitlementRequest;
import com.example.bff.intranet.application.service.ChannelManagementService;
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
 * 渠道开通管理 Controller 测试
 *
 * <p>覆盖 enable 与 get 方法，其余 2 个方法同模式。
 *
 * @author bff
 */
@WebMvcTest(BffChannelController.class)
class BffChannelControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChannelManagementService channelManagementService;

    @Test
    @DisplayName("POST /management/channels/enable 透明转发到 auth-service")
    void enable_forwardsToAuthService() throws Exception {
        when(channelManagementService.enable(any()))
                .thenReturn(ApiResult.success(new CustomerChannelEntitlementResponse(
                        "cust-001", List.of("INTRANET"), "ACTIVE")));

        EnableChannelRequest request = new EnableChannelRequest("cust-001", "INTRANET");

        mockMvc.perform(post("/management/channels/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON.0000"));
    }

    @Test
    @DisplayName("POST /management/channels/get 透明转发到 auth-service")
    void get_forwardsToAuthService() throws Exception {
        when(channelManagementService.get(any()))
                .thenReturn(ApiResult.success(new CustomerChannelEntitlementResponse(
                        "cust-001", List.of("INTRANET"), "ACTIVE")));

        GetEntitlementRequest request = new GetEntitlementRequest("cust-001");

        mockMvc.perform(post("/management/channels/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON.0000"))
                .andExpect(jsonPath("$.data.customerNo").value("cust-001"));
    }

    @Test
    @DisplayName("POST /management/channels/disable 透明转发到 auth-service")
    void disable_forwardsToAuthService() throws Exception {
        when(channelManagementService.disable(any())).thenReturn(ApiResult.success(null));

        DisableChannelRequest request = new DisableChannelRequest("cust-001", "INTRANET");

        mockMvc.perform(post("/management/channels/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON.0000"));
    }

    @Test
    @DisplayName("POST /management/channels/replace 透明转发到 auth-service")
    void replace_forwardsToAuthService() throws Exception {
        when(channelManagementService.replace(any()))
                .thenReturn(ApiResult.success(new CustomerChannelEntitlementResponse(
                        "cust-001", List.of("INTRANET", "INTERNET"), "ACTIVE")));

        ReplaceChannelsRequest request = new ReplaceChannelsRequest("cust-001", List.of("INTRANET", "INTERNET"));

        mockMvc.perform(post("/management/channels/replace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON.0000"));
    }
}
