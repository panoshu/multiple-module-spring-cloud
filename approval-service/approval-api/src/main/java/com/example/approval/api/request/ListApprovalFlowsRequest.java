package com.example.approval.api.request;

import com.example.shared.page.Pagination;
import jakarta.validation.constraints.NotNull;

/**
 * 列表查询审批流请求
 *
 * @author approval-service
 */
public record ListApprovalFlowsRequest(
  /**
   * 业务类型（可选）
   */
  String businessType,
  /**
   * 状态：ACTIVE-有效，DEPRECATED-已废弃（可选）
   */
  String status,
  /**
   * 分页参数
   */
  @NotNull(message = "分页参数不能为空")
  Pagination pagination
) {
}
