package com.example.shared.core.trace;

/**
 * TraceConstants
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2025/12/27 22:11
 */
public class TraceConstants {
  public static final String HEADER_BIZ_ID = "X-Biz-Id";
  public static final String HEADER_BATCH_ID = "X-Batch-Id";
  public static final String HEADER_JNL_NO = "X-Jnl-No";
  // 也可以复用标准的 Traceparent 头，这里演示自定义业务头
}
