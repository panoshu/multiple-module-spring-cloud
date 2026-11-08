package com.example.bff.shared.business;

import com.example.bff.shared.business.dto.BffApplicationDetailRequest;
import com.example.bff.shared.business.dto.BffBatchDetailRequest;
import com.example.bff.shared.business.dto.BffCreateBatchRequest;
import com.example.bff.shared.business.dto.BffFormTokenRequest;
import com.example.bff.shared.business.dto.BffListMaterialsRequest;
import com.example.bff.shared.business.dto.BffSubmitRequest;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.core.api.material.response.MaterialItemResponse;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * BFF 业务调度服务（互联网/内网共用）
 *
 * <p>纯委托角色：根据请求中的 {@code businessType} 查找
 * {@link BusinessScenarioHandler} 并转发调用，自身不含业务逻辑。
 *
 * @author bff
 */
@Service
public class BffBusinessService {

  private final ScenarioHandlerRegistry registry;

  public BffBusinessService(ScenarioHandlerRegistry registry) {
    this.registry = registry;
  }

  public ApiResult<BatchCreatedResponse> createBatch(BffCreateBatchRequest request) {
    return registry.handlerFor(request.businessType()).createBatch(request);
  }

  public ApiResult<BatchDetailResponse> batchDetail(BffBatchDetailRequest request) {
    return registry.handlerFor(request.businessType()).batchDetail(request);
  }

  public ApiResult<UploadTokenResponse> applyUploadToken(BffFormTokenRequest request) {
    return registry.handlerFor(request.businessType()).applyUploadToken(request);
  }

  public ApiResult<SubmitResponse> submitApplication(BffSubmitRequest request) {
    return registry.handlerFor(request.businessType()).submitApplication(request);
  }

  public ApiResult<ApplicationDetailResponse> applicationDetail(BffApplicationDetailRequest request) {
    return registry.handlerFor(request.businessType()).applicationDetail(request);
  }

  public ApiResult<List<MaterialItemResponse>> listMaterials(BffListMaterialsRequest request) {
    return registry.handlerFor(request.businessType()).listMaterials(request);
  }
}
