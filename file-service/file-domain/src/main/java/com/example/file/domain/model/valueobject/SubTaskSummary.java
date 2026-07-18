package com.example.file.domain.model.valueobject;

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
    if (subTaskId == null) throw new IllegalArgumentException("SubTaskSummary.subTaskId null");
    if (status == null) status = SubTaskStatus.PENDING;
  }
}
