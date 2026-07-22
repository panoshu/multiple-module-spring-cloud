package com.example.core.domain.spi;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.enums.status.StepExecutionStatus;

/**
 * 业务步骤主处理器 (Step Main Handler)
 * <p>
 * 该接口是流程引擎中某个具体步骤（Step）的核心执行单元。
 * 每一个步骤最多配置一个 Main Handler。
 * * <h3>使用场景与最佳实践：</h3>
 * <ul>
 * <li><b>模式 1：轻量级直接处理（充血模型）</b><br>
 * 如果逻辑很简单，可以直接在实现类中调用聚合根的方法。
 * 例如：{@code app.assignMaterials(items); return StepExecutionStatus.SUCCESS;}
 * </li>
 * <li><b>模式 2：委托给领域服务（Domain Service）</b><br>
 * 如果涉及复杂的跨聚合逻辑或特定领域的算法，请不要在 Handler 中写面条代码，
 * 应注入相应的 Domain Service 进行委托处理。
 * </li>
 * <li><b>模式 3：异步网关触发（防腐层）</b><br>
 * 如果当前步骤需要调用外部系统（如发起影像解析、调用核心记账），
 * 应通过 Gateway 发送消息/RPC，并返回 {@link StepExecutionStatus#SUSPEND_ASYNC_WAIT} 挂起引擎。
 * </li>
 * </ul>
 * * <h3>⚠️ 注意事项：</h3>
 * 1. <b>不要在此处执行 Save</b>：Handler 仅负责改变 {@link BusinessApplication} 的内存状态，持久化由引擎统一完成。<br>
 * 2. <b>幂等性</b>：如果步骤失败可能引发重试，请确保您的执行逻辑具有幂等性。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/13 13:24
 */
public interface StepActionHandler {
  /**
   * 获取处理器的全局唯一标识，对应配置中心 JSON 里的 mainHandlerName
   */
  String handlerName();

  /**
   * 执行当前步骤的核心业务逻辑。
   *
   * @param app     当前业务申请单聚合根 (可直接调用其业务方法改变状态)
   * @param context 包含扩展事实的业务全局上下文，只读 (Immutable)
   * @return 真实物理执行状态，决定引擎后续行为（成功、失败阻断、异步挂起）
   * SUCCESS: 表示执行成功，需引擎继续流转
   * SUSPEND_ASYNC_WAIT: 触发了异步动作需等待
   * FAILED: 失败
   */
  StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context);
}
