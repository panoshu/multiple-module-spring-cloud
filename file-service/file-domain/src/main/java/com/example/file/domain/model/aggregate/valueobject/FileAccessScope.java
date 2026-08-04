package com.example.file.domain.model.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.ProductNo;

/**
 * 文件访问范围值对象（企业 + 产品）
 */
public record FileAccessScope(CustomerNo customerNo, ProductNo productNo) implements ValueObject {
  public FileAccessScope {
    if (customerNo == null) throw new IllegalArgumentException("customerNo 不能为空");
    if (productNo == null) throw new IllegalArgumentException("productNo 不能为空");
  }
}
