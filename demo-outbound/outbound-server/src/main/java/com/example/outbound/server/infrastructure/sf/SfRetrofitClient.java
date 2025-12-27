package com.example.outbound.server.infrastructure.sf;

import com.github.lianjiatech.retrofit.spring.boot.core.RetrofitClient;
import retrofit2.http.Body;
import retrofit2.http.HeaderMap;
import retrofit2.http.Headers;
import retrofit2.http.POST;

import java.util.Map;

/**
 * SfRetrofitClient
 *
 * @author YourName
 * @since 2025/12/14 21:32
 */
@RetrofitClient(baseUrl = "${external.sf-express.url}")
public interface SfRetrofitClient {

  @POST("/api/routes/query")
  // 这里如果需要特定的 Headers，可以使用 @Headers 或 局部拦截器
  @Headers("Content-Type: application/json")
  SfQueryResponse doQuery(@HeaderMap Map<String, String> headers, @Body SfQueryRequest req);
}
