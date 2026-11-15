package com.example.core.application.engine.callback;

import com.example.core.types.ApplicationId;

/**
 * 回调关联结果。
 *
 * @param classification 关联分类，决定监听器是否推进流程
 * @param applicationId  命中协作时携带的申请单 ID（仅 APPLIED 非空），供按权威 ID 推进
 */
public record CorrelationResult(
  CorrelationClassification classification,
  ApplicationId applicationId
) {
  public static CorrelationResult applied(ApplicationId applicationId) {
    return new CorrelationResult(CorrelationClassification.APPLIED, applicationId);
  }

  public static CorrelationResult noCorrelation() {
    return new CorrelationResult(CorrelationClassification.NO_CORRELATION, null);
  }

  public static CorrelationResult unmatched() {
    return new CorrelationResult(CorrelationClassification.UNMATCHED, null);
  }

  public static CorrelationResult outOfOrder() {
    return new CorrelationResult(CorrelationClassification.OUT_OF_ORDER, null);
  }

  public boolean isApplied() {
    return classification == CorrelationClassification.APPLIED;
  }
}
