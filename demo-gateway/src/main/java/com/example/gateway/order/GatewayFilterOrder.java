package com.example.gateway.order;

public enum GatewayFilterOrder {

  // 安全组（最早执行）
  IP_BLOCK(-300),
  EXCLUDE_ROUTE(-250),
  AUTH(-200),
  // 会话上下文注入（在 AUTH 之后，签名透传下游）
  SESSION_CONTEXT_INJECT(-150),

  // 流控组
  RATE_LIMIT(-100),

  // 业务前置
  TENANT_RESOLVE(-50),

  // 加密组
  CRYPTO(-10),

  // 默认层
  REQUEST_LOG(0),

  // 最后进行响应重写等操作
  RESPONSE_REWRITE(200);

  private final int order;

  GatewayFilterOrder(int order) {
    this.order = order;
  }

  public int value() {
    return this.order;
  }
}
