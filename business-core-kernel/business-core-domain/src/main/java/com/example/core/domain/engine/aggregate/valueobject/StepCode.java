package com.example.core.domain.engine.aggregate.valueobject;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.exception.DomainException;

/**
 * 步骤编码。kernel 以开放编码标识步骤，替代封闭的流程步骤枚举。
 */
public record StepCode(String value) implements ValueObject {

  public StepCode {
    if (value == null || value.isBlank()) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("步骤编码不能为空");
    }
  }

  public static StepCode of(String value) {
    return new StepCode(value);
  }
}
