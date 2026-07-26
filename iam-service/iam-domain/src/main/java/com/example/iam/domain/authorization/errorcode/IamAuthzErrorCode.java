package com.example.iam.domain.authorization.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * iam-service 授权上下文错误码定义。
 *
 * <p>错误码区间 {@code SERVICE.IAM.0100-SERVICE.IAM.0188},遵循 {@code 08-错误码规范.md}:
 * <ul>
 *   <li>层级字符串格式:SERVICE.IAM.XXXX(业务服务 - iam-service - 授权)</li>
 *   <li>消息使用纯文本,禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 *
 * <p>码段内部分组:
 * <ul>
 *   <li>SERVICE.IAM.0100-0109:权限规则</li>
 *   <li>SERVICE.IAM.0120-0129:计划代办关系</li>
 *   <li>SERVICE.IAM.0140-0144:业务定义</li>
 *   <li>SERVICE.IAM.0160-0163:路由规则</li>
 *   <li>SERVICE.IAM.0180-0188:外部数据加载(计划/客户/产品/运作模式)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum IamAuthzErrorCode implements ErrorDefinition {

  // ==================== 权限规则(SERVICE.IAM.0100-0109) ====================
  PERMISSION_RULE_NOT_FOUND("SERVICE.IAM.0100", "权限规则不存在"),
  PERMISSION_RULE_CODE_DUPLICATE("SERVICE.IAM.0101", "规则编码重复"),
  PERMISSION_RULE_STATUS_INVALID("SERVICE.IAM.0102", "规则状态不允许此操作"),
  SUBJECT_TYPE_INVALID("SERVICE.IAM.0103", "主体类型无效"),
  SUBJECT_ID_REQUIRED("SERVICE.IAM.0104", "主体标识不能为空"),
  OVERRIDE_MODE_INVALID("SERVICE.IAM.0105", "覆盖模式无效"),
  ACTION_EMPTY("SERVICE.IAM.0106", "动作集合不能为空"),
  BUSINESS_CODE_INVALID("SERVICE.IAM.0107", "业务编码无效"),
  PRIORITY_INVALID("SERVICE.IAM.0108", "优先级无效"),
  RULE_EFFECTIVE_PERIOD_INVALID("SERVICE.IAM.0109", "规则生效时间区间无效"),

  // ==================== 计划代办关系(SERVICE.IAM.0120-0129) ====================
  PLAN_DELEGATION_NOT_FOUND("SERVICE.IAM.0120", "计划代办关系不存在"),
  PLAN_DELEGATION_CODE_DUPLICATE("SERVICE.IAM.0121", "代办编码重复"),
  PLAN_DELEGATION_STATUS_INVALID("SERVICE.IAM.0122", "代办状态不允许此操作"),
  PLAN_DELEGATION_DUPLICATE("SERVICE.IAM.0123", "代办关系已存在"),
  PLAN_DELEGATION_SELF_DELEGATION("SERVICE.IAM.0124", "授权方和被授权方不能相同"),
  DELEGATION_TYPE_INVALID("SERVICE.IAM.0125", "代办类型无效"),
  DELEGATION_OPERATOR_NOT_SPECIFIED("SERVICE.IAM.0126", "未指定代办操作员"),
  DELEGATION_PERMISSION_EMPTY("SERVICE.IAM.0127", "代办权限不能为空"),
  DELEGATION_OPERATOR_DUPLICATE("SERVICE.IAM.0128", "代办操作员重复指定"),
  DELEGATION_PERMISSION_DUPLICATE("SERVICE.IAM.0129", "代办权限重复指定"),

  // ==================== 业务定义(SERVICE.IAM.0140-0144) ====================
  BUSINESS_DEFINITION_NOT_FOUND("SERVICE.IAM.0140", "业务定义不存在"),
  BUSINESS_CODE_DUPLICATE("SERVICE.IAM.0141", "业务编码重复"),
  BUSINESS_DEFINITION_STATUS_INVALID("SERVICE.IAM.0142", "业务定义状态不允许此操作"),
  BUSINESS_ACTION_NOT_SUPPORTED("SERVICE.IAM.0143", "业务不支持该动作"),
  BUSINESS_ACTION_DUPLICATE("SERVICE.IAM.0144", "业务动作重复"),

  // ==================== 路由规则(SERVICE.IAM.0160-0163) ====================
  ROUTE_RULE_NOT_FOUND("SERVICE.IAM.0160", "路由规则不存在"),
  ROUTE_PATTERN_DUPLICATE("SERVICE.IAM.0161", "路由匹配模式重复"),
  ROUTE_RULE_CHECK_TYPE_INVALID("SERVICE.IAM.0162", "路由校验类型无效"),
  ROUTE_RULE_PRIORITY_INVALID("SERVICE.IAM.0163", "路由规则优先级无效"),

  // ==================== 外部数据加载(SERVICE.IAM.0180-0188) ====================
  PLAN_NOT_FOUND("SERVICE.IAM.0180", "计划不存在"),
  PLAN_NOT_SELECTABLE("SERVICE.IAM.0181", "计划不可选择"),
  PLAN_NOT_AUTHORIZED("SERVICE.IAM.0182", "计划未授权"),
  CUSTOMER_NOT_FOUND("SERVICE.IAM.0183", "客户不存在"),
  PRODUCT_NOT_FOUND("SERVICE.IAM.0184", "产品不存在"),
  OPERATION_MODE_INVALID("SERVICE.IAM.0185", "运作模式无效"),
  ACCOUNT_MANAGER_CODE_INVALID("SERVICE.IAM.0186", "账管人编号无效"),
  CUSTOMER_TYPE_INVALID("SERVICE.IAM.0187", "客户类型无效"),
  NO_SELECTABLE_PLAN("SERVICE.IAM.0188", "无可选计划"),
  ;

  private final String code;
  private final String message;

  IamAuthzErrorCode(String code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public String code() {
    return this.code;
  }

  @Override
  public String message() {
    return this.message;
  }
}
