package com.example.file.application.usecase;

import com.example.file.application.result.FileTaskDetailResult;
import com.example.file.domain.model.aggregate.root.ParseTask;
import com.example.file.domain.model.valueobject.TaskError;
import com.example.file.domain.repository.ParseTaskRepository;
import com.example.file.domain.repository.SubTaskDataRepository;
import com.example.file.types.FileTaskId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetFileTaskUseCase {

  private final ParseTaskRepository parseTaskRepository;
  private final SubTaskDataRepository subTaskDataRepository;

  public GetFileTaskUseCase(ParseTaskRepository parseTaskRepository,
                             SubTaskDataRepository subTaskDataRepository) {
    this.parseTaskRepository = parseTaskRepository;
    this.subTaskDataRepository = subTaskDataRepository;
  }

  @Transactional(readOnly = true)
  public FileTaskDetailResult execute(String fileTaskId) {
    FileTaskId taskId = FileTaskId.of(fileTaskId);
    ParseTask task = parseTaskRepository.findById(taskId)
        .orElseThrow(() -> new IllegalArgumentException("Task not found: " + fileTaskId));

    var summaries = subTaskDataRepository.findSummariesByTask(taskId);
    List<FileTaskDetailResult.SubTaskSummaryItem> subTasks = summaries.stream()
        .map(s -> new FileTaskDetailResult.SubTaskSummaryItem(
            s.subTaskId().value(),
            s.splitKeyValue(),
            s.totalRows(),
            s.validRows(),
            s.invalidRows(),
            s.status().name()
        ))
        .toList();

    String errorMsg = task.errors().stream()
        .findFirst()
        .map(TaskError::message)
        .orElse(null);

    return new FileTaskDetailResult(
        task.id().value(),
        task.bizType().value(),
        task.templateCode() != null ? task.templateCode().value() : null,
        task.sourceFileName(),
        task.status().name(),
        task.subTaskCount(),
        task.totalRows(),
        task.validCount(),
        task.invalidCount(),
        errorMsg,
        task.createdAt(),
        task.finishedAt(),
        subTasks
    );
  }
}
