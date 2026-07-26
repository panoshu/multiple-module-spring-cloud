package com.example.core.api.application.response;

/**
 * 提交申请单响应
 *
 * <p>{@code needApproval} 当前固定为 false,{@code approvalInstanceId} 设为 null,
 * 审批判断由管道 preValidation 中的 handler 完成,本响应字段保留供后续扩展。
 *
 * @author panoshu
 */
public record SubmitResponse(
    String applicationId,
    boolean needApproval,
    String approvalInstanceId
) {
}
