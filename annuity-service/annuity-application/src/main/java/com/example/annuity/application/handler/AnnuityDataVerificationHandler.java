package com.example.annuity.application.handler;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.errorcode.AnnuityDomainErrorCode;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.annuity.domain.service.AnnuityEmployeeVerificationRule;
import com.example.annuity.domain.service.AnnuityExtensionResolver;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.spi.StepActionHandler;
import com.example.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 年金数据核查主处理器
 * <p>
 * 编排逻辑:解析扩展字段 → 反查员工批次 → 逐条核查 → 判定批次状态 → 持久化。
 * 业务规则委托给 {@link AnnuityEmployeeVerificationRule} domain service。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Slf4j
@Component("annuityDataVerificationHandler")
@RequiredArgsConstructor
public class AnnuityDataVerificationHandler implements StepActionHandler {

  private final AnnuityExtensionResolver extensionResolver;
  private final AnnuityEmployeeVerificationRule verificationRule;
  private final AnnuityEmployeeBatchRepository batchRepository;

  @Override
  public String handlerName() {
    return "annuityDataVerificationHandler";
  }

  @Override
  public StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context) {
    log.info("开始执行年金数据核查, applicationId={}", app.id());

    AnnuityEmployeeBatch batch = batchRepository.findByApplicationId(app.id())
      .orElseThrow(() -> new BusinessException(AnnuityDomainErrorCode.EMPLOYEE_BATCH_NOT_FOUND)
        .withLogDetail("申请单未关联员工批次: " + app.id().value()));

    for (AnnuityEmployeeDetail detail : batch.pendingDetails()) {
      Optional<String> error = verificationRule.verify(detail);
      if (error.isPresent()) {
        batch.markDetailAnomaly(detail.id(), error.get(), app.updatedBy());
        log.warn("员工明细核查异常, detailId={}, reason={}", detail.id().value(), error.get());
      } else {
        batch.markDetailProcessed(detail.id(), app.updatedBy());
      }
    }

    if (batch.anomalyCount() > 0) {
      batch.fail("存在异常明细", app.updatedBy());
      batchRepository.save(batch);
      log.info("年金数据核查失败(存在异常明细), applicationId={}, anomalyCount={}",
        app.id(), batch.anomalyCount());
      return StepExecutionStatus.FAILED;
    }

    batch.complete(app.updatedBy());
    batchRepository.save(batch);
    log.info("年金数据核查完成, applicationId={}, processedCount={}, anomalyCount={}",
      app.id(), batch.processedCount(), batch.anomalyCount());
    return StepExecutionStatus.SUCCESS;
  }
}
