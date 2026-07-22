package com.example.annuity.domain.service;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.dto.AnnuityEmployeeDTO;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.shared.domain.annotation.DomainService;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.UserNo;

import java.util.UUID;

/**
 * 年金员工 DTO 到实体的映射规则
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@DomainService
public class AnnuityEmployeeMapper {

  private static final UserNo SYSTEM_USER = UserNo.of("SYSTEM");

  /**
   * 将 DTO 映射为员工明细实体
   *
   * @param dto       JSON DTO
   * @param appId     申请单 ID(用于关联批次)
   * @param rowIndex  行号(用于追溯)
   * @return 员工明细实体
   */
  public AnnuityEmployeeDetail mapToEntity(AnnuityEmployeeDTO dto, ApplicationId appId, int rowIndex) {
    return new AnnuityEmployeeDetail(
        AnnuityEmployeeDetailId.of("D-" + UUID.randomUUID()),
        AnnuityEmployeeBatchId.of("B-" + appId.value()),
        dto.employeeName(),
        dto.idCardNo(),
        dto.age(),
        dto.monthlySalary(),
        dto.monthlyContribution(),
        SYSTEM_USER
    );
  }
}
