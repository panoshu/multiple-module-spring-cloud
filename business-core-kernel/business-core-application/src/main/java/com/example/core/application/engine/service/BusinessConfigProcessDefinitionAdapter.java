package com.example.core.application.engine.service;

import com.example.core.domain.engine.aggregate.valueobject.BusinessTypeCode;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;
import com.example.core.domain.engine.aggregate.valueobject.RetryPolicy;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.StepTransition;
import com.example.core.domain.engine.aggregate.valueobject.config.StepRouteConfig;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.ActionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.BatchMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InstanceGranularity;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.StartMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.TaskType;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import com.example.core.domain.engine.gateway.ProcessDefinitionGateway;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 过渡期默认 {@link ProcessDefinitionGateway} 实现。
 * <p>
 * 把存量 {@link BusinessConfigGateway#getNextStep} 的步骤路由适配为版本化、不可变的
 * {@link ProcessDefinition}，使运行时路由由定义统一接管，而不新增一套旁路配置。
 * 定义不可变，按「业务类型 + 版本」缓存；业务服务可提供自定义实现覆盖本默认实现，
 * 或迁移到独立的版本化定义存储后停用本适配器。
 * <p>
 * 存量业务均为表单驱动、批次驱动，故默认声明 {@code FORM_DRIVEN / PER_SUB_TASK / REQUIRED}；
 * 自定义业务应通过自定义 {@link ProcessDefinitionGateway} 声明真实模式。
 */
public class BusinessConfigProcessDefinitionAdapter implements ProcessDefinitionGateway {

  private final BusinessConfigGateway configGateway;
  private final Map<String, ProcessDefinition> definitions = new ConcurrentHashMap<>();

  public BusinessConfigProcessDefinitionAdapter(BusinessConfigGateway configGateway) {
    this.configGateway = configGateway;
  }

  @Override
  public Optional<ProcessDefinition> load(BusinessTypeCode businessType, DefinitionVersion version) {
    // 过渡期仅存在初始版本；在途实例携带其他版本时不静默回退，交由对账/迁移处理
    if (!DefinitionVersion.initial().equals(version)) {
      return Optional.empty();
    }
    String cacheKey = businessType.value() + ":" + version.value();
    return Optional.of(definitions.computeIfAbsent(cacheKey, key -> build(businessType, version)));
  }

  @Override
  public Optional<ProcessDefinition> loadLatest(BusinessTypeCode businessType) {
    return load(businessType, DefinitionVersion.initial());
  }

  private ProcessDefinition build(BusinessTypeCode businessType, DefinitionVersion version) {
    // 覆盖全部存量步骤枚举，保证任何已配置后继都能在定义内找到目标步骤
    List<StepDefinition> steps = Arrays.stream(ApplicationFlowStep.values())
      .map(this::toStepDefinition)
      .toList();
    return new ProcessDefinition(
      businessType, version,
      StartMode.FORM_DRIVEN, InstanceGranularity.PER_SUB_TASK, BatchMode.REQUIRED,
      StepCode.of(ApplicationFlowStep.DRAFT.name()), steps
    );
  }

  private StepDefinition toStepDefinition(ApplicationFlowStep step) {
    StepRouteConfig route = configGateway.getNextStep(null, step);
    if (route == null) {
      return new StepDefinition(
        StepCode.of(step.name()), TaskType.SYSTEM_TASK, ActionType.NONE, null,
        List.of(), List.of(), List.of(), List.of(), RetryPolicy.defaults(), null);
    }

    List<StepTransition> successors = route.nextStep() == null
      ? List.of()
      : List.of(new StepTransition(StepCode.of(route.nextStep().name()), null));
    TaskType taskType = route.taskType() == null
      ? TaskType.SYSTEM_TASK
      : TaskType.valueOf(route.taskType().name());
    ActionType actionType = hasText(route.mainProcessor()) ? ActionType.BUSINESS : ActionType.NONE;

    return new StepDefinition(
      StepCode.of(step.name()), taskType, actionType, route.mainProcessor(),
      route.preValidations(), route.detailProcessors(), route.sideEffects(),
      successors, RetryPolicy.defaults(), null);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
