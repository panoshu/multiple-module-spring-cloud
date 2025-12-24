package com.example.outbound.server.domain.logistics;

import com.example.outbound.server.exception.OutboundErrorCode;
import com.example.shared.core.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * CargoType
 *
 * @author YourName
 * @since 2025/12/14 22:13
 */
@Getter
@AllArgsConstructor
public enum CargoType {

  /** 普通货物 */
  STANDARD("STD", "普通货物"),

  /** 生鲜 (需要冷链，通常走顺丰) */
  FRESH("FRESH", "生鲜食品"),

  /** 电子产品 (高价值，需要保价) */
  ELECTRONICS("ELEC", "电子数码"),

  /** 大件货物 */
  BULKY("BULKY", "大件物流");

  private final String code;
  private final String description;

  /**
   * 静态工厂方法：用于将 API 传入的 String 转换为领域枚举
   * 如果找不到，抛出业务异常
   */
  public static CargoType resolve(String code) {
    return Arrays.stream(values())
      .filter(type -> type.code.equalsIgnoreCase(code))
      .findFirst()
      .orElseThrow(() -> new BusinessException(
        OutboundErrorCode.PARAM_ERROR,
        "不支持的货物类型: " + code
      ));
  }

  /**
   * 领域逻辑：是否需要冷链
   */
  public boolean needColdChain() {
    return this == FRESH;
  }
}
