package com.example.bff.intranet.api.dto;

import com.example.core.api.material.query.ListMaterialsQuery;
import jakarta.validation.constraints.NotBlank;

/**
 * 材料列表请求
 *
 * @author bff
 */
public record BffListMaterialsRequest(
  @NotBlank(message = "业务类型不能为空") String businessType,
  @NotBlank(message = "申请单ID不能为空") String applicationId
) {
  public ListMaterialsQuery toQuery() {
    return new ListMaterialsQuery(applicationId);
  }
}
