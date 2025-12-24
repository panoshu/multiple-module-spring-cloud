package com.example.gateway.order;

public enum GatewayFilterOrder {

  // 安全组（最早执行）
  IP_BLOCK(-300),
  EXCLUDE_ROUTE(-250),
  AUTH(-200),

  // 流控组
  RATE_LIMIT(-100),

  // 业务前置
  TENANT_RESOLVE(-50),

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
