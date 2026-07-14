package com.example.core.application.extension;

import com.example.core.domain.gateway.ConditionEvaluationGateway;
import com.example.core.domain.aggregateroot.BusinessApplication;
import com.example.core.domain.vauleobject.BusinessMetaContext;
import com.example.core.domain.vauleobject.ExtensionExecutionResult;
import com.example.core.domain.spi.StepExtensionAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 流式处理明细动作基类 (适合只读、计算、内存聚合、无更新或少更新的场景)
 * * 【警告】：
 * 底层使用 ResultSet 流式读取，会长期占用一个数据库连接。
 * 如果需要在流中批量 UPDATE 数据，【必须】确保 saveModifiedDetailsBatch 内部开启了全新的事务 (REQUIRES_NEW)，
 * 避免和流查询争抢同一个 Connection 导致驱动报错或死锁。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 07:58
 */
@Slf4j
public abstract class AbstractStreamingDetailAction<T> implements StepExtensionAction {

  private final ConditionEvaluationGateway conditionEvaluator;
  private final TransactionTemplate newTxTemplate;

  protected AbstractStreamingDetailAction(ConditionEvaluationGateway conditionEvaluator,
                                          PlatformTransactionManager transactionManager) {
    this.conditionEvaluator = conditionEvaluator;
    this.newTxTemplate = new TransactionTemplate(transactionManager);
    // 核心保护：强制开启新事务，避免和流式查询游标串用连接
    this.newTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    String detailCondition = Objects.toString(params.get("detailCondition"), null);
    int batchSize = getBatchSize();

    class ExecutionContext {
      final List<T> currentBatch = new ArrayList<>(batchSize);
      int totalScanned = 0, totalProcessed = 0, totalSkipped = 0, totalFailed = 0;
    }
    ExecutionContext ctx = new ExecutionContext();

    log.info("开始执行[流式查询]明细扩展动作 [{}], appId={}", actionName(), app.id());

    // 【重构核心】：不再直接调用 Mapper，而是调用抽象方法，将 Consumer 传进去
    streamDetails(app.id(), detail -> {
      ctx.totalScanned++;
      try {
        Map<String, Object> detailFacts = extractFacts(detail);

        // 1. 过滤逻辑
        if (StringUtils.hasText(detailCondition)) {
          if (!conditionEvaluator.evaluate(detailCondition, detailFacts)) {
            ctx.totalSkipped++;
            return; // 跳过当前记录
          }
        }

        // 2. 业务逻辑
        boolean isModified = doExecute(detail, detailFacts, params);
        if (isModified) {
          ctx.currentBatch.add(detail);
        }
        ctx.totalProcessed++;

      } catch (Exception e) {
        ctx.totalFailed++;
        log.error("动作 [{}] 流式处理异常", actionName(), e);
      }

      // 3. 凑齐一批则执行短事务落库
      if (ctx.currentBatch.size() >= batchSize) {
        List<T> batchToSave = new ArrayList<>(ctx.currentBatch);
        ctx.currentBatch.clear();
        newTxTemplate.executeWithoutResult(status -> saveModifiedDetailsBatch(batchToSave));
      }
    });

    // 处理尾部数据
    if (!ctx.currentBatch.isEmpty()) {
      newTxTemplate.executeWithoutResult(status -> saveModifiedDetailsBatch(ctx.currentBatch));
    }

    log.info("[流式查询]动作 [{}] 执行完毕，扫描: {}, 处理: {}, 跳过: {}, 失败: {}",
      actionName(), ctx.totalScanned, ctx.totalProcessed, ctx.totalSkipped, ctx.totalFailed);

    return ExtensionExecutionResult.success(ctx.totalScanned, ctx.totalProcessed, ctx.totalSkipped, ctx.totalFailed);
  }

  // ===================== 钩子与抽象方法 =====================

  protected int getBatchSize() {
    return 500;
  }

  /**
   * 【重构核心】：子类实现：如何流式获取数据并消费。
   * 应用层不关心 MyBatis 还是 JPA，只关心 "流出" 的领域对象。
   */
  protected abstract void streamDetails(Object appId, Consumer<T> consumer);

  protected abstract Map<String, Object> extractFacts(T detail);

  protected abstract boolean doExecute(T detail, Map<String, Object> detailFacts, Map<String, Object> params);

  protected abstract void saveModifiedDetailsBatch(List<T> modifiedDetails);
}
