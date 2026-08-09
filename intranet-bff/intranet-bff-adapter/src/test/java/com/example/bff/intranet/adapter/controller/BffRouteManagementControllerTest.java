package com.example.bff.intranet.adapter.controller;

import com.example.bff.intranet.api.dto.BffRouteConfigDeleteRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigGetRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigResponse;
import com.example.bff.intranet.api.dto.BffRouteConfigUpdateRequest;
import com.example.bff.intranet.application.service.RouteConfigManagementService;
import com.example.bff.shared.route.ChannelScope;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BffRouteManagementController.class)
class BffRouteManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RouteConfigManagementService routeConfigManagementService;

    @Test
    @DisplayName("POST /management/routes/create 调用 service.create")
    void create_callsService() throws Exception {
        when(routeConfigManagementService.create(any())).thenReturn(ApiResult.success(100L));

        BffRouteConfigRequest request = new BffRouteConfigRequest("TYPE_X", "svc-x", ChannelScope.ALL);

        mockMvc.perform(post("/management/routes/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(100));
    }

    @Test
    @DisplayName("POST /management/routes/get 调用 service.get")
    void get_callsService() throws Exception {
        BffRouteConfigResponse response = new BffRouteConfigResponse(1L, "TYPE_Y", "svc-y", ChannelScope.INTRANET);
        when(routeConfigManagementService.get(eq(1L))).thenReturn(ApiResult.success(response));

        BffRouteConfigGetRequest request = new BffRouteConfigGetRequest(1L);

        mockMvc.perform(post("/management/routes/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessType").value("TYPE_Y"));
    }

    @Test
    @DisplayName("POST /management/routes/list 调用 service.list")
    void list_callsService() throws Exception {
        when(routeConfigManagementService.list()).thenReturn(ApiResult.success(List.of()));

        mockMvc.perform(post("/management/routes/list")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON.0000"));
    }

    @Test
    @DisplayName("POST /management/routes/delete 调用 service.delete")
    void delete_callsService() throws Exception {
        when(routeConfigManagementService.delete(eq(5L))).thenReturn(ApiResult.success(null));

        BffRouteConfigDeleteRequest request = new BffRouteConfigDeleteRequest(5L);

        mockMvc.perform(post("/management/routes/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(routeConfigManagementService).delete(5L);
    }

    @Test
    @DisplayName("POST /management/routes/refresh-cache 调用 service.refreshCache")
    void refreshCache_callsService() throws Exception {
        when(routeConfigManagementService.refreshCache()).thenReturn(ApiResult.success(null));

        mockMvc.perform(post("/management/routes/refresh-cache")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(routeConfigManagementService).refreshCache();
    }

    @Test
    @DisplayName("POST /management/routes/update 调用 service.update")
    void update_callsService() throws Exception {
        when(routeConfigManagementService.update(eq(2L), any())).thenReturn(ApiResult.success(null));

        BffRouteConfigRequest config = new BffRouteConfigRequest("TYPE_Z", "svc-z", ChannelScope.ALL);
        BffRouteConfigUpdateRequest request = new BffRouteConfigUpdateRequest(2L, config);

        mockMvc.perform(post("/management/routes/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(routeConfigManagementService).update(eq(2L), any());
    }
}
