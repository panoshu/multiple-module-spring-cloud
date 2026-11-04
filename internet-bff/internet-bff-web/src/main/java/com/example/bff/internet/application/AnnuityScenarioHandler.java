package com.example.bff.internet.application;

import com.example.annuity.api.kernel.AnnuityApplicationApi;
import com.example.annuity.api.kernel.AnnuityBatchApi;
import com.example.annuity.api.kernel.AnnuityFormApi;
import com.example.annuity.api.kernel.AnnuityMaterialApi;
import com.example.annuity.api.kernel.AnnuityProgressApi;
import com.example.bff.shared.business.BffAggregationException;
import com.example.bff.shared.business.BffResponseAssembler;
import com.example.bff.shared.business.BusinessScenarioHandler;
import com.example.bff.shared.business.ScenarioHandlerRegistry;
import com.example.bff.shared.business.dto.BatchOverviewResponse;
import com.example.bff.shared.business.dto.BffApplicationDetailRequest;
import com.example.bff.shared.business.dto.BffBatchDetailRequest;
import com.example.bff.shared.business.dto.BffBatchOverviewRequest;
import com.example.bff.shared.business.dto.BffCreateBatchRequest;
import com.example.bff.shared.business.dto.BffFormTokenRequest;
import com.example.bff.shared.business.dto.BffListMaterialsRequest;
import com.example.bff.shared.business.dto.BffSubmitRequest;
import com.example.core.api.application.command.SubmitApplicationCommand;
import com.example.core.api.application.query.FindApplicationListQuery;
import com.example.core.api.application.query.GetApplicationDetailQuery;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.api.batch.command.CreateBatchCommand;
import com.example.core.api.batch.query.GetBatchDetailQuery;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.form.command.ApplyUploadTokenCommand;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.core.api.material.query.ListMaterialsQuery;
import com.example.core.api.material.response.MaterialItemResponse;
import com.example.core.api.progress.query.GetBatchProgressQuery;
import com.example.core.api.progress.response.BatchProgressResponse;
import com.example.shared.web.core.api.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 年金业务场景处理器 — 覆盖 ANNUITY_OPEN / ANNUITY_CHANGE
 *
 * <p>简单转发方法：BFF Request → Kernel Command/Query → annuity kernel API proxy → 返回。
 * 聚合方法（batchOverview）：并发调用 batch detail / progress / application list，
 * 主实体失败时快速抛出，辅助数据失败时降级为 null。
 *
 * @author bff
 */
@Service
public class AnnuityScenarioHandler implements BusinessScenarioHandler {

  private static final Logger log = LoggerFactory.getLogger(AnnuityScenarioHandler.class);
  private static final Set<String> SUPPORTED_TYPES = Set.of("ANNUITY_OPEN", "ANNUITY_CHANGE");

  private final AnnuityBatchApi batchApi;
  private final AnnuityFormApi formApi;
  private final AnnuityApplicationApi applicationApi;
  private final AnnuityMaterialApi materialApi;
  private final AnnuityProgressApi progressApi;
  private final Executor asyncExecutor;

  public AnnuityScenarioHandler(
    AnnuityBatchApi batchApi,
    AnnuityFormApi formApi,
    AnnuityApplicationApi applicationApi,
    AnnuityMaterialApi materialApi,
    AnnuityProgressApi progressApi,
    Executor asyncExecutor
  ) {
    this.batchApi = batchApi;
    this.formApi = formApi;
    this.applicationApi = applicationApi;
    this.materialApi = materialApi;
    this.progressApi = progressApi;
    this.asyncExecutor = asyncExecutor;
  }

  @Override
  public Set<String> supportedTypes() {
    return SUPPORTED_TYPES;
  }

  @Override
  public ApiResult<BatchCreatedResponse> createBatch(BffCreateBatchRequest request) {
    return batchApi.create(new CreateBatchCommand(
      request.businessType(), request.planNo(), request.operatorRemark()
    ));
  }

  @Override
  public ApiResult<BatchDetailResponse> batchDetail(BffBatchDetailRequest request) {
    return batchApi.detail(new GetBatchDetailQuery(request.batchId()));
  }

  @Override
  public ApiResult<UploadTokenResponse> applyUploadToken(BffFormTokenRequest request) {
    return formApi.applyUploadToken(new ApplyUploadTokenCommand(
      request.batchId(), request.fileName(), request.fileSize(), request.contentType()
    ));
  }

  @Override
  public ApiResult<SubmitResponse> submitApplication(BffSubmitRequest request) {
    return applicationApi.submit(new SubmitApplicationCommand(request.applicationId()));
  }

  @Override
  public ApiResult<ApplicationDetailResponse> applicationDetail(BffApplicationDetailRequest request) {
    return applicationApi.detail(new GetApplicationDetailQuery(request.applicationId()));
  }

  @Override
  public ApiResult<List<MaterialItemResponse>> listMaterials(BffListMaterialsRequest request) {
    return materialApi.list(new ListMaterialsQuery(request.applicationId()));
  }

  /**
   * 批次概览（聚合场景）— 并发调用 batch detail / progress / application list。
   *
   * <p>主实体（batch detail）失败时通过 {@link BffAggregationException} 快速传播；
   * 辅助数据（progress / applications）失败时降级为 null，不影响主数据返回。
   */
  @Override
  public ApiResult<BatchOverviewResponse> batchOverview(BffBatchOverviewRequest request) {
    // 主实体 — 必须成功
    CompletableFuture<BatchDetailResponse> detailFuture = CompletableFuture.supplyAsync(() -> {
      ApiResult<BatchDetailResponse> result = batchApi.detail(new GetBatchDetailQuery(request.batchId()));
      if (result.isFailure()) {
        throw new BffAggregationException(result.code(), result.message());
      }
      return result.data();
    }, asyncExecutor);

    // 辅助数据 — 失败降级
    CompletableFuture<BatchProgressResponse> progressFuture = CompletableFuture.supplyAsync(() -> {
      try {
        ApiResult<BatchProgressResponse> result = progressApi.batchProgress(
          new GetBatchProgressQuery(request.batchId())
        );
        return result.isSuccess() ? result.data() : null;
      } catch (Exception ex) {
        log.warn("批次进度查询失败，降级为 null，batchId={}", request.batchId(), ex);
        return null;
      }
    }, asyncExecutor);

    CompletableFuture<List<ApplicationSummaryResponse>> applicationsFuture = CompletableFuture.supplyAsync(() -> {
      try {
        ApiResult<List<ApplicationSummaryResponse>> result = applicationApi.list(
          new FindApplicationListQuery(request.batchId(), null)
        );
        return result.isSuccess() ? result.data() : null;
      } catch (Exception ex) {
        log.warn("申请单列表查询失败，降级为 null，batchId={}", request.batchId(), ex);
        return null;
      }
    }, asyncExecutor);

    // 等待所有完成，主实体异常会在此抛出
    BatchDetailResponse detail = detailFuture.join();
    BatchProgressResponse progress = progressFuture.join();
    List<ApplicationSummaryResponse> applications = applicationsFuture.join();

    return ApiResult.success(BffResponseAssembler.assemble(detail, progress, applications));
  }
}
