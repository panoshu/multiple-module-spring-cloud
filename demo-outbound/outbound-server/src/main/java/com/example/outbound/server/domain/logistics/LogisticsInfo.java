package com.example.outbound.server.domain.logistics;

import com.example.outbound.server.exception.OutboundErrorCode;
import com.example.shared.core.exception.BusinessException;

import java.util.List;

/**
 * LogisticsInfo
 *
 * @author YourName
 * @since 2025/12/14 21:33
 */
public record LogisticsInfo(
  String channel,
  LogisticsStatus status,
  String message,
  CargoType type,
  List<LogisticsNode> nodes
) {

  // --- 领域行为 (Behavior) ---

  /**
   * 业务规则：只有在运输中状态下，才允许发起拦截申请
   */
  public void checkInterceptable() {
    // 使用 CargoType 的逻辑
    if (this.type.needColdChain()) {
      // 抛出 BusinessException
      throw new BusinessException(
        OutboundErrorCode.STATUS_CANNOT_INTERCEPT,
        "生鲜产品发货后严禁拦截，存在变质风险"
      );
    }

    if (this.status != LogisticsStatus.TRANSPORTING) {
      throw new BusinessException(OutboundErrorCode.STATUS_CANNOT_INTERCEPT, "商品尚未开始运输");
    }
  }
}
