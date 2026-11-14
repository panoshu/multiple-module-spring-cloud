package com.example.approval.api.request;

import com.example.shared.page.Pagination;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 待审批列表请求
 *
 * @author approval-service
 */
public record ListMyPendingApprovalsRequest(
  /**
   * 兼容字段：服务端已忽略该值（批次 56，越权治理）。
   * <p>
   * 实际审批人由服务端从带签名的会话账号（{@code X-Account-Id}）解析，
   * 调用方无法通过本字段查询他人待审批列表。保留字段仅为兼容既有调用方
   * （含 annuity 链路测试与 BFF），不移除以避免破坏跨服务契约。
   */
  @NotBlank(message = "审批人不能为空")
  String approver,
  /**
   * 分页参数
   */
  @NotNull(message = "分页参数不能为空")
  Pagination pagination
) {
}
