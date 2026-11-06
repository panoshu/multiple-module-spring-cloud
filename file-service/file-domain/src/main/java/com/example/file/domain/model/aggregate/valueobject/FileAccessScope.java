package com.example.file.domain.model.aggregate.valueobject;

import com.example.shared.exception.CommonError;
import com.example.shared.exception.DomainException;
import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.core.types.CustomerNo;
import com.example.core.types.ProductNo;

/**
 * 文件访问范围值对象（企业 + 产品）
 */
public record FileAccessScope(CustomerNo customerNo, ProductNo productNo) implements ValueObject {
  public FileAccessScope {
    if (customerNo == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("customerNo 不能为空");
    }
    if (productNo == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("productNo 不能为空");
    }
  }
}
