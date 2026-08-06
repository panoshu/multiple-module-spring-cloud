package com.pension.permission.domain.channel.aggregate;

import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.enumeration.SessionStatus;
import com.pension.permission.domain.channel.event.SessionClosed;
import com.pension.permission.domain.channel.event.SessionCreated;
import com.pension.permission.domain.channel.event.SessionPlanSelected;
import com.pension.permission.domain.fixture.ChannelFixtures;
import com.pension.permission.types.SecondaryAuthSessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Session 聚合根测试")
class SessionTest {

  @Nested
  @DisplayName("创建 create")
  class CreateTest {

    @Test
    @DisplayName("应创建 ACTIVE 状态会话并注册事件")
    void shouldCreateActiveSession() {
      var session = ChannelFixtures.onlineSession("user-1");

      assertThat(session.status()).isEqualTo(SessionStatus.ACTIVE);
      assertThat(session.domainEvents()).anyMatch(e -> e instanceof SessionCreated);
    }
  }

  @Nested
  @DisplayName("计划选择 selectPlan")
  class SelectPlanTest {

    @Test
    @DisplayName("选择计划应更新 selectedPlanId 并注册事件")
    void shouldSelectPlan() {
      var session = ChannelFixtures.onlineSession("user-1");

      session.selectPlan(PlanNo.of("PLAN-001"), UserNo.of("user-1"));

      assertThat(session.selectedPlanId()).isEqualTo(PlanNo.of("PLAN-001"));
      assertThat(session.domainEvents()).anyMatch(e -> e instanceof SessionPlanSelected);
    }

    @Test
    @DisplayName("planId 为 null 应抛 IllegalArgumentException")
    void shouldThrowWhenPlanIdNull() {
      var session = ChannelFixtures.onlineSession("user-1");

      assertThatThrownBy(() -> session.selectPlan(null, UserNo.of("user-1")))
        .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("关闭 close")
  class CloseTest {

    @Test
    @DisplayName("关闭活跃会话应转为 CLOSED 并注册事件")
    void shouldCloseActiveSession() {
      var session = ChannelFixtures.onlineSession("user-1");

      session.close(UserNo.of("user-1"));

      assertThat(session.status()).isEqualTo(SessionStatus.CLOSED);
      assertThat(session.domainEvents()).anyMatch(e -> e instanceof SessionClosed);
    }
  }

  @Nested
  @DisplayName("过期 expire")
  class ExpireTest {

    @Test
    @DisplayName("未到过期时间调用 expire 应抛 IllegalStateException")
    void shouldThrowWhenNotExpiredYet() {
      var session = ChannelFixtures.onlineSession("user-1");

      assertThatThrownBy(() -> session.expire(UserNo.of("user-1")))
        .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("二次授权 applySecondaryAuth")
  class ApplySecondaryAuthTest {

    @Test
    @DisplayName("非网点渠道调用应抛 DomainException")
    void shouldThrowWhenNotBranchChannel() {
      var session = ChannelFixtures.onlineSession("user-1");
      var secondaryId = new SecondaryAuthSessionId("sa-1");
      var identity = ChannelFixtures.directIdentity("user-2");

      assertThatThrownBy(() -> session.applySecondaryAuth(secondaryId, identity, UserNo.of("user-1")))
        .isInstanceOf(DomainException.class);
    }
  }
}
