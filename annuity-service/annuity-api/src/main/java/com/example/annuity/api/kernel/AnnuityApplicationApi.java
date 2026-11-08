package com.example.annuity.api.kernel;

import com.example.core.api.application.BusinessApplicationApi;
import com.example.core.api.application.command.AdvanceStepCommand;
import com.example.core.api.application.command.SubmitApplicationCommand;
import com.example.core.api.application.query.FindApplicationListQuery;
import com.example.core.api.application.query.GetApplicationDetailQuery;
import com.example.core.api.application.response.AdvanceStepResponse;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 年金服务 — 申请单管理 API（服务专有 kernel API）
 *
 * <p>继承 {@link BusinessApplicationApi} 纯契约接口，添加服务级路径前缀
 * {@code /annuity/core/application}，供 BFF 通过 httpexchange 代理调用。
 *
 * @author panoshu
 */
@HttpExchange("/annuity/core/application")
public interface AnnuityApplicationApi extends BusinessApplicationApi {

  @PostExchange("/list")
  @Override
  ApiResult<List<ApplicationSummaryResponse>> list(@Valid @RequestBody FindApplicationListQuery query);

  @PostExchange("/detail")
  @Override
  ApiResult<ApplicationDetailResponse> detail(@Valid @RequestBody GetApplicationDetailQuery query);

  @PostExchange("/advance")
  @Override
  ApiResult<AdvanceStepResponse> advance(@Valid @RequestBody AdvanceStepCommand command);

  @PostExchange("/submit")
  @Override
  ApiResult<SubmitResponse> submit(@Valid @RequestBody SubmitApplicationCommand command);
}
