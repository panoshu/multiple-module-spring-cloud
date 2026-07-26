package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 代办类型 - 计划代办关系的授权范围。
 *
 * <p>设计文档 3.4 节(PlanDelegation 聚合):
 * <ul>
 *   <li>{@code ALL_OPERATORS} - 计划 A 授权给计划 B 代办,A 下所有经办都拥有 B 的授权</li>
 *   <li>{@code SPECIFIC_OPERATORS} - 计划 A 指定其下部分经办拥有指定计划指定业务指定权限</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum DelegationType implements ValueObject {
  /** 全部经办:授权方计划下所有经办均获得代办授权 */
  ALL_OPERATORS,
  /** 指定经办:仅授权方指定的经办获得代办授权 */
  SPECIFIC_OPERATORS
}
