package com.example.auth.api.dto;

/**
 * 权限项元数据.
 *
 * @param businessCode  业务编码
 * @param actionCode    操作编码
 * @param category      权限类别
 * @param displayName   显示名称
 * @param description   描述
 * @param categoryGroup 分组名称
 * @param sortOrder     排序序号
 */
public record PermissionItemResponse(String businessCode, String actionCode, String category,
        String displayName, String description, String categoryGroup, int sortOrder) {}
