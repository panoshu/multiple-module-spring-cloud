package com.example.core.api.form.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询表单状态
 *
 * @author panoshu
 */
public record GetFormStatusQuery(
    @NotBlank(message = "表单ID不能为空") String formId
) {
}
