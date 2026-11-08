package com.example.integration.infrastructure.core.trade.mapper;

import com.example.integration.domain.trade.model.FileSubmissionResult;
import com.example.integration.domain.trade.model.FileSubmissionResult.SubmissionStatus;
import com.example.integration.infrastructure.core.trade.dto.Submit5565Res;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/**
 * 文件提交基础设施层转换器
 * <p>
 * 负责外部交易报文 DTO ↔ 领域模型的转换。
 */
@Mapper(
  componentModel = "spring",
  unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface FileSubmissionInfraMapper {

  /**
   * 基础设施响应 → 领域模型
   */
  @Mapping(target = "tradeCode", constant = "5565")
  @Mapping(target = "status", source = "res", qualifiedByName = "mapStatus")
  @Mapping(target = "message", source = "processMessage")
  @Mapping(target = "externalSerialNo", source = "transactionSerialNo")
  @Mapping(target = "responseTime", ignore = true)
  FileSubmissionResult toResult(Submit5565Res res);

  @Named("mapStatus")
  default SubmissionStatus mapStatus(Submit5565Res res) {
    if (res == null) {
      return SubmissionStatus.FAILED;
    }
    return res.isProcessedSuccessfully() ? SubmissionStatus.SUCCESS : SubmissionStatus.FAILED;
  }
}
