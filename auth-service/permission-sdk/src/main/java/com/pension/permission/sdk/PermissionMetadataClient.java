package com.pension.permission.sdk;

import com.pension.permission.sdk.dto.PermissionGroupDTO;
import com.pension.permission.sdk.dto.PermissionItemDTO;

import java.util.List;

/**
 * 业务服务用来查询权限点元数据的客户端接口（供权限配置页面使用）。
 * <p>沿用 PermissionClient 风格——纯 Java 接口，零依赖，
 * 实现可以是 HttpPermissionMetadataClient 或自定义实现。
 */
public interface PermissionMetadataClient {

  /**
   * 查询权限点列表（扁平）。
   * @param category 可选过滤类别：BUSINESS / PLATFORM，null 表示全部
   */
  List<PermissionItemDTO> listItems(String category);

  /**
   * 查询权限点列表（按 categoryGroup 分组）。
   */
  List<PermissionGroupDTO> listGroupedItems(String category);
}
