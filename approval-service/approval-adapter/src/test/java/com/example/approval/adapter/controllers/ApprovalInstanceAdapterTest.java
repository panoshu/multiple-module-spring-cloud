package com.example.approval.adapter.controllers;

import com.example.approval.api.dto.PendingApprovalDTO;
import com.example.approval.api.request.ApproveRequest;
import com.example.approval.api.request.ListMyPendingApprovalsRequest;
import com.example.approval.api.request.RejectRequest;
import com.example.approval.api.request.TransferRequest;
import com.example.approval.api.request.WithdrawRequest;
import com.example.approval.application.service.ApprovalInstanceService;
import com.example.approval.types.ApprovalInstanceId;
import com.example.shared.exception.BusinessException;
import com.example.shared.page.Pagination;
import com.example.shared.permission.AccountIdResolver;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批次 56/57：审批查询与操作的身份越权治理。
 * 服务端必须以带签名的会话账号为准，忽略请求体传入的身份字段；会话缺失时 fail-closed。
 */
@DisplayName("审批会话越权防护（批次 56/57）")
class ApprovalInstanceAdapterTest {

  private ApprovalInstanceService service;
  private AccountIdResolver accountIdResolver;
  private ApprovalInstanceAdapter adapter;

  @BeforeEach
  void setUp() {
    service = mock(ApprovalInstanceService.class);
    accountIdResolver = mock(AccountIdResolver.class);
    adapter = new ApprovalInstanceAdapter(service, accountIdResolver);
    when(service.listMyPendingApprovals(any())).thenReturn(PageData.empty());
  }

  @Test
  @DisplayName("请求体传入他人 approver 被会话账号覆盖，分页参数保持不变")
  void listMyPending_overridesRequestBodyApproverWithSessionAccount() {
    when(accountIdResolver.resolve(isNull())).thenReturn("U-SESSION");
    ListMyPendingApprovalsRequest request =
      new ListMyPendingApprovalsRequest("U-ATTACKER", Pagination.of(5, 10));

    ApiResult<PageData<PendingApprovalDTO>> result = adapter.listMyPending(request);

    assertThat(result).isNotNull();
    ArgumentCaptor<ListMyPendingApprovalsRequest> captor =
      ArgumentCaptor.forClass(ListMyPendingApprovalsRequest.class);
    verify(service).listMyPendingApprovals(captor.capture());
    assertThat(captor.getValue().approver()).isEqualTo("U-SESSION");
    assertThat(captor.getValue().pagination()).isEqualTo(Pagination.of(5, 10));
  }

  @Test
  @DisplayName("会话账号缺失时 fail-closed，不调用应用服务")
  void listMyPending_failsClosedWhenSessionMissing() {
    when(accountIdResolver.resolve(isNull())).thenReturn(null);
    ListMyPendingApprovalsRequest request =
      new ListMyPendingApprovalsRequest("U-ATTACKER", Pagination.of(0, 10));

    assertThatThrownBy(() -> adapter.listMyPending(request))
      .isInstanceOf(BusinessException.class);

    verify(service, never()).listMyPendingApprovals(any());
  }

  @Test
  @DisplayName("会话账号为空串同样 fail-closed")
  void listMyPending_failsClosedWhenSessionAccountBlank() {
    when(accountIdResolver.resolve(isNull())).thenReturn("  ");
    ListMyPendingApprovalsRequest request =
      new ListMyPendingApprovalsRequest("U-ATTACKER", Pagination.of(0, 10));

    assertThatThrownBy(() -> adapter.listMyPending(request))
      .isInstanceOf(BusinessException.class);

    verify(service, never()).listMyPendingApprovals(any());
  }

  @Test
  @DisplayName("approve：请求体 approver 被会话账号覆盖，实例ID与意见保留")
  void approve_overridesRequestBodyApprover() {
    when(accountIdResolver.resolve(isNull())).thenReturn("U-SESSION");
    ApproveRequest request = new ApproveRequest(ApprovalInstanceId.of(1L), "U-ATTACKER", "同意");

    adapter.approve(request);

    ArgumentCaptor<ApproveRequest> captor = ArgumentCaptor.forClass(ApproveRequest.class);
    verify(service).approve(captor.capture());
    assertThat(captor.getValue().approver()).isEqualTo("U-SESSION");
    assertThat(captor.getValue().instanceId()).isEqualTo(ApprovalInstanceId.of(1L));
    assertThat(captor.getValue().comment()).isEqualTo("同意");
  }

  @Test
  @DisplayName("reject：请求体 approver 被会话账号覆盖，驳回原因保留")
  void reject_overridesRequestBodyApprover() {
    when(accountIdResolver.resolve(isNull())).thenReturn("U-SESSION");
    RejectRequest request = new RejectRequest(ApprovalInstanceId.of(1L), "U-ATTACKER", "资料不全");

    adapter.reject(request);

    ArgumentCaptor<RejectRequest> captor = ArgumentCaptor.forClass(RejectRequest.class);
    verify(service).reject(captor.capture());
    assertThat(captor.getValue().approver()).isEqualTo("U-SESSION");
    assertThat(captor.getValue().reason()).isEqualTo("资料不全");
  }

  @Test
  @DisplayName("transfer：当前审批人取会话账号，目标审批人作为业务参数保留")
  void transfer_overridesCurrentApproverKeepsTarget() {
    when(accountIdResolver.resolve(isNull())).thenReturn("U-SESSION");
    TransferRequest request = new TransferRequest(
      ApprovalInstanceId.of(1L), "U-ATTACKER", "U-TARGET", "休假转交");

    adapter.transfer(request);

    ArgumentCaptor<TransferRequest> captor = ArgumentCaptor.forClass(TransferRequest.class);
    verify(service).transfer(captor.capture());
    assertThat(captor.getValue().currentApprover()).isEqualTo("U-SESSION");
    assertThat(captor.getValue().targetApprover()).isEqualTo("U-TARGET");
    assertThat(captor.getValue().reason()).isEqualTo("休假转交");
  }

  @Test
  @DisplayName("withdraw：发起人取会话账号，撤回原因保留")
  void withdraw_overridesInitiator() {
    when(accountIdResolver.resolve(isNull())).thenReturn("U-SESSION");
    WithdrawRequest request = new WithdrawRequest(ApprovalInstanceId.of(1L), "U-ATTACKER", "操作有误");

    adapter.withdraw(request);

    ArgumentCaptor<WithdrawRequest> captor = ArgumentCaptor.forClass(WithdrawRequest.class);
    verify(service).withdraw(captor.capture());
    assertThat(captor.getValue().initiator()).isEqualTo("U-SESSION");
    assertThat(captor.getValue().reason()).isEqualTo("操作有误");
  }

  @Test
  @DisplayName("approve：会话缺失时 fail-closed，不执行审批操作")
  void approve_failsClosedWhenSessionMissing() {
    when(accountIdResolver.resolve(isNull())).thenReturn(null);
    ApproveRequest request = new ApproveRequest(ApprovalInstanceId.of(1L), "U-ATTACKER", "同意");

    assertThatThrownBy(() -> adapter.approve(request))
      .isInstanceOf(BusinessException.class);

    verify(service, never()).approve(any());
  }
}
