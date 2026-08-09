package com.example.bff.intranet.adapter.controller;

import com.example.bff.intranet.api.dto.BffBusinessTypeResponse;
import com.example.bff.intranet.api.dto.BffSystemInfoResponse;
import com.example.bff.intranet.application.service.SystemManagementService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 系统配置管理 Controller 测试
 *
 * @author bff
 */
@WebMvcTest(BffSystemController.class)
class BffSystemControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SystemManagementService systemManagementService;

    @Test
    @DisplayName("POST /management/system/info 返回 BFF 系统信息")
    void getInfo_returnsSystemInfo() throws Exception {
        BffSystemInfoResponse response = new BffSystemInfoResponse(
                "INTRANET", "intranet-bff", "18091", "/intranet-bff");
        when(systemManagementService.getInfo()).thenReturn(ApiResult.success(response));

        mockMvc.perform(post("/management/system/info")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channelScope").value("INTRANET"))
                .andExpect(jsonPath("$.data.serviceName").value("intranet-bff"))
                .andExpect(jsonPath("$.data.port").value("18091"))
                .andExpect(jsonPath("$.data.contextPath").value("/intranet-bff"));
    }

    @Test
    @DisplayName("POST /management/system/business-types 返回业务类型列表")
    void listBusinessTypes_returnsList() throws Exception {
        List<BffBusinessTypeResponse> list = List.of(
                new BffBusinessTypeResponse("ACC_PLAN_CREATE", "annuity-service", "INTRANET"),
                new BffBusinessTypeResponse("LOAN_APPLY", "loan-service", "ALL")
        );
        when(systemManagementService.listBusinessTypes()).thenReturn(ApiResult.success(list));

        mockMvc.perform(post("/management/system/business-types")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].businessType").value("ACC_PLAN_CREATE"))
                .andExpect(jsonPath("$.data[1].serviceName").value("loan-service"));
    }
}
