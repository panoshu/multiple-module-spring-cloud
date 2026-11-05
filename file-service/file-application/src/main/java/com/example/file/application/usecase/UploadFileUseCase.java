package com.example.file.application.usecase;

import com.example.file.application.command.UploadFileCommand;
import com.example.file.application.result.UploadFileResult;
import com.example.file.domain.model.aggregate.root.ParseTask;
import com.example.file.domain.model.enums.ErrorPolicy;
import com.example.file.domain.repository.ParseTaskRepository;
import com.example.file.types.BizType;
import com.example.file.types.FileTaskId;
import com.example.file.types.TemplateCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UploadFileUseCase {

  private final com.example.shared.identifier.contract.IdService idService;
  private final ParseTaskRepository parseTaskRepository;

  public UploadFileUseCase(com.example.shared.identifier.contract.IdService idService,
                          ParseTaskRepository parseTaskRepository) {
    this.idService = idService;
    this.parseTaskRepository = parseTaskRepository;
  }

  @Transactional
  public UploadFileResult execute(UploadFileCommand cmd) {
    FileTaskId taskId = idService.nextId(FileTaskId.class);
    ParseTask task = ParseTask.create(
      taskId,
      BizType.of(cmd.bizType()),
      cmd.sourceFileName(),
      cmd.sourceFileId(),
      ErrorPolicy.COLLECT_ALL,
      List.of(),
      cmd.uploader()
    );
    if (cmd.templateCode() != null && !cmd.templateCode().isBlank()) {
      task.bindTemplate(TemplateCode.of(cmd.templateCode()));
    }
    parseTaskRepository.save(task);
    return new UploadFileResult(task.id().value(), task.status().name(), task.createdAt());
  }
}
