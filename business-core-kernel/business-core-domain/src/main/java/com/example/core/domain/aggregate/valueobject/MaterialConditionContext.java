package com.example.core.domain.aggregate.valueobject;

/**
 * 材料条件评估上下文 (核心域定义的 SPI)
 * 作用：判断某个条件规则在当前业务上下文下是否成立
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 14:32
 */
@FunctionalInterface
public interface MaterialConditionContext {
  /**
   * 评估条件规则
   *
   * @param conditionRule 规则标识 (如: "age>60", "is_proxy=true")
   * @return true 表示条件命中（该材料变为必传），false 表示未命中
   */
  boolean evaluate(String conditionRule);
}
