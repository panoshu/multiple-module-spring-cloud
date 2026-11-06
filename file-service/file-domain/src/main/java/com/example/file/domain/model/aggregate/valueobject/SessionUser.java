package com.example.file.domain.model.aggregate.valueobject;

import com.example.shared.exception.CommonError;
import com.example.shared.exception.DomainException;
import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.core.types.CustomerNo;
import com.example.core.types.ProductNo;
import com.example.core.types.UserNo;

/**
 * 会话用户值对象（从 HTTP Header 提取）
 */
public record SessionUser(UserNo userNo, CustomerNo customerNo, ProductNo productNo) implements ValueObject {
  public SessionUser {
    if (userNo == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("userNo 不能为空");
    }
    if (customerNo == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("customerNo 不能为空");
    }
    if (productNo == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("productNo 不能为空");
    }
  }
}
