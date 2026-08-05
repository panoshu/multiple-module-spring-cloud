package com.pension.permission.domain.credential.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * CredentialError
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 21:16
 */
@Getter
@RequiredArgsConstructor
public enum CredentialError
  implements ErrorDefinition {


  /**
   * 凭证基础信息
   */
  CREDENTIAL_ID_REQUIRED(
    "SERVICE.AUTH.0501",
    "凭证编号不能为空"
  ),


  CREDENTIAL_OWNER_REQUIRED(
    "SERVICE.AUTH.0502",
    "凭证持有者不能为空"
  ),


  CREDENTIAL_TYPE_REQUIRED(
    "SERVICE.AUTH.0503",
    "凭证类型不能为空"
  ),


  /**
   * 凭证状态
   */
  CREDENTIAL_STATUS_REQUIRED(
    "SERVICE.AUTH.0511",
    "凭证状态不能为空"
  ),


  CREDENTIAL_NOT_ACTIVE(
    "SERVICE.AUTH.0512",
    "凭证当前不可用"
  ),


  CREDENTIAL_ALREADY_REVOKED(
    "SERVICE.AUTH.0513",
    "凭证已经被撤销"
  ),


  CREDENTIAL_STATUS_CHANGE_NOT_ALLOWED(
    "SERVICE.AUTH.0514",
    "凭证状态不允许变更"
  ),

  CREDENTIAL_EXPIRED(
    "SERVICE.AUTH.0515",
    "凭证已失效"
  ),


  /**
   * 密码凭证
   */
  PASSWORD_HASH_REQUIRED(
    "SERVICE.AUTH.0521",
    "密码凭证摘要不能为空"
  ),


  PASSWORD_ROTATE_NOT_ALLOWED(
    "SERVICE.AUTH.0522",
    "当前凭证状态不允许修改密码"
  ),


  PASSWORD_SAME_AS_OLD(
    "SERVICE.AUTH.0523",
    "新密码不能与旧密码相同"
  ),


  /**
   * UKey凭证
   */
  U_KEY_SERIAL_REQUIRED(
    "SERVICE.AUTH.0531",
    "UKey序列号不能为空"
  ),


  U_KEY_ALREADY_BOUND(
    "SERVICE.AUTH.0532",
    "UKey已经绑定其他持有者"
  ),


  U_KEY_NOT_FOUND(
    "SERVICE.AUTH.0533",
    "UKey不存在"
  ),


  /**
   * 通用业务规则
   */
  CHANNEL_REQUIRED(
    "SERVICE.AUTH.0541",
    "凭证适用渠道不能为空"
  ),


  CHANNEL_NOT_SUPPORTED(
    "SERVICE.AUTH.0542",
    "凭证不支持当前渠道"
  ),


  CREDENTIAL_OWNER_MISMATCH(
    "SERVICE.AUTH.0543",
    "凭证持有者不匹配"
  );


  private final String code;


  private final String message;


}
