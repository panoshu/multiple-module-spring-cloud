package com.example.annuity.application.extension;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.dto.AnnuityEmployeeDTO;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.annuity.domain.service.AnnuityEmployeeMapper;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.gateway.ParsedSubTaskReferenceGateway;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import com.example.core.domain.engine.spi.StepExtensionAction;
import com.example.core.types.ApplicationId;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 年金员工明细摄入扩展动作（file-service canonical 数据分页消费）。
 *
 * <p><b>数据链路</b>：file-service 解析完成后以 {@code subTaskId} 作为标准数据凭证；
 * 本动作按 applicationId 读取 subTaskId，再通过
 * {@link FileIntegrationGateway#fetchParsedRows(String, String, int, int)} 分页拉取行数据。
 * 数据留在 file 域（数据不动原则），消费方低内存、天然支持万行级数据。</p>
 *
 * <p>每批使用 REQUIRES_NEW 短事务落库；单行映射失败隔离，不阻断整批。</p>
 *
 * @author annuity-service
 */
@Slf4j
@Component("annuityDetailIngestionAction")
public class AnnuityDetailIngestionAction implements StepExtensionAction {

  private static final String SYSTEM_USER = "SYSTEM";
  private static final int PAGE_SIZE = 100;

  private final FileIntegrationGateway fileGateway;
  private final ParsedSubTaskReferenceGateway parsedSubTaskGateway;
  private final ObjectMapper objectMapper;
  private final AnnuityEmployeeMapper employeeMapper;
  private final AnnuityEmployeeBatchRepository batchRepository;
  private final TransactionTemplate newTxTemplate;

  public AnnuityDetailIngestionAction(
    FileIntegrationGateway fileGateway,
    ParsedSubTaskReferenceGateway parsedSubTaskGateway,
    ObjectMapper objectMapper,
    PlatformTransactionManager txManager,
    AnnuityEmployeeMapper employeeMapper,
    AnnuityEmployeeBatchRepository batchRepository) {
    this.fileGateway = fileGateway;
    this.parsedSubTaskGateway = parsedSubTaskGateway;
    this.objectMapper = objectMapper;
    this.employeeMapper = employeeMapper;
    this.batchRepository = batchRepository;
    this.newTxTemplate = new TransactionTemplate(txManager);
    this.newTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public String actionName() {
    return "annuityDetailIngestionAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app,
                                          BusinessMetaContext context,
                                          Map<String, Object> params) {
    String subTaskId = parsedSubTaskGateway.load(app.id()).orElse(null);
    if (!StringUtils.hasText(subTaskId)) {
      return ExtensionExecutionResult.failure(
        "MISSING_SUBTASK", "申请单缺少解析子任务凭证");
    }

    log.info("开始分页摄入员工明细: appId={}, subTaskId={}", app.id().value(), subTaskId);
    int scanned = 0;
    int processed = 0;
    int skipped = 0;
    int failed = 0;
    int pageNo = 1;
    List<AnnuityEmployeeDetail> pending = new ArrayList<>(PAGE_SIZE);

    try {
      while (true) {
        FileIntegrationGateway.ParsedRowsPage page =
          fileGateway.fetchParsedRows(subTaskId, null, pageNo, PAGE_SIZE);
        if (page.rows() == null || page.rows().isEmpty()) {
          break;
        }
        for (Map<String, Object> row : page.rows()) {
          scanned++;
          try {
            AnnuityEmployeeDTO dto = objectMapper.convertValue(row, AnnuityEmployeeDTO.class);
            AnnuityEmployeeDetail detail = employeeMapper.mapToEntity(dto, app.id(), scanned);
            pending.add(detail);
            if (pending.size() >= PAGE_SIZE) {
              processed += flush(app.id(), pending);
            }
          } catch (Exception e) {
            failed++;
            log.warn("单行明细解析/映射失败（隔离跳过）: appId={}, rowIndex={}",
              app.id().value(), scanned, e);
          }
        }
        if (!page.hasMore()) {
          break;
        }
        pageNo++;
      }
      if (!pending.isEmpty()) {
        processed += flush(app.id(), pending);
      }
      log.info("员工明细分页摄入完成: appId={}, subTaskId={}, 扫描={}, 处理={}, 失败={}",
        app.id().value(), subTaskId, scanned, processed, failed);
      return ExtensionExecutionResult.success(scanned, processed, skipped, failed);
    } catch (Exception e) {
      log.error("员工明细摄入发生致命异常: appId={}, subTaskId={}", app.id().value(), subTaskId, e);
      return ExtensionExecutionResult.failure("INGEST_FATAL", e.getMessage());
    }
  }

  /**
   * 短事务落库；批次失败降级为逐条重试，保持与 JSON 流模式一致的隔离语义。
   */
  private int flush(ApplicationId appId, List<AnnuityEmployeeDetail> pending) {
    List<AnnuityEmployeeDetail> batch = new ArrayList<>(pending);
    pending.clear();
    try {
      newTxTemplate.executeWithoutResult(status -> saveBatch(appId, batch));
      return batch.size();
    } catch (Exception e) {
      log.warn("明细批次落库失败，降级逐条重试: appId={}, size={}", appId.value(), batch.size(), e);
      int succeeded = 0;
      for (AnnuityEmployeeDetail detail : batch) {
        try {
          newTxTemplate.executeWithoutResult(status -> saveBatch(appId, List.of(detail)));
          succeeded++;
        } catch (Exception single) {
          log.error("明细单条落库失败（隔离跳过）: appId={}, detailId={}",
            appId.value(), detail.id().value(), single);
        }
      }
      return succeeded;
    }
  }

  private void saveBatch(ApplicationId appId, List<AnnuityEmployeeDetail> details) {
    if (details.isEmpty()) {
      return;
    }
    AnnuityEmployeeBatch batch = batchRepository.findByApplicationId(appId)
      .orElseGet(() -> AnnuityEmployeeBatch.create(
        AnnuityEmployeeBatchId.of("B-" + appId.value()),
        appId, details.size(), SYSTEM_USER));
    details.forEach(batch::addDetail);
    batchRepository.save(batch);
  }
}
