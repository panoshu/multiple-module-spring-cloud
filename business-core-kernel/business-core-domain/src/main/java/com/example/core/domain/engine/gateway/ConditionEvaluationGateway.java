package com.example.core.domain.engine.gateway;

import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;

import java.util.Map;

/**
 * 条件表达式求值网关
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 18:39
 */
public interface ConditionEvaluationGateway {
  boolean evaluate(String conditionExpression, BusinessMetaContext context);

  /**
   * 评估表达式是否成立
   *
   * @param expression 表达式字符串 (如 "headcount > 10")
   * @param context    运行时事实上下文
   * @return 是否满足条件
   */
  boolean evaluate(String expression, Map<String, Object> context);

  /**
   * 预校验条件表达式语法（spec §7.1 发布约束，批次 34）。
   * <p>
   * 只做解析/编译，不注入运行时事实、不求值；空表达式（null/空白）视为无条件、直接通过。
   * 语法非法时由实现抛出 {@code SystemException} 等配置错误，禁止静默放行，
   * 以免发布后才在关键校验处被当作 false 跳过。
   *
   * @param expression 条件表达式
   */
  void validate(String expression);
}
