package com.example.annuity.api.kernel;

import com.example.core.api.progress.BusinessProgressApi;
import com.example.core.api.progress.query.GetBatchProgressQuery;
import com.example.core.api.progress.response.BatchProgressResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 年金服务 — 进度查询 API（服务专有 kernel API）
 *
 * <p>继承 {@link BusinessProgressApi} 纯契约接口，添加服务级路径前缀
 * {@code /annuity/core/progress}，供 BFF 通过 httpexchange 代理调用。
 *
 * @author panoshu
 */
@HttpExchange("/annuity/core/progress")
public interface AnnuityProgressApi extends BusinessProgressApi {

  @PostExchange("/batch")
  @Override
  ApiResult<BatchProgressResponse> batchProgress(@Valid @RequestBody GetBatchProgressQuery query);
}
