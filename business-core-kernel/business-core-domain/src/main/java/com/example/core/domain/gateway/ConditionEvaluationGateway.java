package com.example.core.domain.gateway;

import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;

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
}
