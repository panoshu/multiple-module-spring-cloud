package com.example.shared.web.trace.aspect;

import com.example.shared.web.core.annotation.BizTag;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.trace.config.TraceContextProperties;
import com.example.shared.web.trace.spi.BizContextAccessor;
import com.example.shared.web.trace.spi.ThrowableSupplier;
import com.example.shared.web.trace.util.BizTagReflectionExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.example.shared.web.trace.constant.TraceConstants.CONTEXT_BACKUP_KEY;

/**
 * 业务上下文切面
 * <p>
 * 职责：
 * 1. 在 Controller 执行前，扫描入参中的 ID，注入 Tracing Scope (MDC/Baggage)。
 * 2. 在 Controller 执行后，扫描返回值中的 ID，注入 Response Header。
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class BizContextAspect {

  private final TraceContextProperties properties;
  private final BizContextAccessor contextAccessor;

  // 拦截所有 @RestController 类中的 public 方法
  @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
  public void restControllerMethods() {
  }

  @Around("restControllerMethods()")
  public Object handleBizContext(ProceedingJoinPoint joinPoint) throws Throwable {

    // 1. 【Input 阶段】提取入参中的业务 ID
    Map<String, String> inputContext = new HashMap<>();
    try {
      extractInputContext(joinPoint, inputContext);

      // 【备份到 Request Attribute，给 GlobalExceptionHandler 备用
      backupToRequest(inputContext);

      // 入参提取到的 ID，立即回写到响应头
      if (!inputContext.isEmpty()) {
        writeToResponseHeader(inputContext);
      }

    } catch (Exception e) {
      log.warn("[BizContext] Failed to extract input context", e);
    }

    // 2. 【Execution 阶段】在 Context Scope 中执行业务逻辑
    return contextAccessor.withContext(inputContext, (ThrowableSupplier<Object>) () -> {

      Object result = joinPoint.proceed();

      // 3. 【Output 阶段】提取返回值中的业务 ID 并写入响应头
      try {
        processOutputContext(result);
      } catch (Exception e) {
        log.warn("[BizContext] Failed to process output context", e);
      }

      return result;
    });
  }

  /**
   * 提取入参上下文
   */
  private void extractInputContext(ProceedingJoinPoint joinPoint, Map<String, String> contextMap) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Object[] args = joinPoint.getArgs();
    Parameter[] parameters = signature.getMethod().getParameters();

    // 遍历所有参数
    for (int i = 0; i < parameters.length; i++) {
      Object argValue = args[i];
      Parameter parameter = parameters[i];

      if (argValue == null) {
        continue;
      }

      // A. 简单类型参数注解：public void update(@BizTag("order_id") String id)
      if (parameter.isAnnotationPresent(BizTag.class)) {
        BizTag annotation = parameter.getAnnotation(BizTag.class);
        String alias = annotation.value();
        if (properties.hasAlias(alias)) {
          contextMap.put(alias, argValue.toString());
        }
      }
      // B. 复杂对象/Record：public void create(@RequestBody OrderCmd cmd)
      else {
        extractFromComplexObject(argValue, contextMap);
      }
    }
  }

  /**
   * 处理返回值上下文 (写入 Response Header)
   */
  private void processOutputContext(Object rawResult) {
    if (rawResult == null) {
      return;
    }

    // 1. 智能拆箱 ResponseEntity
    Object actualBody = switch (rawResult) {
      case ResponseEntity<?> responseEntity
        when responseEntity.getStatusCode().is2xxSuccessful() -> responseEntity.getBody();
      case ApiResult<?> result when result.isSuccess() -> result.data();
      default -> null; // 包括 null、其他类型、失败状态
    };

    if (actualBody == null) {
      return;
    }

    // 2. 提取 ID 并写入 Header
    Map<String, String> outputMap = new HashMap<>();
    extractFromComplexObject(actualBody, outputMap);

    if (!outputMap.isEmpty()) {
      writeToResponseHeader(outputMap);
    }
  }

  /**
   * 从复杂对象 (Class 或 Record) 中利用反射提取 @BizTag
   */
  private void extractFromComplexObject(Object target, Map<String, String> collector) {
    // 性能优化：跳过集合、数组、Map 以及 JDK 原生类型
    if (target == null || target instanceof Collection<?> || target instanceof Map<?, ?> || target.getClass().isArray() || target.getClass().getPackageName().startsWith("java.")) {
      return;
    }

    // 使用带缓存的反射工具提取字段
    var fields = BizTagReflectionExtractor.getAnnotatedFields(target.getClass());

    for (Field field : fields) {
      try {
        Object value = field.get(target);
        if (value != null) {
          BizTag annotation = field.getAnnotation(BizTag.class);
          if (properties.hasAlias(annotation.value())) {
            collector.put(annotation.value(), value.toString());
          }
        }
      } catch (IllegalAccessException e) {
        // 忽略无法访问的字段
      }
    }
  }

  /**
   * 将收集到的 ID 写入 HttpServletResponse Header
   */
  private void writeToResponseHeader(Map<String, String> headers) {
    HttpServletResponse response = getHttpServletResponse();
    // 1. 检查 Response 状态
    if (response == null) {
      log.warn("[BizContext] HttpServletResponse is null, cannot write headers");
      return;
    }
    if (response.isCommitted()) {
      log.warn("[BizContext] Response is already committed, cannot write headers: {}", headers.keySet());
      return;
    }

    headers.forEach((key, value) -> {
      log.debug("[BizContext] Writing header [{}:{}] to response", key, value);
      // 避免覆盖已有的 Header
      if (!response.containsHeader(key) && properties.hasAlias(key)) {
        response.setHeader(properties.getHeaderKey(key), value);
        log.debug("[BizContext] Writing response header: {}={}", properties.getHeaderKey(key), value);
      }
    });
  }

  /**
   * 【新增】将当前 Context 备份到 Request 域
   */
  private void backupToRequest(Map<String, String> currentContext) {
    if (currentContext == null || currentContext.isEmpty()) {
      return;
    }

    HttpServletRequest request = getHttpServletRequest();
    if (request != null) {
      try {
        // 取出已有的备份 (来自 Filter)
        @SuppressWarnings("unchecked")
        Map<String, String> backup = (Map<String, String>) request.getAttribute(CONTEXT_BACKUP_KEY);
        if (backup == null) {
          backup = new HashMap<>();
          request.setAttribute(CONTEXT_BACKUP_KEY, backup);
        }
        // 合并当前 Body 解析出的 ID
        backup.putAll(currentContext);
      } catch (Exception e) {
        // 忽略备份失败，不影响主流程
      }
    }
  }

  /**
   * 安全获取 HttpServletResponse (原有方法)
   */
  private HttpServletResponse getHttpServletResponse() {
    var attrs = RequestContextHolder.getRequestAttributes();
    if (attrs instanceof ServletRequestAttributes sra) {
      return sra.getResponse();
    }
    return null;
  }

  /**
   * 【新增】安全获取 HttpServletRequest (你缺失的方法)
   */
  private HttpServletRequest getHttpServletRequest() {
    var attrs = RequestContextHolder.getRequestAttributes();
    if (attrs instanceof ServletRequestAttributes sra) {
      return sra.getRequest();
    }
    return null;
  }
}
