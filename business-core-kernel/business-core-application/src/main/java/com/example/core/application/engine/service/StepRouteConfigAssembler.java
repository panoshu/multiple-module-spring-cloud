package com.example.core.application.engine.service;

import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.StepTransition;
import com.example.core.domain.engine.aggregate.valueobject.config.StepRouteConfig;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 将版本化 {@link StepDefinition} 还原为管线可执行的 {@link StepRouteConfig}。
 * <p>
 * 存量流程步骤仍以 {@link ApplicationFlowStep} 枚举持久化，此处按名称映射；
 * 扩展链直接透传 {@code StepExtensionConfig}，保证 order/critical/async 等语义无损。
 */
@Component
public class StepRouteConfigAssembler {

  public StepRouteConfig toRouteConfig(StepDefinition definition) {
    List<StepTransition> successors = definition.successors();
    ApplicationFlowStep next = successors.isEmpty()
      ? null
      : ApplicationFlowStep.valueOf(successors.get(0).targetStep().value());

    return new StepRouteConfig(
      ApplicationFlowStep.valueOf(definition.stepCode().value()),
      next,
      StepRouteConfig.StepTaskType.valueOf(definition.taskType().name()),
      definition.preValidations(),
      definition.mainProcessor(),
      definition.detailProcessors(),
      definition.sideEffects()
    );
  }
}
