package com.example.approval.adapter.controllers;

import com.example.approval.api.dto.PendingApprovalDTO;
import com.example.approval.api.request.ListMyPendingApprovalsRequest;
import com.example.approval.application.service.ApprovalInstanceService;
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
 * 批次 56：{@code listMyPending} 的 approver 越权治理。
 * 服务端必须以带签名的会话账号为准，忽略请求体传入的 approver；会话缺失时 fail-closed。
 */
@DisplayName("待审批列表会话越权防护（批次 56）")
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
}
