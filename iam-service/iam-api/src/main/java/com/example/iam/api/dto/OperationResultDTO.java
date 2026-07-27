package com.example.iam.api.dto;

/**
 * 通用操作结果DTO
 *
 * <p>用于无具体返回数据的操作接口,统一返回成功标志和提示消息。
 *
 * @author iam-service
 */
public record OperationResultDTO(
    /**
     * 是否操作成功
     */
    boolean success,
    /**
     * 提示消息(可空)
     */
    String message
) {
}
