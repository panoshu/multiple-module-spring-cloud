package com.example.core.application.extension;

import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.engine.spi.StepExtensionAction;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.FileId;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 核心域基类：工业级 JSON 数据流式摄入与明细落库模板
 *
 * @param <D> 承接 JSON 数据的 DTO 类型 (强类型映射)
 * @param <T> 落库的领域实体类型
 * @author 架构组
 */
@Slf4j
public abstract class AbstractJsonStreamIngestionAction<D, T> implements StepExtensionAction {

  private final FileIntegrationGateway fileGateway;
  private final ObjectReader dtoReader; // 【优化】预热的 ObjectReader，极大提升循环内解析性能
  private final TransactionTemplate newTxTemplate;

  protected AbstractJsonStreamIngestionAction(
    FileIntegrationGateway fileGateway,
    ObjectMapper objectMapper,
    PlatformTransactionManager txManager,
    Class<D> dtoClass) {

    this.fileGateway = fileGateway;
    this.dtoReader = objectMapper.readerFor(dtoClass);
    this.newTxTemplate = new TransactionTemplate(txManager);
    // 核心保护：强制开启新事务，避免批次写入污染外层事务
    this.newTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    FileId jsonFileId = app.getParsedJsonFileId();
    if (jsonFileId == null) {
      return ExtensionExecutionResult.failure("MISSING_JSON", "申请单缺少解析后的 JSON 文件凭证");
    }

    log.info("开始流式摄入明细数据, action={}, jsonFileId={}", actionName(), jsonFileId);
    IngestionStats stats = new IngestionStats();

    // 资源管理：使用 try-with-resources 确保流与解析器安全关闭
    try (InputStream inputStream = fileGateway.downloadStream(jsonFileId);
         JsonParser jsonParser = dtoReader.getFactory().createParser(inputStream)) {

      // 委托给内部引擎处理
      processJsonStream(app.id(), jsonParser, stats, params);

    } catch (Exception e) {
      log.error("明细摄入发生致命异常, fileId={}", jsonFileId, e);
      return ExtensionExecutionResult.failure("INGEST_FATAL", e.getMessage());
    }

    log.info("摄入完成 [{}]: 扫描={}, 处理={}, 跳过={}, 失败={}",
      actionName(), stats.getTotalScanned(), stats.getTotalProcessed(),
      stats.getTotalSkipped(), stats.getTotalFailed());

    return ExtensionExecutionResult.success(
      stats.getTotalScanned(),
      stats.getTotalProcessed(),
      stats.getTotalSkipped(),
      stats.getTotalFailed()
    );
  }

  /**
   * 核心逻辑：流式解析、单行隔离与分批控制
   */
  private void processJsonStream(ApplicationId appId, JsonParser jsonParser, IngestionStats stats, Map<String, Object> params) throws Exception {
    // 【优化】严格的根节点防御性校验
    JsonToken firstToken = jsonParser.nextToken();
    if (firstToken != JsonToken.START_ARRAY) {
      throw new IllegalArgumentException("JSON 根节点必须为数组，当前为: " + firstToken);
    }

    int batchSize = getBatchSize();
    List<T> currentBatch = new ArrayList<>(batchSize);

    while (jsonParser.nextToken() == JsonToken.START_OBJECT) {
      stats.totalScanned++;

      try {
        // 【优化】单行数据异常隔离：一行脏数据决不能搞崩整个文件
        D dto = dtoReader.readValue(jsonParser);
        T entity = mapToEntity(appId, dto, params, stats.getTotalScanned());

        if (entity == null) {
          stats.totalSkipped++; // 业务层返回 null 视为逻辑跳过
        } else {
          currentBatch.add(entity);
        }

      } catch (Exception e) {
        stats.totalFailed++;
        log.error("单行明细解析或映射失败, appId={}, scannedIndex={}", appId, stats.totalScanned, e);
      }

      // 满批次处理
      if (currentBatch.size() >= batchSize) {
        flushBatch(currentBatch, stats);
      }
    }

    // 处理尾部不足一批的数据
    if (!currentBatch.isEmpty()) {
      flushBatch(currentBatch, stats);
    }
  }

  /**
   * 核心逻辑：批次落库与批次级异常隔离
   */
  private void flushBatch(List<T> batch, IngestionStats stats) {
    int size = batch.size();
    // 拷贝副本供事务使用，防止外部 currentBatch.clear() 产生引用问题
    List<T> batchToSave = new ArrayList<>(batch);
    batch.clear();

    try {
      // 短事务独立落库
      newTxTemplate.executeWithoutResult(_ -> saveBatch(batchToSave));
      stats.totalProcessed += size;

    } catch (Exception ex) {
      // 【优化】批次级异常隔离：某一批因为 DB 死锁或唯一键冲突失败，不影响其他批次
      stats.totalFailed += size;
      handleBatchError(batchToSave, ex, stats);
    }
  }

  /**
   * 批次落库失败时的降级补偿机制
   *
   */
  protected void handleBatchError(List<T> failedBatch, Exception ex, IngestionStats stats) {
    log.warn("批次落库失败，发生 {}, 触发降级单条重试机制。批次大小: {}", ex.getClass().getSimpleName(), failedBatch.size());

    // 把之前在这批里加上的 totalProcessed 扣除，因为大批次失败了
    stats.setTotalProcessed(stats.getTotalProcessed() - failedBatch.size());

    for (T entity : failedBatch) {
      try {
        // 降级：为每一条数据单独开启新事务进行落库！
        newTxTemplate.executeWithoutResult(_ -> saveBatch(List.of(entity)));

        // 如果单条插入成功，加回成功数
        stats.setTotalProcessed(stats.getTotalProcessed() + 1);

      } catch (Exception singleEx) {
        stats.setTotalFailed(stats.getTotalFailed() + 1);

        // 【终极追踪】：通过回调让子类提取该实体的溯源行号或唯一标识
        String entityId = extractTraceId(entity);
        log.error("【精准拦截】数据库落库脏数据！实体追踪ID(行号/身份证): {}, 错误原因: {}", entityId, singleEx.getMessage());

        // 可选：将这条绝对失败的 entity 写入专门的 dead_letter_log (死信表)
        // writeDeadLetterLog(entity, singleEx);
      }
    }
  }

  /**
   * 子类定义：批处理大小 (默认 500)
   */
  protected int getBatchSize() {
    return 500;
  }


  // ================= 留给业务子类实现的钩子 =================

  /**
   * 子类实现：将强类型的 DTO 映射为领域实体 (若返回 null 则视为跳过)
   */
  protected abstract T mapToEntity(ApplicationId appId, D dto, Map<String, Object> params, int rowIndex);

  /**
   * 子类实现：批量保存到数据库
   */
  protected abstract void saveBatch(List<T> details);

  /**
   * 留给子类的钩子：提取实体的溯源标识，用于精确打印日志
   */
  protected abstract String extractTraceId(T entity);

  /**
   * 内部统计对象，用于追踪摄入指标
   */
  @Data
  public static class IngestionStats {
    private int totalScanned = 0;
    private int totalProcessed = 0;
    private int totalSkipped = 0;
    private int totalFailed = 0;
  }
}
