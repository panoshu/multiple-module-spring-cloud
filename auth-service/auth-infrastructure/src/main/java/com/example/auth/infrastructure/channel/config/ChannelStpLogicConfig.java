package com.example.auth.infrastructure.channel.config;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpLogic;
import com.example.core.types.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;

/**
 * 认证服务渠道 StpLogic 注册配置.
 *
 * <p>网关侧通过 {@code ChannelAwareSaRouter} 为每个渠道创建独立的 StpLogic，
 * token 存储在 Redis 中按 loginType 隔离。auth-service 必须使用相同的 loginType
 * 签发 token，否则网关无法识别。
 *
 * <p>渠道 → loginType 映射：
 * <ul>
 *   <li>NETAPP → internet（satoken-internet header）</li>
 *   <li>TELLER → hq（satoken-hq header）</li>
 *   <li>BANK_BRANCH → branch（satoken-branch header）</li>
 * </ul>
 */
@Slf4j
@Configuration
public class ChannelStpLogicConfig {

  @Bean
  public Map<Channel, StpLogic> channelStpLogicMap() {
    Map<Channel, StpLogic> map = new EnumMap<>(Channel.class);
    registerChannel(map, Channel.NETAPP, "internet", "satoken-internet");
    registerChannel(map, Channel.TELLER, "hq", "satoken-hq");
    registerChannel(map, Channel.BANK_BRANCH, "branch", "satoken-branch");
    log.info("渠道 StpLogic 注册完成: {}", map.keySet());
    return map;
  }

  private void registerChannel(Map<Channel, StpLogic> map, Channel channel,
                                String loginType, String tokenName) {
    StpLogic logic = new StpLogic(loginType);
    SaTokenConfig config = new SaTokenConfig();
    config.setTokenName(tokenName);
    config.setIsReadHeader(true);
    config.setIsReadCookie(false);
    logic.setConfig(config);
    map.put(channel, logic);
  }
}
