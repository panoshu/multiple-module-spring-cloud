package com.example.core.api.application.response;

/**
 * 推进申请单响应
 *
 * @author panoshu
 */
public record AdvanceStepResponse(
  String applicationId,
  String nextStep,
  String status
) {
}
