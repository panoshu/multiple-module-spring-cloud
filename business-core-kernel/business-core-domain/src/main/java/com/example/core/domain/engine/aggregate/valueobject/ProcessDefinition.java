package com.example.core.domain.engine.aggregate.valueobject;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.BatchMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InstanceGranularity;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.StartMode;
import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.exception.DomainException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 不可变流程定义。按业务类型与版本唯一标识，发布后不可修改，变更必须产生新版本。
 * <p>
 * 发布校验集中在紧凑构造器中：步骤非空、编码唯一、初始步骤存在、后继步骤存在。
 * Handler 注册表校验与条件表达式求值由运行时网关负责，不在领域模型内执行。
 */
public record ProcessDefinition(
  BusinessTypeCode businessType,
  DefinitionVersion version,
  StartMode startMode,
  InstanceGranularity instanceGranularity,
  BatchMode batchMode,
  StepCode initialStep,
  List<StepDefinition> steps
) implements ValueObject {

  public ProcessDefinition {
    if (businessType == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA).withLogDetail("业务类型不能为空");
    }
    if (version == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA).withLogDetail("定义版本不能为空");
    }
    if (startMode == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA).withLogDetail("启动方式不能为空");
    }
    if (instanceGranularity == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA).withLogDetail("实例粒度不能为空");
    }
    if (batchMode == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA).withLogDetail("批次模式不能为空");
    }
    if (initialStep == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA).withLogDetail("初始步骤不能为空");
    }

    steps = steps == null ? List.of() : List.copyOf(steps);
    if (steps.isEmpty()) {
      throw new DomainException(CoreDomainErrorCode.PROCESS_DEFINITION_INVALID)
        .withLogDetail("流程定义至少包含一个步骤");
    }

    Set<StepCode> codes = new HashSet<>();
    for (StepDefinition step : steps) {
      if (!codes.add(step.stepCode())) {
        throw new DomainException(CoreDomainErrorCode.STEP_CODE_DUPLICATED)
          .withLogDetail("流程步骤编码重复");
      }
    }
    if (!codes.contains(initialStep)) {
      throw new DomainException(CoreDomainErrorCode.INITIAL_STEP_NOT_FOUND)
        .withLogDetail("初始步骤不在流程定义步骤列表中");
    }
    for (StepDefinition step : steps) {
      for (StepTransition successor : step.successors()) {
        if (!codes.contains(successor.targetStep())) {
          throw new DomainException(CoreDomainErrorCode.SUCCESSOR_STEP_NOT_FOUND)
            .withLogDetail("后继步骤不在流程定义步骤列表中");
        }
      }
    }
  }

  public boolean requiresBatch() {
    return batchMode == BatchMode.REQUIRED;
  }

  public boolean hasStep(StepCode stepCode) {
    return stepOf(stepCode).isPresent();
  }

  public Optional<StepDefinition> stepOf(StepCode stepCode) {
    return steps.stream().filter(step -> step.stepCode().equals(stepCode)).findFirst();
  }

  public boolean isTerminal(StepCode stepCode) {
    return stepOf(stepCode).map(step -> step.successors().isEmpty()).orElse(false);
  }
}
