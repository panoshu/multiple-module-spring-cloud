package com.example.core.domain.engine.aggregate.valueobject;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.ActionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.TaskType;
import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.exception.DomainException;

import java.util.List;

/**
 * 单个步骤定义，属于不可变流程定义的一部分。
 * <p>
 * 扩展链以处理器名称引用，kernel 不在领域模型中感知业务 Handler 实现；
 * 外部协作步骤必须声明外部配置引用，业务步骤必须声明主处理器。
 */
public record StepDefinition(
  StepCode stepCode,
  TaskType taskType,
  ActionType actionType,
  String mainProcessor,
  List<String> preValidations,
  List<String> detailProcessors,
  List<String> sideEffects,
  List<StepTransition> successors,
  RetryPolicy retryPolicy,
  String externalConfigRef
) implements ValueObject {

  public StepDefinition {
    if (stepCode == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("步骤编码不能为空");
    }
    if (taskType == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("任务类型不能为空");
    }
    if (actionType == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("动作类型不能为空");
    }

    preValidations = immutableCopy(preValidations);
    detailProcessors = immutableCopy(detailProcessors);
    sideEffects = immutableCopy(sideEffects);
    successors = successors == null ? List.of() : List.copyOf(successors);
    retryPolicy = retryPolicy == null ? RetryPolicy.defaults() : retryPolicy;

    if (actionType.requiresExternalConfig() && isBlank(externalConfigRef)) {
      throw new DomainException(CoreDomainErrorCode.STEP_CONFIG_INCOMPLETE)
        .withLogDetail("外部协作步骤必须配置外部配置引用");
    }
    if (actionType == ActionType.BUSINESS && isBlank(mainProcessor)) {
      throw new DomainException(CoreDomainErrorCode.STEP_CONFIG_INCOMPLETE)
        .withLogDetail("业务步骤必须配置主处理器");
    }
  }

  public static StepDefinition business(StepCode stepCode, TaskType taskType,
                                        String mainProcessor, List<StepTransition> successors) {
    return new StepDefinition(stepCode, taskType, ActionType.BUSINESS, mainProcessor,
      List.of(), List.of(), List.of(), successors, RetryPolicy.defaults(), null);
  }

  public static StepDefinition external(StepCode stepCode, ActionType actionType,
                                        String externalConfigRef, List<StepTransition> successors) {
    return new StepDefinition(stepCode, TaskType.SYSTEM_TASK, actionType, null,
      List.of(), List.of(), List.of(), successors, RetryPolicy.defaults(), externalConfigRef);
  }

  private static List<String> immutableCopy(List<String> source) {
    return source == null ? List.of() : List.copyOf(source);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
