package com.example.core.types;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * X-05：ChannelType → 年金 Channel 的集中映射。
 */
@DisplayName("Channel 渠道映射")
class ChannelMappingTest {

  @Test
  @DisplayName("安全渠道类型映射：INTERNET/HQ/BRANCH")
  void mapsSecurityChannelTypes() {
    assertThat(Channel.fromChannelType("INTERNET")).isEqualTo(Channel.NETAPP);
    assertThat(Channel.fromChannelType("HQ")).isEqualTo(Channel.TELLER);
    assertThat(Channel.fromChannelType("BRANCH")).isEqualTo(Channel.BANK_BRANCH);
  }

  @Test
  @DisplayName("兼容年金枚举名（大小写不敏感）")
  void mapsCoreEnumNames() {
    assertThat(Channel.fromChannelType("netapp")).isEqualTo(Channel.NETAPP);
    assertThat(Channel.fromChannelType("BANK_BRANCH")).isEqualTo(Channel.BANK_BRANCH);
    assertThat(Channel.fromChannelType("REGIONAL_CENTER")).isEqualTo(Channel.REGIONAL_CENTER);
  }

  @Test
  @DisplayName("空值回退网上渠道，未知值 fail-fast")
  void rejectsUnknownChannel() {
    assertThat(Channel.fromChannelType(null)).isEqualTo(Channel.NETAPP);
    assertThatThrownBy(() -> Channel.fromChannelType("MOBILE"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("MOBILE");
  }
}
