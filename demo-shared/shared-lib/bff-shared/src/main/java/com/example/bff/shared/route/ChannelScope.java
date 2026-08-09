package com.example.bff.shared.route;

/**
 * BFF 渠道范围
 *
 * <p>用于路由表 channel_scope 字段，支持同一业务类型在不同渠道路由到不同服务。
 * internet-bff 使用 {@link #INTERNET}，intranet-bff 使用 {@link #INTRANET}，
 * 默认 {@link #ALL} 表示所有渠道通用。
 *
 * @author bff
 */
public enum ChannelScope {

  ALL,
  INTERNET,
  INTRANET
}
