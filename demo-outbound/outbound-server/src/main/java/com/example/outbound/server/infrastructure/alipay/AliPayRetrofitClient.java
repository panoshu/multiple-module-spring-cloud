package com.example.outbound.server.infrastructure.alipay;

import com.github.lianjiatech.retrofit.spring.boot.core.RetrofitClient;
import com.github.lianjiatech.retrofit.spring.boot.interceptor.Intercept;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * AliPayRetrofitApi
 *
 * @author YourName
 * @since 2025/12/14 20:59
 */
@RetrofitClient(baseUrl = "${external.alipay.url}")
@Intercept(handler = AliPaySignInterceptor.class)
public interface AliPayRetrofitClient {

  @POST("gateway.do")
    // 使用 DTO 接收外部系统的原始响应
  AliPayResponse doPay(@Body AliPayRequest request);
}
