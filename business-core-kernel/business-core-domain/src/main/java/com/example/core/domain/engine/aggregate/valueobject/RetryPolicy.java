package com.example.core.domain.engine.aggregate.valueobject;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.exception.DomainException;

import java.time.Duration;

/**
 * 步骤超时与重试策略。timeout 为空表示不设超时。
 */
public record RetryPolicy(int maxAttempts, Duration timeout) implements ValueObject {

  public RetryPolicy {
    if (maxAttempts < 1) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("最大尝试次数必须大于零");
    }
  }

  public static RetryPolicy defaults() {
    return new RetryPolicy(1, null);
  }
}
