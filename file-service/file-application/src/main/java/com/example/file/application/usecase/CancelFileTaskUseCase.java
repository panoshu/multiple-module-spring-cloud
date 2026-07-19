package com.example.file.application.usecase;

import com.example.file.domain.model.aggregate.root.ParseTask;
import com.example.file.domain.model.valueobject.TaskError;
import com.example.file.domain.repository.ParseTaskRepository;
import com.example.file.types.FileTaskId;
import com.example.shared.primitives.identity.UserNo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelFileTaskUseCase {

  private final ParseTaskRepository parseTaskRepository;

  public CancelFileTaskUseCase(ParseTaskRepository parseTaskRepository) {
    this.parseTaskRepository = parseTaskRepository;
  }

  @Transactional
  public void execute(String fileTaskId, String operator) {
    FileTaskId taskId = FileTaskId.of(fileTaskId);
    ParseTask task = parseTaskRepository.findById(taskId)
        .orElseThrow(() -> new IllegalArgumentException("Task not found: " + fileTaskId));
    task.markFailed(new TaskError("CANCELLED", "任务已取消", "操作人: " + operator));
    parseTaskRepository.save(task);
  }
}
