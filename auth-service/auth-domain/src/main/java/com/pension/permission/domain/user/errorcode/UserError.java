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
    "USER-001",
    "用户编号不能为空"
  ),


  USER_TYPE_REQUIRED(
    "USER-002",
    "用户类型不能为空"
  ),


  /**
   * 身份证件
   */
  IDENTITY_DOCUMENT_REQUIRED(
    "USER-101",
    "用户证件信息不能为空"
  ),


  ONLY_ID_CARD_SUPPORTED(
    "USER-102",
    "当前用户仅支持居民身份证"
  ),


  IDENTITY_DOCUMENT_INVALID(
    "USER-103",
    "用户证件信息无效"
  ),


  /**
   * 联系方式
   */
  MOBILE_REQUIRED(
    "USER-201",
    "用户手机号不能为空"
  ),


  EMAIL_INVALID(
    "USER-202",
    "用户邮箱格式无效"
  ),


  /**
   * 用户状态
   */
  USER_STATUS_REQUIRED(
    "USER-301",
    "用户状态不能为空"
  ),


  USER_ALREADY_FROZEN(
    "USER-302",
    "用户已经被冻结"
  ),


  USER_ALREADY_ACTIVE(
    "USER-303",
    "用户已经是激活状态"
  ),


  USER_STATUS_CHANGE_NOT_ALLOWED(
    "USER-304",
    "用户状态不允许变更"
  ),


  /**
   * 用户身份
   */
  USER_IDENTITY_NOT_FOUND(
    "USER-401",
    "用户身份不存在"
  ),


  USER_IDENTITY_CONFLICT(
    "USER-402",
    "用户身份存在冲突"
  );

  private final String code;
  private final String message;

}
