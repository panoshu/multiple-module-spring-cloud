package com.example.core.application.engine.step.handler;

import com.example.core.application.engine.dispatch.OutboundDispatchPayload;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.spi.StepActionHandler;
import com.example.shared.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 出站文件生成步骤处理器（登记意图，批次 30 改为事务外派发）。
 *
 * <p><b>三方分工</b>（设计决策 D1）：本 handler 只登记出站意图——把不可重建的
 * {@code fileTaskId}/{@code targetTemplateId} 编码为派发载荷写入上下文，
 * 返回 {@link StepExecutionStatus#SUSPEND_ASYNC_WAIT} 挂起；实际调用 file-service
 * 由 {@code ExternalInteractionDispatcher} + {@code FileOutboundDispatchHandler}
 * 在事务外完成（§9.2 阶段 B）。完成事件经 kernel 监听器推进流程。</p>
 *
 * <p><b>step 配置声明</b>：{@code mainProcessor: outboundGenerationHandler}。</p>
 *
 * @author business-core-kernel
 */
@Slf4j
@Component
public class OutboundGenerationHandler implements StepActionHandler {

  @Override
  public String handlerName() {
    return "outboundGenerationHandler";
  }

  @Override
  public StepExecutionStatus execute(
      com.example.core.domain.business.aggregate.root.BusinessApplication app,
      BusinessMetaContext context) {
    log.info("登记出站文件生成意图, appId={}", app.id());

    String fileTaskId = resolveRequiredFact(context, "outboundFileTaskId");
    String targetTemplateId = resolveRequiredFact(context, "outboundTargetTemplateId");
    context.registerDispatchPayload(
      new OutboundDispatchPayload(fileTaskId, targetTemplateId).encode());

    return StepExecutionStatus.SUSPEND_ASYNC_WAIT;
  }

  /** 出站凭证来自业务事实提取器注入的扩展事实，事务外不可重建，故需登记载荷。 */
  private String resolveRequiredFact(BusinessMetaContext context, String factKey) {
    Object value = context.extensionFacts() == null ? null : context.extensionFacts().get(factKey);
    if (value == null) {
      throw new SystemException(
          com.example.core.application.engine.errorcode.CoreAppErrorCode.INVALID_DATA)
        .withLogDetail("业务上下文缺少出站声明（" + factKey + "）: " + context);
    }
    return String.valueOf(value);
  }
}
