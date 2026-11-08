package com.example.auth.api;

import com.example.auth.api.dto.PermissionResponse;
import com.example.auth.api.query.GetBusinessPermissionsRequest;
import com.example.auth.api.query.GetPlatformPermissionsRequest;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Set;

/**
 * 权限快照查询 API（供前端展示可见性，不参与后端鉴权）.
 *
 * @author auth-api
 */
@HttpExchange("/permission-cache")
public interface PermissionCacheApi {

  @PostExchange("/platform")
  ApiResult<Set<PermissionResponse>> getPlatformPermissions(@RequestBody @Valid GetPlatformPermissionsRequest request);

  @PostExchange("/business")
  ApiResult<Set<PermissionResponse>> getBusinessPermissions(@RequestBody @Valid GetBusinessPermissionsRequest request);
}
