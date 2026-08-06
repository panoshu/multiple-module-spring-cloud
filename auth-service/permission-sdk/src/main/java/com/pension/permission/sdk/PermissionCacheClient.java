package com.pension.permission.sdk;

import com.pension.permission.sdk.dto.PermissionDTO;

import java.util.Set;

/**
 * 业务服务用来查询 SessionPermissionCache 的客户端接口。
 * <p>前端通过业务服务代理调用，获取当前用户的可见权限集合。
 */
public interface PermissionCacheClient {

  /**
   * 查询当前用户的平台管理权限集合（登录后拉取）。
   */
  Set<PermissionDTO> getPlatformPermissions(String accountId);

  /**
   * 查询当前用户在指定计划下的业务权限集合（选计划后拉取）。
   */
  Set<PermissionDTO> getBusinessPermissions(String accountId, String planId);
}
