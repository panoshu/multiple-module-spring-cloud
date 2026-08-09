package com.example.bff.intranet.api;

import com.example.bff.intranet.api.dto.*;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * BFF 路由配置管理 API
 *
 * @author bff
 */
@HttpExchange("/management/routes")
public interface BffRouteManagementApi {

  @PostExchange("/create")
  ApiResult<Long> create(@Valid @RequestBody BffRouteConfigRequest request);

  @PostExchange("/update")
  ApiResult<Void> update(@Valid @RequestBody BffRouteConfigUpdateRequest request);

  @PostExchange("/delete")
  ApiResult<Void> delete(@Valid @RequestBody BffRouteConfigDeleteRequest request);

  @PostExchange("/get")
  ApiResult<BffRouteConfigResponse> get(@Valid @RequestBody BffRouteConfigGetRequest request);

  @PostExchange("/list")
  ApiResult<List<BffRouteConfigResponse>> list();

  @PostExchange("/refresh-cache")
  ApiResult<Void> refreshCache();
}
