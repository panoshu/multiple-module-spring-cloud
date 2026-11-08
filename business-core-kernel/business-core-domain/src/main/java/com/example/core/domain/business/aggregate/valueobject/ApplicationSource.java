package com.example.core.domain.business.aggregate.valueobject;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.types.BatchId;
import com.example.core.types.FormId;
import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.exception.DomainException;

/**
 * 申请来源。批次驱动业务必须先有批次和表单，再创建申请。
 */
public record ApplicationSource(
  FormId formId,
  BatchId batchId
) implements ValueObject {

  public ApplicationSource {
    if (formId == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("申请来源表单不能为空");
    }
    if (batchId == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("申请所属批次不能为空");
    }
  }
}
