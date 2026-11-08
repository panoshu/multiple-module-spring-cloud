package com.example.auth.domain.fixture;

import com.example.core.types.Channel;
import com.example.core.types.UserNo;
import com.example.auth.domain.channel.aggregate.Session;
import com.example.auth.domain.channel.valueobject.EffectiveIdentity;
import com.example.auth.types.SessionId;

import java.time.Duration;

/**
 * channel 域测试数据工厂。
 */
public final class ChannelFixtures {

  private ChannelFixtures() {
  }

  public static EffectiveIdentity directIdentity(String userNo) {
    return EffectiveIdentity.direct(UserNo.of(userNo));
  }

  public static Session onlineSession(String userNo) {
    return Session.create(
      new SessionId("s-1"),
      UserNo.of("creator-1"),
      UserNo.of(userNo),
      Channel.NETAPP,
      directIdentity(userNo),
      Duration.ofHours(2));
  }

  public static Session branchSession(String userNo) {
    return Session.create(
      new SessionId("s-2"),
      UserNo.of("creator-1"),
      UserNo.of(userNo),
      Channel.BANK_BRANCH,
      directIdentity(userNo),
      Duration.ofHours(2));
  }
}
