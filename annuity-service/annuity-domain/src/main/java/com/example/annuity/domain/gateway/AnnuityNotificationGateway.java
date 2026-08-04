package com.example.annuity.domain.gateway;

import com.example.annuity.domain.aggregate.valueobject.NotificationType;
import com.example.shared.identifier.id.UserNo;

/**
 * 年金通知网关接口
 * <p>
 * 防腐层接口,供 application 层的扩展动作发送通知。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public interface AnnuityNotificationGateway {

  /**
   * 通知操作人
   *
   * @param operatorNo 操作人编号
   * @param type       通知类型
   * @param content    通知内容
   */
  void notifyOperator(UserNo operatorNo, NotificationType type, String content);
}
