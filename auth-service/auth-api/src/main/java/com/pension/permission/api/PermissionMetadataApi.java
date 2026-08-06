package com.pension.permission.api;

import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.api.dto.PermissionGroupResponse;
import com.pension.permission.api.dto.PermissionItemResponse;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

@HttpExchange("/permission-metadata")
public interface PermissionMetadataApi {

  @GetExchange("/items")
  ApiResult<List<PermissionItemResponse>> listItems(@RequestParam(required = false) String category);

  @GetExchange("/items/grouped")
  ApiResult<List<PermissionGroupResponse>> listGroupedItems(@RequestParam(required = false) String category);
}
