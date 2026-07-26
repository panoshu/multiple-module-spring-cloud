package com.example.core.api.application.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询申请单详情
 *
 * @author panoshu
 */
public record GetApplicationDetailQuery(
    @NotBlank(message = "申请单ID不能为空") String applicationId
) {
}
