package com.example.bff.intranet.adapter.controller;

import com.example.approval.api.dto.*;
import com.example.approval.api.request.*;
import com.example.approval.api.response.ApprovalFlowIdResponse;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.ApprovalInstanceId;
import com.example.approval.types.NodeId;
import com.example.approval.types.RecordId;
import com.example.bff.intranet.application.service.ApprovalManagementService;
import com.example.shared.page.Pagination;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
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
 * <p>覆盖审批流（BffApprovalFlowApi）与审批实例（BffApprovalInstanceApi）两个拆分接口的转发。
 *
 * @author bff
 */
@WebMvcTest({BffApprovalFlowController.class, BffApprovalInstanceController.class})
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
  @DisplayName("POST /management/approval/flows/list 返回审批流分页")
  void listFlows_forwardsToApprovalService() throws Exception {
    ApprovalFlowDTO dto = new ApprovalFlowDTO(
      ApprovalFlowId.of(1L), "flow-1", "ACC_PLAN_CREATE", "ACTIVE", 1,
      new MatchRulesDTO(List.of("AM01"), List.of("ACC_PLAN_CREATE"), null, null),
      List.of(), "user-001", LocalDateTime.now(), null);
    when(approvalManagementService.listFlows(any()))
      .thenReturn(ApiResult.success(new PageData<>(1, 0, 1, false, List.of(dto))));

    ListApprovalFlowsRequest request = new ListApprovalFlowsRequest("ACC_PLAN_CREATE", null, new Pagination(0, 10));

    mockMvc.perform(post("/management/approval/flows/list")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("COMMON.0000"))
      .andExpect(jsonPath("$.data.totalCount").value(1));
  }

  @Test
  @DisplayName("POST /management/approval/flows/get 透明转发到 approval-service")
  void getFlow_forwardsToApprovalService() throws Exception {
    ApprovalFlowDTO dto = new ApprovalFlowDTO(
      ApprovalFlowId.of(2L), "flow-2", "ACC_PLAN_CREATE", "ACTIVE", 1,
      new MatchRulesDTO(List.of("AM01"), List.of("ACC_PLAN_CREATE"), null, null),
      List.of(), "user-001", LocalDateTime.now(), null);
    when(approvalManagementService.getFlow(any())).thenReturn(ApiResult.success(dto));

    GetApprovalFlowRequest request = new GetApprovalFlowRequest(ApprovalFlowId.of(2L));

    mockMvc.perform(post("/management/approval/flows/get")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("COMMON.0000"));
  }

  @Test
  @DisplayName("POST /management/approval/instances/approve 透明转发到 approval-service")
  void approveInstance_forwardsToApprovalService() throws Exception {
    when(approvalManagementService.approveInstance(any())).thenReturn(ApiResult.success(null));

    ApproveRequest request = new ApproveRequest(ApprovalInstanceId.of(1L), "user-001", "approved");

    mockMvc.perform(post("/management/approval/instances/approve")
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

  @Test
  @DisplayName("POST /management/approval/flows/update 透明转发到 approval-service")
  void updateFlow_forwardsToApprovalService() throws Exception {
    when(approvalManagementService.updateFlow(any())).thenReturn(ApiResult.success(null));

    UpdateApprovalFlowRequest request = new UpdateApprovalFlowRequest(
      ApprovalFlowId.of(1L),
      "flow-1",
      new MatchRulesDTO(List.of("AM01"), List.of("ACC_PLAN_CREATE"), null, null),
      List.of(new ApprovalNodeDTO(NodeId.of(1L), "node-1", "ROLE", "ADMIN", null, 1, true)),
      "user-001");

    mockMvc.perform(post("/management/approval/flows/update")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("COMMON.0000"));
  }

  @Test
  @DisplayName("POST /management/approval/flows/deprecate 透明转发到 approval-service")
  void deprecateFlow_forwardsToApprovalService() throws Exception {
    when(approvalManagementService.deprecateFlow(any())).thenReturn(ApiResult.success(null));

    DeprecateApprovalFlowRequest request = new DeprecateApprovalFlowRequest(ApprovalFlowId.of(1L), "user-001");

    mockMvc.perform(post("/management/approval/flows/deprecate")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("COMMON.0000"));
  }

  @Test
  @DisplayName("POST /management/approval/flows/match 透明转发到 approval-service")
  void matchFlow_forwardsToApprovalService() throws Exception {
    ApprovalFlowDTO dto = new ApprovalFlowDTO(
      ApprovalFlowId.of(3L), "flow-3", "ACC_PLAN_CREATE", "ACTIVE", 1,
      new MatchRulesDTO(List.of("AM01"), List.of("ACC_PLAN_CREATE"), null, null),
      List.of(), "user-001", LocalDateTime.now(), null);
    when(approvalManagementService.matchFlow(any())).thenReturn(ApiResult.success(dto));

    MatchApprovalFlowRequest request = new MatchApprovalFlowRequest("ACC_PLAN_CREATE", "AM01", 5000L);

    mockMvc.perform(post("/management/approval/flows/match")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("COMMON.0000"));
  }

  @Test
  @DisplayName("POST /management/approval/instances/start 透明转发到 approval-service")
  void startInstance_forwardsToApprovalService() throws Exception {
    when(approvalManagementService.startInstance(any()))
      .thenReturn(ApiResult.success(new ApprovalInstanceIdResponse(ApprovalInstanceId.of(1L))));

    StartApprovalRequest request = new StartApprovalRequest(
      ApprovalFlowId.of(1L), "BATCH-001", "ACC_PLAN_CREATE", "user-001");

    mockMvc.perform(post("/management/approval/instances/start")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("COMMON.0000"));
  }

  @Test
  @DisplayName("POST /management/approval/instances/reject 透明转发到 approval-service")
  void rejectInstance_forwardsToApprovalService() throws Exception {
    when(approvalManagementService.rejectInstance(any())).thenReturn(ApiResult.success(null));

    RejectRequest request = new RejectRequest(ApprovalInstanceId.of(1L), "user-001", "资料不全");

    mockMvc.perform(post("/management/approval/instances/reject")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("COMMON.0000"));
  }

  @Test
  @DisplayName("POST /management/approval/instances/transfer 透明转发到 approval-service")
  void transferInstance_forwardsToApprovalService() throws Exception {
    when(approvalManagementService.transferInstance(any())).thenReturn(ApiResult.success(null));

    TransferRequest request = new TransferRequest(ApprovalInstanceId.of(1L), "user-001", "user-002", "休假转交");

    mockMvc.perform(post("/management/approval/instances/transfer")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("COMMON.0000"));
  }

  @Test
  @DisplayName("POST /management/approval/instances/withdraw 透明转发到 approval-service")
  void withdrawInstance_forwardsToApprovalService() throws Exception {
    when(approvalManagementService.withdrawInstance(any())).thenReturn(ApiResult.success(null));

    WithdrawRequest request = new WithdrawRequest(ApprovalInstanceId.of(1L), "user-001", "操作有误");

    mockMvc.perform(post("/management/approval/instances/withdraw")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("COMMON.0000"));
  }

  @Test
  @DisplayName("POST /management/approval/instances/my-pending 返回待审批分页")
  void listMyPending_forwardsToApprovalService() throws Exception {
    PendingApprovalDTO dto = new PendingApprovalDTO(
      ApprovalInstanceId.of(1L), "BATCH-001", "ACC_PLAN_CREATE", "acc-plan-create-flow",
      "审批节点1", "user-001", LocalDateTime.now());
    when(approvalManagementService.listMyPending(any()))
      .thenReturn(ApiResult.success(new PageData<>(1, 0, 1, false, List.of(dto))));

    ListMyPendingApprovalsRequest request = new ListMyPendingApprovalsRequest("user-001", new Pagination(0, 10));

    mockMvc.perform(post("/management/approval/instances/my-pending")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("COMMON.0000"))
      .andExpect(jsonPath("$.data.totalCount").value(1));
  }

  @Test
  @DisplayName("POST /management/approval/instances/history 返回审批历史")
  void getHistory_forwardsToApprovalService() throws Exception {
    ApprovalRecordDTO record = new ApprovalRecordDTO(
      RecordId.of(1L), ApprovalInstanceId.of(1L), "审批节点1", "APPROVE",
      "user-001", "同意", LocalDateTime.now());
    when(approvalManagementService.getHistory(any()))
      .thenReturn(ApiResult.success(List.of(record)));

    GetApprovalHistoryRequest request = new GetApprovalHistoryRequest(ApprovalInstanceId.of(1L));

    mockMvc.perform(post("/management/approval/instances/history")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("COMMON.0000"));
  }
}
