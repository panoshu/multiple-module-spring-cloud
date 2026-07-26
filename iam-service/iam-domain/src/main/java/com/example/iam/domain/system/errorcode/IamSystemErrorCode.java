package com.example.iam.domain.system.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * iam-service 系统层错误码定义。
 *
 * <p>错误码区间 {@code SERVICE.IAM.0200-SERVICE.IAM.0265},遵循 {@code 08-错误码规范.md}:
 * <ul>
 *   <li>层级字符串格式:SERVICE.IAM.XXXX(业务服务 - iam-service - 系统)</li>
 *   <li>消息使用纯文本,禁止 {} 占位符和方括号前缀</li>
 *   <li>这些错误码对应 {@code SystemException},表示系统级错误</li>
 * </ul>
 *
 * <p>码段内部分组:
 * <ul>
 *   <li>SERVICE.IAM.0200-0206:权限计算(计算/缓存/策略/快照/上下文/规则加载)</li>
 *   <li>SERVICE.IAM.0220-0225:sa-token 集成(会话/踢人/配置/StpLogic/渠道识别/权限加载)</li>
 *   <li>SERVICE.IAM.0240-0248:外部 API 调用(调用/超时/响应/可用性/反序列化/各类加载失败)</li>
 *   <li>SERVICE.IAM.0260-0265:配置(渠道/权限/二次授权/外部 API/业务注册表)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public enum IamSystemErrorCode implements ErrorDefinition {

  // ==================== 权限计算(SERVICE.IAM.0200-0206) ====================
  PERMISSION_CALCULATION_FAILED("SERVICE.IAM.0200", "权限计算失败"),
  PERMISSION_CACHE_EVICT_FAILED("SERVICE.IAM.0201", "权限缓存失效失败"),
  PERMISSION_STRATEGY_NOT_FOUND("SERVICE.IAM.0202", "权限组合策略未找到"),
  PERMISSION_SNAPSHOT_BUILD_FAILED("SERVICE.IAM.0203", "权限快照构建失败"),
  PERMISSION_SNAPSHOT_EXPIRED("SERVICE.IAM.0204", "权限快照已过期"),
  PERMISSION_CONTEXT_INVALID("SERVICE.IAM.0205", "权限计算上下文无效"),
  PERMISSION_RULE_LOAD_FAILED("SERVICE.IAM.0206", "权限规则加载失败"),

  // ==================== sa-token 集成(SERVICE.IAM.0220-0225) ====================
  SA_TOKEN_SESSION_UPDATE_FAILED("SERVICE.IAM.0220", "sa-token 会话更新失败"),
  SA_TOKEN_KICKOUT_FAILED("SERVICE.IAM.0221", "踢人下线失败"),
  SA_TOKEN_CONFIG_INVALID("SERVICE.IAM.0222", "sa-token 配置无效"),
  SA_TOKEN_STP_LOGIC_NOT_FOUND("SERVICE.IAM.0223", "sa-token StpLogic 未找到"),
  SA_TOKEN_CHANNEL_NOT_RECOGNIZED("SERVICE.IAM.0224", "无法识别当前请求渠道"),
  SA_TOKEN_PERMISSION_LOAD_FAILED("SERVICE.IAM.0225", "sa-token 权限加载失败"),

  // ==================== 外部 API 调用(SERVICE.IAM.0240-0248) ====================
  EXTERNAL_API_CALL_FAILED("SERVICE.IAM.0240", "外部系统调用失败"),
  EXTERNAL_API_TIMEOUT("SERVICE.IAM.0241", "外部系统调用超时"),
  EXTERNAL_API_RESPONSE_INVALID("SERVICE.IAM.0242", "外部系统响应无效"),
  EXTERNAL_API_UNAVAILABLE("SERVICE.IAM.0243", "外部系统不可用"),
  PLAN_METADATA_LOAD_FAILED("SERVICE.IAM.0244", "计划元数据加载失败"),
  CUSTOMER_INFO_LOAD_FAILED("SERVICE.IAM.0245", "客户信息加载失败"),
  PRODUCT_INFO_LOAD_FAILED("SERVICE.IAM.0246", "产品信息加载失败"),
  ORGANIZATION_INFO_LOAD_FAILED("SERVICE.IAM.0247", "组织架构信息加载失败"),
  EXTERNAL_API_DESERIALIZE_FAILED("SERVICE.IAM.0248", "外部系统响应反序列化失败"),

  // ==================== 配置(SERVICE.IAM.0260-0265) ====================
  CONFIG_INVALID("SERVICE.IAM.0260", "配置无效"),
  CHANNEL_CONFIG_NOT_FOUND("SERVICE.IAM.0261", "渠道配置未找到"),
  PERMISSION_CONFIG_INVALID("SERVICE.IAM.0262", "权限配置无效"),
  SECONDARY_AUTH_CONFIG_INVALID("SERVICE.IAM.0263", "二次授权配置无效"),
  EXTERNAL_API_CONFIG_INVALID("SERVICE.IAM.0264", "外部系统 API 配置无效"),
  BUSINESS_REGISTRY_NOT_INITIALIZED("SERVICE.IAM.0265", "业务注册表未初始化"),
  ;

  private final String code;
  private final String message;

  IamSystemErrorCode(String code, String message) {
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
