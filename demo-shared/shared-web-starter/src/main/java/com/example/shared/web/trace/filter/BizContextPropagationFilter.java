package com.example.shared.web.trace.filter;

import com.example.shared.web.trace.config.TraceContextProperties;
import com.example.shared.web.trace.spi.BizContextAccessor;
import com.example.shared.web.trace.spi.ThrowableSupplier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.example.shared.web.trace.constant.TraceConstants.CONTEXT_BACKUP_KEY;

/**
 * 业务上下文入站过滤器
 * 优先级设置得较高，确保在进入 Controller 之前完成上下文初始化
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class BizContextPropagationFilter extends OncePerRequestFilter {

  private final TraceContextProperties properties;
  private final BizContextAccessor contextAccessor;

  @Override
  protected void doFilterInternal(@NotNull HttpServletRequest request,
                                  @NotNull HttpServletResponse response,
                                  @NotNull FilterChain filterChain) throws ServletException, IOException {

    Map<String, String> contextMap = new HashMap<>();

    // 1. 处理业务 ID -> MDC (从 Header 提取)
    for (String headerKey : properties.getHeaderKeys()) {
      String val = request.getHeader(headerKey);
      if (val != null) {
        contextMap.put(properties.getAlias(headerKey), val);
        response.setHeader(headerKey, val); // 回填业务 ID
      }
    }

    // 2. 显式将 Trace ID 写入 Response Header
    // 尝试从 MDC 获取 TraceId 并写入响应头
    // 如果项目引入了 Micrometer，它会在更早的 Filter 中把 traceId 放入 MDC
    // 常见的 Key 是 "traceId" 或 "trace_id"，Logback 默认通常是 "traceId"
    String traceId = MDC.get("traceId");
    if (traceId != null) {
      response.setHeader("trace-id", traceId);
    }

    // 3. 备份到 Request Attribute (给 GlobalExceptionHandler 用)
    request.setAttribute(CONTEXT_BACKUP_KEY, new HashMap<>(contextMap));

    // 3. 执行链路
    try {
      contextAccessor.withContext(contextMap, (ThrowableSupplier<Void>) () -> {
        filterChain.doFilter(request, response);
        return null;
      });
    } catch (ServletException | IOException | RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new ServletException(e);
    }
  }
}
