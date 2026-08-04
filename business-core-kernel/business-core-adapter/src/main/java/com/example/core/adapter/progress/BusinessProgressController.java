package com.example.core.adapter.progress;

import com.example.core.adapter.context.SessionContextResolver;
import com.example.core.adapter.progress.converter.ProgressConverter;
import com.example.core.adapter.security.RequireBusinessPermission;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务进度查询 Controller
 *
 * <p>实现 {@link BusinessProgressApi},入口完成会话解析与功能权限校验,
 * 调用 {@link BusinessProgressAppService} 查询进度数据。
 *
 * <p>后续新增 Controller 方法流程:
 * <ol>
 *   <li>在 API 层接口新增方法签名</li>
 *   <li>在本类实现方法,标注 @RequireBusinessPermission(功能权限码)</li>
 *   <li>通过 ProgressConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BusinessProgressController implements BusinessProgressApi {

  private final BusinessProgressAppService progressAppService;
  private final ProgressConverter converter;
  private final SessionContextResolver sessionResolver;

  @Override
  @RequireBusinessPermission("PROGRESS_VIEW")
  public ApiResult<BatchProgressResponse> batchProgress(@Valid @RequestBody GetBatchProgressQuery query) {
    SessionContext session = sessionResolver.require();
    log.info("查询批次进度: batchId={}, userNo={}", query.batchId(), session.userNo());

    BusinessBatch batch = progressAppService.getBatchProgress(new BatchId(query.batchId()));
    return ApiResult.success(converter.toBatchProgressResponse(batch));
  }
}
