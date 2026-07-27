package com.example.iam.adapter.controller;

import com.example.iam.api.PermissionResolverApi;
import com.example.iam.api.dto.PermissionSnapshotDTO;
import com.example.iam.api.query.ResolvePermissionsQuery;
import com.example.iam.application.service.PermissionResolverAppService;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限解析 Controller
 *
 * <p>实现 {@link PermissionResolverApi} 接口,委托 {@link PermissionResolverAppService}
 * 完成权限快照的计算与预览。本类仅做请求转发,不包含业务逻辑与 DTO 转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PermissionResolverController implements PermissionResolverApi {

  private final PermissionResolverAppService permissionResolverAppService;

  @Override
  public ApiResult<PermissionSnapshotDTO> resolve(ResolvePermissionsQuery query) {
    log.info("解析权限: userId={}, planId={}", query.userId(), query.planId());
    return ApiResult.success(permissionResolverAppService.resolve(query));
  }
}
