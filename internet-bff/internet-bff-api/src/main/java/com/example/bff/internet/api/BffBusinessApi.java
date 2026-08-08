package com.example.bff.internet.api;

import com.example.bff.internet.api.dto.*;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.core.api.material.response.MaterialItemResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 互联网 BFF 业务 API
 *
 * <p>对前端暴露收敛的接口，所有请求携带 {@code businessType} 用于路由。
 *
 * @author bff
 */
@HttpExchange("/bff")
public interface BffBusinessApi {

    @PostExchange("/batch/create")
    ApiResult<BatchCreatedResponse> createBatch(@Valid @RequestBody BffCreateBatchRequest request);

    @PostExchange("/batch/detail")
    ApiResult<BatchDetailResponse> batchDetail(@Valid @RequestBody BffBatchDetailRequest request);

    @PostExchange("/form/upload-token")
    ApiResult<UploadTokenResponse> applyUploadToken(@Valid @RequestBody BffFormTokenRequest request);

    @PostExchange("/application/submit")
    ApiResult<SubmitResponse> submitApplication(@Valid @RequestBody BffSubmitRequest request);

    @PostExchange("/application/detail")
    ApiResult<ApplicationDetailResponse> applicationDetail(@Valid @RequestBody BffApplicationDetailRequest request);

    @PostExchange("/material/list")
    ApiResult<List<MaterialItemResponse>> listMaterials(@Valid @RequestBody BffListMaterialsRequest request);

    @PostExchange("/dashboard/batch-overview")
    ApiResult<BatchOverviewResponse> batchOverview(@Valid @RequestBody BffBatchOverviewRequest request);
}
