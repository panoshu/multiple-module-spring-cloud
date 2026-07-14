package com.example.file.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "shared.file.security")
public record FileSecurityProperties(
  @DefaultValue("true") boolean enabled,
  // 生产环境务必修改此密钥 (32 chars for AES-256)
  @DefaultValue("12345678901234567890123456789012") String secretKey,
  @DefaultValue("5m") Duration defaultExpire,
  @DefaultValue("false") boolean checkIp,
  // 网关对外的统一入口，例如 https://gateway.company.com/api/files
  @DefaultValue("http://localhost:8080/files") String publicUrl
) {
}
