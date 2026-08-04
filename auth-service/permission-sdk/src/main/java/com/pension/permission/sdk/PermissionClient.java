package com.pension.permission.sdk;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务服务用来做"数据级鉴权"的统一入口——判断某个身份在某个计划上，
 * 能不能对某个业务做某个操作。所有实现最终都应该落到权限服务的
 * EffectivePermissionService(两层AND + DENY优先 + 实时角色模板解析)。
 * <p>
 * actionCode 传null代表"不区分具体操作，只看业务本身是否开通"。
 */
public interface PermissionClient {

  boolean checkPermission(String accountId, String planId, String businessCode, String actionCode);

  /**
   * 一次请求要判断多个权限点时用，避免对权限服务发起N次网络调用。
   * 默认实现只是逐个调用单点方法；真正做到"一次网络往返"的实现由具体客户端
   * (如HttpPermissionClient)覆盖这个方法。
   */
  default Map<PermissionCheckRequest, Boolean> checkPermissions(
    String accountId, String planId, List<PermissionCheckRequest> items) {
    Map<PermissionCheckRequest, Boolean> result = new LinkedHashMap<>();
    for (PermissionCheckRequest item : items) {
      result.put(item, checkPermission(accountId, planId, item.businessCode(), item.actionCode()));
    }
    return result;
  }
}
