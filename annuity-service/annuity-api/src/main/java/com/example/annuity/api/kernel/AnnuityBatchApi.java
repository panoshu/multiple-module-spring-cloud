package com.example.annuity.api.kernel;

import com.example.core.api.batch.BusinessBatchApi;
import com.example.core.api.batch.command.CancelBatchCommand;
import com.example.core.api.batch.command.CreateBatchCommand;
import com.example.core.api.batch.query.FindActiveBatchQuery;
import com.example.core.api.batch.query.GetBatchDetailQuery;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.batch.response.BatchSummaryResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 年金服务 — 批次管理 API（服务专有 kernel API）
 *
 * <p>继承 {@link BusinessBatchApi} 纯契约接口，添加服务级路径前缀
 * {@code /annuity/core/batch}，供 BFF 通过 httpexchange 代理调用。
 *
 * @author panoshu
 */
@HttpExchange("/annuity/core/batch")
public interface AnnuityBatchApi extends BusinessBatchApi {

  @PostExchange("/active")
  @Override
  ApiResult<BatchSummaryResponse> findActive(@Valid @RequestBody FindActiveBatchQuery query);

  @PostExchange("/create")
  @Override
  ApiResult<BatchCreatedResponse> create(@Valid @RequestBody CreateBatchCommand command);

  @PostExchange("/detail")
  @Override
  ApiResult<BatchDetailResponse> detail(@Valid @RequestBody GetBatchDetailQuery query);

  @PostExchange("/cancel")
  @Override
  ApiResult<Void> cancel(@Valid @RequestBody CancelBatchCommand command);
}
