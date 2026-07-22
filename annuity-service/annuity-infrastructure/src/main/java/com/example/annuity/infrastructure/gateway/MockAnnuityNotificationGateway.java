package com.example.annuity.infrastructure.gateway;

import com.example.annuity.domain.aggregate.valueobject.NotificationType;
import com.example.annuity.domain.gateway.AnnuityNotificationGateway;
import com.example.shared.primitives.identity.UserNo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock 通知网关实现（演示环境）
 * <p>
 * 记录通知到内存列表,供测试断言。生产环境替换为真实通知服务。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Slf4j
@Component
@Primary
public class MockAnnuityNotificationGateway implements AnnuityNotificationGateway {

  private final List<NotificationRecord> sentNotifications = new ArrayList<>();

  @Override
  public void notifyOperator(UserNo operatorNo, NotificationType type, String content) {
    log.info("[Mock] 发送通知, operator={}, type={}, content={}", operatorNo.value(), type, content);
    sentNotifications.add(new NotificationRecord(operatorNo, type, content));
  }

  public List<NotificationRecord> getSentNotifications() {
    return List.copyOf(sentNotifications);
  }

  public void clear() {
    sentNotifications.clear();
  }

  public record NotificationRecord(UserNo operatorNo, NotificationType type, String content) {
  }
}
