package com.pension.permission.infrastructure.credential.config;

import com.pension.permission.domain.credential.aggregate.Credential;
import com.pension.permission.domain.credential.aggregate.PasswordCredential;
import com.pension.permission.domain.user.service.AuthenticationProvider;
import com.pension.permission.infrastructure.credential.spi.PasswordAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 凭证域基础设施配置 — 装配 AuthenticationProvider 策略 Map.
 */
@Configuration
public class CredentialInfrastructureConfig {

  @Bean
  public Map<Class<? extends Credential>, AuthenticationProvider> authenticationProviderMap(
    PasswordAuthenticationProvider passwordProvider
  ) {
    return Map.of(PasswordCredential.class, passwordProvider);
  }
}
