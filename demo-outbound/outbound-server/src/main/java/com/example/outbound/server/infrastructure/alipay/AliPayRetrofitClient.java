package com.example.outbound.server.infrastructure.alipay;

import com.github.lianjiatech.retrofit.spring.boot.core.RetrofitClient;
import com.github.lianjiatech.retrofit.spring.boot.interceptor.Intercept;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;

import java.util.Map;

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
    // 使用 @HeaderMap 将 BaseExternalRequest 中的 headers 解包传递
  AliPayResponse doPay(@HeaderMap Map<String, String> headers, @Body AliPayRequest request);

  // 对于无参/简单请求，如果不需要 Header，可以保持原样
  @GET("payment/info")
  AliPayResponse queryPaymentInfo();
}
