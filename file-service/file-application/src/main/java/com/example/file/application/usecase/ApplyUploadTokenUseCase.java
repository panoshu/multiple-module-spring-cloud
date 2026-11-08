package com.example.file.application.usecase;

import com.example.file.application.command.ApplyUploadTokenCommand;
import com.example.file.application.util.TokenHashUtil;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.storage.aggregate.FileAccessLog;
import com.example.file.domain.model.storage.aggregate.FileMetadata;
import com.example.file.domain.model.storage.valueobject.FileUsage;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.domain.service.FileTokenService;
import com.example.shared.domain.errorcode.SharedDomainErrorCode;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.contract.IdService;
import com.example.file.types.FileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 申请上传 Token UseCase
 * <p>
 * 流程：
 * 1. 创建 FileMetadata(PENDING_UPLOAD) 并持久化
 * 2. 调用 FileTokenService 生成上传 token
 * 3. 写入 FileAccessLog(APPLY) 审计流水
 * 4. 返回 token + fileId
 * <p>
 * 注意：ttl 必须由调用方显式传入，UseCase 不依赖 file-infrastructure 的 FileTokenProperties
 * (DDD 七层架构规则: application 禁止依赖 infrastructure)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyUploadTokenUseCase {

  private final IdService idService;
  private final FileMetadataRepository metadataRepository;
  private final StorageTargetResolver targetResolver;
  private final FileTokenService tokenService;
  private final FileAccessLogRepository logRepository;
  private final com.example.file.domain.repository.TemplateConfigRepository configRepository;
  private final com.example.file.domain.service.TemplateRoutingService routingService;

  @Transactional
  public ApplyUploadTokenResult apply(ApplyUploadTokenCommand cmd) {
    // 路由预判（批次3 A2，设计决策 D2）：FORM 品类在申请阶段即完成"上下文→模板配置"路由，
    // 无可用配置立即拒绝（fail-fast）——别等文件上传完才发现没有配置
    if (cmd.category() != null && "FORM".equals(cmd.category())) {
      boolean routed = routingService
        .route(configRepository.findAllActive(com.example.file.types.BizType.of(cmd.bizType())),
          cmd.routingContext())
        .isPresent();
      if (!routed) {
        throw new com.example.shared.exception.BusinessException(
            com.example.shared.exception.CommonError.BAD_REQUEST)
          .withUserDetail("该业务上下文未配置表单解析模板（bizType=" + cmd.bizType() + "）");
      }
    }
    if (cmd.ttl() == null) {
      throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
        .withLogDetail("ttl 不能为空");
    }

    FileId fileId = idService.nextId(FileId.class);
    var target = targetResolver.resolveByUsage(FileUsage.SOURCE, cmd.bizType());
    FileMetadata file = FileMetadata.createForUpload(
      fileId, FileUsage.SOURCE, cmd.bizType(), cmd.sourceApp(),
      cmd.businessBatchId(), cmd.accessScope(),
      target.targetId(), target.type(), cmd.uploader(), cmd.expiresAt()
    );
    metadataRepository.save(file);

    String token = tokenService.generateUploadToken(
      file, cmd.allowedContentTypes(), cmd.allowedMaxSize(), cmd.ttl()
    );

    FileAccessLog accessLog = FileAccessLog.apply(
      fileId, FileUsage.SOURCE, cmd.accessScope(), cmd.uploader(),
      cmd.sourceApp(), TokenHashUtil.sha256(token)
    );
    logRepository.save(accessLog);

    log.info("上传 Token 已申请: fileId={}, usage={}, bizType={}", fileId, FileUsage.SOURCE, cmd.bizType());
    return new ApplyUploadTokenResult(token, fileId);
  }

  public record ApplyUploadTokenResult(String token, FileId fileId) {
  }
}
