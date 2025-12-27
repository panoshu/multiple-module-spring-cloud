package com.example.shared.core.trace.filter;

import com.example.shared.core.trace.TraceConstants;
import com.example.shared.core.trace.context.BizContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceContextFilter extends HttpFilter {

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
    try {
      // 【安全防护】Trace 逻辑出错不应影响业务
      safeExtract(request, response);

      // 执行业务
      chain.doFilter(request, response);

    } finally {
      // 清理 MDC，防止线程污染
      BizContext.clear();
    }
  }

  private void safeExtract(HttpServletRequest request, HttpServletResponse response) {
    try {
      // 1. 从 Request Header 提取到 MDC
      String bizId = request.getHeader(TraceConstants.HEADER_BIZ_ID);
      if (StringUtils.hasText(bizId)) {
        BizContext.setBizId(bizId);
        // 【优化】提取到 ID 后，立即写入 Response Header
        // 这样做的好处是避免业务执行完后 Response 已提交导致无法写入
        response.setHeader(TraceConstants.HEADER_BIZ_ID, bizId);
      }

      String batchId = request.getHeader(TraceConstants.HEADER_BATCH_ID);
      if (StringUtils.hasText(batchId)) {
        BizContext.setBatchId(batchId);
        response.setHeader(TraceConstants.HEADER_BATCH_ID, batchId);
      }

      String jnlNo = request.getHeader(TraceConstants.HEADER_JNL_NO);
      if (StringUtils.hasText(jnlNo)) {
        BizContext.setJnlNo(jnlNo);
        response.setHeader(TraceConstants.HEADER_JNL_NO, jnlNo);
      }
    } catch (Exception e) {
      // 仅记录 Debug 日志，不干扰主流程
      log.debug("TraceContextFilter 提取/注入 Header 失败", e);
    }
  }
}
