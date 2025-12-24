package com.example.outbound.server.domain.logistics;

/**
 * CarrierSelectionPolicy
 *
 * @author YourName
 * @since 2025/12/14 22:02
 */
public class CarrierSelectionPolicy {

  /**
   * 根据货物属性决定走哪个渠道
   * 这段逻辑完全独立于框架，非常容易写单元测试
   */
  public String selectChannel(double weight, CargoType type) {
    // 业务规则 1: 生鲜必须走顺丰
    if (CargoType.FRESH.equals(type)) {
      return "SF";
    }
    // 业务规则 2: 小件普通货物走圆通
    if (weight < 1.0) {
      return "YTO";
    }
    // 默认走顺丰
    return "SF";
  }
}
