package com.example.auth.domain.channel.event;

import com.example.core.types.Channel;
import com.example.shared.domain.event.DomainEvent;
import com.example.core.types.CustomerNo;
import com.example.shared.identifier.id.EventId;
import com.example.core.types.UserNo;
import com.example.auth.types.CustomerChannelEntitlementId;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 客户渠道开通集合批量替换事件.
 */
public record CustomerChannelEntitlementReplaced(
  CustomerChannelEntitlementId entitlementId,
  CustomerNo customerNo,
  Set<Channel> oldChannels,
  Set<Channel> newChannels,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo operator
) implements DomainEvent {

  public static CustomerChannelEntitlementReplaced of(
    CustomerChannelEntitlementId entitlementId,
    CustomerNo customerNo,
    Set<Channel> oldChannels,
    Set<Channel> newChannels,
    UserNo operator
  ) {
    return new CustomerChannelEntitlementReplaced(
      entitlementId,
      customerNo,
      oldChannels,
      newChannels,
      EventId.generate(),
      LocalDateTime.now(),
      operator
    );
  }
}
