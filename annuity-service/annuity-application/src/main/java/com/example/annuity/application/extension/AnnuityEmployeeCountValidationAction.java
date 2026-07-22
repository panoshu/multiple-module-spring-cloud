package com.example.annuity.application.extension;

import com.example.annuity.domain.errorcode.AnnuityDomainErrorCode;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.engine.spi.StepExtensionAction;
import com.example.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 年金员工明细数校验扩展动作
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Component("annuityEmployeeCountValidationAction")
@RequiredArgsConstructor
public class AnnuityEmployeeCountValidationAction implements StepExtensionAction {

  private final AnnuityEmployeeBatchRepository batchRepository;

  @Override
  public String actionName() {
    return "annuityEmployeeCountValidationAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    return batchRepository.findByApplicationId(app.id())
        .map(batch -> batch.details().isEmpty()
            ? ExtensionExecutionResult.failure("EMPLOYEE_LIST_EMPTY", "员工明细不能为空")
            : ExtensionExecutionResult.success())
        .orElseThrow(() -> new BusinessException(AnnuityDomainErrorCode.EMPLOYEE_BATCH_NOT_FOUND)
            .withLogDetail("申请单未关联员工批次: " + app.id().value()));
  }
}
