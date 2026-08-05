package com.pension.permission.domain.user.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserError
  implements ErrorDefinition {


  /**
   * 用户基础信息
   */
  USER_ID_REQUIRED(
    "SERVICE.AUTH.0601",
    "用户编号不能为空"
  ),

  USER_TYPE_REQUIRED(
    "SERVICE.AUTH.0602",
    "用户类型不能为空"
  ),


  /**
   * 身份证件
   */
  IDENTITY_DOCUMENT_REQUIRED(
    "SERVICE.AUTH.0611",
    "用户证件信息不能为空"
  ),


  ONLY_ID_CARD_SUPPORTED(
    "SERVICE.AUTH.0612",
    "当前用户仅支持居民身份证"
  ),

  IDENTITY_DOCUMENT_INVALID(
    "SERVICE.AUTH.0613",
    "用户证件信息无效"
  ),


  /**
   * 联系方式
   */
  MOBILE_REQUIRED(
    "SERVICE.AUTH.0621",
    "用户手机号不能为空"
  ),


  EMAIL_INVALID(
    "SERVICE.AUTH.0622",
    "用户邮箱格式无效"
  ),


  /**
   * 用户状态
   */
  USER_STATUS_REQUIRED(
    "SERVICE.AUTH.0631",
    "用户状态不能为空"
  ),


  USER_ALREADY_FROZEN(
    "SERVICE.AUTH.0632",
    "用户已经被冻结"
  ),


  USER_ALREADY_ACTIVE(
    "SERVICE.AUTH.0633",
    "用户已经是激活状态"
  ),

  USER_STATUS_CHANGE_NOT_ALLOWED(
    "SERVICE.AUTH.0634",
    "用户状态不允许变更"
  ),


  /**
   * 用户身份
   */
  USER_IDENTITY_NOT_FOUND(
    "SERVICE.AUTH.0641",
    "用户身份不存在"
  ),


  USER_IDENTITY_CONFLICT(
    "SERVICE.AUTH.0642",
    "用户身份存在冲突"
  );

  private final String code;
  private final String message;

}
