package com.example.integration.infrastructure.core.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

// 对应最外层的 JSON
public record TradeRootRequest<T>(
  @JsonProperty("AppRequest") AppRequest<T> appRequest
) implements Serializable {

  public static <T> TradeRootRequest<T> assembly(TradeReqHead head, T body) {
    return new TradeRootRequest<>(new AppRequest<>(head, body));
  }

  public record AppRequest<T>(
    @JsonProperty("AppReqHead") TradeReqHead head,
    @JsonProperty("AppReqBody") T body
  ) implements Serializable {
  }
}
