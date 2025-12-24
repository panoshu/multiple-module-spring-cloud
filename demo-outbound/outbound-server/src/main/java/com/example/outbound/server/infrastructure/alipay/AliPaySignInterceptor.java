package com.example.outbound.server.infrastructure.alipay;

import com.github.lianjiatech.retrofit.spring.boot.interceptor.BasePathMatchInterceptor;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * AliPaySignInterceptor
 *
 * @author YourName
 * @since 2025/12/14 21:04
 */
@Component
public class AliPaySignInterceptor extends BasePathMatchInterceptor {
  @Override
  public @NonNull Response intercept(Chain chain) throws IOException {
    // 实现针对支付宝的加签逻辑...
    return chain.proceed(chain.request());
  }

  @Override
  protected Response doIntercept(Chain chain) {
    return null;
  }
}
