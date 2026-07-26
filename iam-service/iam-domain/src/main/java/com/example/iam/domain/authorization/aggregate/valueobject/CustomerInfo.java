package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 客户信息 - 防腐层从外部系统加载的客户基础信息。
 *
 * <p>通过 {@code CustomerGateway.findByCustomerNo(customerNo)} 加载,
 * 用于客户级权限规则匹配与客户类型校验。
 *
 * <p>字段说明:
 * <ul>
 *   <li>{@code customerNo} - 客户编号(外部系统标识)</li>
 *   <li>{@code customerName} - 客户名称</li>
 *   <li>{@code customerType} - 客户类型(如 CUSTOMER/BRANCH 等)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record CustomerInfo(
    String customerNo,
    String customerName,
    String customerType
) implements ValueObject {
}
