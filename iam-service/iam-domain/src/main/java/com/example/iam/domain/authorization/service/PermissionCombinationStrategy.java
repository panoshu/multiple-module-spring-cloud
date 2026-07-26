package com.example.iam.domain.authorization.service;

import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCombinationContext;

import java.util.Set;

/**
 * 权限组合策略 SPI - 将多个维度的权限规则组合为最终权限码集合。
 *
 * <p>设计文档 3.6.3 节:本 SPI 是权限组合算法的扩展点,业务规则调整时可新增策略实现,
 * 通过 YAML 配置 {@code iam.permission.combination-strategy} 切换默认策略。
 *
 * <p>设计文档 3.7 节:PermissionResolver 在加载完规则与代办权限后调用本策略,
 * 策略实现决定如何应用优先级覆盖(ADD/REMOVE)与代办合并。
 *
 * <p>本接口属于 {@code domain.service} 包,作为领域扩展点(SPI),不依赖任何外部框架。
 * 实现类位于 {@code domain.strategy} 包(默认实现)或 {@code iam-infrastructure} 层
 * (自定义实现),通过 Spring {@code @Component} 自动注册。
 *
 * <p>线程安全:实现必须无状态,可在多线程环境并发调用。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface PermissionCombinationStrategy {

  /**
   * 策略名称(用于配置切换与日志识别)。
   *
   * <p>对应 YAML 配置 {@code iam.permission.combination-strategy} 的 Bean 名称。
   *
   * @return 策略名称(非空)
   */
  String name();

  /**
   * 将多个维度的规则与代办权限组合为最终权限码集合。
   *
   * <p>实现职责:
   * <ul>
   *   <li>对 {@link PermissionCombinationContext#matchedRules()} 按优先级算法应用 ADD/REMOVE</li>
   *   <li>合并 {@link PermissionCombinationContext#delegationPermissions()} 到最终集合</li>
   *   <li>返回不可变集合</li>
   * </ul>
   *
   * @param context 组合上下文(非空,包含已加载的规则与代办权限)
   * @return 最终权限码集合(非空,可能为空集)
   */
  Set<PermissionCode> combine(PermissionCombinationContext context);
}
