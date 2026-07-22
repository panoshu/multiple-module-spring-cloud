package com.example.core.application.extension;

import com.example.core.domain.gateway.ConditionEvaluationGateway;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.spi.StepExtensionAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 游标分页明细扩展动作基类 (更新类批处理的【首选】)
 * * 【开发规范】：
 * 1. 查询规范：必须使用游标字段（如自增主键 id）进行排序，条件如 `id > lastId ORDER BY id ASC LIMIT batchSize`。
 * 2. 断点续传：强烈建议在明细表增加 `process_status` 字段。查询时只拉取未处理的数据，处理后落库状态，天然支持服务重启重试。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 07:46
 */
@Slf4j
public abstract class AbstractCursorPagingDetailAction<T> implements StepExtensionAction {

  private final ConditionEvaluationGateway conditionEvaluator;

  protected AbstractCursorPagingDetailAction(ConditionEvaluationGateway conditionEvaluator) {
    this.conditionEvaluator = conditionEvaluator;
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    String detailCondition = Objects.toString(params.get("detailCondition"), null);
    int batchSize = getBatchSize();
    Object cursor = getInitCursor();

    int totalScanned = 0, totalProcessed = 0, totalSkipped = 0, totalFailed = 0;

    log.info("开始执行[游标分页]明细扩展动作 [{}], appId={}", actionName(), app.id());

    while (true) {
      BatchData<T> batchData = loadDetailsBatch(app.id(), cursor, batchSize);
      List<T> details = batchData.details();

      if (details == null || details.isEmpty()) {
        break; // 没数据了，退出
      }

      List<T> modifiedDetails = new ArrayList<>(details.size());

      for (T detail : details) {
        totalScanned++;
        try {
          Map<String, Object> detailFacts = extractFacts(detail);

          // 1. SpEL 过滤
          if (StringUtils.hasText(detailCondition)) {
            if (!conditionEvaluator.evaluate(detailCondition, detailFacts)) {
              totalSkipped++;
              continue;
            }
          }

          // 2. 业务执行（核心：这里可以修改 detail 的状态，如 status=SUCCESS）
          boolean isModified = doExecute(detail, detailFacts, params);
          if (isModified) {
            modifiedDetails.add(detail);
          }
          totalProcessed++;

        } catch (Exception e) {
          totalFailed++;
          log.error("动作 [{}] 处理明细异常, detailId={}", actionName(), getDetailId(detail), e);
          // 业务同学可以在 doExecute 内部捕获异常并设置 detail.setStatus(FAILED)，然后返回 true 让其落库
        }
      }

      // 3. 批量更新修改过的数据
      if (!modifiedDetails.isEmpty()) {
        saveModifiedDetailsBatch(modifiedDetails);
      }

      // 4. 游标步进
      if (details.size() < batchSize) {
        break; // 最后一页
      }
      cursor = batchData.nextCursor();
    }

    log.info("[游标分页]动作 [{}] 执行完毕，扫描: {}, 处理: {}, 跳过: {}, 失败: {}",
      actionName(), totalScanned, totalProcessed, totalSkipped, totalFailed);

    return ExtensionExecutionResult.success(totalScanned, totalProcessed, totalSkipped, totalFailed);
  }

  // ===================== 钩子与抽象方法 =====================

  protected int getBatchSize() {
    return 500;
  }

  protected Object getInitCursor() {
    return null;
  }

  protected abstract BatchData<T> loadDetailsBatch(Object appId, Object cursor, int batchSize);

  protected abstract Map<String, Object> extractFacts(T detail);

  protected abstract boolean doExecute(T detail, Map<String, Object> detailFacts, Map<String, Object> params);

  protected abstract void saveModifiedDetailsBatch(List<T> modifiedDetails);

  protected abstract Object getDetailId(T detail);

  public record BatchData<T>(
    List<T> details,
    Object nextCursor
  ) {
  }
}
