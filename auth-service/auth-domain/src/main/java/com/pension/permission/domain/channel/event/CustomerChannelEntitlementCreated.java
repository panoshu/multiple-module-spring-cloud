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
 * 客户渠道开通记录创建事件.
 *
 * @param entitlementId       开通记录 ID
 * @param customerNo          客户编号
 * @param enabledChannels     初始开通的渠道集合
 * @param eventId             事件 ID
 * @param occurredOn          事件发生时间
 * @param createdBy           创建人
 */
public record CustomerChannelEntitlementCreated(
  CustomerChannelEntitlementId entitlementId,
  CustomerNo customerNo,
  Set<AnnuityChannel> enabledChannels,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static CustomerChannelEntitlementCreated of(
    CustomerChannelEntitlementId entitlementId,
    CustomerNo customerNo,
    Set<AnnuityChannel> enabledChannels,
    UserNo createdBy
  ) {
    return new CustomerChannelEntitlementCreated(
      entitlementId,
      customerNo,
      enabledChannels,
      EventId.generate(),
      LocalDateTime.now(),
      createdBy
    );
  }
}
