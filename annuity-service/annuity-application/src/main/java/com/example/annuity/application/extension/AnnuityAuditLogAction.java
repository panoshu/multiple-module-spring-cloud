package com.example.annuity.application.extension;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.engine.spi.StepExtensionAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 年金审计日志扩展动作(sideEffect)
 * <p>
 * 通过日志记录步骤执行轨迹,演示审计场景。生产环境可替换为写入审计表。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Slf4j
@Component("annuityAuditLogAction")
public class AnnuityAuditLogAction implements StepExtensionAction {

  @Override
  public String actionName() {
    return "annuityAuditLogAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    log.info("[审计] applicationId={}, step={}, operator={}",
      app.id().value(),
      app.currentStep(),
      app.operatorInfo() != null ? app.operatorInfo().operatorId().value() : "UNKNOWN");
    return ExtensionExecutionResult.success();
  }
}
