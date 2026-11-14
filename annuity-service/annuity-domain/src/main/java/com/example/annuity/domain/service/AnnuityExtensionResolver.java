package com.example.annuity.domain.service;

import com.example.annuity.domain.errorcode.AnnuityDomainErrorCode;
import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.annuity.domain.repository.AnnuityApplicationExtensionStore;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.shared.domain.annotation.DomainService;
import com.example.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;

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
@RequiredArgsConstructor
public class AnnuityExtensionResolver {

  private final AnnuityApplicationExtensionStore extensionStore;

  /**
   * 类型安全地解析年金扩展字段（spec §6.2，批次 37b）。
   * <p>
   * 扩展数据已迁出 kernel 核心聚合，由业务服务自有存储按 {@code applicationId} 关联；
   * 本方法保持「传入申请单」的签名，使既有 Handler/Extractor/Action 调用点零改动。
   *
   * @param app 业务申请单
   * @return 年金扩展字段
   * @throws DomainException 如果未挂载年金扩展字段
   */
  public AnnuityApplicationExtension resolve(BusinessApplication app) {
    return extensionStore.load(app.id())
      .orElseThrow(() -> new DomainException(AnnuityDomainErrorCode.INVALID_EXTENSION_TYPE)
        .withLogDetail("申请单未挂载年金扩展字段: " + app.id().value()));
  }
}
