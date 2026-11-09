package com.example.core.application.engine.service;

import com.example.core.application.engine.errorcode.CoreAppErrorCode;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessTypeCode;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.TaskType;
import com.example.core.domain.engine.gateway.ProcessDefinitionGateway;
import com.example.shared.exception.SystemException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 按流程实例固定的定义版本解析当前步骤，是运行时路由的唯一入口。
 * <p>
 * 定义升级不影响在途实例：始终使用 {@link BusinessApplication#definitionVersion()}。
 * 定义或步骤缺失时显式失败，不静默降级到旧配置。
 */
@Component
@RequiredArgsConstructor
public class StepDefinitionResolver {

  private final ProcessDefinitionGateway definitionGateway;

  public StepDefinition resolve(BusinessApplication app) {
    BusinessTypeCode businessType = BusinessTypeCode.of(app.businessContext().businessType().name());
    ProcessDefinition definition = definitionGateway.load(businessType, app.definitionVersion())
      .orElseThrow(() -> new SystemException(CoreAppErrorCode.PROCESS_DEFINITION_NOT_FOUND)
        .withLogDetail("未找到业务类型与版本对应的流程定义"));

    return definition.stepOf(StepCode.of(app.currentStep().name()))
      .orElseThrow(() -> new SystemException(CoreAppErrorCode.STEP_NOT_IN_DEFINITION)
        .withLogDetail("当前步骤不在流程定义中"));
  }

  public boolean isSystemTask(BusinessApplication app) {
    return resolve(app).taskType() == TaskType.SYSTEM_TASK;
  }
}
