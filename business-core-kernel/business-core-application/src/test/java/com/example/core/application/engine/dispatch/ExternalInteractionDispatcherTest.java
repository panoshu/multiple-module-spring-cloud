package com.example.core.application.engine.dispatch;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.repository.ExternalInteractionRepository;
import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import com.example.core.types.StepExecutionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 外部协作派发器编排（阶段 B 骨架，批次 28）。 */
@DisplayName("ExternalInteractionDispatcher 事务外派发编排")
class ExternalInteractionDispatcherTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 10, 0);

  private ExternalInteractionRepository repository;
  private InteractionDispatchHandler handler;

  @BeforeEach
  void setUp() {
    repository = mock(ExternalInteractionRepository.class);
    handler = mock(InteractionDispatchHandler.class);
  }

  @Test
  @DisplayName("认领并派发成功：PENDING_DISPATCH → WAITING_CALLBACK，保存 externalTaskId")
  void dispatchPending_claimsAndWaitsCallback() {
    ExternalInteraction pending = reconstitute(1L, InteractionType.FILE_PARSE,
      ExternalInteractionStatus.PENDING_DISPATCH);
    when(repository.findByStatus(ExternalInteractionStatus.PENDING_DISPATCH))
      .thenReturn(List.of(pending));
    when(handler.supportedType()).thenReturn(InteractionType.FILE_PARSE);
    when(handler.dispatch(pending)).thenReturn("TASK-1");
    ExternalInteractionDispatcher dispatcher = dispatcher(handler);

    assertThat(dispatcher.dispatchPending()).isEqualTo(1);

    assertThat(pending.status()).isEqualTo(ExternalInteractionStatus.WAITING_CALLBACK);
    assertThat(pending.externalTaskId()).isEqualTo("TASK-1");
    verify(repository, times(2)).save(pending); // 认领 + 等待回调
  }

  @Test
  @DisplayName("handler 未拿到 taskId：认领后保持 DISPATCHING 待回收对账")
  void dispatchPending_keepsDispatchingWhenNoTaskId() {
    ExternalInteraction pending = reconstitute(1L, InteractionType.FILE_PARSE,
      ExternalInteractionStatus.PENDING_DISPATCH);
    when(repository.findByStatus(ExternalInteractionStatus.PENDING_DISPATCH))
      .thenReturn(List.of(pending));
    when(handler.supportedType()).thenReturn(InteractionType.FILE_PARSE);
    when(handler.dispatch(pending)).thenReturn(null);
    ExternalInteractionDispatcher dispatcher = dispatcher(handler);

    assertThat(dispatcher.dispatchPending()).isZero();

    assertThat(pending.status()).isEqualTo(ExternalInteractionStatus.DISPATCHING);
    verify(repository, times(1)).save(pending);
  }

  @Test
  @DisplayName("无匹配 handler：仅认领到 DISPATCHING，不调网关")
  void dispatchPending_claimsButSkipsWhenNoHandler() {
    ExternalInteraction pending = reconstitute(1L, InteractionType.FILE_OUTBOUND,
      ExternalInteractionStatus.PENDING_DISPATCH);
    when(repository.findByStatus(ExternalInteractionStatus.PENDING_DISPATCH))
      .thenReturn(List.of(pending));
    when(handler.supportedType()).thenReturn(InteractionType.FILE_PARSE);
    ExternalInteractionDispatcher dispatcher = dispatcher(handler);

    assertThat(dispatcher.dispatchPending()).isZero();

    assertThat(pending.status()).isEqualTo(ExternalInteractionStatus.DISPATCHING);
    verify(handler, never()).dispatch(any());
    verify(repository, times(1)).save(pending);
  }

  @Test
  @DisplayName("空表 no-op")
  void dispatchPending_emptyNoop() {
    when(repository.findByStatus(ExternalInteractionStatus.PENDING_DISPATCH))
      .thenReturn(List.of());
    ExternalInteractionDispatcher dispatcher = dispatcher(handler);

    assertThat(dispatcher.dispatchPending()).isZero();

    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("handler 抛异常时单条隔离，不影响后续协作")
  void dispatchPending_isolatesSingleFailure() {
    ExternalInteraction first = reconstitute(1L, InteractionType.FILE_PARSE,
      ExternalInteractionStatus.PENDING_DISPATCH);
    ExternalInteraction second = reconstitute(2L, InteractionType.FILE_PARSE,
      ExternalInteractionStatus.PENDING_DISPATCH);
    when(repository.findByStatus(ExternalInteractionStatus.PENDING_DISPATCH))
      .thenReturn(List.of(first, second));
    when(handler.supportedType()).thenReturn(InteractionType.FILE_PARSE);
    when(handler.dispatch(first)).thenThrow(new RuntimeException("gateway down"));
    when(handler.dispatch(second)).thenReturn("TASK-2");
    ExternalInteractionDispatcher dispatcher = dispatcher(handler);

    assertThat(dispatcher.dispatchPending()).isEqualTo(1);

    assertThat(first.status()).isEqualTo(ExternalInteractionStatus.DISPATCHING);
    assertThat(second.status()).isEqualTo(ExternalInteractionStatus.WAITING_CALLBACK);
  }

  @Test
  @DisplayName("只扫描 PENDING_DISPATCH，不动其他状态")
  void dispatchPending_scansOnlyPendingDispatch() {
    when(repository.findByStatus(ExternalInteractionStatus.PENDING_DISPATCH))
      .thenReturn(List.of());
    ExternalInteractionDispatcher dispatcher = dispatcher(handler);

    dispatcher.dispatchPending();

    verify(repository).findByStatus(ExternalInteractionStatus.PENDING_DISPATCH);
    verify(repository, never()).save(any());
  }

  private ExternalInteractionDispatcher dispatcher(InteractionDispatchHandler... handlers) {
    return new ExternalInteractionDispatcher(repository, List.of(handlers));
  }

  private ExternalInteraction reconstitute(long id, InteractionType type,
                                           ExternalInteractionStatus status) {
    return ExternalInteraction.reconstitute(
      ExternalInteractionId.of(id), "U-C", "U-U", NOW.minusDays(1), NOW, Version.of(1L),
      new ApplicationId("APP-1"), StepExecutionId.of(9L), type,
      "SE-" + id, null, status,
      null, 0, null, null, null);
  }
}
