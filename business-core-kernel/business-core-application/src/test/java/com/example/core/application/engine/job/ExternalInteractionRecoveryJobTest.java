package com.example.core.application.engine.job;

import com.example.core.application.business.service.BusinessBatchAppService;
import com.example.core.application.engine.dispatch.ExternalInteractionDispatcher;
import com.example.core.application.engine.service.ExternalInteractionRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalInteractionRecoveryJob 调度与异常隔离")
class ExternalInteractionRecoveryJobTest {

  @Mock
  private ExternalInteractionDispatcher dispatcher;
  @Mock
  private ExternalInteractionRecoveryService recoveryService;
  @Mock
  private BusinessBatchAppService batchAppService;

  private ExternalInteractionRecoveryJob job;

  @BeforeEach
  void setUp() {
    job = new ExternalInteractionRecoveryJob(dispatcher, recoveryService, batchAppService);
  }

  @Test
  @DisplayName("recover 标注 @Scheduled 以启用兜底调度")
  void hasScheduledAnnotation() throws NoSuchMethodException {
    assertThat(ExternalInteractionRecoveryJob.class
      .getMethod("recover").isAnnotationPresent(Scheduled.class)).isTrue();
  }

  @Test
  @DisplayName("一次调度先派发，再执行三类恢复动作")
  void recover_runsDispatchAndThreeRecoveryActions() {
    job.recover();

    verify(dispatcher).dispatchPending();
    verify(recoveryService).reclaimStaleDispatching(any(LocalDateTime.class));
    verify(recoveryService).processDueRetries(any(LocalDateTime.class));
    verify(recoveryService).reconcileUnknown();
    verify(batchAppService).reconcileAllActive();
  }

  @Test
  @DisplayName("批次统计重算异常不阻断其余恢复动作")
  void recover_isolatesBatchStatisticsFailure() {
    doThrow(new RuntimeException("db down")).when(batchAppService).reconcileAllActive();

    job.recover();

    verify(dispatcher).dispatchPending();
    verify(recoveryService).reconcileUnknown();
  }

  @Test
  @DisplayName("派发动作异常不阻断其余恢复动作")
  void recover_isolatesActionFailure() {
    doThrow(new RuntimeException("db down")).when(dispatcher).dispatchPending();

    job.recover();

    verify(recoveryService).reclaimStaleDispatching(any(LocalDateTime.class));
    verify(recoveryService).processDueRetries(any(LocalDateTime.class));
    verify(recoveryService).reconcileUnknown();
  }
}
