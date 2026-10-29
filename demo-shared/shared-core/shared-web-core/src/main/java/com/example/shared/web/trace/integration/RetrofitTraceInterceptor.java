package com.example.shared.web.trace.integration;

import com.example.shared.web.trace.config.TraceContextProperties;
import com.github.lianjiatech.retrofit.spring.boot.interceptor.GlobalInterceptor;
import lombok.RequiredArgsConstructor;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Retrofit 业务上下文透传拦截器
 * 实现 GlobalInterceptor 接口，用于 Retrofit Spring Boot Starter
 */
@RequiredArgsConstructor
public class RetrofitTraceInterceptor implements GlobalInterceptor {

  private final TraceContextProperties properties;

  @NotNull
  @Override
  public Response intercept(@NotNull Interceptor.Chain chain) throws IOException {
    // 复用 OkHttpTraceInterceptor 的逻辑
    return new OkHttpTraceInterceptor(properties).intercept(chain);
  }
}
