package com.example.core.domain.aggregate.valueobject.enums.status;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/12 12:59
 */
public enum BatchStatus {
  CREATED, PROCESSING, PARTIAL_FAILED, FAILED, COMPLETED;

  public static BatchStatus determine(int failedCount, int totalCount) {

    if (failedCount < 0 || failedCount > totalCount || totalCount == 0) {
      throw new IllegalArgumentException(String.format("计数参数非法: failedCount=%d, totalCount=%d", failedCount, totalCount));
    }

    if (failedCount == totalCount) {
      return FAILED;
    }
    if (failedCount > 0) {
      return PARTIAL_FAILED;
    }
    return COMPLETED;
  }

  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED || this == PARTIAL_FAILED;
  }
}
