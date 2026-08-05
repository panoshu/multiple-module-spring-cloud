package com.pension.permission.domain.assignment.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/8/3 15:32
 */
@Getter
@AllArgsConstructor
public enum RoleError implements ErrorDefinition {

  ROLE_TEMPLATE_NOT_FOUND("SERVICE.AUTH.0202", "角色不存在"),
  UNSUPPORTED_SCOPE_DIMENSION("SERVICE.AUTH.0201", "不支持的范围维度"),

  ;

  private final String code;
  private final String message;

}
