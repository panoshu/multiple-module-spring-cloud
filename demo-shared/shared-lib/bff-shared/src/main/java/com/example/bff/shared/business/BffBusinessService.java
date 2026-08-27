package com.example.bff.shared.business;

import com.example.bff.shared.business.dto.BffApplicationDetailRequest;
import com.example.bff.shared.business.dto.BffBatchDetailRequest;
import com.example.bff.shared.business.dto.BffCreateBatchRequest;
import com.example.bff.shared.business.dto.BffFormTokenRequest;
import com.example.bff.shared.business.dto.BffListMaterialsRequest;
import com.example.bff.shared.business.dto.BffSubmitRequest;
import com.example.bff.shared.registry.KernelApiRegistry;
import com.example.bff.shared.route.BusinessTypeRouter;
import com.example.core.api.application.command.SubmitApplicationCommand;
import com.example.core.api.application.query.FindApplicationListQuery;
import com.example.core.api.application.query.GetApplicationDetailQuery;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.api.batch.command.CreateBatchCommand;
import com.example.core.api.batch.query.GetBatchDetailQuery;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.form.command.ApplyUploadTokenCommand;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.core.api.material.query.ListMaterialsQuery;
import com.example.core.api.material.response.MaterialItemResponse;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * BFF 业务路由服务（互联网/内网共用）
 *
 * <p>封装路由解析 + kernel API 代理调用，Controller 仅做请求转发。
 *
 * @author bff
 */
@Service
public class BffBusinessService {

  private final BusinessTypeRouter router;
  private final KernelApiRegistry kernelApiRegistry;

  public BffBusinessService(BusinessTypeRouter router, KernelApiRegistry kernelApiRegistry) {
    this.router = router;
    this.kernelApiRegistry = kernelApiRegistry;
  }

  public ApiResult<BatchCreatedResponse> createBatch(BffCreateBatchRequest request) {
    String serviceName = router.resolveServiceName(request.businessType());
    return kernelApiRegistry.getBatchApi(serviceName)
      .create(new CreateBatchCommand(request.businessType(), request.planNo(), request.operatorRemark()));
  }

  public ApiResult<BatchDetailResponse> batchDetail(BffBatchDetailRequest request) {
    String serviceName = router.resolveServiceName(request.businessType());
    return kernelApiRegistry.getBatchApi(serviceName)
      .detail(new GetBatchDetailQuery(request.batchId()));
  }

  public ApiResult<UploadTokenResponse> applyUploadToken(BffFormTokenRequest request) {
    String serviceName = router.resolveServiceName(request.businessType());
    return kernelApiRegistry.getFormApi(serviceName)
      .applyUploadToken(new ApplyUploadTokenCommand(request.batchId(), request.fileName(), request.fileSize(), request.contentType()));
  }

  public ApiResult<SubmitResponse> submitApplication(BffSubmitRequest request) {
    String serviceName = router.resolveServiceName(request.businessType());
    return kernelApiRegistry.getApplicationApi(serviceName)
      .submit(new SubmitApplicationCommand(request.applicationId()));
  }

  public ApiResult<ApplicationDetailResponse> applicationDetail(BffApplicationDetailRequest request) {
    String serviceName = router.resolveServiceName(request.businessType());
    return kernelApiRegistry.getApplicationApi(serviceName)
      .detail(new GetApplicationDetailQuery(request.applicationId()));
  }

  public ApiResult<List<MaterialItemResponse>> listMaterials(BffListMaterialsRequest request) {
    String serviceName = router.resolveServiceName(request.businessType());
    return kernelApiRegistry.getMaterialApi(serviceName)
      .list(new ListMaterialsQuery(request.applicationId()));
  }
}
