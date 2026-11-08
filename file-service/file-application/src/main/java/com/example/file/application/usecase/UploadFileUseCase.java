package com.example.file.application.usecase;

import com.example.file.application.command.ParseFileCommand;
import com.example.file.application.command.UploadFileCommand;
import com.example.file.application.result.UploadFileResult;
import com.example.file.domain.model.aggregate.root.ParseTask;
import com.example.file.domain.model.enums.ErrorPolicy;
import com.example.file.domain.repository.ParseTaskRepository;
import com.example.file.domain.model.aggregate.root.TemplateConfig;
import com.example.file.domain.repository.TemplateConfigRepository;
import com.example.file.types.BizType;
import com.example.file.types.FileTaskId;
import com.example.file.types.TemplateCode;
import com.example.shared.utils.concurrent.VirtualThreadExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 上传创建解析任务（2026-11-08 批次1 接线：自动触发异步解析）.
 *
 * <p>表单类上传（bizType 有活跃模板配置）在事务提交后由虚拟线程异步执行
 * {@link ParseFileUseCase}；材料类（无配置）仅建任务等待后续品类模型细化.
 * 解析结果经 FileParsedEvent 领域事件 → event-starter 通知消费方.</p>
 */
@Slf4j
@Service
public class UploadFileUseCase {

  private final com.example.shared.identifier.contract.IdService idService;
  private final ParseTaskRepository parseTaskRepository;
  private final TemplateConfigRepository configRepository;
  private final ParseFileUseCase parseFileUseCase;
  private final com.example.file.domain.service.TemplateRoutingService routingService;

  public UploadFileUseCase(com.example.shared.identifier.contract.IdService idService,
                          ParseTaskRepository parseTaskRepository,
                          TemplateConfigRepository configRepository,
                          ParseFileUseCase parseFileUseCase,
                          com.example.file.domain.service.TemplateRoutingService routingService) {
    this.idService = idService;
    this.parseTaskRepository = parseTaskRepository;
    this.configRepository = configRepository;
    this.parseFileUseCase = parseFileUseCase;
    this.routingService = routingService;
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

    // 品类判定（批次3 A1/A3）：显式 category 优先；空则按配置存在性兼容（存量行为）
    boolean isFormUpload;
    if (cmd.category() != null && !cmd.category().isBlank()) {
      isFormUpload = "FORM".equals(cmd.category());
    } else {
      isFormUpload = configRepository.findActive(BizType.of(cmd.bizType())).isPresent();
    }
    if (isFormUpload) {
      // 路由选择（批次3 A3）：上下文→模板配置；命中即绑定模板（解析阶段精确使用）
      TemplateConfig routed = routingService
        .route(configRepository.findAllActive(BizType.of(cmd.bizType())), cmd.routingContext())
        .orElse(null);
      if (routed != null) {
        task.bindTemplate(TemplateCode.of(routed.id().value()));
        parseTaskRepository.save(task);
      }
      scheduleAsyncParsing(taskId, cmd.uploader());
    }
    return new UploadFileResult(task.id().value(), task.status().name(), task.createdAt());
  }

  /**
   * 事务提交后异步触发解析（afterCommit 确保异步线程能读到已提交的 ParseTask）.
   */
  private void scheduleAsyncParsing(FileTaskId taskId, String operator) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        VirtualThreadExecutor.executeAsync(() -> {
          try {
            log.info("异步解析触发: fileTaskId={}", taskId.value());
            parseFileUseCase.execute(new ParseFileCommand(taskId.value(), operator));
          } catch (Exception e) {
            // ParseFileUseCase 内部吞异常并 markFailed（失败事件已发）——此处仅兜底日志
            log.error("异步解析触发失败: fileTaskId={}", taskId.value(), e);
          }
        });
      }
    });
  }
}
