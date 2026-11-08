package com.example.integration.adapter.trade;

import com.example.integration.adapter.trade.mapper.FileSubmissionConverter;
import com.example.integration.api.core.trade.api.TradeSubmissionApi;
import com.example.integration.api.core.trade.dto.FileSubmissionRequestDTO;
import com.example.integration.api.core.trade.dto.FileSubmissionResponseDTO;
import com.example.integration.application.trade.service.TradeSubmissionAppService;
import com.example.integration.domain.trade.model.FileSubmissionResult;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * 出站文件提交适配器
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class TradeSubmissionAdapter implements TradeSubmissionApi {

  private final TradeSubmissionAppService submissionAppService;
  private final FileSubmissionConverter fileSubmissionConverter;

  @Override
  public ApiResult<FileSubmissionResponseDTO> submitFile(FileSubmissionRequestDTO request) {
    // 1. 调用应用服务
    FileSubmissionResult result = submissionAppService.submitOutboundFile(
      request.fileId(), request.tradeCode(), request.headParams()
    );

    // 2. 领域模型 -> 响应 DTO
    FileSubmissionResponseDTO response = fileSubmissionConverter.toResponse(result);

    return ApiResult.success(response);
  }
}
