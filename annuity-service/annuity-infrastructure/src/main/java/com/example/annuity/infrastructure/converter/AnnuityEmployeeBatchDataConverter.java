package com.example.annuity.infrastructure.converter;

import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeBatchStatus;
import com.example.annuity.infrastructure.entity.AnnuityEmployeeBatchDO;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.UserNo;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import org.mapstruct.Mapper;

import java.time.LocalDateTime;

/**
 * 年金员工批次 DO ↔ 领域对象转换器
 * <p>
 * 手写转换以处理字段名差异（createTime/createdAt、batchStatus/status）和类型转换
 * （String↔UserNo、Integer↔Version、String↔枚举、String↔ID）。
 * {@code details} 不在此转换，由 {@code AnnuityEmployeeBatchRepositoryImpl} 通过
 * {@link AnnuityEmployeeBatch#attachDetail} 注入。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Mapper(componentModel = "spring")
public interface AnnuityEmployeeBatchDataConverter {

  /**
   * 领域对象 → DO
   */
  default AnnuityEmployeeBatchDO toDO(AnnuityEmployeeBatch batch) {
    if (batch == null) {
      return null;
    }
    AnnuityEmployeeBatchDO aDo = new AnnuityEmployeeBatchDO();
    aDo.setId(batch.id() != null ? batch.id().value() : null);
    aDo.setApplicationId(batch.applicationId() != null ? batch.applicationId().value() : null);
    aDo.setBatchStatus(batch.status() != null ? batch.status().name() : null);
    aDo.setTotalEmployeeCount(batch.totalEmployeeCount());
    aDo.setProcessedCount(batch.processedCount());
    aDo.setAnomalyCount(batch.anomalyCount());

    aDo.setCreatedBy(batch.createdBy() != null ? batch.createdBy().value() : null);
    aDo.setUpdatedBy(batch.updatedBy() != null ? batch.updatedBy().value() : null);
    aDo.setCreateTime(batch.createdAt());
    aDo.setUpdateTime(batch.updatedAt());
    aDo.setDeleted(false);
    aDo.setVersion(batch.version() != null ? (int) batch.version().value() : 0);
    return aDo;
  }

  /**
   * DO → 领域对象（不含 details，由 Repository 注入）
   */
  default AnnuityEmployeeBatch toDomain(AnnuityEmployeeBatchDO aDo) {
    if (aDo == null) {
      return null;
    }
    return new AnnuityEmployeeBatch(
        aDo.getId() != null ? AnnuityEmployeeBatchId.of(aDo.getId()) : null,
        aDo.getApplicationId() != null ? new ApplicationId(aDo.getApplicationId()) : null,
        null,
        aDo.getBatchStatus() != null ? AnnuityEmployeeBatchStatus.valueOf(aDo.getBatchStatus()) : null,
        aDo.getTotalEmployeeCount() != null ? aDo.getTotalEmployeeCount() : 0,
        aDo.getProcessedCount() != null ? aDo.getProcessedCount() : 0,
        aDo.getAnomalyCount() != null ? aDo.getAnomalyCount() : 0,
        aDo.getCreatedBy() != null ? UserNo.of(aDo.getCreatedBy()) : null,
        aDo.getUpdatedBy() != null ? UserNo.of(aDo.getUpdatedBy()) : null,
        aDo.getCreateTime(),
        aDo.getUpdateTime(),
        aDo.getVersion() != null ? Version.of(aDo.getVersion().longValue()) : Version.initial()
    );
  }
}
