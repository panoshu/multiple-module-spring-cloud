package com.example.file.application.usecase;

import com.example.file.application.result.FileTaskDetailResult;
import com.example.file.domain.model.parse.aggregate.ParseTask;
import com.example.file.domain.model.parse.valueobject.TaskError;
import com.example.file.domain.repository.ParseTaskRepository;
import com.example.file.domain.repository.SubTaskDataRepository;
import com.example.file.types.FileTaskId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class GetFileTaskUseCase {

  private final ParseTaskRepository parseTaskRepository;
  private final SubTaskDataRepository subTaskDataRepository;

  public GetFileTaskUseCase(ParseTaskRepository parseTaskRepository,
                            SubTaskDataRepository subTaskDataRepository) {
    this.parseTaskRepository = parseTaskRepository;
    this.subTaskDataRepository = subTaskDataRepository;
  }

  /** 按外部协作幂等键查询任务 ID（响应丢失对账，spec §11.4）。 */
  @Transactional(readOnly = true)
  public Optional<String> findIdByRequestNo(String requestNo) {
    if (requestNo == null || requestNo.isBlank()) {
      return Optional.empty();
    }
    return parseTaskRepository.findByRequestNo(requestNo).map(t -> t.id().value());
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
