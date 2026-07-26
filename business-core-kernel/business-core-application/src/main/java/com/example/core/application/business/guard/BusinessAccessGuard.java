package com.example.core.application.business.guard;

import com.example.core.api.context.BusinessMetaContext;
import com.example.core.api.context.SessionContext;

/**
 * 业务访问守门人 SPI
 *
 * <p>定义业务办理的统一权限校验契约,由 {@code DefaultBusinessAccessGuard} 提供默认实现,
 * 业务服务可覆盖以扩展自定义校验(如年金服务的外资业务准入)。
 *
 * <p>校验范围(按渠道差异化):
 * <ul>
 *   <li>通用:计划一致性 + 客户一致性 + 业务类型办理权限</li>
 *   <li>INTERNET:代办时校验 planNo 在 delegatedPlanNos 内</li>
 *   <li>BRANCH:必须 hasSecondaryAuth=true</li>
 *   <li>HQ:无额外校验</li>
 * </ul>
 *
 * @author panoshu
 */
public interface BusinessAccessGuard {

    /**
     * 校验当前会话用户对指定业务类型的办理权限(含渠道差异化校验:代办 / 二次授权)。
     *
     * @param session 会话上下文
     * @param meta 业务元数据上下文
     * @throws com.example.shared.exception.BusinessException 校验不通过时
     */
    void checkCanHandle(SessionContext session, BusinessMetaContext meta);
}
