package com.example.file.application.usecase;

import com.example.file.application.service.FileAccessLogWriter;
import com.example.file.application.util.TokenHashUtil;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileAccessResult;
import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;
import com.example.file.domain.model.aggregate.valueobject.SessionUser;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.domain.service.FileTokenService;
import com.example.shared.exception.DomainException;
import com.example.shared.exception.SystemException;
import com.example.shared.primitives.identity.FileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

/**
 * 使用下载 Token 准备下载 UseCase
 * <p>
 * 流程：
 * 1. 解密 token 获取 payload（不消费 token，仅取出 fileId）
 * 2. load FileMetadata
 * 3. verifyAndConsumeDownloadToken 完整校验 + 一次性消费
 * 4. 写入 FileAccessLog(ACCESS, SUCCESS/FAIL) 审计流水
 * 5. 返回 DownloadContext（包含文件元信息，调用方自行调用 openStream 获取流）
 * <p>
 * 失败分支：
 * - token 解密失败：仅日志记录（无 fileId，无法写 FileAccessLog）
 * - load/verify 失败：写 FileAccessLog(ACCESS, FAIL)
 * <p>
 * 审计流水写入通过独立的 {@link FileAccessLogWriter} Bean 完成，
 * 其上的 {@code @Transactional(REQUIRES_NEW)} 才能通过 Spring AOP 代理生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadFileWithTokenUseCase {

    private final FileMetadataRepository metadataRepository;
    private final FileTokenService tokenService;
    private final FileStorageGateway storageGateway;
    private final FileAccessLogRepository logRepository;
    private final FileAccessLogWriter fileAccessLogWriter;

    @Transactional
    public DownloadContext prepareDownload(String token, SessionUser session, String clientIp) {
        // 1. 先解密 token 获取 payload（不消费）
        FileTokenPayload payload;
        try {
            payload = tokenService.decrypt(token);
        } catch (SystemException e) {
            // 解密失败：无 fileId 可用，仅记录日志，无法写审计流水
            log.warn("下载 Token 解密失败, 无法记录审计日志: tokenHash={}, error={}",
                TokenHashUtil.sha256(token), e.getMessage());
            throw e;
        }

        FileId fileId = payload.fileId();

        // 2. load FileMetadata + 3. 完整校验并消费 token
        FileMetadata file;
        try {
            file = metadataRepository.loadOrThrow(fileId);
            tokenService.verifyAndConsumeDownloadToken(token, session, file);
        } catch (SystemException | DomainException e) {
            fileAccessLogWriter.writeAccessLogFailed(fileId, payload, session, clientIp, token, e.getMessage());
            throw e;
        }

        // 4. 写入 ACCESS 流水（成功）
        FileAccessLog accessLog = FileAccessLog.access(
            file.id(), file.usage(), file.accessScope(), session.userNo(),
            file.sourceApp(), clientIp, TokenHashUtil.sha256(token),
            FileAccessResult.SUCCESS, null
        );
        logRepository.save(accessLog);

        log.info("文件已通过 Token 准备下载: fileId={}, usage={}", file.id(), file.usage());
        // 5. 返回 DownloadContext
        return new DownloadContext(file.id(), file.originalName(), file.size(),
            file.contentType(), file.digest());
    }

    /**
     * 打开文件流（独立事务外，避免长事务持有流）
     */
    public InputStream openStream(FileId fileId) {
        return storageGateway.open(fileId);
    }

    public record DownloadContext(
        FileId fileId,
        String originalName,
        Long size,
        String contentType,
        String digest
    ) {}
}
