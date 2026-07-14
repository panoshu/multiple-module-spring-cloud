package com.example.shared.web.trace.integration;

import com.example.shared.web.trace.config.TraceContextProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;

import java.io.IOException;

/**
 * OkHttp/Retrofit 业务上下文透传拦截器
 */
@Slf4j
@RequiredArgsConstructor
public class OkHttpTraceInterceptor implements Interceptor {

  private final TraceContextProperties properties;

  @NotNull
  @Override
  public Response intercept(Chain chain) throws IOException {
    Request.Builder builder = chain.request().newBuilder();

    for (String alias : properties.getAliases()) {
      String value = MDC.get(alias);
      if (value != null) {
        builder.addHeader(properties.getHeaderKey(alias), value);
      }
    }

    return chain.proceed(builder.build());
  }
}
