package com.pension.permission.domain.channel.event;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.CustomerChannelEntitlementId;

import java.time.LocalDateTime;

/**
 * 客户关闭某登录渠道事件.
 */
public record ChannelDisabled(
  CustomerChannelEntitlementId entitlementId,
  CustomerNo customerNo,
  AnnuityChannel channel,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo operator
) implements DomainEvent {

  public static ChannelDisabled of(
    CustomerChannelEntitlementId entitlementId,
    CustomerNo customerNo,
    AnnuityChannel channel,
    UserNo operator
  ) {
    return new ChannelDisabled(
      entitlementId,
      customerNo,
      channel,
      EventId.generate(),
      LocalDateTime.now(),
      operator
    );
  }
}
