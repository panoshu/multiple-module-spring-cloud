package com.pension.permission.application.channel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 二次授权配置.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "auth.secondary-auth")
public class SecondaryAuthConfig {

  /**
   * 授权策略标识（sms-code / face-recognition / ukey-signature）.
   */
  private String strategy = "sms-code";

  /**
   * 待授权超时时间（默认 5 分钟）.
   */
  private Duration pendingTimeout = Duration.ofMinutes(5);

  /**
   * 授权后会话过期时间（默认 2 小时）.
   */
  private Duration sessionTimeout = Duration.ofHours(2);

  /**
   * 权限快照 TTL（默认 30 秒）.
   */
  private Duration snapshotTtl = Duration.ofSeconds(30);

  /**
   * 验证码长度（默认 6 位）.
   */
  private int verificationCodeLength = 6;

  /**
   * 验证码最大重试次数（默认 3 次）.
   */
  private int verificationMaxAttempts = 3;

  /**
   * 短信发送开关（测试环境可关闭）.
   */
  private boolean smsEnabled = true;
}
