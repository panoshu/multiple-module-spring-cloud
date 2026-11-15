package com.example.core.domain.engine.aggregate.valueobject;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.exception.DomainException;

/**
 * 业务类型编码。kernel 以开放编码标识业务类型，替代封闭的业务枚举。
 */
public record BusinessTypeCode(String value) implements ValueObject {

  public BusinessTypeCode {
    if (value == null || value.isBlank()) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("业务类型编码不能为空");
    }
  }

  public static BusinessTypeCode of(String value) {
    return new BusinessTypeCode(value);
  }
}
