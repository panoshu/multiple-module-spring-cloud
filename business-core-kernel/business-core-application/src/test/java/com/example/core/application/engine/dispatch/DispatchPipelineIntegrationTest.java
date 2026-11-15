package com.example.core.application.engine.dispatch;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.repository.ExternalInteractionRepository;
import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import com.example.core.types.StepExecutionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 阶段 B 全链路验收：三类 PENDING_DISPATCH 协作按类型派发至 WAITING_CALLBACK（批次 31）。 */
@DisplayName("事务外派发全链路验收")
class DispatchPipelineIntegrationTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 10, 0);

  @Test
  @DisplayName("三类协作各由匹配 handler 受理，全部推进 WAITING_CALLBACK")
  void dispatchPending_routesAllThreeTypesToWaitingCallback() {
    ExternalInteractionRepository repository = mock(ExternalInteractionRepository.class);
    ExternalInteraction parse = pending(1L, InteractionType.FILE_PARSE);
    ExternalInteraction outbound = pending(2L, InteractionType.FILE_OUTBOUND);
    ExternalInteraction approval = pending(3L, InteractionType.APPROVAL);
    when(repository.findByStatus(ExternalInteractionStatus.PENDING_DISPATCH))
      .thenReturn(List.of(parse, outbound, approval));

    InteractionDispatchHandler parseHandler = stubHandler(InteractionType.FILE_PARSE, "TASK-PARSE");
    InteractionDispatchHandler outboundHandler = stubHandler(InteractionType.FILE_OUTBOUND, "TASK-OUT");
    InteractionDispatchHandler approvalHandler = stubHandler(InteractionType.APPROVAL, "AI-APPROVE");
    ExternalInteractionDispatcher dispatcher = new ExternalInteractionDispatcher(repository,
      List.of(parseHandler, outboundHandler, approvalHandler));

    assertThat(dispatcher.dispatchPending()).isEqualTo(3);

    assertThat(parse.status()).isEqualTo(ExternalInteractionStatus.WAITING_CALLBACK);
    assertThat(parse.externalTaskId()).isEqualTo("TASK-PARSE");
    assertThat(outbound.status()).isEqualTo(ExternalInteractionStatus.WAITING_CALLBACK);
    assertThat(outbound.externalTaskId()).isEqualTo("TASK-OUT");
    assertThat(approval.status()).isEqualTo(ExternalInteractionStatus.WAITING_CALLBACK);
    assertThat(approval.externalTaskId()).isEqualTo("AI-APPROVE");
  }

  @Test
  @DisplayName("其中一类未受理不影响其余类型推进")
  void dispatchPending_isolatesUnacceptedType() {
    ExternalInteractionRepository repository = mock(ExternalInteractionRepository.class);
    ExternalInteraction parse = pending(1L, InteractionType.FILE_PARSE);
    ExternalInteraction approval = pending(2L, InteractionType.APPROVAL);
    when(repository.findByStatus(ExternalInteractionStatus.PENDING_DISPATCH))
      .thenReturn(List.of(parse, approval));

    InteractionDispatchHandler parseHandler = stubHandler(InteractionType.FILE_PARSE, null);
    InteractionDispatchHandler approvalHandler = stubHandler(InteractionType.APPROVAL, "AI-9");
    ExternalInteractionDispatcher dispatcher = new ExternalInteractionDispatcher(repository,
      List.of(parseHandler, approvalHandler));

    assertThat(dispatcher.dispatchPending()).isEqualTo(1);

    assertThat(parse.status()).isEqualTo(ExternalInteractionStatus.DISPATCHING);
    assertThat(approval.status()).isEqualTo(ExternalInteractionStatus.WAITING_CALLBACK);
  }

  private InteractionDispatchHandler stubHandler(InteractionType type, String taskId) {
    InteractionDispatchHandler handler = mock(InteractionDispatchHandler.class);
    when(handler.supportedType()).thenReturn(type);
    // 任意协作入参均返回该类型的受理结果；本集成测试关注分派与状态推进，不校验具体参数
    org.mockito.Mockito.lenient().when(handler.dispatch(org.mockito.ArgumentMatchers.any()))
      .thenReturn(taskId);
    return handler;
  }

  private ExternalInteraction pending(long id, InteractionType type) {
    return ExternalInteraction.reconstitute(
      ExternalInteractionId.of(id), "U-C", "U-U", NOW.minusDays(1), NOW, Version.of(1L),
      new ApplicationId("APP-1"), StepExecutionId.of(9L + id), type,
      "SE-" + id, null, ExternalInteractionStatus.PENDING_DISPATCH,
      null, 0, null, null, null);
  }
}
