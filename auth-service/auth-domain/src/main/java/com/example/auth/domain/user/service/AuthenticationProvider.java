package com.example.auth.domain.user.service;


import com.example.auth.domain.credential.aggregate.Credential;

/**
 * 每种凭证类型对应一种认证策略实现(Strategy模式)。
 */
public interface AuthenticationProvider {
  boolean authenticate(Credential credential, String proof);
}
