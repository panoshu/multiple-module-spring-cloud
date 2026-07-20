package com.example.file.application.command;

import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.UserNo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 申请上传 Token 命令
 * <p>
 * ttl 必须由调用方（adapter/controller）显式传入，UseCase 不依赖 file-infrastructure
 * 的 FileTokenProperties（避免 application 反向依赖 infrastructure 形成循环依赖）。
 *
 * @param bizType             业务类型
 * @param sourceApp           来源应用
 * @param businessBatchId     业务批次 ID
 * @param accessScope         文件访问范围（企业 + 产品）
 * @param uploader            上传人
 * @param expiresAt           文件过期时间
 * @param allowedContentTypes 允许的 Content-Type 列表
 * @param allowedMaxSize      允许的最大字节数
 * @param ttl                 token 有效期（必传，调用方从 FileTokenProperties 读取后传入）
 */
public record ApplyUploadTokenCommand(
    String bizType,
    String sourceApp,
    BatchId businessBatchId,
    FileAccessScope accessScope,
    UserNo uploader,
    LocalDateTime expiresAt,
    List<String> allowedContentTypes,
    Long allowedMaxSize,
    Duration ttl
) {}
