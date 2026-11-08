package com.example.core.api.material.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询材料列表
 *
 * @author panoshu
 */
public record ListMaterialsQuery(
  @NotBlank(message = "申请单ID不能为空") String applicationId
) {
}
