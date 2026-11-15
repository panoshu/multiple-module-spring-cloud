package com.example.core.infrastructure.engine.evaluation;

import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.gateway.ConditionEvaluationGateway;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Aviator 的条件求值网关实现
 * <p>
 * Aviator 表达式语法与 SpEL 不同,典型用法:
 * <ul>
 *   <li>变量直接引用:{@code customerNo.value == 'C-001'}</li>
 *   <li>字典访问:{@code facts['hasForeignInvestment'] == true}</li>
 *   <li>数值比较:{@code facts['headcount'] > 10}</li>
 * </ul>
 * <p>
 * 编译后的 {@link Expression} 实例会被缓存,避免相同表达式重复编译。
 * 通过配置 {@code core.engine.condition-evaluator.type=aviator} 启用本实现,
 * 缺省时使用 {@link SpelConditionEvaluationGateway}。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/7/23
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "core.engine.condition-evaluator", name = "type", havingValue = "aviator")
public class AviatorConditionEvaluationGateway implements ConditionEvaluationGateway {

  private final AviatorEvaluatorInstance engine = AviatorEvaluator.getInstance();
  private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

  @Override
  public boolean evaluate(String conditionExpression, BusinessMetaContext context) {
    if (!StringUtils.hasText(conditionExpression)) {
      return true;
    }
    Map<String, Object> env = new HashMap<>();
    env.put("customerNo", context.customerNo());
    env.put("productNo", context.productNo());
    env.put("businessType", context.businessType());
    env.put("facts", context.extensionFacts());
    return doEvaluate(conditionExpression, env);
  }

  @Override
  public boolean evaluate(String conditionExpression, Map<String, Object> pureFacts) {
    if (!StringUtils.hasText(conditionExpression)) {
      return true;
    }
    Map<String, Object> env = new HashMap<>();
    env.put("facts", pureFacts != null ? pureFacts : Map.of());
    return doEvaluate(conditionExpression, env);
  }

  private boolean doEvaluate(String expressionStr, Map<String, Object> env) {
    try {
      // isCache=false:不使用 Aviator 内部缓存,由 expressionCache 统一管理,避免双层缓存
      Expression expression = expressionCache.computeIfAbsent(
        expressionStr, k -> engine.compile(k, false));
      Object result = expression.execute(env);
      return Boolean.TRUE.equals(result);
    } catch (Exception e) {
      log.error("Aviator 表达式执行失败: expression={}", expressionStr, e);
      return false; // 容错策略:校验异常视为不通过
    }
  }
}
