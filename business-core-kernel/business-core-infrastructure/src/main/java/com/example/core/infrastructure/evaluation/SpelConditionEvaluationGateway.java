package com.example.core.infrastructure.evaluation;

import com.example.core.domain.gateway.ConditionEvaluationGateway;
import com.example.core.domain.aggregate.vauleobject.BusinessMetaContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 18:40
 */
@Slf4j
@Component
public class SpelConditionEvaluationGateway implements ConditionEvaluationGateway {

  private final ExpressionParser parser = new SpelExpressionParser();

  @Override
  public boolean evaluate(String conditionExpression, BusinessMetaContext context) {
    if (!StringUtils.hasText(conditionExpression)) {
      return true;
    }

    StandardEvaluationContext spelContext = new StandardEvaluationContext();
    // 注入强类型基础属性
    spelContext.setVariable("customerNo", context.customerNo());
    spelContext.setVariable("productNo", context.productNo());
    spelContext.setVariable("businessType", context.businessType());
    // 注入扩展字典
    spelContext.setVariable("facts", context.extensionFacts());

    return doEvaluate(conditionExpression, spelContext);
  }

  @Override
  public boolean evaluate(String conditionExpression, Map<String, Object> pureFacts) {
    if (!StringUtils.hasText(conditionExpression)) {
      return true;
    }

    StandardEvaluationContext spelContext = new StandardEvaluationContext();
    // 明细层降维，只注入 facts
    spelContext.setVariable("facts", pureFacts);

    return doEvaluate(conditionExpression, spelContext);
  }

  private boolean doEvaluate(String expressionStr, StandardEvaluationContext context) {
    try {
      Expression expression = parser.parseExpression(expressionStr);
      Boolean result = expression.getValue(context, Boolean.class);
      return Boolean.TRUE.equals(result);
    } catch (Exception e) {
      log.error("SpEL 表达式执行失败: expression={}", expressionStr, e);
      return false; // 容错策略：校验异常视为不通过
    }
  }
}
