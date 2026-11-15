package com.example.core.application.engine.step.handler;

import com.example.core.application.engine.dispatch.OutboundDispatchPayload;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.spi.StepActionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通用出站文件生成步骤处理器（登记意图，批次 30 改为事务外派发）。
 * <p>
 * 校验并登记出站凭证（{@code fileTaskId} 取申请单 ID、{@code targetTemplateId} 取扩展事实），
 * 编码为派发载荷写入上下文后返回挂起；实际调用 file-service 由
 * {@code FileOutboundDispatchHandler} 在事务外完成（§9.2 阶段 B）。
 * 出站生成完成事件回调后由 {@code OutboundFileGeneratedEventListener} 推进流程。
 *
 * <p><b>配置</b>：{@code mainProcessor: archiveSubmissionHandler}。</p>
 *
 * @author core-kernel
 * @since 2026/10/29
 */
@Slf4j
@Component
public class ArchiveSubmissionHandler implements StepActionHandler {

  private static final String TARGET_TEMPLATE_ID_KEY = "targetTemplateId";

  @Override
  public String handlerName() {
    return "archiveSubmissionHandler";
  }

  @Override
  public StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context) {
    String fileTaskId = app.id().value();
    String targetTemplateId = resolveTargetTemplateId(context);

    log.info("登记出站文件生成意图, applicationId={}, targetTemplateId={}", app.id(), targetTemplateId);
    context.registerDispatchPayload(
      new OutboundDispatchPayload(fileTaskId, targetTemplateId).encode());

    return StepExecutionStatus.SUSPEND_ASYNC_WAIT;
  }

  private String resolveTargetTemplateId(BusinessMetaContext context) {
    Object value = context.extensionFacts().get(TARGET_TEMPLATE_ID_KEY);
    if (value instanceof String templateId && !templateId.isBlank()) {
      return templateId;
    }
    return "DEFAULT";
  }
}
