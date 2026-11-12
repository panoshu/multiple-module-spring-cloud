package com.example.core.application.engine.job;

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
  private ExternalInteractionRecoveryService recoveryService;

  private ExternalInteractionRecoveryJob job;

  @BeforeEach
  void setUp() {
    job = new ExternalInteractionRecoveryJob(recoveryService);
  }

  @Test
  @DisplayName("recover 标注 @Scheduled 以启用兜底调度")
  void hasScheduledAnnotation() throws NoSuchMethodException {
    assertThat(ExternalInteractionRecoveryJob.class
      .getMethod("recover").isAnnotationPresent(Scheduled.class)).isTrue();
  }

  @Test
  @DisplayName("一次调度依次执行四类恢复动作")
  void recover_runsAllFourActions() {
    job.recover();

    verify(recoveryService).claimPendingDispatch();
    verify(recoveryService).reclaimStaleDispatching(any(LocalDateTime.class));
    verify(recoveryService).processDueRetries(any(LocalDateTime.class));
    verify(recoveryService).reconcileUnknown();
  }

  @Test
  @DisplayName("单个动作异常不阻断其余动作")
  void recover_isolatesActionFailure() {
    doThrow(new RuntimeException("db down")).when(recoveryService).claimPendingDispatch();

    job.recover();

    verify(recoveryService).reclaimStaleDispatching(any(LocalDateTime.class));
    verify(recoveryService).processDueRetries(any(LocalDateTime.class));
    verify(recoveryService).reconcileUnknown();
  }
}
