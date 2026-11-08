package com.example.core.application.engine.step.handler;

import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import com.example.core.domain.engine.spi.StepActionHandler;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.shared.exception.SystemException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 出站文件生成步骤处理器（2026-11-08 批次4 C3：导出闭环——kernel 内置件）.
 *
 * <p><b>三方分工</b>（设计决策 D1）：本 handler 只做"触发"——委托
 * {@link FileIntegrationGateway#triggerOutboundGeneration}，随后返回
 * {@link StepExecutionStatus#SUSPEND_ASYNC_WAIT} 挂起；file-service 生成完成
 * 发布 OutboundFileGeneratedEvent → kernel 的
 * {@code OutboundFileGeneratedEventListener} 按事件推进流程；
 * integration-service 监听同一事件做协议适配提交外部系统.</p>
 *
 * <p><b>step 配置声明</b>：{@code mainProcessor: outboundGenerationHandler}；
 * 目标模板经 {@code BusinessConfigGateway} 的解析配置传递（批次5 随字段域
 * 配置模型一起收敛到 FormParsingConfig 的出站扩展）.</p>
 *
 * @author business-core-kernel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboundGenerationHandler implements StepActionHandler {

  private final FileIntegrationGateway fileIntegrationGateway;

  @Override
  public String handlerName() {
    return "outboundGenerationHandler";
  }

  @Override
  public StepExecutionStatus execute(
      com.example.core.domain.business.aggregate.root.BusinessApplication app,
      BusinessMetaContext context) {
    log.info("触发出站文件生成, appId={}", app.id());

    String fileTaskId = resolveRequiredFact(context, "outboundFileTaskId");
    String targetTemplateId = resolveRequiredFact(context, "outboundTargetTemplateId");
    Map<String, Object> supplementalData = Map.of();
    String operator = "SYSTEM";

    fileIntegrationGateway.triggerOutboundGeneration(
      fileTaskId, targetTemplateId, supplementalData, operator);

    log.info("已派发出站生成任务, fileTaskId={}, targetTemplateId={}", fileTaskId, targetTemplateId);
    // 异步挂起：等待 OutboundFileGeneratedEvent 唤醒（同表单解析的 SUSPEND 模式）
    return StepExecutionStatus.SUSPEND_ASYNC_WAIT;
  }

  /**
   * 批次4：出站凭证（outboundFileTaskId / outboundTargetTemplateId）从扩展事实读取——
   * 业务线经 BusinessFactExtractor 注入；批次5 字段域配置化后收敛到
   * BusinessConfigGateway 的出站配置.
   */
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
