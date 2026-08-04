package com.pension.permission.domain.user.service;


import com.pension.permission.domain.credential.aggregate.Credential;

/**
 * 每种凭证类型对应一种认证策略实现(Strategy模式)。
 */
public interface AuthenticationProvider {
  boolean authenticate(Credential credential, String proof);
}
