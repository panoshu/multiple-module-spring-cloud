package com.example.approval.api.request;

import jakarta.validation.constraints.NotBlank;

/** 按外部协作幂等键查询审批实例（spec §12.3 响应丢失对账）。 */
public record GetByRequestNoRequest(
  @NotBlank(message = "requestNo不能为空")
  String requestNo
) {
}
