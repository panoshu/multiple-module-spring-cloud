package com.example.file.application.usecase;

import com.example.file.application.command.ApplyDownloadTokenCommand;
import com.example.file.application.util.TokenHashUtil;
import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.domain.service.FileTokenService;
import com.example.shared.domain.errorcode.SharedDomainErrorCode;
import com.example.shared.exception.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 申请下载 Token UseCase
 * <p>
 * 流程：
 * 1. load FileMetadata
 * 2. file.verifyDownloadable() 校验可下载（状态 + 过期）
 * 3. 调用 FileTokenService.generateDownloadToken 生成 token
 * 4. 写入 FileAccessLog(APPLY) 审计流水
 * 5. 返回 token
 * <p>
 * 注意：ttl 必须由调用方显式传入，UseCase 不依赖 file-infrastructure 的 FileTokenProperties
 * (DDD 七层架构规则: application 禁止依赖 infrastructure)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyDownloadTokenUseCase {

  private final FileMetadataRepository metadataRepository;
  private final FileTokenService tokenService;
  private final FileAccessLogRepository logRepository;

  @Transactional
  public String apply(ApplyDownloadTokenCommand cmd) {
    if (cmd.ttl() == null) {
      throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
        .withLogDetail("ttl 不能为空");
    }

    FileMetadata file = metadataRepository.loadOrThrow(cmd.fileId());
    file.verifyDownloadable();

    String token = tokenService.generateDownloadToken(file, cmd.ttl());

    FileAccessLog accessLog = FileAccessLog.apply(
      cmd.fileId(), file.usage(), file.accessScope(), cmd.downloader(),
      cmd.sourceApp(), TokenHashUtil.sha256(token)
    );
    logRepository.save(accessLog);

    log.info("下载 Token 已申请: fileId={}, usage={}", cmd.fileId(), file.usage());
    return token;
  }
}
