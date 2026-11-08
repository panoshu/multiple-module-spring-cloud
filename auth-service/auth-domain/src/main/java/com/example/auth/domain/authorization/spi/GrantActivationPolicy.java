package com.example.auth.domain.authorization.spi;


import com.example.auth.domain.authorization.enumeration.GrantOrigin;
import com.example.auth.domain.authorization.enumeration.GrantType;

/**
 * 决定一条新创建的Grant要不要走审批。不同来源、不同类型的Grant可以配不同策略，
 * 新增审批规则时只需要替换/新增实现，不需要改动Grant或判定引擎本身(开闭原则)。
 */
public interface GrantActivationPolicy {
  boolean requiresApproval(GrantOrigin origin, GrantType grantType);
}
