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
    "CREDENTIAL-001",
    "凭证编号不能为空"
  ),


  CREDENTIAL_OWNER_REQUIRED(
    "CREDENTIAL-002",
    "凭证持有者不能为空"
  ),


  CREDENTIAL_TYPE_REQUIRED(
    "CREDENTIAL-003",
    "凭证类型不能为空"
  ),


  /**
   * 凭证状态
   */
  CREDENTIAL_STATUS_REQUIRED(
    "CREDENTIAL-101",
    "凭证状态不能为空"
  ),


  CREDENTIAL_NOT_ACTIVE(
    "CREDENTIAL-102",
    "凭证当前不可用"
  ),


  CREDENTIAL_ALREADY_REVOKED(
    "CREDENTIAL-103",
    "凭证已经被撤销"
  ),


  CREDENTIAL_STATUS_CHANGE_NOT_ALLOWED(
    "CREDENTIAL-104",
    "凭证状态不允许变更"
  ),

  CREDENTIAL_EXPIRED(
    "CREDENTIAL-105",
    "凭证已失效"
  ),


  /**
   * 密码凭证
   */
  PASSWORD_HASH_REQUIRED(
    "CREDENTIAL-201",
    "密码凭证摘要不能为空"
  ),


  PASSWORD_ROTATE_NOT_ALLOWED(
    "CREDENTIAL-202",
    "当前凭证状态不允许修改密码"
  ),


  PASSWORD_SAME_AS_OLD(
    "CREDENTIAL-203",
    "新密码不能与旧密码相同"
  ),


  /**
   * UKey凭证
   */
  U_KEY_SERIAL_REQUIRED(
    "CREDENTIAL-301",
    "UKey序列号不能为空"
  ),


  U_KEY_ALREADY_BOUND(
    "CREDENTIAL-302",
    "UKey已经绑定其他持有者"
  ),


  U_KEY_NOT_FOUND(
    "CREDENTIAL-303",
    "UKey不存在"
  ),


  /**
   * 通用业务规则
   */
  CHANNEL_REQUIRED(
    "CREDENTIAL-401",
    "凭证适用渠道不能为空"
  ),


  CHANNEL_NOT_SUPPORTED(
    "CREDENTIAL-402",
    "凭证不支持当前渠道"
  ),


  CREDENTIAL_OWNER_MISMATCH(
    "CREDENTIAL-403",
    "凭证持有者不匹配"
  );


  private final String code;


  private final String message;


}
