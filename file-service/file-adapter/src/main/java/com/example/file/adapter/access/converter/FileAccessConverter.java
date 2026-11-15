package com.example.file.adapter.access.converter;

import com.example.file.api.request.ApplyDownloadTokenRequest;
import com.example.file.api.request.ApplyUploadTokenRequest;
import com.example.file.application.command.ApplyDownloadTokenCommand;
import com.example.file.application.command.ApplyUploadTokenCommand;
import com.example.file.domain.model.storage.valueobject.FileAccessScope;
import com.example.core.types.BatchId;
import com.example.core.types.CustomerNo;
import com.example.core.types.ProductNo;
import com.example.core.types.UserNo;
import org.mapstruct.Mapper;

import java.time.Duration;

/**
 * 文件访问 Token 相关 Request → Command 转换器（MapStruct）。
 *
 * <p>Task 15 fix 后 Command 必传 {@code ttl}，由 adapter 从
 * {@link com.example.file.application.access.FileTokenPolicy} 读取默认值后显式传入，
 * UseCase 不依赖 file-infrastructure（DDD 七层架构: application 禁止依赖 infrastructure）。
 *
 * <p>Request DTO 中的 customerNo/productNo/uploader/fileId 已经是强类型 ID（Task 14），
 * 直接透传即可；businessBatchId 在 Request 中是 String，需用 {@code new BatchId(...)} 包装；
 * (customerNo, productNo) 组合为 {@link FileAccessScope} 值对象。
 */
@Mapper(componentModel = "spring")
public interface FileAccessConverter {

  /**
   * 将 {@link ApplyUploadTokenRequest} 转换为 {@link ApplyUploadTokenCommand}。
   *
   * @param request 上传 token 申请请求
   * @param ttl     token 有效期，由 adapter 从 FileTokenPolicy 读取后传入
   */
  default ApplyUploadTokenCommand toCommand(ApplyUploadTokenRequest request, Duration ttl) {
    return new ApplyUploadTokenCommand(
      request.bizType(),
      request.category(),
      request.routingContext(),
      request.sourceApp(),
      // 兼容平台级/测试调用：批次号可空（BatchId 构造器拒绝空串）
      request.businessBatchId() != null && !request.businessBatchId().isBlank()
        ? new BatchId(request.businessBatchId()) : null,
      // 业务上传必须携带企业+产品访问范围；缺失时保持 null，由 UseCase 明确拒绝，
      // 避免在此处抛 IllegalArgumentException 被兜底成 500（丢失可诊断信息）。
      request.customerNo() != null && request.productNo() != null
        ? new FileAccessScope(new CustomerNo(request.customerNo()), new ProductNo(request.productNo()))
        : null,
      new UserNo(request.uploader()),
      request.expiresAt(),
      request.allowedContentTypes(),
      request.allowedMaxSize(),
      ttl
    );
  }

  /**
   * 将 {@link ApplyDownloadTokenRequest} 转换为 {@link ApplyDownloadTokenCommand}。
   *
   * @param request 下载 token 申请请求
   * @param ttl     token 有效期，由 adapter 从 FileTokenPolicy 读取后传入
   */
  default ApplyDownloadTokenCommand toCommand(ApplyDownloadTokenRequest request, Duration ttl) {
    return new ApplyDownloadTokenCommand(
      request.fileId(),
      request.sourceApp(),
      new FileAccessScope(new CustomerNo(request.customerNo()), new ProductNo(request.productNo())),
      new UserNo(request.downloader()),
      ttl
    );
  }
}
