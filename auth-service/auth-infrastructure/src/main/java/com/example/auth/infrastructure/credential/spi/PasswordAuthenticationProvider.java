package com.example.auth.infrastructure.credential.spi;

import com.example.auth.domain.credential.aggregate.Credential;
import com.example.auth.domain.credential.aggregate.PasswordCredential;
import com.example.auth.domain.user.service.AuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码认证策略 — 校验 BCrypt 哈希.
 */
@Component
public class PasswordAuthenticationProvider implements AuthenticationProvider {

  private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

  @Override
  public boolean authenticate(Credential credential, String proof) {
    if (!(credential instanceof PasswordCredential pc)) {
      return false;
    }
    return ENCODER.matches(proof, pc.passwordHash());
  }
}
