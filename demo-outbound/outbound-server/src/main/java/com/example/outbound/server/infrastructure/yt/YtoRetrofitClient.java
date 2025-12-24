package com.example.outbound.server.infrastructure.yt;

import com.github.lianjiatech.retrofit.spring.boot.core.RetrofitClient;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * YtoRetrofitClient
 *
 * @author YourName
 * @since 2025/12/14 21:48
 */
@RetrofitClient(baseUrl = "${external.yto-express.url}")
public interface YtoRetrofitClient {

  @POST("/api/trace/query")
  YtoResponse queryTrace(@Body YtoRequest request);
}
