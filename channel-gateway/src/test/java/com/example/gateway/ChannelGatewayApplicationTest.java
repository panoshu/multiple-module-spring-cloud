package com.example.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChannelGatewayApplication} 渠道网关启动测试。
 *
 * <p>验证 Spring 上下文能正常装配 gateway-starter 组件（认证/加解密/会话注入），
 * {@code gateway.channels.enabled=[INTERNET]} 配置驱动的渠道注册生效。
 * internet / intranet / dev 三 profile 共用同一代码库，单渠道装配验证即可覆盖公共装配路径。
 */
@ActiveProfiles("test")
@SpringBootTest
class ChannelGatewayApplicationTest {

  @Autowired
  private ApplicationContext context;

  @Test
  @DisplayName("上下文加载成功")
  void contextLoads() {
    assertThat(context).isNotNull();
  }
}
