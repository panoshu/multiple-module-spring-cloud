package com.example.bff.intranet.adapter.controller;

import com.example.bff.shared.business.BffBusinessApi;
import com.example.bff.shared.business.BffAggregationService;
import com.example.bff.shared.business.BffBusinessService;
import com.example.bff.shared.business.dto.BffApplicationDetailRequest;
import com.example.bff.shared.business.dto.BffBatchDetailRequest;
import com.example.bff.shared.business.dto.BffBatchOverviewRequest;
import com.example.bff.shared.business.dto.BffCreateBatchRequest;
import com.example.bff.shared.business.dto.BffFormTokenRequest;
import com.example.bff.shared.business.dto.BffListMaterialsRequest;
import com.example.bff.shared.business.dto.BffSubmitRequest;
import com.example.bff.shared.business.dto.BatchOverviewResponse;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.core.api.material.response.MaterialItemResponse;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内网 BFF 业务 Controller
 *
 * <p>实现 {@link BffBusinessApi}，委托 {@link BffBusinessService} 执行路由 + API 调用，
 * 聚合场景委托给 {@link BffAggregationService}。
 *
 * @author bff
 */
@RestController
public class BffBusinessController implements BffBusinessApi {

  private final BffBusinessService businessService;
  private final BffAggregationService aggregationService;

  public BffBusinessController(BffBusinessService businessService,
                               BffAggregationService aggregationService) {
    this.businessService = businessService;
    this.aggregationService = aggregationService;
  }

  @Override
  public ApiResult<BatchCreatedResponse> createBatch(BffCreateBatchRequest request) {
    return businessService.createBatch(request);
  }

  @Override
  public ApiResult<BatchDetailResponse> batchDetail(BffBatchDetailRequest request) {
    return businessService.batchDetail(request);
  }

  @Override
  public ApiResult<UploadTokenResponse> applyUploadToken(BffFormTokenRequest request) {
    return businessService.applyUploadToken(request);
  }

  @Override
  public ApiResult<SubmitResponse> submitApplication(BffSubmitRequest request) {
    return businessService.submitApplication(request);
  }

  @Override
  public ApiResult<ApplicationDetailResponse> applicationDetail(BffApplicationDetailRequest request) {
    return businessService.applicationDetail(request);
  }

  @Override
  public ApiResult<List<MaterialItemResponse>> listMaterials(BffListMaterialsRequest request) {
    return businessService.listMaterials(request);
  }

  @Override
  public ApiResult<BatchOverviewResponse> batchOverview(BffBatchOverviewRequest request) {
    return aggregationService.getBatchOverview(request);
  }
}
