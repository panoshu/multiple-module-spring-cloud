package com.pension.permission.domain.fixture;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.types.SessionId;

import java.time.Duration;

/**
 * channel 域测试数据工厂。
 */
public final class ChannelFixtures {

  private ChannelFixtures() {}

  public static EffectiveIdentity directIdentity(String userNo) {
    return EffectiveIdentity.direct(UserNo.of(userNo));
  }

  public static Session onlineSession(String userNo) {
    return Session.create(
      new SessionId("s-1"),
      UserNo.of("creator-1"),
      UserNo.of(userNo),
      AnnuityChannel.NETAPP,
      directIdentity(userNo),
      Duration.ofHours(2));
  }

  public static Session branchSession(String userNo) {
    return Session.create(
      new SessionId("s-2"),
      UserNo.of("creator-1"),
      UserNo.of(userNo),
      AnnuityChannel.BANK_BRANCH,
      directIdentity(userNo),
      Duration.ofHours(2));
  }
}
