package com.example.annuity.domain.extension;

import com.example.core.domain.aggregate.valueobject.BusinessExtension;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;

/**
 * 年金业务申请扩展字段
 * <p>
 * 实现 kernel 的 {@link BusinessExtension} 多态值对象接口，承载年金业务专属数据：
 * <ul>
 *   <li>{@code planType} - 年金计划操作类型（NEW/MODIFY/DELETE）</li>
 *   <li>{@code initialContribution} - 初始缴费金额（单位：分，避免 BigDecimal 精度问题）</li>
 *   <li>{@code hasForeignInvestment} - 是否含外资成分（影响审批路径）</li>
 * </ul>
 * 通过 Jackson Mix-in（{@code BusinessExtensionMixIn}）以 {@code businessType} 作为类型标识符
 * 进行多态序列化，持久化到 PostgreSQL JSONB / MySQL JSON 列。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
public record AnnuityApplicationExtension(
    BusinessType businessType,
    String planType,
    Long initialContribution,
    boolean hasForeignInvestment
) implements BusinessExtension {

  /**
   * 年金计划操作类型常量
   */
  public static final String PLAN_TYPE_NEW = "NEW";
  public static final String PLAN_TYPE_MODIFY = "MODIFY";
  public static final String PLAN_TYPE_DELETE = "DELETE";

  /**
   * 业务类型由 record 自动提供 accessor，满足 {@link BusinessExtension#businessType()} 契约。
   * 映射关系：
   * <ul>
   *   <li>{@link BusinessType#ACC_PLAN_CREATE} ↔ {@link #PLAN_TYPE_NEW}</li>
   *   <li>{@link BusinessType#ACC_PLAN_MODIFY} ↔ {@link #PLAN_TYPE_MODIFY}</li>
   *   <li>{@link BusinessType#ACC_PLAN_DELETE} ↔ {@link #PLAN_TYPE_DELETE}</li>
   * </ul>
   */
  @Override
  public BusinessType businessType() {
    return businessType;
  }
}
