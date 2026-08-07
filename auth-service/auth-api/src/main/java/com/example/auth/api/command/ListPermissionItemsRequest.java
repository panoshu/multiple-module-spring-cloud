package com.example.auth.api.command;

/**
 * 查询权限项列表请求.
 *
 * @param category 权限类别（null 表示全部）
 */
public record ListPermissionItemsRequest(String category) {}
