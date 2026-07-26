package com.example.core.api.form.response;

/**
 * 表单状态响应
 *
 * @author panoshu
 */
public record FormStatusResponse(
    String formId,
    String status,
    int parseProgress,
    int applicationCount,
    String errorMsg
) {
}
