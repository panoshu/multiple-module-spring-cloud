package com.example.outbound.server.infrastructure;

import com.github.lianjiatech.retrofit.spring.boot.interceptor.GlobalInterceptor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * GlobalAuditInterceptor
 *
 * @author YourName
 * @since 2025/12/14 21:06
 */
@Component
@Slf4j
public class GlobalAuditInterceptor implements GlobalInterceptor {
  @Override
  public @NonNull Response intercept(Chain chain) throws IOException {
    long start = System.nanoTime();
    Request request = chain.request();

    try {
      Response response = chain.proceed(request);
      long cost = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

      // 打印统一日志格式：[系统] [耗时] [URL] [状态码]
      log.info("[External-Call] [{}ms] [{}] [{}]", cost, request.url(), response.code());
      return response;
    } catch (Exception e) {
      log.error("[External-Call] Failed: {}", request.url(), e);
      throw e;
    }
  }
}
