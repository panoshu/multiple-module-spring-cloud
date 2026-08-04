package com.example.annuity.adapter.controller;

import com.example.annuity.adapter.converter.AnnuityApiConverter;
import com.example.annuity.api.AnnuityApi;
import com.example.annuity.api.dto.ApplicationResponse;
import com.example.annuity.api.dto.BatchStatusResponse;
import com.example.annuity.api.dto.UploadFormRequest;
import com.example.annuity.application.service.AnnuityAppService;
import com.example.shared.identifier.id.ApplicationId;
import com.example.shared.identifier.id.BatchId;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 年金服务 Adapter 层 Controller
 * <p>
 * 实现 {@link AnnuityApi} 接口，严格遵循项目规则：
 * <ul>
 *   <li>所有 API 在 API 层定义接口，Adapter 层仅实现</li>
 *   <li>不编写业务逻辑，通过 {@link AnnuityApiConverter} 完成 DTO 转换</li>
 *   <li>通过构造函数注入依赖</li>
 * </ul>
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Slf4j
@RestController
@RequestMapping("/api/annuity")
@RequiredArgsConstructor
public class AnnuityController implements AnnuityApi {

  private final AnnuityAppService annuityAppService;
  private final AnnuityApiConverter converter;

  @Override
  public ApiResult<BatchStatusResponse> uploadForm(UploadFormRequest request) {
    log.info("接收年金表单上传请求: customerNo={}, planNo={}, businessType={}",
      request.customerNo(), request.planNo(), request.businessType());
    var command = converter.toCommand(request);
    var result = annuityAppService.uploadForm(command);
    return ApiResult.success(converter.toBatchResponse(result));
  }

  @Override
  public ApiResult<ApplicationResponse> getApplication(ApplicationIdRequest request) {
    log.info("查询年金申请详情: applicationId={}", request.applicationId());
    ApplicationId applicationId = new ApplicationId(request.applicationId());
    var result = annuityAppService.getApplication(applicationId);
    return ApiResult.success(converter.toApplicationResponse(result));
  }

  @Override
  public ApiResult<BatchStatusResponse> getBatchStatus(BatchIdRequest request) {
    log.info("查询年金批次状态: batchId={}", request.batchId());
    BatchId batchId = new BatchId(request.batchId());
    var result = annuityAppService.getBatchStatus(batchId);
    return ApiResult.success(converter.toBatchStatusResponse(result));
  }
}
