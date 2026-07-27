package com.example.iam.api.dto;

import java.time.LocalDateTime;

/**
 * 登录失败记录DTO
 *
 * <p>对应登录失败记录实体(LoginFailureRecord)的展示视图,记录单次登录失败的原因与发生时间。
 *
 * @author iam-service
 */
public record LoginFailureRecordDTO(
    /**
     * 失败记录ID
     */
    Long recordId,
    /**
     * 失败原因代码(如 WRONG_PASSWORD/USER_NOT_FOUND)
     */
    String reason,
    /**
     * 人类可读详情(可空)
     */
    String detail,
    /**
     * 发生时间
     */
    LocalDateTime occurredAt
) {
}
