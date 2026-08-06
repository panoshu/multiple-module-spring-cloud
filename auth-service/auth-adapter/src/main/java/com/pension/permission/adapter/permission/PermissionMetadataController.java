package com.pension.permission.adapter.permission;

import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.api.PermissionMetadataApi;
import com.pension.permission.api.dto.PermissionGroupResponse;
import com.pension.permission.api.dto.PermissionItemResponse;
import com.pension.permission.adapter.permission.converter.PermissionMetadataConverter;
import com.pension.permission.application.permission.PermissionMetadataApplicationService;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.permission.aggregate.PermissionItem;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@AllArgsConstructor
public class PermissionMetadataController implements PermissionMetadataApi {

  private final PermissionMetadataApplicationService service;
  private final PermissionMetadataConverter converter;

  @Override
  public ApiResult<List<PermissionItemResponse>> listItems(String category) {
    PermissionCategory cat = category != null ? PermissionCategory.valueOf(category) : null;
    List<PermissionItem> items = service.listItems(cat);
    return ApiResult.success(converter.toResponseList(items));
  }

  @Override
  public ApiResult<List<PermissionGroupResponse>> listGroupedItems(String category) {
    PermissionCategory cat = category != null ? PermissionCategory.valueOf(category) : null;
    Map<String, List<PermissionItem>> grouped = service.listGroupedItems(cat);
    return ApiResult.success(converter.toGroupedResponse(grouped));
  }
}
