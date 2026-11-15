package com.example.core.domain.engine.aggregate.valueobject.enums.status;

/**
 * Handler 真实返回的结果
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/16 20:58
 */
public enum StepExecutionStatus {
  SUCCESS,              // 执行成功，引擎可进入下一步
  SUSPEND_ASYNC_WAIT,   // 触发异步调用，引擎挂起，等待回调唤醒
  FAILED                // 执行失败，阻断
}
