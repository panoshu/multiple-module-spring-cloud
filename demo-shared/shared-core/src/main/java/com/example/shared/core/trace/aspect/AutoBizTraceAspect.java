package com.example.shared.core.trace.aspect;

import com.example.shared.core.trace.annotation.BizTrace;
import com.example.shared.core.trace.context.BizContext;
import com.example.shared.core.trace.properties.TraceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全链路业务 ID 自动追踪切面
 * <p>
 * 功能：
 * 1. 智能嗅探：自动从参数中提取 bizId, batchId 等（基于反射缓存，无实例化高性能模式）
 * 2. 注解支持：处理 @BizTrace 注解，支持 SpEL 表达式
 * 3. 自动清理：方法结束后自动清理 MDC
 * 4. 安全防护：切面内部异常被隔离，不影响主业务逻辑
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class AutoBizTraceAspect {

  private final TraceProperties properties;
  private final ExpressionParser parser = new SpelExpressionParser();
  private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

  // 反射缓存：Class -> (FieldType -> PropertyName)
  private final Map<Class<?>, Map<String, Integer>> reflectionCache = new ConcurrentHashMap<>();

  private static final int TYPE_BIZ_ID = 1;
  private static final int TYPE_BATCH_ID = 2;
  private static final int TYPE_JNL_NO = 3;

  @Around("@within(org.springframework.web.bind.annotation.RestController) || @annotation(com.example.shared.core.trace.annotation.BizTrace)")
  public Object around(ProceedingJoinPoint point) throws Throwable {
    try {
      // 【安全防护】将整个切面逻辑包裹在 try-catch 中
      // 确保切面自身的任何 bug (如解析失败、空指针) 绝不阻断业务
      safeHandleTrace(point);
    } catch (Exception e) {
      // 仅记录 Debug 日志，避免刷屏
      log.debug("AutoBizTraceAspect 执行异常，已忽略", e);
    }

    try {
      // 执行业务逻辑
      return point.proceed();
    } finally {
      // 确保清理 MDC，防止线程污染
      BizContext.clear();
    }
  }

  private void safeHandleTrace(ProceedingJoinPoint point) {
    // 1. 优先处理注解 (兜底策略)
    MethodSignature signature = (MethodSignature) point.getSignature();
    Method method = signature.getMethod();
    BizTrace annotation = method.getAnnotation(BizTrace.class);

    if (annotation != null) {
      handleAnnotation(point, method, annotation);
    }

    // 2. 智能嗅探参数 (如果 MDC 里还没值，尝试从参数里找)
    if (needSniffing()) {
      handleSniffing(point.getArgs());
    }
  }

  private boolean needSniffing() {
    // 只要有任何一个 ID 还没值，就尝试去参数里找
    return BizContext.getBizId() == null ||
      BizContext.getBatchId() == null ||
      BizContext.getJnlNo() == null;
  }

  // —————— 注解处理逻辑 ——————

  private void handleAnnotation(ProceedingJoinPoint point, Method method, BizTrace bizTrace) {
    EvaluationContext context = null;

    if (StringUtils.hasText(bizTrace.bizId())) {
      context = ensureContext(context, point, method);
      BizContext.setBizId(parseExpression(bizTrace.bizId(), context));
    }

    if (StringUtils.hasText(bizTrace.batchId())) {
      context = ensureContext(context, point, method);
      BizContext.setBatchId(parseExpression(bizTrace.batchId(), context));
    }

    if (StringUtils.hasText(bizTrace.jnlNo())) {
      context = ensureContext(context, point, method);
      BizContext.setJnlNo(parseExpression(bizTrace.jnlNo(), context));
    }
  }

  /**
   * 懒加载上下文的辅助方法，消除 IDE "context always null" 警告
   */
  private EvaluationContext ensureContext(EvaluationContext currentContext, ProceedingJoinPoint point, Method method) {
    if (currentContext != null) {
      return currentContext;
    }
    return createEvaluationContext(point, method);
  }

  private EvaluationContext createEvaluationContext(ProceedingJoinPoint point, Method method) {
    StandardEvaluationContext context = new StandardEvaluationContext();
    Object[] args = point.getArgs();
    String[] paramNames = nameDiscoverer.getParameterNames(method);

    if (paramNames != null) {
      for (int i = 0; i < paramNames.length; i++) {
        context.setVariable(paramNames[i], args[i]);
      }
    }
    return context;
  }

  private String parseExpression(String expression, EvaluationContext context) {
    try {
      return parser.parseExpression(expression).getValue(context, String.class);
    } catch (Exception e) {
      log.warn("解析业务ID SpEL 失败: {}", expression, e);
      return null;
    }
  }

  // —————— 智能嗅探逻辑 ——————

  private void handleSniffing(Object[] args) {
    if (args == null) return;
    for (Object arg : args) {
      if (arg == null || isPrimitive(arg.getClass())) continue;

      // 获取该类字段的映射关系（带缓存）
      Map<String, Integer> fieldMapping = getCachedFieldMapping(arg.getClass());

      if (fieldMapping.isEmpty()) continue;

      BeanWrapper wrapper = new BeanWrapperImpl(arg);
      for (Map.Entry<String, Integer> entry : fieldMapping.entrySet()) {
        String propName = entry.getKey();
        Integer type = entry.getValue();

        try {
          Object val = wrapper.getPropertyValue(propName);
          if (val != null) {
            String strVal = val.toString();
            // 仅当 MDC 中对应 Key 为空时才注入，避免覆盖注解设置的值
            switch (type) {
              case TYPE_BIZ_ID -> {
                if (BizContext.getBizId() == null) BizContext.setBizId(strVal);
              }
              case TYPE_BATCH_ID -> {
                if (BizContext.getBatchId() == null) BizContext.setBatchId(strVal);
              }
              case TYPE_JNL_NO -> {
                if (BizContext.getJnlNo() == null) BizContext.setJnlNo(strVal);
              }
            }
          }
        } catch (Exception e) {
          // 忽略取值异常
        }
      }
    }
  }

  /**
   * 【优化】：反射缓存加载逻辑，不进行实例化
   * 直接分析类结构，避免 newInstance() 带来的副作用和性能开销
   */
  private Map<String, Integer> getCachedFieldMapping(Class<?> clazz) {
    return reflectionCache.computeIfAbsent(clazz, cls -> {
      Map<String, Integer> map = new ConcurrentHashMap<>();
      try {
        // 使用 Spring 的 BeanUtils 直接获取属性描述符，无需实例化对象
        PropertyDescriptor[] pds = org.springframework.beans.BeanUtils.getPropertyDescriptors(cls);

        for (PropertyDescriptor pd : pds) {
          if (pd.getReadMethod() == null) continue; // 必须有 Getter 方法

          String name = pd.getName();
          // 根据配置判断该字段属于哪种 ID
          if (properties.bizIdFields().contains(name)) map.put(name, TYPE_BIZ_ID);
          else if (properties.batchIdFields().contains(name)) map.put(name, TYPE_BATCH_ID);
          else if (properties.jnlNoFields().contains(name)) map.put(name, TYPE_JNL_NO);
        }
      } catch (Exception e) {
        // 忽略分析错误，下次可能会重试或一直为空
      }
      return map;
    });
  }

  private boolean isPrimitive(Class<?> clazz) {
    return clazz.isPrimitive() || clazz.getName().startsWith("java.") || clazz.isEnum();
  }
}
