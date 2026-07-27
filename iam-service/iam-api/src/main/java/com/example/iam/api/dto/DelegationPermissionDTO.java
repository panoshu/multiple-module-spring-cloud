package com.example.iam.api.dto;

/**
 * 代办权限明细DTO
 *
 * <p>对应代办权限值对象(DelegationPermission)的展示视图,声明被授权方获得的某业务某动作。
 *
 * @author iam-service
 */
public record DelegationPermissionDTO(
    /**
     * 业务编码
     */
    String businessCode,
    /**
     * 业务动作(HANDLE/QUERY/AUDIT 等)
     */
    String action
) {
}
