package com.example.core.api.application.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询申请单列表
 *
 * @author panoshu
 */
public record FindApplicationListQuery(
  @NotBlank(message = "批次ID不能为空") String batchId,
  String status
) {
}
