package com.example.auth.api;

import com.example.auth.api.query.ListPermissionItemsRequest;
import com.example.auth.api.dto.PermissionGroupResponse;
import com.example.auth.api.dto.PermissionItemResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 权限元数据查询 API（供前端渲染权限项列表）.
 *
 * @author auth-api
 */
@HttpExchange("/permission-metadata")
public interface PermissionMetadataApi {

    @PostExchange("/items")
    ApiResult<List<PermissionItemResponse>> listItems(@RequestBody @Valid ListPermissionItemsRequest request);

    @PostExchange("/items/grouped")
    ApiResult<List<PermissionGroupResponse>> listGroupedItems(@RequestBody @Valid ListPermissionItemsRequest request);
}
