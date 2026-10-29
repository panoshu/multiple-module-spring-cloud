package com.example.annuity.adapter.controller.kernel;

import com.example.auth.api.annotation.RequirePermission;
import com.example.core.adapter.context.SessionContextResolver;
import com.example.core.adapter.progress.converter.ProgressConverter;
import com.example.core.api.context.SessionContext;
import com.example.core.api.progress.BusinessProgressApi;
import com.example.core.api.progress.query.GetBatchProgressQuery;
import com.example.core.api.progress.response.BatchProgressResponse;
import com.example.core.application.business.service.BusinessProgressAppService;
import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.shared.identifier.id.BatchId;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 年金服务 - 进度查询 Controller（服务级路径前缀版）
 *
 * <p>实现 {@link BusinessProgressApi}（纯契约接口），路径前缀为 {@code /annuity/core/progress}。
 *
 * @author panoshu
 */
@Slf4j
@RestController
@RequestMapping("/annuity/core/progress")
@RequiredArgsConstructor
public class AnnuityKernelProgressController implements BusinessProgressApi {

  private final BusinessProgressAppService progressAppService;
  private final ProgressConverter converter;
  private final SessionContextResolver sessionResolver;

  @Override
  @PostMapping("/batch")
  @RequirePermission(business = "PROGRESS", action = "VIEW")
  public ApiResult<BatchProgressResponse> batchProgress(@Valid @RequestBody GetBatchProgressQuery query) {
    SessionContext session = sessionResolver.require();
    log.info("查询批次进度: batchId={}, userNo={}", query.batchId(), session.userNo());

    BusinessBatch batch = progressAppService.getBatchProgress(new BatchId(query.batchId()));
    return ApiResult.success(converter.toBatchProgressResponse(batch));
  }
}
