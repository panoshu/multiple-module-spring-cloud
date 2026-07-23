package com.example.core.infrastructure.engine.evaluation;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.QLResult;
import com.alibaba.qlexpress4.api.parsecache.LoadedParseCache;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.gateway.ConditionEvaluationGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于 QLExpress4 的条件求值网关实现
 * <p>
 * 解析后的 {@link LoadedParseCache} 实例会被缓存，避免相同表达式重复编译带来的性能开销。
 * <p>
 * 通过配置 {@code core.engine.condition-evaluator.type=qlexpress} 显式启用。
 * 若需切换为其他引擎，设置对应 type 值即可。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 2.0
 * @since 2026/7/23
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "core.engine.condition-evaluator", name = "type", havingValue = "qlexpress")
public class QLExpressConditionEvaluationGateway implements ConditionEvaluationGateway {

  /** QLExpress4 核心运行器，线程安全，单例复用 */
  private final Express4Runner runner;

  public QLExpressConditionEvaluationGateway() {
    // 默认采用隔离安全策略
    this.runner = new Express4Runner(InitOptions.DEFAULT_OPTIONS);
  }

  @Override
  public boolean evaluate(String conditionExpression, BusinessMetaContext context) {
    if (!StringUtils.hasText(conditionExpression)) {
      return true;
    }

    Map<String, Object> params = new HashMap<>();
    params.put("customerNo", context.customerNo());
    params.put("productNo", context.productNo());
    params.put("businessType", context.businessType());
    params.put("facts", context.extensionFacts());

    return doEvaluate(conditionExpression, params);
  }

  @Override
  public boolean evaluate(String conditionExpression, Map<String, Object> pureFacts) {
    if (!StringUtils.hasText(conditionExpression)) {
      return true;
    }

    Map<String, Object> params = new HashMap<>();
    params.put("facts", pureFacts);

    return doEvaluate(conditionExpression, params);
  }

  private boolean doEvaluate(String expressionStr, Map<String, Object> context) {
    try {
      // 【核心改动】：直接传入 String 表达式和 Map，并开启 cache(true)
      // Express4Runner 内部会自动检查缓存，命中则直接执行，未命中则编译并自动缓存
      // 无需手动维护 ConcurrentHashMap
      QLResult result = runner.execute(
        expressionStr,
        context,
        QLOptions.builder().cache(true).build()
      );

      Object value = result.getResult();
      return Boolean.TRUE.equals(value);

    } catch (Exception e) {
      log.error("QLExpress 表达式执行失败: expression={}", expressionStr, e);
      return false; // 容错策略：校验异常视为不通过
    }
  }
}
