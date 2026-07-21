package com.example.annuity.api;

import com.example.annuity.api.dto.ApplicationResponse;
import com.example.annuity.api.dto.BatchStatusResponse;
import com.example.annuity.api.dto.UploadFormRequest;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 年金服务对外 HTTP API
 * <p>
 * 定义年金业务的对外协议，由 {@code AnnuityController}（Adapter 层）实现。
 * 严格遵守项目规则：
 * <ul>
 *   <li>仅使用 {@code @HttpExchange}/@PostExchange，禁用 GET/POST 之外的请求类型</li>
 *   <li>请求体使用 @RequestBody + @Valid，返回体统一 {@link ApiResult}</li>
 *   <li>仅定义协议，不包含实现逻辑</li>
 * </ul>
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@HttpExchange("/api/annuity")
public interface AnnuityApi {

  /**
   * 上传年金表单，触发批次创建与表单解析流程
   */
  @PostExchange("/upload")
  ApiResult<BatchStatusResponse> uploadForm(@Valid @RequestBody UploadFormRequest request);

  /**
   * 根据申请单 ID 查询年金申请详情
   */
  @PostExchange("/applications/get")
  ApiResult<ApplicationResponse> getApplication(@RequestBody ApplicationIdRequest request);

  /**
   * 根据批次 ID 查询批次及关联申请单的状态摘要
   */
  @PostExchange("/batches/get")
  ApiResult<BatchStatusResponse> getBatchStatus(@RequestBody BatchIdRequest request);

  /**
   * 申请单 ID 查询请求（避免裸 String 作为请求体）
   */
  record ApplicationIdRequest(String applicationId) {
  }

  /**
   * 批次 ID 查询请求
   */
  record BatchIdRequest(String batchId) {
  }
}
