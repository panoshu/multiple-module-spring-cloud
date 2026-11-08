package com.example.bff.intranet.api;

import com.example.bff.intranet.api.dto.BffBusinessTypeResponse;
import com.example.bff.intranet.api.dto.BffSystemInfoResponse;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * BFF 系统配置管理 API
 *
 * @author bff
 */
@HttpExchange("/management/system")
public interface BffSystemApi {

  @PostExchange("/info")
  ApiResult<BffSystemInfoResponse> getInfo();

  @PostExchange("/business-types")
  ApiResult<List<BffBusinessTypeResponse>> listBusinessTypes();
}
