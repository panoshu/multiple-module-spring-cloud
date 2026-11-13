package com.example.core.application.engine.step.handler;

import com.example.core.application.engine.dispatch.OutboundDispatchPayload;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.shared.exception.SystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** 出站 Handler 登记意图（批次 30）。 */
@DisplayName("出站 Handler 登记派发意图，不再事务内直调网关")
class OutboundGenerationHandlerTest {

  @Test
  @DisplayName("登记载荷并返回挂起，且凭证可解码还原")
  void execute_registersPayloadAndSuspends() {
    OutboundGenerationHandler handler = new OutboundGenerationHandler();
    BusinessMetaContext context = contextWith("outboundFileTaskId", "FT-1",
      "outboundTargetTemplateId", "TPL-1");

    StepExecutionStatus status = handler.execute(mock(BusinessApplication.class), context);

    assertThat(status).isEqualTo(StepExecutionStatus.SUSPEND_ASYNC_WAIT);
    OutboundDispatchPayload payload = OutboundDispatchPayload.decode(context.dispatchPayload());
    assertThat(payload.fileTaskId()).isEqualTo("FT-1");
    assertThat(payload.targetTemplateId()).isEqualTo("TPL-1");
  }

  @Test
  @DisplayName("缺少出站凭证时抛 SystemException")
  void execute_rejectsMissingFacts() {
    OutboundGenerationHandler handler = new OutboundGenerationHandler();
    BusinessMetaContext context = contextWith("outboundFileTaskId", "FT-1");

    assertThatThrownBy(() -> handler.execute(mock(BusinessApplication.class), context))
      .isInstanceOf(SystemException.class);
  }

  private BusinessMetaContext contextWith(Object... kv) {
    java.util.Map<String, Object> facts = new java.util.HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      facts.put((String) kv[i], kv[i + 1]);
    }
    return new BusinessMetaContext(null, null, null, null, null, null, facts);
  }
}
