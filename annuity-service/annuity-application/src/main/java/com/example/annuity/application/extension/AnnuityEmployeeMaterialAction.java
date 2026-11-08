package com.example.annuity.application.extension;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeMaterial;
import com.example.annuity.domain.errorcode.AnnuityDomainErrorCode;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.annuity.domain.service.AnnuityEmployeeMaterialRule;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.engine.spi.StepExtensionAction;
import com.example.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 年金员工级材料计算扩展动作(detailProcessor)
 * <p>
 * 委托 {@link AnnuityEmployeeMaterialRule} 为每个已核查明细计算材料清单。
 * kernel 的 {@code PlanMaterialPreparationHandler} 处理计划层材料,本 Action 处理明细层。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Slf4j
@Component("annuityEmployeeMaterialAction")
@RequiredArgsConstructor
public class AnnuityEmployeeMaterialAction implements StepExtensionAction {

  private final AnnuityEmployeeMaterialRule materialRule;
  private final AnnuityEmployeeBatchRepository batchRepository;

  @Override
  public String actionName() {
    return "annuityEmployeeMaterialAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    log.info("开始计算员工级材料清单, applicationId={}", app.id());

    AnnuityEmployeeBatch batch = batchRepository.findByApplicationId(app.id())
      .orElseThrow(() -> new BusinessException(AnnuityDomainErrorCode.EMPLOYEE_BATCH_NOT_FOUND)
        .withLogDetail("申请单未关联员工批次: " + app.id().value()));

    for (AnnuityEmployeeDetail detail : batch.verifiedDetails()) {
      List<AnnuityEmployeeMaterial> materials = materialRule.calculate(detail, context);
      detail.assignMaterials(materials, app.updatedBy());
    }

    batchRepository.save(batch);
    log.info("员工级材料清单计算完成, applicationId={}", app.id());
    return ExtensionExecutionResult.success();
  }
}
