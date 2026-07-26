package com.example.iam.domain.authentication.event;

import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.types.LoginLogId;
import com.example.iam.types.UserId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 用户登录成功事件。
 *
 * <p>由 {@code LoginLog.createSuccess} 注册,触发后:
 * <ul>
 *   <li>应用层更新 User.lastLoginTime/lastLoginIp</li>
 *   <li>清除累计的失败次数(风控计数器重置)</li>
 *   <li>触发 sa-token 登录流程</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record UserLoginSucceededEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    LoginLogId loginLogId,
    UserId userId,
    ChannelType channelType,
    String loginIp,
    LocalDateTime loginTime
) implements DomainEvent {

  public static UserLoginSucceededEvent of(LoginLogId loginLogId, UserId userId,
                                            ChannelType channelType,
                                            String loginIp, LocalDateTime loginTime) {
    return new UserLoginSucceededEvent(EventId.generate(), LocalDateTime.now(),
        loginLogId, userId, channelType, loginIp, loginTime);
  }
}
