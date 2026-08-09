package com.example.gateway.security;

/**
 * IAM 三渠道类型枚举。
 *
 * <p>对应设计文档 4.1 节定义的三套 StpLogic 渠道:
 * <ul>
 *   <li>{@link #INTERNET} - 网上渠道 (satoken-internet Header),路径前缀 {@code /internet}</li>
 *   <li>{@link #HQ} - 总部渠道 (satoken-hq Header),路径前缀 {@code /hq}</li>
 *   <li>{@link #BRANCH} - 网点渠道 (satoken-branch Header),路径前缀 {@code /branch}</li>
 * </ul>
 *
 * <p>各网关通过 {@link com.example.gateway.config.GatewayChannelProperties} 配置启用哪些渠道，
 * 网关通过请求路径前缀识别渠道类型,分派到对应 StpLogic 进行登录/权限校验。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum ChannelType {

  /**
   * 网上渠道:经办人(HR)通过互联网办理年金业务。
   */
  INTERNET("internet", "satoken-internet", "/internet"),

  /**
   * 总部渠道:运营人员通过内网办理年金业务,可选择任意计划。
   */
  HQ("hq", "satoken-hq", "/hq"),

  /**
   * 网点渠道:银行网点柜员办理年金业务,需二次授权。
   */
  BRANCH("branch", "satoken-branch", "/branch");

  private final String loginType;
  private final String tokenHeader;
  private final String pathPrefix;

  ChannelType(String loginType, String tokenHeader, String pathPrefix) {
    this.loginType = loginType;
    this.tokenHeader = tokenHeader;
    this.pathPrefix = pathPrefix;
  }

  /**
   * 根据请求路径前缀识别渠道类型。
   *
   * @param path 请求路径 (如 /internet/business/handle)
   * @return 匹配的渠道类型,未匹配返回 null (公共接口)
   */
  public static ChannelType fromPath(String path) {
    if (path == null || path.isBlank()) {
      return null;
    }
    for (ChannelType channel : values()) {
      if (path.startsWith(channel.pathPrefix + "/") || path.equals(channel.pathPrefix)) {
        return channel;
      }
    }
    return null;
  }

  /**
   * 根据 loginType 反查渠道类型。
   *
   * <p>配置绑定（{@code gateway.channels.enabled}）由 Spring 按枚举名精确绑定，
   * 本方法仅用于按 sa-token loginType（如 "internet"/"hq"/"branch"）识别渠道，
   * 保持大小写敏感，与 loginType 语义一致。
   *
   * @param loginType sa-token loginType（如 "internet"/"hq"/"branch"）
   * @return 匹配的渠道类型;未知返回 null
   */
  public static ChannelType fromName(String loginType) {
    if (loginType == null || loginType.isBlank()) {
      return null;
    }
    for (ChannelType channel : values()) {
      if (channel.loginType().equals(loginType)) {
        return channel;
      }
    }
    return null;
  }

  /**
   * 获取 sa-token loginType (StpLogic 类型标识)。
   */
  public String loginType() {
    return loginType;
  }

  /**
   * 获取渠道对应的 Token Header 名称。
   */
  public String tokenHeader() {
    return tokenHeader;
  }

  /**
   * 获取渠道路径前缀 (如 /internet, /hq, /branch)。
   */
  public String pathPrefix() {
    return pathPrefix;
  }
}