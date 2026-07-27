package com.example.iam.api.dto;

/**
 * 通用ID响应DTO
 *
 * <p>创建资源后返回的通用响应对象,仅包含新建资源的ID。
 *
 * @author iam-service
 */
public record IdResponseDTO(
    /**
     * 资源ID
     */
    Long id
) {
}
