package com.example.shared.core.trace.filter;

import com.example.shared.core.trace.TraceConstants;
import com.example.shared.core.trace.context.BizContext;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;

public class TraceClientInterceptor implements Interceptor {

  private static final Logger log = LoggerFactory.getLogger(TraceClientInterceptor.class);

  @NotNull
  @Override
  public Response intercept(@NotNull Chain chain) throws IOException {
    Request original = chain.request();
    Request.Builder builder = original.newBuilder();

    try {
      // 【安全防护】注入过程异常被捕获
      injectHeaders(builder);
    } catch (Exception e) {
      log.debug("TraceClientInterceptor 注入 Header 失败", e);
    }

    return chain.proceed(builder.build());
  }

  private void injectHeaders(Request.Builder builder) {
    String bizId = BizContext.getBizId();
    if (StringUtils.hasText(bizId)) {
      builder.header(TraceConstants.HEADER_BIZ_ID, bizId);
    }

    String batchId = BizContext.getBatchId();
    if (StringUtils.hasText(batchId)) {
      builder.header(TraceConstants.HEADER_BATCH_ID, batchId);
    }

    String jnlNo = BizContext.getJnlNo();
    if (StringUtils.hasText(jnlNo)) {
      builder.header(TraceConstants.HEADER_JNL_NO, jnlNo);
    }
  }
}
