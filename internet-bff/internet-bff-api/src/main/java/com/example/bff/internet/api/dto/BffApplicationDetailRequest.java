package com.example.bff.internet.api.dto;

import com.example.core.api.application.query.GetApplicationDetailQuery;
import jakarta.validation.constraints.NotBlank;

/**
 * 申请单详情请求
 *
 * @author bff
 */
public record BffApplicationDetailRequest(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "申请单ID不能为空") String applicationId
) {
    public GetApplicationDetailQuery toQuery() {
        return new GetApplicationDetailQuery(applicationId);
    }
}
