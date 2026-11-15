package com.example.core.application.engine.service;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.StepTransition;
import com.example.core.domain.engine.aggregate.valueobject.config.StepExtensionConfig;
import com.example.core.domain.engine.gateway.ConditionEvaluationGateway;
import com.example.core.domain.engine.gateway.ProcessDefinitionStore;
import com.example.core.domain.engine.service.registry.ExtensionActionRegistry;
import com.example.core.domain.engine.service.registry.StepActionHandlerRegistry;
import com.example.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 流程定义发布服务（spec §7.1 发布约束）。
 * <p>
 * 统一发布入口：校验主处理器已注册、拒绝重复版本（已发布定义不可变），
 * 发布后由存储更新 latest 指针（缓存失效）。定义自身不变量（步骤编码唯一、
 * 初始步骤存在、后继步骤存在、外部步骤配置完整）由 ProcessDefinition 与
 * StepDefinition 的紧凑构造器保证；发布时经 ConditionEvaluationGateway 预校验
 * 全部步骤的条件表达式语法（spec §7.1，非法表达式按配置错误拒绝发布）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDefinitionPublishService {

  private final ProcessDefinitionStore store;
  private final StepActionHandlerRegistry handlerRegistry;
  private final ConditionEvaluationGateway conditionEvaluator;
  private final ExtensionActionRegistry extensionRegistry;

  public void publish(ProcessDefinition definition) {
    if (definition == null) {
      throw new DomainException(CoreDomainErrorCode.PROCESS_DEFINITION_INVALID)
        .withLogDetail("待发布流程定义不能为空");
    }
    validateHandlers(definition);
    validateExtensions(definition);
    validateConditions(definition);
    store.publish(definition);
    log.info("流程定义发布完成: businessType={}, version={}",
      definition.businessType().value(), definition.version().value());
  }

  private void validateHandlers(ProcessDefinition definition) {
    for (StepDefinition step : definition.steps()) {
      String processor = step.mainProcessor();
      if (StringUtils.hasText(processor) && !handlerRegistry.contains(processor)) {
        throw new DomainException(CoreDomainErrorCode.PROCESS_DEFINITION_INVALID)
          .withLogDetail("流程定义引用了未注册的主处理器: " + processor);
      }
    }
  }

  /** 发布期校验全部扩展动作的 beanName 已注册（批次 63），避免运行期 registry.get 抛异常。 */
  private void validateExtensions(ProcessDefinition definition) {
    for (StepDefinition step : definition.steps()) {
      validateExtensionGroup(step, "前置校验", step.preValidations());
      validateExtensionGroup(step, "明细处理", step.detailProcessors());
      validateExtensionGroup(step, "后置动作", step.sideEffects());
    }
  }

  private void validateExtensionGroup(StepDefinition step, String group,
                                     List<StepExtensionConfig> extensions) {
    for (StepExtensionConfig extension : extensions) {
      String beanName = extension.beanName();
      if (!StringUtils.hasText(beanName) || !extensionRegistry.contains(beanName)) {
        throw new DomainException(CoreDomainErrorCode.PROCESS_DEFINITION_INVALID)
          .withLogDetail("流程定义引用了未注册或空的扩展动作: step=%s, %s, beanName=%s"
            .formatted(step.stepCode().value(), group, beanName));
      }
    }
  }

  /** 发布期预校验全部步骤的后继条件与扩展动作条件语法（§7.1）。 */
  private void validateConditions(ProcessDefinition definition) {
    for (StepDefinition step : definition.steps()) {
      String stepCode = step.stepCode().value();
      for (StepTransition transition : step.successors()) {
        validateCondition(stepCode, "后继", transition.condition());
      }
      for (StepExtensionConfig extension : step.preValidations()) {
        validateCondition(stepCode, "前置校验", extension.condition());
      }
      for (StepExtensionConfig extension : step.detailProcessors()) {
        validateCondition(stepCode, "明细处理", extension.condition());
      }
      for (StepExtensionConfig extension : step.sideEffects()) {
        validateCondition(stepCode, "后置动作", extension.condition());
      }
    }
  }

  private void validateCondition(String stepCode, String location, String expression) {
    if (!StringUtils.hasText(expression)) {
      return;
    }
    try {
      conditionEvaluator.validate(expression);
    } catch (RuntimeException e) {
      throw new DomainException(CoreDomainErrorCode.PROCESS_DEFINITION_INVALID)
        .withLogDetail("流程定义条件表达式语法非法: step=%s, %s, expression=%s"
          .formatted(stepCode, location, expression));
    }
  }
}
