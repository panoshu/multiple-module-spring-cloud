package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.Objects;

/**
 * 业务编码 - 标识一类企业年金业务。
 *
 * <p>格式如 {@code ANNUITY_ESTABLISH}(年金计划设立)、{@code ANNUITY_CONTRIBUTION}(年金缴费)、
 * {@code ANNUITY_PAYMENT}(年金支付)等。业务编码由 {@code BusinessDefinition} 定义,
 * 权限规则通过 businessCode 关联到具体业务。
 *
 * <p>权限码由 {@code businessCode + "." + action} 组成(如 {@code ANNUITY_ESTABLISH.HANDLE}),
 * 由 {@code PermissionResolver} 在计算时生成。
 *
 * <p>不变量:
 * <ul>
 *   <li>value 非 null(违反抛 {@link NullPointerException})</li>
 *   <li>value 非 blank(违反抛 {@link IllegalArgumentException})</li>
 *   <li>value 仅包含大写字母、数字、下划线(违反抛 {@link IllegalArgumentException})</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record BusinessCode(String value) implements ValueObject {

  private static final java.util.regex.Pattern PATTERN =
      java.util.regex.Pattern.compile("^[A-Z][A-Z0-9_]*$");

  public BusinessCode {
    Objects.requireNonNull(value, "BusinessCode value cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("BusinessCode value cannot be blank");
    }
    if (!PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "BusinessCode value must match pattern [A-Z][A-Z0-9_]*: " + value);
    }
  }

  /**
   * 静态工厂方法。
   *
   * @param value 业务编码字符串(如 "ANNUITY_ESTABLISH")
   * @return 业务编码值对象
   */
  public static BusinessCode of(String value) {
    return new BusinessCode(value);
  }

  /**
   * 拼接动作生成权限码。
   *
   * @param action 业务动作
   * @return 权限码(如 "ANNUITY_ESTABLISH.HANDLE")
   */
  public PermissionCode toPermissionCode(Action action) {
    Objects.requireNonNull(action, "action cannot be null");
    return PermissionCode.of(value + "." + action.name());
  }
}
