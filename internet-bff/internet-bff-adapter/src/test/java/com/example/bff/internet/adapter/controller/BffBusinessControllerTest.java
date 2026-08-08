package com.example.bff.internet.adapter.controller;

import com.example.bff.internet.api.BffBusinessApi;
import com.example.bff.internet.api.dto.*;
import com.example.bff.internet.application.service.BffAggregationService;
import com.example.bff.shared.registry.KernelApiRegistry;
import com.example.bff.shared.route.BusinessTypeRouter;
import com.example.core.api.application.BusinessApplicationApi;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.api.batch.BusinessBatchApi;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.form.BusinessFormApi;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.core.api.material.MaterialAppApi;
import com.example.core.api.material.response.MaterialItemResponse;
import com.example.shared.web.core.api.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BffBusinessController.class)
class BffBusinessControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BusinessTypeRouter router;
    @MockitoBean
    private KernelApiRegistry kernelApiRegistry;
    @MockitoBean
    private BffAggregationService aggregationService;
    @MockitoBean
    private BusinessBatchApi batchApi;
    @MockitoBean
    private BusinessFormApi formApi;
    @MockitoBean
    private BusinessApplicationApi applicationApi;
    @MockitoBean
    private MaterialAppApi materialApi;

    @Test
    @DisplayName("POST /bff/batch/create 路由到 kernel BusinessBatchApi.create")
    void createBatch_routesToBatchApi() throws Exception {
        when(router.resolveServiceName("ACC_PLAN_CREATE")).thenReturn("annuity-service");
        when(kernelApiRegistry.getBatchApi("annuity-service")).thenReturn(batchApi);
        when(batchApi.create(any())).thenReturn(ApiResult.success(
                new BatchCreatedResponse("batch-001", "CREATED", LocalDateTime.now())));

        BffCreateBatchRequest request = new BffCreateBatchRequest("ACC_PLAN_CREATE", "PLAN001", null);

        mockMvc.perform(post("/bff/batch/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON.0000"))
                .andExpect(jsonPath("$.data.batchId").value("batch-001"));
    }

    @Test
    @DisplayName("POST /bff/batch/detail 路由到 kernel BusinessBatchApi.detail")
    void batchDetail_routesToBatchApi() throws Exception {
        when(router.resolveServiceName("ACC_PLAN_CREATE")).thenReturn("annuity-service");
        when(kernelApiRegistry.getBatchApi("annuity-service")).thenReturn(batchApi);
        when(batchApi.detail(any())).thenReturn(ApiResult.success(
                new BatchDetailResponse("batch-001", "ACC_PLAN_CREATE", "PLAN001",
                        "C001", "客户A", "PROCESSING", 10, 5, 3, 2,
                        LocalDateTime.now(), LocalDateTime.now(), List.of())));

        BffBatchDetailRequest request = new BffBatchDetailRequest("ACC_PLAN_CREATE", "batch-001");

        mockMvc.perform(post("/bff/batch/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchId").value("batch-001"));
    }

    @Test
    @DisplayName("POST /bff/application/submit 路由到 kernel BusinessApplicationApi.submit")
    void submitApplication_routesToApplicationApi() throws Exception {
        when(router.resolveServiceName("ACC_PLAN_CREATE")).thenReturn("annuity-service");
        when(kernelApiRegistry.getApplicationApi("annuity-service")).thenReturn(applicationApi);
        when(applicationApi.submit(any())).thenReturn(ApiResult.success(
                new SubmitResponse("app-001", false, null)));

        BffSubmitRequest request = new BffSubmitRequest("ACC_PLAN_CREATE", "app-001");

        mockMvc.perform(post("/bff/application/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value("app-001"));
    }

    @Test
    @DisplayName("POST /bff/dashboard/batch-overview 路由到聚合服务")
    void batchOverview_routesToAggregationService() throws Exception {
        BatchOverviewResponse overview = new BatchOverviewResponse(
                new BatchDetailResponse("batch-001", "ACC_PLAN_CREATE", "PLAN001",
                        "C001", "客户A", "PROCESSING", 10, 5, 3, 2,
                        LocalDateTime.now(), LocalDateTime.now(), List.of()),
                null,
                List.of()
        );
        when(aggregationService.getBatchOverview(any())).thenReturn(ApiResult.success(overview));

        BffBatchOverviewRequest request = new BffBatchOverviewRequest("ACC_PLAN_CREATE", "batch-001");

        mockMvc.perform(post("/bff/dashboard/batch-overview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchDetail.batchId").value("batch-001"));
    }
}
