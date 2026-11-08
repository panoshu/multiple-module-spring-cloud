package com.example.file.api;

import com.example.file.api.request.GenerateOutboundFileRequest;
import com.example.file.api.response.OutboundFileIdResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 出站文件生成 API
 * <p>
 * 由 kernel 的 FileServiceIntegrationGateway 通过 HTTP 调用触发。
 */
@HttpExchange("/api/file/outbound")
public interface OutboundApi {

  @PostExchange("/generate")
  ApiResult<OutboundFileIdResponse> generate(@RequestBody @Valid GenerateOutboundFileRequest request);
}
