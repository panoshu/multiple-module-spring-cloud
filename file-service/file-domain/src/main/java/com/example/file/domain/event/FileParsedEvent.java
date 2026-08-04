package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.ParseTask;
import com.example.file.domain.model.enums.TaskStatus;
import com.example.file.domain.model.valueobject.SubTaskSummary;
import com.example.file.types.BizType;
import com.example.file.types.FileTaskId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;

import java.time.LocalDateTime;
import java.util.List;

public record FileParsedEvent(
  EventId eventId,
  LocalDateTime occurredOn,
  FileTaskId fileTaskId,
  BizType bizType,
  TaskStatus status,
  int totalSubTasks,
  List<SubTaskSummary> subTasks,
  String failureReason
) implements DomainEvent {

  public static FileParsedEvent of(ParseTask task) {
    return new FileParsedEvent(
      EventId.generate(),
      LocalDateTime.now(),
      task.id(),
      task.bizType(),
      task.status(),
      task.subTaskSummaries().size(),
      task.subTaskSummaries(),
      task.errors().isEmpty() ? null : task.errors().toString()
    );
  }
}
