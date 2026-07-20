package com.example.file.domain.service;

import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.FileTokenGateway;
import com.example.file.domain.gateway.FileTokenStore;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;
import com.example.file.domain.model.aggregate.valueobject.SessionUser;
import com.example.shared.domain.annotation.DomainService;
import com.example.shared.exception.DomainException;
import com.example.shared.exception.SystemException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 文件 Token 领域服务
 * <p>
 * 负责 token 的生成、校验、消费（一次性使用标记）。
 * 业务规则（用户对比、文件类型/大小限制、过期判断）在此层实现。
 */
@DomainService
public class FileTokenService {

    private final FileTokenGateway tokenGateway;
    private final FileTokenStore tokenStore;

    public FileTokenService(FileTokenGateway tokenGateway, FileTokenStore tokenStore) {
        this.tokenGateway = tokenGateway;
        this.tokenStore = tokenStore;
    }

    /**
     * 仅解密 token，不做业务校验（不校验过期/会话/文件，不消费 token）。
     * <p>
     * 用于应用层在调用 {@link #verifyAndConsumeUploadToken} / {@link #verifyAndConsumeDownloadToken}
     * 之前先取出 fileId 加载 FileMetadata 的场景：因为 verify 系列方法要求 file 非空，
     * 而应用层需要 fileId 才能从 Repository load 出 file。
     * <p>
     * 解密失败或格式错误抛 SystemException(FILE_TOKEN_INVALID)（由 FileTokenGateway 抛出）。
     */
    public FileTokenPayload decrypt(String token) {
        return tokenGateway.decrypt(token);
    }

    /**
     * 生成上传 token
     */
    public String generateUploadToken(FileMetadata file, List<String> allowedContentTypes,
                                       Long allowedMaxSize, Duration ttl) {
        FileTokenPayload payload = new FileTokenPayload(
            UUID.randomUUID().toString(),
            file.id(),
            file.usage(),
            file.bizType(),
            file.accessScope().customerNo(),
            file.accessScope().productNo(),
            file.uploadedBy(),
            allowedContentTypes,
            allowedMaxSize,
            LocalDateTime.now().plus(ttl)
        );
        return tokenGateway.encrypt(payload);
    }

    /**
     * 生成下载 token
     */
    public String generateDownloadToken(FileMetadata file, Duration ttl) {
        assertDownloadable(file);
        FileTokenPayload payload = new FileTokenPayload(
            UUID.randomUUID().toString(),
            file.id(),
            file.usage(),
            file.bizType(),
            file.accessScope().customerNo(),
            file.accessScope().productNo(),
            file.uploadedBy(),
            null,
            null,
            LocalDateTime.now().plus(ttl)
        );
        return tokenGateway.encrypt(payload);
    }

    /**
     * 校验上传 token 并消费（一次性使用）
     */
    public FileTokenPayload verifyAndConsumeUploadToken(String token, SessionUser session,
                                                         FileMetadata file) {
        FileTokenPayload payload = decryptAndVerify(token, session, file);

        if (payload.usage() != file.usage()) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_MISMATCH)
                .withLogDetail("token usage: " + payload.usage() + ", file usage: " + file.usage());
        }

        // 一次性使用标记
        Duration remainingTtl = Duration.between(LocalDateTime.now(), payload.expireAt());
        if (!tokenStore.markUsed(payload.tokenId(), remainingTtl)) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_ALREADY_USED)
                .withLogDetail("tokenId: " + payload.tokenId());
        }

        return payload;
    }

    /**
     * 校验下载 token 并消费
     */
    public FileTokenPayload verifyAndConsumeDownloadToken(String token, SessionUser session,
                                                           FileMetadata file) {
        FileTokenPayload payload = decryptAndVerify(token, session, file);
        assertDownloadable(file);

        Duration remainingTtl = Duration.between(LocalDateTime.now(), payload.expireAt());
        if (!tokenStore.markUsed(payload.tokenId(), remainingTtl)) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_ALREADY_USED)
                .withLogDetail("tokenId: " + payload.tokenId());
        }

        return payload;
    }

    private FileTokenPayload decryptAndVerify(String token, SessionUser session, FileMetadata file) {
        FileTokenPayload payload = tokenGateway.decrypt(token);

        // 过期校验
        if (payload.expireAt().isBefore(LocalDateTime.now())) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_EXPIRED)
                .withLogDetail("tokenId: " + payload.tokenId());
        }

        // 会话用户对比
        if (!payload.operator().equals(session.userNo())) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_MISMATCH)
                .withLogDetail("token operator: " + payload.operator() + ", session: " + session.userNo());
        }
        if (!payload.customerNo().equals(session.customerNo())) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_MISMATCH)
                .withLogDetail("token customer: " + payload.customerNo() + ", session: " + session.customerNo());
        }
        if (!payload.productNo().equals(session.productNo())) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_MISMATCH)
                .withLogDetail("token product: " + payload.productNo() + ", session: " + session.productNo());
        }

        // 文件 ID 对比
        if (!payload.fileId().equals(file.id())) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_MISMATCH)
                .withLogDetail("token fileId: " + payload.fileId() + ", file: " + file.id());
        }

        return payload;
    }

    /**
     * 校验文件可下载，统一转换为 SystemException(FILE_NOT_DOWNLOADABLE)
     * <p>
     * file.verifyDownloadable() 抛 DomainException(SharedDomainErrorCode.INVALID_OPERATION)，
     * 与本服务其他失败路径（SystemException(FILE_TOKEN_*)) 不一致，此处统一异常类型，
     * 便于上层应用服务（Task 15 UseCases）按 SystemException 统一处理。
     */
    private void assertDownloadable(FileMetadata file) {
        try {
            file.verifyDownloadable();
        } catch (DomainException e) {
            throw new SystemException(FileErrorCodes.FILE_NOT_DOWNLOADABLE)
                .withLogDetail("文件状态不允许下载, fileId=" + file.id() + ", status=" + file.status());
        }
    }
}
