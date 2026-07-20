package com.example.file.application.usecase;

import com.example.file.application.util.TokenHashUtil;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StoreResult;
import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileAccessResult;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;
import com.example.file.domain.model.aggregate.valueobject.SessionUser;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.domain.service.FileTokenService;
import com.example.shared.exception.SystemException;
import com.example.shared.primitives.identity.FileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 使用上传 Token 上传文件 UseCase
 * <p>
 * 流程：
 * 1. 解密 token 获取 payload（不消费 token，仅取出 fileId）
 * 2. load FileMetadata
 * 3. verifyAndConsumeUploadToken 完整校验 + 一次性消费
 * 4. 调用 FileStorageGateway.store 存储文件
 * 5. FileMetadata.completeUpload 标记 UPLOADED
 * 6. 写入 FileAccessLog(ACCESS, SUCCESS/FAIL) 审计流水
 * <p>
 * 失败分支：
 * - token 解密失败：仅日志记录（无 fileId，无法写 FileAccessLog）
 * - load/verify/store/complete 失败：写 FileAccessLog(ACCESS, FAIL)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadFileWithTokenUseCase {

    private final FileMetadataRepository metadataRepository;
    private final FileTokenService tokenService;
    private final FileStorageGateway storageGateway;
    private final FileAccessLogRepository logRepository;

    @Transactional
    public FileId upload(String token, SessionUser session, MultipartFile file, String clientIp) {
        // 1. 先解密 token 获取 payload（不消费）
        FileTokenPayload payload;
        try {
            payload = tokenService.decrypt(token);
        } catch (SystemException e) {
            // 解密失败：无 fileId 可用，仅记录日志，无法写审计流水
            log.warn("上传 Token 解密失败, 无法记录审计日志: tokenHash={}, error={}",
                TokenHashUtil.sha256(token), e.getMessage());
            throw e;
        }

        FileId fileId = payload.fileId();

        // 2. load FileMetadata + 3. 完整校验并消费 token
        FileMetadata meta;
        try {
            meta = metadataRepository.loadOrThrow(fileId);
            tokenService.verifyAndConsumeUploadToken(token, session, meta);
        } catch (SystemException e) {
            writeAccessLogFailed(fileId, payload, session, clientIp, token, e.getMessage());
            throw e;
        }

        // 4. 存储文件 + 5. completeUpload
        try {
            StoreResult result = storageGateway.store(meta.id(), file.getInputStream(), file.getSize());
            meta.completeUpload(
                file.getOriginalFilename(), file.getSize(), file.getContentType(),
                result.storageKey(), result.digest()
            );
            metadataRepository.save(meta);
            writeAccessLogSuccess(meta, session, clientIp, token);
            log.info("文件已通过 Token 上传: fileId={}, storageKey={}", meta.id(), result.storageKey());
            return meta.id();
        } catch (IOException | RuntimeException e) {
            writeAccessLogFailed(fileId, payload, session, clientIp, token, e.getMessage());
            throw new SystemException(com.example.file.domain.errorcode.FileErrorCodes.FILE_STORAGE_FAILED, e)
                .withLogDetail("fileId=" + fileId + ", error=" + e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeAccessLogSuccess(FileMetadata meta, SessionUser session, String clientIp, String token) {
        FileAccessLog accessLog = FileAccessLog.access(
            meta.id(), meta.usage(), meta.accessScope(), session.userNo(),
            meta.sourceApp(), clientIp, TokenHashUtil.sha256(token),
            FileAccessResult.SUCCESS, null
        );
        logRepository.save(accessLog);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeAccessLogFailed(FileId fileId, FileTokenPayload payload, SessionUser session,
                                      String clientIp, String token, String reason) {
        FileAccessScope scope = new FileAccessScope(session.customerNo(), session.productNo());
        FileAccessLog accessLog = FileAccessLog.access(
            fileId, payload.usage(), scope, session.userNo(),
            "unknown", clientIp, TokenHashUtil.sha256(token),
            FileAccessResult.FAIL, reason
        );
        logRepository.save(accessLog);
    }
}
