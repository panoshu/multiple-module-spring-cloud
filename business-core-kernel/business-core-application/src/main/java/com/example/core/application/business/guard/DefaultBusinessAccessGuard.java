package com.example.core.application.business.guard;

import com.example.core.api.context.BusinessMetaContext;
import com.example.core.api.context.SessionContext;
import com.example.shared.exception.BusinessException;
import com.example.shared.exception.CommonError;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 默认业务访问守门人实现
 *
 * <p>按渠道分支差异化校验:
 * <ul>
 *   <li>通用校验(所有渠道):计划一致性 + 客户一致性 + 业务类型办理权限</li>
 *   <li>INTERNET 渠道:代办时校验 planNo 在 delegatedPlanNos 内</li>
 *   <li>BRANCH 渠道:必须 hasSecondaryAuth=true</li>
 *   <li>HQ 渠道:无额外校验</li>
 * </ul>
 *
 * <p>业务服务可通过提供自定义 {@link BusinessAccessGuard} Bean 覆盖本实现。
 *
 * @author panoshu
 */
@Component
@ConditionalOnMissingBean(BusinessAccessGuard.class)
public class DefaultBusinessAccessGuard implements BusinessAccessGuard {

  private static final String INTERNET = "INTERNET";
  private static final String BRANCH = "BRANCH";
  private static final String HQ = "HQ";

  @Override
  public void checkCanHandle(SessionContext session, BusinessMetaContext meta) {
    checkCommon(session, meta);
    switch (session.channelType()) {
      case INTERNET -> checkInternetProxy(session, meta);
      case BRANCH -> checkBranchSecondaryAuth(session);
      case HQ -> { /* 无额外校验 */ }
      default -> throw new BusinessException(CommonError.BAD_REQUEST)
        .withUserDetail("不支持的渠道类型")
        .withLogDetail("channelType=%s".formatted(session.channelType()));
    }
  }

  private void checkCommon(SessionContext session, BusinessMetaContext meta) {
    if (!Objects.equals(meta.planNo(), session.planNo())) {
      throw new BusinessException(CommonError.BAD_REQUEST)
        .withUserDetail("所选计划与会话中的计划不一致")
        .withLogDetail("metaPlanNo=%s, sessionPlanNo=%s".formatted(meta.planNo(), session.planNo()));
    }
    if (!Objects.equals(meta.customerNo(), session.customerNo())) {
      throw new BusinessException(CommonError.BAD_REQUEST)
        .withUserDetail("客户信息不一致")
        .withLogDetail("metaCustomerNo=%s, sessionCustomerNo=%s".formatted(meta.customerNo(), session.customerNo()));
    }
    String requiredPermission = "BUSINESS_%s_HANDLE".formatted(meta.businessType());
    if (session.permissionCodes() == null || !session.permissionCodes().contains(requiredPermission)) {
      throw new BusinessException(CommonError.FORBIDDEN)
        .withUserDetail("无办理权限")
        .withLogDetail("requiredPermission=%s, owned=%s".formatted(requiredPermission, session.permissionCodes()));
    }
  }

  private void checkInternetProxy(SessionContext session, BusinessMetaContext meta) {
    if (session.isProxy()) {
      if (session.delegatedPlanNos() == null || !session.delegatedPlanNos().contains(meta.planNo())) {
        throw new BusinessException(CommonError.FORBIDDEN)
          .withUserDetail("无代办权限")
          .withLogDetail("proxy planNo=%s not in delegated=%s".formatted(meta.planNo(), session.delegatedPlanNos()));
      }
    }
  }

  private void checkBranchSecondaryAuth(SessionContext session) {
    if (!session.hasSecondaryAuth()) {
      throw new BusinessException(CommonError.FORBIDDEN)
        .withUserDetail("网点渠道办理业务需要二次授权")
        .withLogDetail("BRANCH channel without secondary auth");
    }
  }
}
