package com.example.integration.infrastructure.core.common.gateway;

import com.example.integration.infrastructure.core.common.adapter.TradeQueryClient;
import com.example.integration.infrastructure.core.common.annotation.TradeCode;
import com.example.integration.infrastructure.core.common.model.TradeReqHead;
import com.example.integration.infrastructure.core.common.model.TradeRootRequest;
import com.example.shared.client.gateway.BaseGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.util.function.Function;
import java.util.function.UnaryOperator;

public abstract class BaseTradeGateway extends BaseGateway {

  private final ObjectMapper objectMapper;
  private final TradeQueryClient tradeQueryClient;

  protected BaseTradeGateway(ObjectMapper objectMapper, TradeQueryClient tradeQueryClient) {
    this.objectMapper = objectMapper;
    this.tradeQueryClient = tradeQueryClient;
  }

  /**
   * 完整版：组装 -> 调用 -> 解包 -> Jackson转换 -> 领域转换
   */
  protected <Req, Res, Target> Target performTradeCall(
    Req body,
    UnaryOperator<TradeReqHead> headCustomizer,
    Class<Res> responseClass,
    Function<Res, Target> mapper
  ) {
    // 1. 校验注解获取交易码
    TradeCode annotation = body.getClass().getAnnotation(TradeCode.class);
    Assert.notNull(annotation, "Request body must be annotated with @TradeCode: " + body.getClass());

    // 2. 创建并定制 Head
    TradeReqHead head = TradeReqHead.createDefault(annotation.code());
    if (headCustomizer != null) {
      head = headCustomizer.apply(head);
    }

    // 3. 组装请求（使用 Object 擦除泛型，适配 Retrofit）
    TradeRootRequest<Object> request = TradeRootRequest.assembly(head, body);

    // 4. 执行调用（使用 performRaw 获取原始 Object）
    Object rawData = performRaw(() -> {
      // 这里利用 TradeRootResponse 实现的 ExternalResult 接口
      return tradeQueryClient.execute(request);
    });

    // 5. Jackson 转换为具体响应类型
    Res response = objectMapper.convertValue(rawData, responseClass);

    // 6. 转换为领域模型
    return mapper.apply(response);
  }

  /**
   * 简化版：不需要自定义 Head
   */
  protected <Req, Res, Target> Target performTradeCall(
    Req body,
    Class<Res> responseClass,
    Function<Res, Target> mapper
  ) {
    return performTradeCall(body, null, responseClass, mapper);
  }

  /**
   * 最简版：响应直接作为领域模型
   */
  protected <Req, Res> Res performTradeCall(
    Req body,
    Class<Res> responseClass
  ) {
    return performTradeCall(body, null, responseClass, Function.identity());
  }

  /**
   * 带 Head 自定义的最简版
   */
  protected <Req, Res> Res performTradeCall(
    Req body,
    UnaryOperator<TradeReqHead> headCustomizer,
    Class<Res> responseClass
  ) {
    return performTradeCall(body, headCustomizer, responseClass, Function.identity());
  }
}
