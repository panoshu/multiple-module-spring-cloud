package com.example.core.application.engine.step.extension;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.engine.spi.StepExtensionAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 通用审计日志扩展动作（kernel 默认实现）
 * <p>
 * <b>【架构设计与职责边界】</b>
 * <p>本类属于核心编排域 (kernel)，是一个开箱即用的标准化组件。
 * 通过日志记录步骤执行轨迹，适用于审计场景。生产环境可替换为写入审计表。
 * <p>
 * <b>【配置中心 JSON 配置】</b>
 * <p>将本动作的 bean 名称 {@code defaultAuditLogAction} 配置到步骤的
 * {@code preExtensions} 或 {@code postExtensions} 中即可启用。
 *
 * @author core-kernel
 * @since 2026/8/8
 */
@Slf4j
@Component("defaultAuditLogAction")
public class DefaultAuditLogAction implements StepExtensionAction {

  @Override
  public String actionName() {
    return "defaultAuditLogAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    log.info("[审计] applicationId={}, step={}, operator={}",
      app.id().value(),
      app.currentStep(),
      app.operatorInfo() != null ? app.operatorInfo().operatorId().value() : "UNKNOWN");
    return ExtensionExecutionResult.success();
  }
}
