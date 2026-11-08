package com.example.core.application.engine.step.handler;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import com.example.core.domain.engine.spi.StepActionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通用步骤处理器：触发出站文件生成
 * <p>
 * <b>【职责边界】</b>
 * <p>本处理器属于核心编排域 (kernel)，是一个开箱即用的标准化组件。
 * 通过 {@link FileIntegrationGateway} 防腐层触发出站文件生成，并将流程挂起等待生成完成回调唤醒。
 *
 * <b>【配置中心 JSON 配置】</b>
 * <p>将本步骤的 {@code mainProcessor} 配置为 {@code "archiveSubmissionHandler"} 即可启用。
 *
 * <b>【异步唤醒机制】</b>
 * <p>本处理器返回 {@link StepExecutionStatus#SUSPEND_ASYNC_WAIT} 后，引擎会挂起并提交事务。
 * file-service 完成出站文件生成后通过集成事件回调，由 {@code OutboundFileEventListener} 驱动引擎进入下一节点。
 *
 * <b>【出站参数来源】</b>
 * <ul>
 * <li>{@code fileTaskId} — 使用 {@code applicationId} 作为关联键，回调事件以此定位申请单</li>
 * <li>{@code targetTemplateId} — 从 {@link BusinessMetaContext#extensionFacts()} 读取 {@code targetTemplateId}</li>
 * <li>{@code supplementalData} — 从 {@link BusinessMetaContext#extensionFacts()} 读取 {@code biz_} 前缀字段</li>
 * </ul>
 *
 * @author core-kernel
 * @since 2026/10/29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArchiveSubmissionHandler implements StepActionHandler {

  private static final String TARGET_TEMPLATE_ID_KEY = "targetTemplateId";
  private static final String BIZ_DATA_PREFIX = "biz_";

  private final FileIntegrationGateway fileIntegrationGateway;

  @Override
  public String handlerName() {
    return "archiveSubmissionHandler";
  }

  @Override
  public StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context) {
    String fileTaskId = app.id().value();
    String targetTemplateId = resolveTargetTemplateId(context);
    Map<String, Object> supplementalData = resolveSupplementalData(context);
    String operator = app.updatedBy() != null ? app.updatedBy() : "SYSTEM";

    log.info("开始触发出站文件生成, applicationId={}, targetTemplateId={}", app.id(), targetTemplateId);

    try {
      fileIntegrationGateway.triggerOutboundGeneration(fileTaskId, targetTemplateId, supplementalData, operator);
      log.info("已成功触发出站文件生成, applicationId={}", app.id());
      return StepExecutionStatus.SUSPEND_ASYNC_WAIT;
    } catch (Exception e) {
      log.error("触发出站文件生成失败, applicationId={}", app.id(), e);
      return StepExecutionStatus.FAILED;
    }
  }

  private String resolveTargetTemplateId(BusinessMetaContext context) {
    Object value = context.extensionFacts().get(TARGET_TEMPLATE_ID_KEY);
    if (value instanceof String templateId && !templateId.isBlank()) {
      return templateId;
    }
    return "DEFAULT";
  }

  private Map<String, Object> resolveSupplementalData(BusinessMetaContext context) {
    return context.extensionFacts().entrySet().stream()
      .filter(e -> e.getKey().startsWith(BIZ_DATA_PREFIX))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}