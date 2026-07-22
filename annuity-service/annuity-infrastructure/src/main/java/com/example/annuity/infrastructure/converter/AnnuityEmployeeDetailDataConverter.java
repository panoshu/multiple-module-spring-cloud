package com.example.annuity.infrastructure.converter;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeDetailStatus;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeMaterial;
import com.example.annuity.infrastructure.entity.AnnuityEmployeeDetailDO;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 年金员工明细 DO ↔ 领域对象转换器
 * <p>
 * 手写转换以处理 materials 字段的 JSON 序列化/反序列化、字段名差异（detailStatus/status、
 * createTime/createdAt）和类型转换（String↔ID、Integer↔Version、String↔UserNo、String↔枚举）。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Mapper(componentModel = "spring")
public interface AnnuityEmployeeDetailDataConverter {

  ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * 领域对象 → DO
   */
  default AnnuityEmployeeDetailDO toDO(AnnuityEmployeeDetail detail) {
    if (detail == null) {
      return null;
    }
    AnnuityEmployeeDetailDO aDo = new AnnuityEmployeeDetailDO();
    aDo.setId(detail.id() != null ? detail.id().value() : null);
    aDo.setBatchId(detail.batchId() != null ? detail.batchId().value() : null);
    aDo.setEmployeeName(detail.employeeName());
    aDo.setIdCardNo(detail.idCardNo());
    aDo.setAge(detail.age());
    aDo.setMonthlySalary(detail.monthlySalary());
    aDo.setMonthlyContribution(detail.monthlyContribution());
    aDo.setDetailStatus(detail.status() != null ? detail.status().name() : null);
    aDo.setAnomalyReason(detail.anomalyReason());
    aDo.setMaterials(materialsToJson(detail.materials()));
    aDo.setVerifiedAt(detail.verifiedAt());
    aDo.setMaterialPreparedAt(detail.materialPreparedAt());

    aDo.setCreatedBy(detail.createdBy() != null ? detail.createdBy().value() : null);
    aDo.setUpdatedBy(detail.updatedBy() != null ? detail.updatedBy().value() : null);
    aDo.setCreateTime(detail.createdAt());
    aDo.setUpdateTime(detail.updatedAt());
    aDo.setDeleted(false);
    aDo.setVersion(detail.version() != null ? (int) detail.version().value() : 0);
    return aDo;
  }

  /**
   * DO → 领域对象
   */
  default AnnuityEmployeeDetail toDomain(AnnuityEmployeeDetailDO aDo) {
    if (aDo == null) {
      return null;
    }
    return new AnnuityEmployeeDetail(
        aDo.getId() != null ? AnnuityEmployeeDetailId.of(aDo.getId()) : null,
        aDo.getBatchId() != null ? AnnuityEmployeeBatchId.of(aDo.getBatchId()) : null,
        aDo.getEmployeeName(),
        aDo.getIdCardNo(),
        aDo.getAge(),
        aDo.getMonthlySalary(),
        aDo.getMonthlyContribution(),
        aDo.getDetailStatus() != null ? AnnuityEmployeeDetailStatus.valueOf(aDo.getDetailStatus()) : null,
        aDo.getAnomalyReason(),
        jsonToMaterials(aDo.getMaterials()),
        aDo.getVerifiedAt(),
        aDo.getMaterialPreparedAt(),
        aDo.getCreatedBy() != null ? UserNo.of(aDo.getCreatedBy()) : null,
        aDo.getUpdatedBy() != null ? UserNo.of(aDo.getUpdatedBy()) : null,
        aDo.getCreateTime(),
        aDo.getUpdateTime(),
        aDo.getVersion() != null ? Version.of(aDo.getVersion().longValue()) : Version.initial()
    );
  }

  private String materialsToJson(List<AnnuityEmployeeMaterial> materials) {
    if (materials == null || materials.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(materials);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("序列化材料清单失败", e);
    }
  }

  private List<AnnuityEmployeeMaterial> jsonToMaterials(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("反序列化材料清单失败: " + json, e);
    }
  }
}
