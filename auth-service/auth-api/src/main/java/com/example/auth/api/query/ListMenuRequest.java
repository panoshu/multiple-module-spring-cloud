package com.example.auth.api.query;

/**
 * 管理端查询菜单列表请求.
 *
 * @param category 菜单类别（null 表示全部）
 */
public record ListMenuRequest(String category) {
}
