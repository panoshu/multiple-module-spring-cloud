package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.Objects;

/**
 * 权限规则匹配上下文 - {@link com.example.iam.domain.authorization.aggregate.root.PermissionRule}
 * 用于判断是否适用的维度集合。
 *
 * <p>来源于 {@link PlanMetadata}(通过防腐层从外部系统加载),包含 5 个主体维度:
 * <ul>
 *   <li>{@code customerNo} - 客户编号(关联 CUSTOMER 级规则)</li>
 *   <li>{@code operationMode} - 运作模式(关联 OPERATION_MODE 级规则)</li>
 *   <li>{@code productNo} - 产品编号(关联 PRODUCT 级规则)</li>
 *   <li>{@code planNo} - 计划编号(关联 PLAN 级规则)</li>
 *   <li>{@code accountManagerCode} - 账管人编号(关联 ACCOUNT_MANAGER 级规则)</li>
 * </ul>
 *
 * <p>设计文档 3.4.4 节:PermissionRule.matches(context) 根据 subjectType 取对应维度
 * 与 subjectId 比较,相等即匹配。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record PermissionMatchContext(
    String customerNo,
    OperationMode operationMode,
    String productNo,
    String planNo,
    String accountManagerCode
) implements ValueObject {

  public PermissionMatchContext {
    Objects.requireNonNull(customerNo, "customerNo cannot be null");
    Objects.requireNonNull(operationMode, "operationMode cannot be null");
    Objects.requireNonNull(productNo, "productNo cannot be null");
    Objects.requireNonNull(planNo, "planNo cannot be null");
    Objects.requireNonNull(accountManagerCode, "accountManagerCode cannot be null");
  }

  /**
   * 从 PlanMetadata 构建匹配上下文。
   *
   * @param planMetadata 计划元数据
   * @return 匹配上下文
   */
  public static PermissionMatchContext from(PlanMetadata planMetadata) {
    Objects.requireNonNull(planMetadata, "planMetadata cannot be null");
    return new PermissionMatchContext(
        planMetadata.customerNo(),
        planMetadata.operationMode(),
        planMetadata.productNo(),
        planMetadata.planNo(),
        planMetadata.accountManagerCode()
    );
  }

  /**
   * 根据主体维度类型获取对应的主体标识。
   *
   * @param subjectType 主体维度类型
   * @return 对应维度的标识字符串
   */
  public String subjectIdFor(SubjectType subjectType) {
    Objects.requireNonNull(subjectType, "subjectType cannot be null");
    return switch (subjectType) {
      case CUSTOMER -> customerNo;
      case OPERATION_MODE -> operationMode.name();
      case PRODUCT -> productNo;
      case PLAN -> planNo;
      case ACCOUNT_MANAGER -> accountManagerCode;
    };
  }
}
