package com.pension.permission.domain.authorization.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.GrantId;

import java.time.LocalDateTime;

/**
 * 最关键的一个事件：账号冻结/代办关系撤销这类紧急场景，
 * 应用层监听到这条事件后应该同步淘汰对应身份的有效权限快照缓存，不等TTL。
 */
public record GrantRevoked(
  GrantId grantId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static GrantRevoked of(GrantId grantId, UserNo createdBy) {
    return new GrantRevoked(grantId, EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
