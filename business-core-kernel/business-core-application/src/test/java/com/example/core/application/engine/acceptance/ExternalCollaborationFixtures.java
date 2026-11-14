package com.example.core.application.engine.acceptance;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.root.StepExecution;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionState;
import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import com.example.core.types.StepExecutionId;
import com.example.shared.domain.aggregate.valueobject.Version;

import java.time.LocalDateTime;

/** §21.3 / §21.5 验收测试共享的真实领域聚合夹具（避免两个验收类重复 reconstitute）。 */
public final class ExternalCollaborationFixtures {

  public static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0);
  public static final ApplicationId APP_ID = new ApplicationId("APP-1");
  public static final StepExecutionId STEP_EXEC_ID = StepExecutionId.of(9L);
  public static final String REQUEST_NO = "SE-9";
  public static final String TASK_ID = "FT-9";
  public static final String APPROVAL_INSTANCE_ID = "AI-9";
  public static final String OPERATOR = "SYSTEM";

  private ExternalCollaborationFixtures() {
  }

  public static ExternalInteraction interaction(long id, InteractionType type, String requestNo,
                                                String externalTaskId, ExternalInteractionStatus status) {
    return ExternalInteraction.reconstitute(
      ExternalInteractionId.of(id), "U-C", "U-U", NOW.minusDays(1), NOW, Version.of(1L),
      APP_ID, STEP_EXEC_ID, type, requestNo, externalTaskId, status,
      null, 0, null, null, null);
  }

  public static StepExecution waitingExternal() {
    return StepExecution.reconstitute(
      STEP_EXEC_ID, "U-C", "U-U", NOW.minusMinutes(5), NOW, Version.of(1L),
      APP_ID, DefinitionVersion.initial(), StepCode.of("FORM_DETAIL_INGESTION"),
      1, REQUEST_NO, StepExecutionState.WAITING_EXTERNAL,
      NOW.minusMinutes(5), null, null, null, null);
  }
}
