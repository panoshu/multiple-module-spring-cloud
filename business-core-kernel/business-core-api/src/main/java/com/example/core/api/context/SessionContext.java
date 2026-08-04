package com.example.core.api.context;

import java.util.Set;

/**
 * 会话上下文 DTO
 *
 * <p>由 iam-service 写入 sa-token Token-Session,gateway 透传到 X-Session-Context header,
 * kernel 通过 {@link com.example.core.adapter.context.SessionContextResolver} 解析。
 *
 * <p>字段超集,各渠道按需填充:
 * <ul>
 *   <li>身份/渠道/客户/计划字段:全部渠道</li>
 *   <li>代办字段(isProxy/onBehalfOf*):仅 INTERNET 渠道</li>
 *   <li>二次授权字段(hasSecondaryAuth/borrowedApproverId):仅 BRANCH 渠道</li>
 *   <li>代办范围(delegatedPlanNos):仅 INTERNET 渠道</li>
 * </ul>
 *
 * @author panoshu
 */
public record SessionContext(
  String userNo,
  String userType,
  String loginName,
  String displayName,
  String channelType,
  String clientId,
  String clientIp,
  String customerNo,
  String customerName,
  String planNo,
  String planName,
  String productNo,
  String productName,
  String operationModel,
  String accountManager,
  boolean isProxy,
  String onBehalfOfUserNo,
  String onBehalfOfLoginName,
  boolean hasSecondaryAuth,
  Long secondaryAuthSessionId,
  String borrowedApproverId,
  Set<String> permissionCodes,
  Set<String> delegatedPlanNos
) {
}
