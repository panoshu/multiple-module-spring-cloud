package com.example.annuity.domain.repository;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.core.types.ApplicationId;

import java.util.Optional;

/**
 * 年金业务申请扩展字段自有存储 SPI（spec §6.2，批次 37b）。
 * <p>
 * 业务扩展大对象从 kernel 的 {@code BusinessApplication} 核心聚合迁出，
 * 由业务服务按 {@code applicationId} 自行持有；domain 层只定义契约，
 * 具体的内存/持久化实现由基础设施层提供。
 *
 * @author annuity-service
 */
public interface AnnuityApplicationExtensionStore {

  void save(ApplicationId applicationId, AnnuityApplicationExtension extension);

  Optional<AnnuityApplicationExtension> load(ApplicationId applicationId);
}
