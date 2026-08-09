package com.example.gateway.config;

import com.example.gateway.security.ChannelType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 网关渠道注册配置。
 *
 * <p>配置前缀：{@code gateway.channels}
 *
 * <p>通过配置控制当前网关实例启用哪些渠道（{@link ChannelType}），实现配置驱动的渠道注册。
 * 各网关应用按需启用渠道：
 * <ul>
 *   <li>internet-gateway：仅 {@code [INTERNET]}</li>
 *   <li>intranet-gateway：{@code [HQ, BRANCH]}</li>
 *   <li>demo-gateway（开发入口）：{@code [INTERNET, HQ, BRANCH]}</li>
 * </ul>
 *
 * @author demo-gateway
 * @since 2026/8/9
 */
@ConfigurationProperties(prefix = "gateway.channels")
public record GatewayChannelProperties(
    /**
     * 启用渠道列表（顺序即 StpLogic 注册与遍历顺序）。
     *
     * <p>为空时默认不启用任何渠道（仅剩余公共能力）。
     */
    @DefaultValue List<ChannelType> enabled
) {

  public GatewayChannelProperties {
    enabled = enabled != null ? List.copyOf(enabled) : List.of();
  }
}