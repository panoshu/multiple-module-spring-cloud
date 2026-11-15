package com.example.auth.infrastructure.channel.converter;

import com.example.auth.infrastructure.channel.entity.CustomerChannelEntitlementDO;
import com.example.core.types.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("客户渠道开通记录转换（name/CodeEnum 双兼容）")
class CustomerChannelEntitlementPoConverterTest {

  private CustomerChannelEntitlementPoConverter converter;

  @BeforeEach
  void setUp() {
    converter = new CustomerChannelEntitlementPoConverter() {
    };
    converter.objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("DB 中 Channel.name() 可正确还原为 Channel.NETAPP")
  void readsNameEncodedChannels() {
    var domain = converter.toDomain(doRow("[\"NETAPP\"]"));

    assertThat(domain.isEnabled(Channel.NETAPP)).isTrue();
  }

  @Test
  @DisplayName("兼容历史 CodeEnum 编码 01")
  void readsLegacyCodeEncodedChannels() {
    var domain = converter.toDomain(doRow("[\"01\"]"));

    assertThat(domain.isEnabled(Channel.NETAPP)).isTrue();
  }

  private CustomerChannelEntitlementDO doRow(String channelsJson) {
    CustomerChannelEntitlementDO row = new CustomerChannelEntitlementDO();
    row.setId("ent-test-1");
    row.setCustomerNo("C-1");
    row.setEnabledChannels(channelsJson);
    row.setCreatedBy("seed");
    row.setCreateTime(LocalDateTime.now());
    row.setUpdateTime(LocalDateTime.now());
    row.setDeleted(false);
    row.setVersion(0);
    return row;
  }
}
