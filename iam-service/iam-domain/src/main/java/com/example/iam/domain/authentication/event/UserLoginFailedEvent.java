package com.example.iam.domain.authentication.event;

import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.types.LoginLogId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 用户登录失败事件。
 *
 * <p>由 {@code LoginLog.createFailure} 注册,触发后:
 * <ul>
 *   <li>LoginRiskService 累加失败次数</li>
 *   <li>连续失败达阈值时触发 User.lock(账号锁定)</li>
 *   <li>同 IP 失败达阈值时触发 IP 黑名单</li>
 * </ul>
 *
 * <p>{@code userId} 可空(用户不存在场景),此时仅记录 IP 风控。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record UserLoginFailedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    LoginLogId loginLogId,
    Long userId,
    String loginName,
    ChannelType channelType,
    String loginIp,
    String reason
) implements DomainEvent {

  public static UserLoginFailedEvent of(LoginLogId loginLogId, Long userId,
                                         String loginName, ChannelType channelType,
                                         String loginIp, String reason) {
    return new UserLoginFailedEvent(EventId.generate(), LocalDateTime.now(),
        loginLogId, userId, loginName, channelType, loginIp, reason);
  }
}
