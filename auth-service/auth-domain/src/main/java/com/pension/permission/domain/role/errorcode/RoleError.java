package com.pension.permission.domain.role.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 角色模板错误码（SERVICE.AUTH.03xx）.
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/8/3 15:32
 */
@Getter
@AllArgsConstructor
public enum RoleError implements ErrorDefinition {

  ROLE_TEMPLATE_NOT_FOUND("SERVICE.AUTH.0301", "角色模板不存在"),

  ;

  private final String code;
  private final String message;

}
