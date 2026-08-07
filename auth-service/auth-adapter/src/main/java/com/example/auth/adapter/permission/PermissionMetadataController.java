package com.example.auth.adapter.permission;

import com.example.auth.api.PermissionMetadataApi;
import com.example.auth.api.annotation.PermissionCategory;
import com.example.auth.api.annotation.RequirePermission;
import com.example.auth.api.command.ListPermissionItemsRequest;
import com.example.auth.api.dto.PermissionGroupResponse;
import com.example.auth.api.dto.PermissionItemResponse;
import com.example.auth.adapter.converter.PermissionMetadataConverter;
import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.application.permission.PermissionMetadataApplicationService;
import com.pension.permission.domain.permission.aggregate.PermissionItem;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 权限元数据查询 Controller.
 *
 * <p>实现 {@link PermissionMetadataApi}，供前端渲染权限项列表。
 *
 * @author auth-adapter
 */
@RestController
@AllArgsConstructor
public class PermissionMetadataController implements PermissionMetadataApi {

  private final PermissionMetadataApplicationService service;
  private final PermissionMetadataConverter converter;

  @Override
  @RequirePermission(business = "PERMISSION_METADATA", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<List<PermissionItemResponse>> listItems(ListPermissionItemsRequest request) {
    com.pension.permission.domain.authorization.enumeration.PermissionCategory cat = resolveCategory(request.category());
    List<PermissionItem> items = service.listItems(cat);
    return ApiResult.success(converter.toResponseList(items));
  }

  @Override
  @RequirePermission(business = "PERMISSION_METADATA", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<List<PermissionGroupResponse>> listGroupedItems(ListPermissionItemsRequest request) {
    com.pension.permission.domain.authorization.enumeration.PermissionCategory cat = resolveCategory(request.category());
    Map<String, List<PermissionItem>> grouped = service.listGroupedItems(cat);
    return ApiResult.success(converter.toGroupedResponse(grouped));
  }

  private com.pension.permission.domain.authorization.enumeration.PermissionCategory resolveCategory(String category) {
    return category != null && !category.isBlank()
      ? com.pension.permission.domain.authorization.enumeration.PermissionCategory.valueOf(category)
      : null;
  }
}
