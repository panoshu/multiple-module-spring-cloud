package com.example.iam.domain.authorization.gateway;

/**
 * 组织架构信息查询网关 - 防腐层接口,校验外部组织架构系统的账管人有效性。
 *
 * <p>用于账管人级权限规则匹配前的账管人存在性校验。
 *
 * <p>实现位于 infrastructure 层,调用外部组织架构服务。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface OrganizationGateway {

  /**
   * 校验账管人编号是否有效。
   *
   * @param accountManagerCode 账管人编号(外部系统标识)
   * @return 账管人存在且有效时返回 true;否则返回 false
   */
  boolean isAccountManagerValid(String accountManagerCode);
}
