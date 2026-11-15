package com.example.core.types;

import com.example.shared.enumeration.CodeEnum;

/**
 * 年金渠道
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 20:23
 */
public enum Channel implements CodeEnum<String> {
  NETAPP("01", "网上渠道"),
  TELLER("02", "总部柜面"),
  WECHAT("03", "微信"),
  REGIONAL_CENTER("04", "区域中心"),
  BANK_BRANCH("05", "合作银行网点");

  private final String code;
  private final String description;

  Channel(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * 统一渠道映射（X-05）：会话中的 {@code channelType} 可能来自 shared-security 的
   * {@code ChannelType}（INTERNET/HQ/BRANCH），也可能是本枚举名。集中在此转换，
   * 避免各 Controller 复制不一致的 switch。
   *
   * @param channelType 会话渠道类型（大小写不敏感）
   * @return 年金业务渠道
   * @throws IllegalArgumentException 未知渠道，禁止静默回退到网上渠道
   */
  public static Channel fromChannelType(String channelType) {
    if (channelType == null || channelType.isBlank()) {
      return NETAPP;
    }
    return switch (channelType.trim().toUpperCase(java.util.Locale.ROOT)) {
      case "INTERNET", "NETAPP" -> NETAPP;
      case "HQ", "TELLER" -> TELLER;
      case "BRANCH", "BANK_BRANCH" -> BANK_BRANCH;
      case "REGIONAL_CENTER" -> REGIONAL_CENTER;
      case "WECHAT" -> WECHAT;
      default -> throw new IllegalArgumentException("未知渠道类型: " + channelType);
    };
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getDescription() {
    return description;
  }
}
