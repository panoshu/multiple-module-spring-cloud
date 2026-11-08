package com.example.file.domain.model.parse.valueobject;

import com.example.shared.exception.CommonError;
import com.example.shared.exception.DomainException;
import com.example.file.domain.model.enums.SubTaskStatus;
import com.example.file.types.SubTaskId;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

public record SubTaskSummary(
  SubTaskId subTaskId,
  String splitKeyValue,
  int totalRows,
  int validRows,
  int invalidRows,
  SubTaskStatus status
) implements ValueObject {
  public SubTaskSummary {
    if (subTaskId == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("SubTaskSummary.subTaskId null");
    }
    if (status == null) {
      status = SubTaskStatus.PENDING;
    }
  }
}
