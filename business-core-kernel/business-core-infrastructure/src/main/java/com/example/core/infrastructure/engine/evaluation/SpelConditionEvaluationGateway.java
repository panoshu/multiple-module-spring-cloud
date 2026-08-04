package com.example.core.infrastructure.engine.evaluation;

import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.gateway.ConditionEvaluationGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Spring SpEL 的条件求值网关实现
 * <p>
 * 解析后的 {@link Expression} 实例会被缓存，避免相同表达式重复解析带来的性能开销。
 * <p>
 * 通过配置 {@code core.engine.condition-evaluator.type=spel} 显式启用,或保持缺省(无配置)
 * 自动启用。若需切换为 Aviator 引擎,设置 {@code core.engine.condition-evaluator.type=aviator}
 * 即会激活 {@link AviatorConditionEvaluationGateway} 并停用本实现。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 18:40
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "core.engine.condition-evaluator", name = "type", havingValue = "spel", matchIfMissing = true)
public class SpelConditionEvaluationGateway implements ConditionEvaluationGateway {

  private final ExpressionParser parser = new SpelExpressionParser();
  private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

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
      Expression expression = expressionCache.computeIfAbsent(expressionStr, parser::parseExpression);
      Boolean result = expression.getValue(context, Boolean.class);
      return Boolean.TRUE.equals(result);
    } catch (Exception e) {
      log.error("SpEL 表达式执行失败: expression={}", expressionStr, e);
      return false; // 容错策略：校验异常视为不通过
    }
  }
}
