package com.example.shared.web.trace.filter;

import com.example.shared.web.trace.config.TraceContextProperties;
import com.example.shared.web.trace.spi.BizContextAccessor;
import com.example.shared.web.trace.spi.ThrowableSupplier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.example.shared.web.trace.constant.TraceConstants.CONTEXT_BACKUP_KEY;

/**
 * 业务上下文入站过滤器
 * 优先级设置得较高，确保在进入 Controller 之前完成上下文初始化。
 *
 * <p>V-05：traceId 是链路追踪的基础设施字段，不依赖 Micrometer。过滤器优先复用
 * 上游传入的 {@code X-Trace-Id}，缺失或非法时生成 32 位十六进制 ID；写入 MDC 后，
 * 业务日志与出站 RestClient/Retrofit/OkHttp 拦截器即可自动携带。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class BizContextPropagationFilter extends OncePerRequestFilter {

  /**
   * MDC 中的链路 ID 键（logback pattern 使用 {@code %X{traceId}}）。
   */
  public static final String TRACE_ID_MDC_KEY = "traceId";

  private static final String TRACE_RESPONSE_HEADER = "trace-id";
  private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

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

    // 2. 解析/生成 traceId（上游透传优先，非法值丢弃后重新生成）
    String traceHeaderKey = properties.getHeaderKey(TRACE_ID_MDC_KEY);
    String traceId = resolveTraceId(request.getHeader(traceHeaderKey));
    contextMap.put(TRACE_ID_MDC_KEY, traceId);
    response.setHeader(traceHeaderKey, traceId);
    response.setHeader(TRACE_RESPONSE_HEADER, traceId);

    // 3. 备份到 Request Attribute (给 GlobalExceptionHandler 用)
    request.setAttribute(CONTEXT_BACKUP_KEY, new HashMap<>(contextMap));

    // 4. 执行链路
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

  private String resolveTraceId(String candidate) {
    if (candidate != null && VALID_TRACE_ID.matcher(candidate).matches()) {
      return candidate;
    }
    return UUID.randomUUID().toString().replace("-", "");
  }
}
