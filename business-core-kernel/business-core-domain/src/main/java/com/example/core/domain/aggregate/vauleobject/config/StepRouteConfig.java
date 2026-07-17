package com.example.core.domain.aggregate.vauleobject.config;

import com.example.core.domain.aggregate.vauleobject.enums.workflow.ApplicationFlowStep;

import java.util.List;

/**
 * 步骤路由与策略配置
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 16:11
 */
public record StepRouteConfig(
  ApplicationFlowStep currentStep,
  ApplicationFlowStep nextStep,
  StepTaskType taskType,        // 决定引擎行为：SYSTEM_TASK / USER_TASK
  // ================== 生命周期插槽 (Pipeline Slots) ==================

  /*
    1. 上下文装配与准入校验 (替代原 preExtensions)
    职责：通过外部接口拉取必要数据放入 mutations；校验前置业务规则 (如果不满足则阻断)。
    例子：客户黑名单校验 (Validator)、查询外部征信接口 (Enricher)。
   */
  List<StepExtensionConfig> preValidations,

  /*
    2. 核心主处理器 (原 mainHandlerName)
    职责：负责该步骤最重要的计划层 (总体) 状态变迁或外部触发。
    例子：planMaterialPreparationHandler, defaultFormParsingHandler。
   */
  String mainProcessor,

  /*
    3. 明细层处理器 (原部分 postExtensions)
    职责：主干逻辑完成后，执行大批量的明细数据(Detail)计算、落库。
    例子：employeeDetailMaterialExtension (明细材料计算), employeeJsonIngestionExtension (流式摄入)。
   */
  List<StepExtensionConfig> detailProcessors,

  /*
    4. 边际动作与副作用 (原部分 postExtensions)
    职责：在所有业务逻辑成功后，执行发通知、推数据、记审计日志等（通常配置为 isAsync = true）。
    例子：smsNotifyAction, auditLogAction。
   */
  List<StepExtensionConfig> sideEffects
) {
  public enum StepTaskType {
    SYSTEM_TASK, // 系统任务：自动执行扩展链和主Handler
    USER_TASK    // 用户任务：挂起，等待用户通过BFF层提交数据后唤醒
  }
}
