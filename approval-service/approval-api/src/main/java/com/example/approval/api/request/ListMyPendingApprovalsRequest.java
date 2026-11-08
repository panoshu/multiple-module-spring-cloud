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
   * 当前审批人
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
