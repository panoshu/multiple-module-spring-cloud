package com.example.iam.domain.authorization.aggregate.root;

import com.example.iam.domain.authorization.aggregate.valueobject.RouteCheckType;
import com.example.iam.domain.authorization.errorcode.IamAuthzErrorCode;
import com.example.iam.types.RouteRuleId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 路由权限规则聚合根 - 网关层动态鉴权的配置单元。
 *
 * <p>设计文档 4.5 节:demo-gateway 启动时加载所有 RouteRule,按 priority 倒序匹配请求路径,
 * 命中后按 {@link RouteCheckType} 执行对应校验。
 *
 * <p>字段:
 * <ul>
 *   <li>{@code routePattern} - 路由匹配模式(Ant 风格,如 /internet/**)</li>
 *   <li>{@code checkType} - 校验类型(LOGIN/PERMISSION/ROLE/CHANNEL/SKIP)</li>
 *   <li>{@code checkValue} - 校验值(权限码/角色名/渠道名,SKIP 时为空)</li>
 *   <li>{@code description} - 规则描述</li>
 *   <li>{@code enabled} - 是否启用</li>
 *   <li>{@code priority} - 优先级(数值越大优先级越高,匹配时按优先级倒序)</li>
 * </ul>
 *
 * <p>核心行为:
 * <ul>
 *   <li>{@link #matches(String)} - 判断请求路径是否匹配本规则</li>
 *   <li>{@link #disable(UserNo)} / {@link #enable(UserNo)} - 禁用/启用</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public class RouteRule extends AggregateRoot<RouteRuleId> {

  private static final Pattern ANT_PATTERN = Pattern.compile(
      "^[a-zA-Z][a-zA-Z0-9_\\-/\\*\\?]*$");

  private final String routePattern;
  private final RouteCheckType checkType;
  private final String checkValue;
  private final String description;
  private final int priority;
  private boolean enabled;

  private RouteRule(RouteRuleId id, String routePattern, RouteCheckType checkType,
                    String checkValue, String description, int priority, boolean enabled,
                    UserNo createdBy, UserNo updatedBy,
                    LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.routePattern = routePattern;
    this.checkType = checkType;
    this.checkValue = checkValue;
    this.description = description;
    this.priority = priority;
    this.enabled = enabled;
    this.validateInvariants();
  }

  /**
   * 工厂方法:创建新路由规则(初始为启用状态)。
   *
   * @param id           规则 ID
   * @param routePattern 路由匹配模式(Ant 风格,如 /internet/**)
   * @param checkType    校验类型
   * @param checkValue   校验值(SKIP 类型时可空)
   * @param description  规则描述(可空)
   * @param priority     优先级(0-999,数值越大优先级越高)
   * @param createdBy    创建人
   * @return 新建的路由规则聚合根
   */
  public static RouteRule create(RouteRuleId id, String routePattern, RouteCheckType checkType,
                                 String checkValue, String description, int priority,
                                 UserNo createdBy) {
    validateCommon(routePattern, checkType, checkValue, priority);
    LocalDateTime now = LocalDateTime.now();
    return new RouteRule(id, routePattern, checkType, checkValue, description, priority, true,
        createdBy, createdBy, now, now, Version.initial());
  }

  /**
   * 工厂方法:从数据库重建聚合。
   */
  public static RouteRule reconstitute(RouteRuleId id, String routePattern, RouteCheckType checkType,
                                       String checkValue, String description, int priority,
                                       boolean enabled,
                                       UserNo createdBy, UserNo updatedBy,
                                       LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    return new RouteRule(id, routePattern, checkType, checkValue, description, priority, enabled,
        createdBy, updatedBy, createdAt, updatedAt, version);
  }

  /**
   * 判断请求路径是否匹配本规则。
   *
   * <p>采用 Ant 风路径径匹配:
   * <ul>
   *   <li>{@code ?} 匹配单字符</li>
   *   <li>{@code *} 匹配 0 个或多个字符(不含路径分隔符)</li>
   *   <li>{@code **} 匹配 0 个或多个路径段</li>
   * </ul>
   *
   * @param path 请求路径
   * @return 匹配返回 true
   */
  public boolean matches(String path) {
    Objects.requireNonNull(path, "path cannot be null");
    return matchAntPattern(routePattern, path);
  }

  /**
   * 禁用规则。
   *
   * @param operator 操作人
   */
  public void disable(UserNo operator) {
    if (!enabled) {
      throw new DomainException(IamAuthzErrorCode.ROUTE_RULE_NOT_FOUND)
          .withUserDetail("路由规则已禁用,不可重复禁用");
    }
    this.enabled = false;
    markUpdated(operator);
  }

  /**
   * 启用规则。
   *
   * @param operator 操作人
   */
  public void enable(UserNo operator) {
    if (enabled) {
      throw new DomainException(IamAuthzErrorCode.ROUTE_RULE_NOT_FOUND)
          .withUserDetail("路由规则已启用,不可重复启用");
    }
    this.enabled = true;
    markUpdated(operator);
  }

  public String routePattern() { return routePattern; }
  public RouteCheckType checkType() { return checkType; }
  public String checkValue() { return checkValue; }
  public String description() { return description; }
  public int priority() { return priority; }
  public boolean isEnabled() { return enabled; }

  @Override
  protected void validateInvariants() {
    if (routePattern == null || routePattern.isBlank()) {
      throw new IllegalStateException("RouteRule.routePattern cannot be null or blank");
    }
    if (checkType == null) {
      throw new IllegalStateException("RouteRule.checkType cannot be null");
    }
    if (priority < 0) {
      throw new IllegalStateException("RouteRule.priority cannot be negative");
    }
    if (checkType != RouteCheckType.SKIP && (checkValue == null || checkValue.isBlank())) {
      throw new IllegalStateException(
          "RouteRule.checkValue cannot be null or blank for non-SKIP checkType");
    }
  }

  private static void validateCommon(String routePattern, RouteCheckType checkType,
                                     String checkValue, int priority) {
    if (routePattern == null || routePattern.isBlank()) {
      throw new DomainException(IamAuthzErrorCode.ROUTE_PATTERN_DUPLICATE)
          .withUserDetail("路由匹配模式不能为空");
    }
    if (!ANT_PATTERN.matcher(routePattern).matches()) {
      throw new DomainException(IamAuthzErrorCode.ROUTE_PATTERN_DUPLICATE)
          .withUserDetail("路由匹配模式格式无效: " + routePattern);
    }
    Objects.requireNonNull(checkType, "checkType cannot be null");
    if (checkType != RouteCheckType.SKIP && (checkValue == null || checkValue.isBlank())) {
      throw new DomainException(IamAuthzErrorCode.ROUTE_RULE_CHECK_TYPE_INVALID)
          .withUserDetail("非 SKIP 校验类型必须提供校验值");
    }
    if (priority < 0) {
      throw new DomainException(IamAuthzErrorCode.ROUTE_RULE_PRIORITY_INVALID)
          .withUserDetail("优先级不能为负数");
    }
  }

  /**
   * Ant 风格路径匹配实现。
   *
   * <p>支持 {@code ?}(单字符)、{@code *}(单段内任意字符)、{@code **}(跨段任意字符)。
   */
  private static boolean matchAntPattern(String pattern, String path) {
    // 简化实现:将 Ant 模式转为正则表达式
    StringBuilder regex = new StringBuilder("^");
    int i = 0;
    while (i < pattern.length()) {
      char c = pattern.charAt(i);
      if (c == '*') {
        if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
          // ** 匹配跨段任意字符
          regex.append(".*");
          i += 2;
          // 跳过紧跟的 /
          if (i < pattern.length() && pattern.charAt(i) == '/') {
            i++;
          }
        } else {
          // * 匹配单段内任意字符
          regex.append("[^/]*");
          i++;
        }
      } else if (c == '?') {
        regex.append("[^/]");
        i++;
      } else if (c == '.' || c == '+' || c == '(' || c == ')' || c == '[' || c == ']'
          || c == '{' || c == '}' || c == '^' || c == '$' || c == '|') {
        regex.append('\\').append(c);
        i++;
      } else {
        regex.append(c);
        i++;
      }
    }
    regex.append("$");
    try {
      return Pattern.compile(regex.toString()).matcher(path).matches();
    } catch (PatternSyntaxException e) {
      return false;
    }
  }
}
