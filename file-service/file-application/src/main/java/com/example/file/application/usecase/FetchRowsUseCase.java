package com.example.file.application.usecase;

import com.example.file.api.request.FetchRowsRequest;
import com.example.file.domain.model.valueobject.FetchPagination;
import com.example.file.domain.model.valueobject.PagedRows;
import com.example.file.domain.repository.SubTaskDataRepository;
import com.example.file.types.SubTaskId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分页拉取子任务解析数据（2026-11-08 批次2：数据消费闭环）.
 *
 * <p>设计契约（file-service 架构决策文档 §4 流程B）：业务方收到 FileParsedEvent
 * 后凭 subTaskId 凭证分页拉取 canonical 数据——数据留在 file 域（数据不动原则），
 * 消费方保持低内存占用并支持游标续传.</p>
 *
 * @author file-service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FetchRowsUseCase {

  private final SubTaskDataRepository subTaskRepository;

  @Transactional(readOnly = true)
  public PagedRows execute(FetchRowsRequest request) {
    String tableCode = resolveTableCode(request);
    FetchPagination pagination = FetchPagination.of(
      tableCode, (request.page() - 1) * request.size(), request.size());
    return subTaskRepository.findPagedRows(SubTaskId.of(request.subTaskId()), pagination);
  }

  /**
   * 未指定表码时取第一个表（单表场景宽容默认；多表场景应显式指定）.
   */
  private String resolveTableCode(FetchRowsRequest request) {
    if (request.tableCode() != null && !request.tableCode().isBlank()) {
      return request.tableCode();
    }
    return subTaskRepository.findById(SubTaskId.of(request.subTaskId()))
      .map(t -> t.tables().keySet().stream().findFirst().orElse("main"))
      .orElse("main");
  }
}
