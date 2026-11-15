package com.example.core.application.engine.step.handler;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.gateway.ParsedFileReferenceGateway;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.spi.StepActionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通用表单异步解析主处理器 (核心域)
 * <p>
 * <b>【架构设计与职责边界】</b>
 * <p>1. 本类属于核心编排域 (kernel)，是一个“开箱即用”的标准化组件。
 * <p>2. 职责：校验待解析文件已挂载，将当前流程节点挂起（登记意图）。实际派发由
 * {@code ExternalInteractionDispatcher} + {@code FileParseDispatchHandler} 在事务外完成（§9.2 阶段 B），
 * 本 Handler 不再在事务内直接调用 file-service。
 * <p>
 * <b>【各业务线开发人员请注意：如何使用和扩展本节点？】</b>
 * <p>如果您的业务包含 Excel/CSV 批量表单上传并需要解析：
 * <p>1. <b>无需编写任何代码</b>：请直接在您的流程 JSON 配置中，将本步骤的 `mainProcessor` 配置为 "defaultFormParsingHandler"。
 * <p>2. <b>配置解析规则</b>：在配置中心的 `FormParsingConfig` 中，为您的业务类型配置好对应的 `parseTemplateId`（底层文档中心需要这个模板来识别如何拆分表格）。
 * <p>3. <b>在哪里落库明细？</b>：本节点只负责“发出指令”并“挂起等待”。当底层解析完毕并回调唤醒引擎进入【下一个步骤 (如 FORM_DETAIL_INGESTION)】时，
 * 请在下一个步骤的 `detailProcessors` 中，挂载您自己实现的 {@link com.example.core.application.engine.step.extension.AbstractJsonStreamIngestionAction} 子类，
 * 去完成 JSON 到具体业务实体 (如 EmployeeDetail) 的映射与落库。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 12:14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultFormParsingHandler implements StepActionHandler {

  private final ParsedFileReferenceGateway parsedFileGateway;

  @Override
  public String handlerName() {
    return "defaultFormParsingHandler";
  }

  /**
   * 登记一次表单解析意图：仅校验前置条件（待解析文件已挂载），随后返回挂起。
   * <p>
   * 引擎收到挂起状态后提交事务并创建 {@code ExternalInteraction(PENDING_DISPATCH)}；
   * 事务外派发由 {@code ExternalInteractionDispatcher} 认领后交给
   * {@code FileParseDispatchHandler} 重建参数调用 file-service，配置缺失等错误在派发阶段
   * 处理（协作保持 DISPATCHING 待对账），不再于本事务内直接发起远程调用。
   */
  @Override
  public StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context) {
    log.info("登记表单解析意图, appId={}", app.id());
    parsedFileGateway.require(app.id());
    return StepExecutionStatus.SUSPEND_ASYNC_WAIT;
  }
}
