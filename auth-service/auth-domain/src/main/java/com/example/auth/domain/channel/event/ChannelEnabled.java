package com.example.auth.domain.channel.event;

import com.example.core.types.Channel;
import com.example.shared.domain.event.DomainEvent;
import com.example.core.types.CustomerNo;
import com.example.shared.identifier.id.EventId;
import com.example.core.types.UserNo;
import com.example.auth.types.CustomerChannelEntitlementId;

import java.time.LocalDateTime;

/**
 * 客户开通某登录渠道事件.
 */
public record ChannelEnabled(
  CustomerChannelEntitlementId entitlementId,
  CustomerNo customerNo,
  Channel channel,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo operator
) implements DomainEvent {

  public static ChannelEnabled of(
    CustomerChannelEntitlementId entitlementId,
    CustomerNo customerNo,
    Channel channel,
    UserNo operator
  ) {
    return new ChannelEnabled(
      entitlementId,
      customerNo,
      channel,
      EventId.generate(),
      LocalDateTime.now(),
      operator
    );
  }
}
