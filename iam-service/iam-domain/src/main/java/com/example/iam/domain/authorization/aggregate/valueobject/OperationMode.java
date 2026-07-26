package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 运作模式 - 计划的业务运作类型,决定账管与受托角色。
 *
 * <p>设计文档 3.5 节,来源于外部系统(通过 {@code PlanMetadataGateway} 加载):
 * <ul>
 *   <li>{@code SINGLE_TRUSTEE} - 单受托产品</li>
 *   <li>{@code SINGLE_ACCOUNT_MANAGER} - 单账管产品</li>
 *   <li>{@code TRUSTEE_AND_ACCOUNT_MANAGER} - 受托+账管产品</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum OperationMode implements ValueObject {
  /** 单受托产品 */
  SINGLE_TRUSTEE,
  /** 单账管产品 */
  SINGLE_ACCOUNT_MANAGER,
  /** 受托+账管产品 */
  TRUSTEE_AND_ACCOUNT_MANAGER
}
