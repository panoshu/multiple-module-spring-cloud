package com.example.core.application.engine.listener;

import com.example.core.application.engine.service.ExternalInteractionRecoveryService;
import com.example.core.domain.engine.event.ExternalInteractionRequestedEvent;
import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalInteractionRequestedListener 事务提交后尽快认领")
class ExternalInteractionRequestedListenerTest {

  @Mock
  private ExternalInteractionRecoveryService recoveryService;

  private ExternalInteractionRequestedListener listener;

  @BeforeEach
  void setUp() {
    listener = new ExternalInteractionRequestedListener(recoveryService);
  }

  @Test
  @DisplayName("标注 @TransactionalEventListener(AFTER_COMMIT)")
  void hasAfterCommitAnnotation() throws NoSuchMethodException {
    TransactionalEventListener annotation = ExternalInteractionRequestedListener.class
      .getMethod("onInteractionRequested", ExternalInteractionRequestedEvent.class)
      .getAnnotation(TransactionalEventListener.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
  }

  @Test
  @DisplayName("协作创建事件触发尽快认领待派发协作")
  void onInteractionRequested_claimsPendingDispatch() {
    ExternalInteractionRequestedEvent event = ExternalInteractionRequestedEvent.of(
      ExternalInteractionId.of(1L), new ApplicationId("APP-1"), "SE-9");

    listener.onInteractionRequested(event);

    verify(recoveryService).claimPendingDispatch();
  }
}
