package com.example.auth.domain.channel.aggregate;

import com.example.core.types.Channel;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.core.types.CustomerNo;
import com.example.core.types.UserNo;
import com.example.auth.domain.channel.errorcode.ChannelErrorCode;
import com.example.auth.domain.channel.event.ChannelDisabled;
import com.example.auth.domain.channel.event.ChannelEnabled;
import com.example.auth.domain.channel.event.CustomerChannelEntitlementCreated;
import com.example.auth.types.CustomerChannelEntitlementId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CustomerChannelEntitlement 聚合根测试")
class CustomerChannelEntitlementTest {

  private static final CustomerNo CUSTOMER_NO = CustomerNo.of("C-001");
  private static final UserNo OPERATOR = UserNo.of("admin-1");

  private CustomerChannelEntitlement createEntitlement(Set<Channel> channels) {
    return CustomerChannelEntitlement.create(
      new CustomerChannelEntitlement.CreateContext(
        new CustomerChannelEntitlementId("e-1"),
        CUSTOMER_NO,
        channels,
        OPERATOR));
  }

  @Nested
  @DisplayName("create: 创建客户渠道开通记录")
  class CreateTest {

    @Test
    @DisplayName("应成功创建并注册 Created 事件")
    void shouldCreateAndRegisterCreatedEvent() {
      Set<Channel> channels = Set.of(Channel.NETAPP, Channel.BANK_BRANCH);

      CustomerChannelEntitlement entitlement = CustomerChannelEntitlement.create(
        new CustomerChannelEntitlement.CreateContext(
          new CustomerChannelEntitlementId("e-1"),
          CUSTOMER_NO,
          channels,
          OPERATOR));

      assertThat(entitlement.customerNo()).isEqualTo(CUSTOMER_NO);
      assertThat(entitlement.enabledChannels()).containsExactlyInAnyOrder(
        Channel.NETAPP, Channel.BANK_BRANCH);
      assertThat(entitlement.domainEvents())
        .hasSize(1)
        .first()
        .isInstanceOf(CustomerChannelEntitlementCreated.class);
    }

    @Test
    @DisplayName("初始开通渠道集合为空时应抛 DomainException")
    void shouldThrowWhenNoChannels() {
      assertThatThrownBy(() -> CustomerChannelEntitlement.create(
        new CustomerChannelEntitlement.CreateContext(
          new CustomerChannelEntitlementId("e-1"),
          CUSTOMER_NO,
          Set.of(),
          OPERATOR)))
        .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("customerNo 为 null 时应抛 NullPointerException")
    void shouldThrowWhenCustomerNoNull() {
      assertThatThrownBy(() -> CustomerChannelEntitlement.create(
        new CustomerChannelEntitlement.CreateContext(
          new CustomerChannelEntitlementId("e-1"),
          null,
          Set.of(Channel.NETAPP),
          OPERATOR)))
        .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("isEnabled: 渠道开通状态查询")
  class IsEnabledTest {

    @Test
    @DisplayName("已开通的渠道应返回 true")
    void shouldReturnTrueForEnabledChannel() {
      CustomerChannelEntitlement entitlement = createEntitlement(Set.of(Channel.NETAPP));

      assertThat(entitlement.isEnabled(Channel.NETAPP)).isTrue();
    }

    @Test
    @DisplayName("未开通的渠道应返回 false")
    void shouldReturnFalseForDisabledChannel() {
      CustomerChannelEntitlement entitlement = createEntitlement(Set.of(Channel.NETAPP));

      assertThat(entitlement.isEnabled(Channel.BANK_BRANCH)).isFalse();
    }
  }

  @Nested
  @DisplayName("enable: 开通渠道")
  class EnableTest {

    @Test
    @DisplayName("开通新渠道应加入集合并注册 ChannelEnabled 事件")
    void shouldAddChannelAndRegisterEvent() {
      CustomerChannelEntitlement entitlement = createEntitlement(Set.of(Channel.NETAPP));
      entitlement.clearDomainEvents();

      entitlement.enable(Channel.BANK_BRANCH, OPERATOR);

      assertThat(entitlement.isEnabled(Channel.BANK_BRANCH)).isTrue();
      assertThat(entitlement.domainEvents())
        .hasSize(1)
        .first()
        .isInstanceOf(ChannelEnabled.class);
    }

    @Test
    @DisplayName("重复开通已开通的渠道应抛 DomainException")
    void shouldThrowWhenEnableExistingChannel() {
      CustomerChannelEntitlement entitlement = createEntitlement(Set.of(Channel.NETAPP));

      assertThatThrownBy(() -> entitlement.enable(Channel.NETAPP, OPERATOR))
        .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("开通渠道应更新 version")
    void shouldIncrementVersionOnEnable() {
      CustomerChannelEntitlement entitlement = createEntitlement(Set.of(Channel.NETAPP));
      Version initialVersion = entitlement.version();

      entitlement.enable(Channel.BANK_BRANCH, OPERATOR);

      assertThat(entitlement.version().value()).isGreaterThan(initialVersion.value());
    }
  }

  @Nested
  @DisplayName("disable: 关闭渠道")
  class DisableTest {

    @Test
    @DisplayName("关闭已开通渠道应从集合移除并注册 ChannelDisabled 事件")
    void shouldRemoveChannelAndRegisterEvent() {
      CustomerChannelEntitlement entitlement = createEntitlement(
        Set.of(Channel.NETAPP, Channel.BANK_BRANCH));
      entitlement.clearDomainEvents();

      entitlement.disable(Channel.BANK_BRANCH, OPERATOR);

      assertThat(entitlement.isEnabled(Channel.BANK_BRANCH)).isFalse();
      assertThat(entitlement.isEnabled(Channel.NETAPP)).isTrue();
      assertThat(entitlement.domainEvents())
        .hasSize(1)
        .first()
        .isInstanceOf(ChannelDisabled.class);
    }

    @Test
    @DisplayName("关闭未开通的渠道应抛 DomainException")
    void shouldThrowWhenDisableNotEnabledChannel() {
      CustomerChannelEntitlement entitlement = createEntitlement(Set.of(Channel.NETAPP));

      assertThatThrownBy(() -> entitlement.disable(Channel.BANK_BRANCH, OPERATOR))
        .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("关闭最后一个渠道应抛 DomainException")
    void shouldThrowWhenDisableLastChannel() {
      CustomerChannelEntitlement entitlement = createEntitlement(Set.of(Channel.NETAPP));

      assertThatThrownBy(() -> entitlement.disable(Channel.NETAPP, OPERATOR))
        .isInstanceOf(DomainException.class)
        .extracting(Throwable::getMessage)
        .asString()
        .contains(ChannelErrorCode.CUSTOMER_CHANNEL_NOT_ENABLED.getCode());
    }
  }

  @Nested
  @DisplayName("replaceChannels: 批量替换渠道集合")
  class ReplaceChannelsTest {

    @Test
    @DisplayName("批量替换应更新集合并注册事件")
    void shouldReplaceAndRegisterEvent() {
      CustomerChannelEntitlement entitlement = createEntitlement(Set.of(Channel.NETAPP));
      entitlement.clearDomainEvents();

      Set<Channel> newChannels = Set.of(Channel.TELLER, Channel.BANK_BRANCH);
      entitlement.replaceChannels(newChannels, OPERATOR);

      assertThat(entitlement.enabledChannels()).containsExactlyInAnyOrder(
        Channel.TELLER, Channel.BANK_BRANCH);
      assertThat(entitlement.domainEvents()).hasSize(1);
    }

    @Test
    @DisplayName("替换为空集合应抛 DomainException")
    void shouldThrowWhenReplaceWithEmpty() {
      CustomerChannelEntitlement entitlement = createEntitlement(Set.of(Channel.NETAPP));

      assertThatThrownBy(() -> entitlement.replaceChannels(Set.of(), OPERATOR))
        .isInstanceOf(DomainException.class);
    }
  }

  @Nested
  @DisplayName("reconstitute: 从持久化数据重建")
  class ReconstituteTest {

    @Test
    @DisplayName("应正确重建聚合根状态且不注册领域事件")
    void shouldReconstituteWithoutEvents() {
      LocalDateTime now = LocalDateTime.now();

      CustomerChannelEntitlement entitlement = CustomerChannelEntitlement.reconstitute(
        new CustomerChannelEntitlement.ReconstituteSnapshot(
          new CustomerChannelEntitlementId("e-1"),
          OPERATOR.value(), OPERATOR.value(),
          now, now,
          new Version(3L),
          CUSTOMER_NO,
          Set.of(Channel.NETAPP, Channel.TELLER)));

      assertThat(entitlement.customerNo()).isEqualTo(CUSTOMER_NO);
      assertThat(entitlement.enabledChannels()).containsExactlyInAnyOrder(
        Channel.NETAPP, Channel.TELLER);
      assertThat(entitlement.version().value()).isEqualTo(3L);
      assertThat(entitlement.domainEvents()).isEmpty();
    }
  }
}
