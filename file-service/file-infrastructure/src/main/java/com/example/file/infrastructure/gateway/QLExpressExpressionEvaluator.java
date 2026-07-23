package com.example.file.infrastructure.gateway;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.QLResult;
import com.alibaba.qlexpress4.api.parsecache.LoadedParseCache;
import com.alibaba.qlexpress4.api.parsecache.SerializableParseCache;
import com.alibaba.qlexpress4.runtime.context.ExpressContext;
import com.alibaba.qlexpress4.runtime.context.MapExpressContext;
import com.example.file.domain.gateway.ExpressionEvaluator;
import com.example.shared.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 QLExpress4 的表达式求值器实现
 * <p>
 * 线程安全，建议作为 Spring 单例 Bean 使用。
 * 默认采用隔离安全策略，不允许脚本访问 Java 对象字段和方法。
 * 若业务需要访问特定 Java 方法，建议在构造时配置白名单安全策略。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 2.0
 * @since 2026/7/23
 */
@Slf4j
@Component
public class QLExpressExpressionEvaluator implements ExpressionEvaluator {
  // Express4Runner 是线程安全的，强烈建议作为单例复用
  private final Express4Runner runner;

  public QLExpressExpressionEvaluator() {
    // 1. 构造函数必须传入 InitOptions，使用默认配置即可
    this.runner = new Express4Runner(InitOptions.DEFAULT_OPTIONS);
  }

  @Override
  public Object evaluate(String expr, Map<String, Object> context) {
    try {
      // 2. QLExpress4 直接接受 Map<String, Object> 作为上下文，无需转换为 DefaultContext

      // 3. 构建执行选项：开启 cache(true) 以利用引擎内部的指令集缓存，大幅提升重复执行性能
      QLOptions options = QLOptions.builder().cache(true).build();

      // 4. execute 方法返回 QLResult 对象，需调用 .getResult() 获取实际结果
      QLResult result = runner.execute(expr, context, options);

      return result.getResult();

    } catch (Exception e) {
      // 接口未声明 throws，需将受检异常包装为 RuntimeException
      throw new RuntimeException("QLExpress4 表达式执行失败: " + expr, e);
    }
  }
}
