package com.example.integration.api.core.trade.api;

import com.example.integration.api.core.trade.dto.FileSubmissionRequestDTO;
import com.example.integration.api.core.trade.dto.FileSubmissionResponseDTO;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 出站文件提交 API
 */
@HttpExchange("/api/v1/trade/submission")
public interface TradeSubmissionApi {

  @PostExchange("/file")
  ApiResult<FileSubmissionResponseDTO> submitFile(@RequestBody @Valid FileSubmissionRequestDTO request);
}
