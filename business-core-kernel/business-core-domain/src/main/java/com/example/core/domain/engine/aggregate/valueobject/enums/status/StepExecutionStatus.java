package com.example.core.domain.engine.aggregate.valueobject.enums.status;

/**
 * StepActionHandler 的领域执行结果（spec §8.2「Handler 返回值只表达领域结果」）。
 * <p>
 * <b>不是</b>步骤执行的持久化状态：§8.2 的八态状态机由 {@link StepExecutionState} 承担，
 * 由应用服务结合异常类型与重试策略完成状态迁移，Handler 不直接写库、不直接决定数据库状态。
 * 本枚举仅表达「本次执行是否成功 / 是否挂起等待外部结果 / 是否失败阻断」三种领域结果。
 * <p>
 * 与 {@link StepExecutionState} 职责不同、必须并存；二者的语义边界由
 * {@code StepExecutionSemanticsContractTest} 机器校验，不得把持久化状态并入本枚举。
 * 本类型是跨服务公开 SPI（{@code StepActionHandler.execute} 返回类型），重命名属破坏性变更。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/16 20:58
 */
public enum StepExecutionStatus {
  /** 执行成功，引擎可进入下一步 */
  SUCCESS,
  /** 已登记外部协作意图，引擎挂起等待回调唤醒 */
  SUSPEND_ASYNC_WAIT,
  /** 执行失败，阻断流程 */
  FAILED
}
