package com.example.file.api.request;

import jakarta.validation.constraints.NotBlank;

public record FetchRowsRequest(
  @NotBlank String subTaskId,
  /**
   * 目标数据表编码（SubTaskData.tables 的 key；单表场景可传主表码，
   * 多表场景必传——2026-11-08 批次2 补齐）.
   */
  String tableCode,
  int page,
  int size
) {
  public FetchRowsRequest {
    if (page <= 0) {
      page = 1;
    }
    if (size <= 0) {
      size = 20;
    }
    if (size > 500) {
      size = 500;
    }
  }
}
