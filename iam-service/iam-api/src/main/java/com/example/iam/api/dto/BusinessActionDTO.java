package com.example.iam.api.dto;

/**
 * 业务动作DTO
 *
 * <p>对应业务动作值对象(BusinessAction)的展示视图,声明某业务支持的动作及其描述。
 *
 * @author iam-service
 */
public record BusinessActionDTO(
    /**
     * 业务动作(HANDLE/QUERY/AUDIT)
     */
    String action,
    /**
     * 动作描述(可空)
     */
    String description
) {
}
