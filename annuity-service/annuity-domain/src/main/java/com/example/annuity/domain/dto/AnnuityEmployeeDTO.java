package com.example.annuity.domain.dto;

/**
 * 年金员工明细 JSON DTO
 * <p>
 * 用于从解析后的 JSON 文件流式摄入员工明细数据。
 *
 * @param employeeName       员工姓名
 * @param idCardNo           身份证号
 * @param age                年龄
 * @param monthlySalary      月薪(分)
 * @param monthlyContribution 月缴费(分)
 * @author annuity-service
 * @since 2026/7/22
 */
public record AnnuityEmployeeDTO(
    String employeeName,
    String idCardNo,
    Integer age,
    Long monthlySalary,
    Long monthlyContribution
) {

  public AnnuityEmployeeDTO {
    if (employeeName == null || employeeName.isBlank()) {
      throw new IllegalArgumentException("employeeName cannot be blank");
    }
    if (idCardNo == null || idCardNo.isBlank()) {
      throw new IllegalArgumentException("idCardNo cannot be blank");
    }
  }
}
