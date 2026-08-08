package com.example.bff.internet.adapter.controller;

import com.example.bff.internet.api.BffBusinessApi;
import com.example.bff.internet.api.dto.*;
import com.example.bff.internet.application.service.BffAggregationService;
import com.example.bff.shared.registry.KernelApiRegistry;
import com.example.bff.shared.route.BusinessTypeRouter;
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
 * 互联网 BFF 业务 Controller
 *
 * <p>实现 {@link BffBusinessApi}，通过 {@link BusinessTypeRouter} 解析服务名，
 * 通过 {@link KernelApiRegistry} 获取 kernel API 代理，转发请求到对应业务服务。
 * 聚合场景委托给 {@link BffAggregationService}。
 *
 * @author bff
 */
@RestController
public class BffBusinessController implements BffBusinessApi {

    private final BusinessTypeRouter router;
    private final KernelApiRegistry kernelApiRegistry;
    private final BffAggregationService aggregationService;

    public BffBusinessController(
            BusinessTypeRouter router,
            KernelApiRegistry kernelApiRegistry,
            BffAggregationService aggregationService) {
        this.router = router;
        this.kernelApiRegistry = kernelApiRegistry;
        this.aggregationService = aggregationService;
    }

    @Override
    public ApiResult<BatchCreatedResponse> createBatch(BffCreateBatchRequest request) {
        String serviceName = router.resolveServiceName(request.businessType());
        return kernelApiRegistry.getBatchApi(serviceName).create(request.toCommand());
    }

    @Override
    public ApiResult<BatchDetailResponse> batchDetail(BffBatchDetailRequest request) {
        String serviceName = router.resolveServiceName(request.businessType());
        return kernelApiRegistry.getBatchApi(serviceName).detail(request.toQuery());
    }

    @Override
    public ApiResult<UploadTokenResponse> applyUploadToken(BffFormTokenRequest request) {
        String serviceName = router.resolveServiceName(request.businessType());
        return kernelApiRegistry.getFormApi(serviceName).applyUploadToken(request.toCommand());
    }

    @Override
    public ApiResult<SubmitResponse> submitApplication(BffSubmitRequest request) {
        String serviceName = router.resolveServiceName(request.businessType());
        return kernelApiRegistry.getApplicationApi(serviceName).submit(request.toCommand());
    }

    @Override
    public ApiResult<ApplicationDetailResponse> applicationDetail(BffApplicationDetailRequest request) {
        String serviceName = router.resolveServiceName(request.businessType());
        return kernelApiRegistry.getApplicationApi(serviceName).detail(request.toQuery());
    }

    @Override
    public ApiResult<List<MaterialItemResponse>> listMaterials(BffListMaterialsRequest request) {
        String serviceName = router.resolveServiceName(request.businessType());
        return kernelApiRegistry.getMaterialApi(serviceName).list(request.toQuery());
    }

    @Override
    public ApiResult<BatchOverviewResponse> batchOverview(BffBatchOverviewRequest request) {
        return aggregationService.getBatchOverview(request);
    }
}
