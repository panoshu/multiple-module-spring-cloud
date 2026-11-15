package com.example.core.domain.business.aggregate.valueobject;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.types.BatchId;
import com.example.core.types.FormId;
import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.exception.DomainException;

/**
 * 申请来源。表单来源必填；批次引用是否必填由流程定义的 batchMode 在应用层决定。
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
  }
}
