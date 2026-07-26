package com.example.iam.domain.authorization.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;
import java.util.Objects;

/**
 * 权限码 - 标识一个具体的业务动作权限。
 *
 * <p>格式如 {@code business1.handle}、{@code business2.query},由业务编码 + 动作组成。
 * 设计文档 3.7 节 PermissionResolver 计算输出权限码集合。
 *
 * <p>不变量:
 * <ul>
 *   <li>value 非 null(违反抛 {@link NullPointerException})</li>
 *   <li>value 非 blank(违反抛 {@link IllegalArgumentException})</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record PermissionCode(String value) implements ValueObject {

  public PermissionCode {
    Objects.requireNonNull(value, "PermissionCode value cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("PermissionCode value cannot be blank");
    }
  }

  /**
   * 静态工厂方法。
   *
   * @param value 权限码字符串(如 "business1.handle")
   * @return 权限码值对象
   */
  public static PermissionCode of(String value) {
    return new PermissionCode(value);
  }
}
