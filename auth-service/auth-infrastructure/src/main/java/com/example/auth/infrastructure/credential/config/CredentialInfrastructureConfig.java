package com.example.auth.infrastructure.credential.config;

import com.example.auth.domain.credential.aggregate.Credential;
import com.example.auth.domain.credential.aggregate.PasswordCredential;
import com.example.auth.domain.user.service.AuthenticationProvider;
import com.example.auth.infrastructure.credential.spi.PasswordAuthenticationProvider;
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
