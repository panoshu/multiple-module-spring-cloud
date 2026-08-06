package com.pension.permission.api;

import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.api.dto.PermissionResponse;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.Set;

@HttpExchange("/permission-cache")
public interface PermissionCacheApi {

  @GetExchange("/platform")
  ApiResult<Set<PermissionResponse>> getPlatformPermissions(@RequestParam String accountId);

  @GetExchange("/business")
  ApiResult<Set<PermissionResponse>> getBusinessPermissions(@RequestParam String accountId,
                                                              @RequestParam String planId);
}
