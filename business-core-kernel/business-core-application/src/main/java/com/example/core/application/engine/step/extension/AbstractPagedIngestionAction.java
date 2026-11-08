package com.example.core.application.engine.step.extension;

import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 分页数据摄取骨架（2026-11-08 批次2：数据消费闭环）.
 *
 * <p><b>与 {@link AbstractJsonStreamIngestionAction}（文件流模式）的差异</b>：
 * 文件流模式把解析结果物化为 JSON 文件后整流消费——适用于系统间文件搬运；
 * 本骨架按 {@code subTaskId} 凭证分页拉取 canonical 行数据（设计文档流程 B
 * 的标准模式）——数据留在 file 域（数据不动原则），消费方低内存、游标续传、
 * 天然支持万行级数据.</p>
 *
 * <p><b>使用方式</b>：业务线 step 的 detailProcessor 继承本类，实现
 * {@link #consumeRow}——每行回调（批量内自动聚合）；分页/游标/重试由骨架管理.</p>
 *
 * @author business-core-kernel
 */
@Slf4j
public abstract class AbstractPagedIngestionAction {

  protected static final int DEFAULT_PAGE_SIZE = 500;

  protected final FileIntegrationGateway fileGateway;

  protected AbstractPagedIngestionAction(FileIntegrationGateway fileGateway) {
    this.fileGateway = fileGateway;
  }

  /**
   * 摄取入口：分页拉取直到 hasMore=false.
   *
   * @param subTaskId 子任务凭证（FileParsedEvent 载荷）
   * @param tableCode 数据表编码（单表场景可 null）
   */
  public void ingest(String subTaskId, String tableCode, BusinessMetaContext context) {
    int page = 1;
    int totalPages;
    int consumed = 0;
    do {
      FileIntegrationGateway.ParsedRowsPage result =
        fileGateway.fetchParsedRows(subTaskId, tableCode, page, DEFAULT_PAGE_SIZE);
      if (result.totalCount() == 0) {
        log.info("分页摄取空结果: subTaskId={}", subTaskId);
        return;
      }
      for (Map<String, Object> row : result.rows()) {
        try {
          consumeRow(subTaskId, row, context);
          consumed++;
        } catch (Exception e) {
          // 单行失败隔离：记录后继续（与 JSON 流摄取的行级隔离语义一致）
          log.warn("行摄取失败（隔离跳过）: subTaskId={}, row={}", subTaskId, row, e);
          onRowFailure(subTaskId, row, context, e);
        }
      }
      totalPages = (result.totalCount() + DEFAULT_PAGE_SIZE - 1) / DEFAULT_PAGE_SIZE;
      onPageCompleted(page, totalPages, consumed);
      page++;
    } while (page <= totalPages);
    log.info("分页摄取完成: subTaskId={}, 共 {} 行", subTaskId, consumed);
  }

  /**
   * 每行回调——业务线实现（行消费/转换/落库）.
   */
  protected abstract void consumeRow(String subTaskId, Map<String, Object> row,
                                     BusinessMetaContext context);

  /**
   * 行失败钩子（默认仅日志；业务可覆盖做错误收集/计数）.
   */
  protected void onRowFailure(String subTaskId, Map<String, Object> row,
                              BusinessMetaContext context, Exception e) {
  }

  /**
   * 页完成钩子（进度上报/断点记录）.
   */
  protected void onPageCompleted(int page, int totalPages, int consumedSoFar) {
  }
}
