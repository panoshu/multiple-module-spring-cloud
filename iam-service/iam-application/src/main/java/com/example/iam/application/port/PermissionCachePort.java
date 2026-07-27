package com.example.iam.application.port;

/**
 * 权限缓存端口 - 封装权限缓存失效操作。
 *
 * <p>权限规则、代办关系、业务定义等变更后,需失效相关用户/计划的权限缓存,
 * 保证 sa-token 在下次访问时重新调用 PermissionResolver 计算最新权限。
 *
 * <p>iam-adapter 或 iam-infrastructure 层提供实现,基于 Redis/Caffeine 等缓存失效。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface PermissionCachePort {

  /**
   * 按用户失效权限缓存。
   *
   * <p>用于用户状态变更(禁用/锁定)、用户档案变更等场景。
   *
   * @param userId 用户 ID
   */
  void evictByUser(Long userId);

  /**
   * 按计划失效权限缓存。
   *
   * <p>用于计划维度相关的权限规则/代办关系变更场景。
   *
   * @param planNo 计划编号
   */
  void evictByPlan(String planNo);

  /**
   * 失效全部权限缓存。
   *
   * <p>用于大规模规则变更或系统维护场景,慎用。
   */
  void evictAll();
}
