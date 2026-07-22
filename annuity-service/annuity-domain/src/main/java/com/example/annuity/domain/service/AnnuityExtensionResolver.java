package com.example.annuity.domain.service;

import com.example.annuity.domain.errorcode.AnnuityDomainErrorCode;
import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.BusinessExtension;
import com.example.shared.domain.annotation.DomainService;
import com.example.shared.exception.DomainException;

/**
 * 年金扩展字段类型安全解析器
 * <p>
 * 集中处理 instanceof pattern matching,其他 domain service 与 application 层 Handler/Action
 * 通过注入本解析器获取强类型扩展字段,消除散落多处的强制类型转换。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@DomainService
public class AnnuityExtensionResolver {

  /**
   * 类型安全地解析年金扩展字段
   *
   * @param app 业务申请单
   * @return 年金扩展字段
   * @throws DomainException 如果扩展字段类型不匹配
   */
  public AnnuityApplicationExtension resolve(BusinessApplication app) {
    BusinessExtension ext = app.businessExtension();
    if (ext instanceof AnnuityApplicationExtension annuityExt) {
      return annuityExt;
    }
    throw new DomainException(AnnuityDomainErrorCode.INVALID_EXTENSION_TYPE)
        .withLogDetail("期望 AnnuityApplicationExtension,实际: "
            + (ext == null ? "null" : ext.getClass().getName()));
  }
}
