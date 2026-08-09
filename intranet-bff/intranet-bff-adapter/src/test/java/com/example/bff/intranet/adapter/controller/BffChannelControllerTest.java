package com.example.bff.intranet.adapter.controller;

import com.example.auth.api.command.EnableChannelRequest;
import com.example.auth.api.dto.CustomerChannelEntitlementResponse;
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
 * <p>展示 enable 方法，其余 3 个方法同模式。
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
}
