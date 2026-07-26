package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 计划元数据 - 防腐层从外部系统加载的计划基础信息。
 *
 * <p>设计文档 3.7 节 PermissionResolver 计算流程步骤 1:
 * 通过 {@code PlanMetadataGateway.findByPlanNo(planNo)} 加载计划元数据,
 * 提供权限规则匹配所需的上下文维度(客户/产品/运作模式/账管人)。
 *
 * <p>字段说明:
 * <ul>
 *   <li>{@code planNo} - 计划编号(外部系统标识)</li>
 *   <li>{@code customerNo} - 客户编号(关联客户级规则)</li>
 *   <li>{@code productNo} - 产品编号(关联产品级规则)</li>
 *   <li>{@code operationMode} - 运作模式(关联运作模式级规则)</li>
 *   <li>{@code accountManagerCode} - 账管人编号(关联账管人级规则)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record PlanMetadata(
    String planNo,
    String customerNo,
    String productNo,
    OperationMode operationMode,
    String accountManagerCode
) implements ValueObject {
}
