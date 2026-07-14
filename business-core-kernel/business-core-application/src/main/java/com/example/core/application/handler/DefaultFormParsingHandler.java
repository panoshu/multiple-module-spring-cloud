package com.example.core.application.handler;

import com.example.core.application.errorcode.CoreAppErrorCode;
import com.example.core.domain.gateway.BusinessConfigGateway;
import com.example.core.domain.gateway.FileIntegrationGateway;
import com.example.core.domain.aggregateroot.BusinessApplication;
import com.example.core.domain.vauleobject.BusinessMetaContext;
import com.example.core.domain.vauleobject.enums.status.StepExecutionStatus;
import com.example.core.domain.vauleobject.config.FormParsingConfig;
import com.example.core.domain.spi.StepActionHandler;
import com.example.shared.exception.SystemException;
import com.example.shared.primitives.identity.FileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通用表单异步解析主处理器 (核心域)
 * <p>
 * <b>【架构设计与职责边界】</b>
 * <p>1. 本类属于核心编排域 (kernel)，是一个“开箱即用”的标准化组件。
 * <p>2. 职责：查询通用的表单解析配置，触发底层基础设施 (FileIntegrationGateway) 发起异步解析，并将当前流程节点挂起。
 * <p>
 * <b>【各业务线开发人员请注意：如何使用和扩展本节点？】</b>
 * <p>如果您的业务包含 Excel/CSV 批量表单上传并需要解析：
 * <p>1. <b>无需编写任何代码</b>：请直接在您的流程 JSON 配置中，将本步骤的 `mainProcessor` 配置为 "defaultFormParsingHandler"。
 * <p>2. <b>配置解析规则</b>：在配置中心的 `FormParsingConfig` 中，为您的业务类型配置好对应的 `parseTemplateId`（底层文档中心需要这个模板来识别如何拆分表格）。
 * <p>3. <b>在哪里落库明细？</b>：本节点只负责“发出指令”并“挂起等待”。当底层解析完毕并回调唤醒引擎进入【下一个步骤 (如 FORM_DETAIL_INGESTION)】时，
 * 请在下一个步骤的 `detailProcessors` 中，挂载您自己实现的 {@link com.example.core.application.extension.AbstractJsonStreamIngestionAction} 子类，
 * 去完成 JSON 到具体业务实体 (如 EmployeeDetail) 的映射与落库。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 12:14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultFormParsingHandler implements StepActionHandler {

  private final BusinessConfigGateway configGateway;
  private final FileIntegrationGateway fileIntegrationGateway;

  @Override
  public String handlerName() {
    return "defaultFormParsingHandler";
  }

  @Override
  public StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context) {
    log.info("开始处理表单解析任务, appId={}", app.id());

    FileId targetFile = app.requireFileForParsing();

    // 从统一下发的配置中心，获取当前业务线专属的解析模板与规则
    FormParsingConfig parsingConfig = configGateway.getFormParsingConfig(context);
    if (parsingConfig == null || parsingConfig.parseTemplateId() == null) {
      throw new SystemException(CoreAppErrorCode.INVALID_DATA)
        .withLogDetail("未找到该业务的表单解析配置, 业务元数据为: " + context);
    }

    // 3. 跨上下文防腐调用：触发底层异步解析
    fileIntegrationGateway.triggerAsyncParsing(
      app.bindedFormId(),
      targetFile,
      parsingConfig.parseTemplateId(),
      parsingConfig.splitRules()
    );

    log.info("已成功向底层文件服务派发表单解析任务, formId={}, templateId={}",
      app.bindedFormId(), parsingConfig.parseTemplateId());

    // 4. 【核心机制】：向引擎返回异步挂起状态。
    // 引擎收到此状态后，会退出当前线程、提交事务。
    // 等待底层解析服务完成后，通过回调接口再次唤醒引擎。
    return StepExecutionStatus.SUSPEND_ASYNC_WAIT;
  }
}
