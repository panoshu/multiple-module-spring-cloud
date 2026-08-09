package com.example.bff.shared.route;

/**
 * BFF 路由配置条目（携带主键 ID）
 *
 * <p>用于管理接口列表查询，使前端能按 ID 进行 update/delete。
 *
 * @param id     路由配置主键 ID
 * @param config 路由配置
 * @author bff
 */
public record BffRouteConfigEntry(
  Long id,
  BffRouteConfig config
) {
}
