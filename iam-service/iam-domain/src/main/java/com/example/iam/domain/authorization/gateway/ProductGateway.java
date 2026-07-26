package com.example.iam.domain.authorization.gateway;

/**
 * 产品信息查询网关 - 防腐层接口,校验外部业务系统的产品有效性。
 *
 * <p>用于产品级权限规则匹配前的产品存在性校验。
 *
 * <p>实现位于 infrastructure 层,调用外部产品服务。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface ProductGateway {

  /**
   * 按产品编号查询产品是否存在且有效。
   *
   * @param productNo 产品编号(外部系统标识)
   * @return 产品存在且有效时返回 true;否则返回 false
   */
  boolean existsByProductNo(String productNo);
}
