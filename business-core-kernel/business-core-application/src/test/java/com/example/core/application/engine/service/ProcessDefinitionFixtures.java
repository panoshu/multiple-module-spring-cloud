package com.example.core.application.engine.service;

import com.example.core.domain.engine.aggregate.valueobject.BusinessTypeCode;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.BatchMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InstanceGranularity;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.StartMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.TaskType;

import java.util.List;

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
}
