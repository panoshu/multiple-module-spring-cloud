package com.example.file.api.request;

import jakarta.validation.constraints.NotBlank;

/** 按外部协作幂等键查询解析任务（spec §11.4 响应丢失对账）。 */
public record GetByRequestNoRequest(
  @NotBlank(message = "requestNo不能为空")
  String requestNo
) {
}
