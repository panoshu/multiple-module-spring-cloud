package com.pension.permission.domain.channel.event;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.CustomerChannelEntitlementId;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 客户渠道开通集合批量替换事件.
 */
public record CustomerChannelEntitlementReplaced(
  CustomerChannelEntitlementId entitlementId,
  CustomerNo customerNo,
  Set<AnnuityChannel> oldChannels,
  Set<AnnuityChannel> newChannels,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo operator
) implements DomainEvent {

  public static CustomerChannelEntitlementReplaced of(
    CustomerChannelEntitlementId entitlementId,
    CustomerNo customerNo,
    Set<AnnuityChannel> oldChannels,
    Set<AnnuityChannel> newChannels,
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
