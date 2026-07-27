package com.example.iam.adapter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IAM 渠道与权限配置属性。
 *
 * <p>对应 application.yml 中 {@code iam.security} 前缀的配置项,支持:
 * <ul>
 *   <li>三渠道(INTERNET/HQ/BRANCH)的 sa-token 会话参数(timeout/active-timeout/is-concurrent)</li>
 *   <li>二次授权会话参数(会话有效期/待授权超时/最大并发数)</li>
 *   <li>权限计算参数(组合策略 Bean 名/主体优先级/缓存配置)</li>
 * </ul>
 *
 * <p>配置示例:
 * <pre>{@code
 * iam:
 *   security:
 *     channels:
 *       internet:
 *         timeout: 2592000
 *         active-timeout: 1800
 *         is-concurrent: false
 *     secondary-auth:
 *       session-timeout: 7200
 *       pending-timeout: 300
 *       max-pending-per-teller: 1
 *     permission:
 *       combination-strategy: priorityOverrideStrategy
 *       subject-priority:
 *         - ACCOUNT_MANAGER
 *         - PLAN
 *         - PRODUCT
 *         - OPERATION_MODE
 *         - CUSTOMER
 *       cache-timeout: 1800
 *       cache-null: true
 * }</pre>
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Data
@ConfigurationProperties(prefix = "iam.security")
public class IamChannelProperties {

  /** 渠道级配置(INTERNET/HQ/BRANCH) */
  private Map<String, ChannelConfig> channels = new HashMap<>();

  /** 二次授权配置 */
  private SecondaryAuthConfig secondaryAuth = new SecondaryAuthConfig();

  /** 权限计算配置 */
  private PermissionConfig permission = new PermissionConfig();

  /**
   * 渠道级 sa-token 配置。
   */
  @Data
  public static class ChannelConfig {
    /** Token 有效期(秒) */
    private long timeout = 28800L;
    /** Token 最低活跃频率(秒) */
    private long activeTimeout = 1800L;
    /** 是否允许同账号多端登录 */
    private boolean isConcurrent = true;
  }

  /**
   * 二次授权会话配置(网点渠道专属)。
   */
  @Data
  public static class SecondaryAuthConfig {
    /** 二次授权会话有效期(秒,默认 2 小时) */
    private long sessionTimeout = 7200L;
    /** 待授权会话过期时间(秒,默认 5 分钟) */
    private long pendingTimeout = 300L;
    /** 同一柜员同时可持有的待授权会话数(默认 1) */
    private int maxPendingPerTeller = 1;
  }

  /**
   * 权限计算配置。
   */
  @Data
  public static class PermissionConfig {
    /** 权限组合策略 Bean 名(默认 priorityOverrideStrategy) */
    private String combinationStrategy = "priorityOverrideStrategy";
    /** 主体优先级顺序(高 → 低) */
    private List<String> subjectPriority = List.of(
        "ACCOUNT_MANAGER", "PLAN", "PRODUCT", "OPERATION_MODE", "CUSTOMER");
    /** 权限缓存有效期(秒,默认 30 分钟) */
    private long cacheTimeout = 1800L;
    /** 是否缓存空值防穿透 */
    private boolean cacheNull = true;
  }
}
