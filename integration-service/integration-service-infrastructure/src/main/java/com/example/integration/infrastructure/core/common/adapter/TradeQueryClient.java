package com.example.integration.infrastructure.core.common.adapter;

import com.example.integration.infrastructure.core.common.model.TradeRootRequest;
import com.example.integration.infrastructure.core.common.model.TradeRootResponse;
import com.example.shared.client.decoder.SimpleErrorDecoder;
import com.github.lianjiatech.retrofit.spring.boot.core.RetrofitClient;
import retrofit2.http.Body;
import retrofit2.http.POST;

@RetrofitClient(
  baseUrl = "${external.trade.url:http://25.35.2.20:18007/trade/api/}",
  errorDecoder = SimpleErrorDecoder.class
)
public interface TradeQueryClient {

  /**
   * 通用查询接口
   * 因为所有交易都在同一个URL下，通过Body内容区分
   */
  @POST("fail")
  <Req, Res> TradeRootResponse<Res> query(
    @Body TradeRootRequest<Req> request
  );

  @POST("query")
  TradeRootResponse<Object> execute(@Body TradeRootRequest<Object> request);

}
