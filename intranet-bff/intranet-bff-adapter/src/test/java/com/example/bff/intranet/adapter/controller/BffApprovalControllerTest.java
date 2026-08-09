package com.example.bff.intranet.adapter.controller;

import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.approval.api.dto.ApprovalNodeDTO;
import com.example.approval.api.dto.MatchRulesDTO;
import com.example.approval.api.request.CreateApprovalFlowRequest;
import com.example.approval.api.request.GetApprovalInstanceRequest;
import com.example.approval.api.response.ApprovalFlowIdResponse;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.ApprovalInstanceId;
import com.example.approval.types.NodeId;
import com.example.bff.intranet.application.service.ApprovalManagementService;
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

/**
 * 审批管理 Controller 测试
 *
 * <p>展示 createFlow 和 getInstance 两个方法的测试，其余 12 个方法同模式（service 委托 → 返回 ApiResult）。
 *
 * @author bff
 */
@WebMvcTest(BffApprovalController.class)
class BffApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ApprovalManagementService approvalManagementService;

    @Test
    @DisplayName("POST /management/approval/flows/create 透明转发到 approval-service")
    void createFlow_forwardsToApprovalService() throws Exception {
        when(approvalManagementService.createFlow(any()))
                .thenReturn(ApiResult.success(new ApprovalFlowIdResponse(ApprovalFlowId.of(1L))));

        CreateApprovalFlowRequest request = new CreateApprovalFlowRequest(
                "flow-1",
                "ACC_PLAN_CREATE",
                new MatchRulesDTO(List.of("AM01"), List.of("ACC_PLAN_CREATE"), null, null),
                List.of(new ApprovalNodeDTO(NodeId.of(1L), "node-1", "ROLE", "ADMIN", null, 1, true)),
                "user-001");

        mockMvc.perform(post("/management/approval/flows/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON.0000"));
    }

    @Test
    @DisplayName("POST /management/approval/instances/get 透明转发到 approval-service")
    void getInstance_forwardsToApprovalService() throws Exception {
        ApprovalInstanceDTO dto = new ApprovalInstanceDTO(
                ApprovalInstanceId.of(1L),
                ApprovalFlowId.of(1L),
                "BATCH-001",
                "ACC_PLAN_CREATE",
                "PENDING",
                "user-001",
                null,
                List.of(),
                LocalDateTime.now(),
                null);
        when(approvalManagementService.getInstance(any()))
                .thenReturn(ApiResult.success(dto));

        GetApprovalInstanceRequest request = new GetApprovalInstanceRequest(ApprovalInstanceId.of(1L));

        mockMvc.perform(post("/management/approval/instances/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON.0000"));
    }
}
