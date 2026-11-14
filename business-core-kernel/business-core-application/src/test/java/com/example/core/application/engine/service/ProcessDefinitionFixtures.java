package com.example.core.application.engine.service;

import com.example.core.domain.engine.aggregate.valueobject.BusinessTypeCode;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.RetryPolicy;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.config.StepExtensionConfig;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.ActionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.BatchMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InstanceGranularity;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.StartMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.TaskType;

import java.util.List;
import java.util.Map;

final class ProcessDefinitionFixtures {

  static final BusinessTypeCode TYPE = BusinessTypeCode.of("ANNUITY");
  static final String HANDLER = "ingestionHandler";

  private ProcessDefinitionFixtures() {
  }

  static ProcessDefinition definition(String version) {
    StepDefinition step = StepDefinition.business(
      StepCode.of("FORM_DETAIL_INGESTION"), TaskType.SYSTEM_TASK, HANDLER, List.of());
    return new ProcessDefinition(
      TYPE, DefinitionVersion.of(version),
      StartMode.FORM_DRIVEN, InstanceGranularity.PER_SUB_TASK, BatchMode.REQUIRED,
      StepCode.of("FORM_DETAIL_INGESTION"), List.of(step));
  }

  static ProcessDefinition definitionWithCondition(String version, String condition) {
    StepExtensionConfig validation = new StepExtensionConfig(
      "validationAction", 1, "VALIDATION", true, false, condition, Map.of());
    StepDefinition step = new StepDefinition(
      StepCode.of("FORM_DETAIL_INGESTION"), TaskType.SYSTEM_TASK, ActionType.BUSINESS,
      HANDLER, List.of(validation), List.of(), List.of(), List.of(),
      RetryPolicy.defaults(), null);
    return new ProcessDefinition(
      TYPE, DefinitionVersion.of(version),
      StartMode.FORM_DRIVEN, InstanceGranularity.PER_SUB_TASK, BatchMode.REQUIRED,
      StepCode.of("FORM_DETAIL_INGESTION"), List.of(step));
  }
}
