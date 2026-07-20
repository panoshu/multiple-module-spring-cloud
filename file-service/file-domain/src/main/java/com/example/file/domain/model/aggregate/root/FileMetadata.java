package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.event.FileDeletedEvent;
import com.example.file.domain.event.FileMetadataCreatedEvent;
import com.example.file.domain.event.FileUploadedEvent;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.domain.errorcode.SharedDomainErrorCode;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 文件元数据聚合根
 * <p>
 * 管理文件的生命周期：PENDING_UPLOAD → UPLOADED → DELETED
 * storageKey 仅在 markUploaded 时设置，不对外暴露（getter 受保护）
 */
public class FileMetadata extends AggregateRoot<FileId> {

    private String originalName;
    private Long size;              // 改为 Long 允许 null（PENDING_UPLOAD 时为 null）
    private String contentType;
    private String md5;             // 旧字段保留
    private String digest;          // 新字段：SM3 摘要
    private String digestAlgorithm; // 新字段：摘要算法

    private FileAccessScope accessScope;  // 新字段

    private String targetId;
    private StorageType storageType;
    private String storageKey;

    private FileUsage usage;
    private String bizType;
    private String sourceApp;
    private BatchId businessBatchId;

    private FileStatus status;
    private UserNo uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime expiresAt;

    // ============ 原有 create（保留向后兼容）============
    private FileMetadata(FileId id, String originalName, long size, String contentType,
                         FileUsage usage, String bizType, String sourceApp, BatchId businessBatchId,
                         String targetId, StorageType storageType,
                         UserNo uploadedBy, LocalDateTime expiresAt) {
        super(id, uploadedBy);
        this.originalName = originalName;
        this.size = size;
        this.contentType = contentType;
        this.usage = usage;
        this.bizType = bizType;
        this.sourceApp = sourceApp;
        this.businessBatchId = businessBatchId;
        this.targetId = targetId;
        this.storageType = storageType;
        this.uploadedBy = uploadedBy;
        this.expiresAt = expiresAt;
        this.status = FileStatus.PENDING_UPLOAD;
        registerDomainEvent(FileMetadataCreatedEvent.of(this));
    }

    // ============ 新增 createForUpload（Token 路径）============
    private FileMetadata(FileId id, FileUsage usage, String bizType, String sourceApp,
                         BatchId businessBatchId, FileAccessScope accessScope,
                         String targetId, StorageType storageType,
                         UserNo uploader, LocalDateTime expiresAt) {
        super(id, uploader);
        this.usage = usage;
        this.bizType = bizType;
        this.sourceApp = sourceApp;
        this.businessBatchId = businessBatchId;
        this.accessScope = accessScope;
        this.targetId = targetId;
        this.storageType = storageType;
        this.uploadedBy = uploader;
        this.expiresAt = expiresAt;
        this.status = FileStatus.PENDING_UPLOAD;
        // originalName/size/contentType/storageKey/digest 留空，completeUpload 时填充
        registerDomainEvent(FileMetadataCreatedEvent.of(this));
    }

    public static FileMetadata createForUpload(FileId id, FileUsage usage, String bizType,
                                                String sourceApp, BatchId businessBatchId,
                                                FileAccessScope accessScope,
                                                String targetId, StorageType storageType,
                                                UserNo uploader, LocalDateTime expiresAt) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        if (usage == null) throw new IllegalArgumentException("usage 不能为空");
        if (accessScope == null) throw new IllegalArgumentException("accessScope 不能为空");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId 不能为空");
        if (storageType == null) throw new IllegalArgumentException("storageType 不能为空");
        if (uploader == null) throw new IllegalArgumentException("uploader 不能为空");
        return new FileMetadata(id, usage, bizType, sourceApp, businessBatchId,
            accessScope, targetId, storageType, uploader, expiresAt);
    }

    // ============ 新增 completeUpload（Token 路径）============
    public void completeUpload(String originalName, long size, String contentType,
                                String storageKey, String digest) {
        if (this.status != FileStatus.PENDING_UPLOAD) {
            throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
                .withLogDetail("当前状态: " + this.status + ", 期望状态: PENDING_UPLOAD, fileId=" + id());
        }
        if (originalName == null || originalName.isBlank()) {
            throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
                .withLogDetail("originalName 不能为空, fileId=" + id());
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
                .withLogDetail("storageKey 不能为空, fileId=" + id());
        }
        this.originalName = originalName;
        this.size = size;
        this.contentType = contentType;
        this.storageKey = storageKey;
        this.digest = digest;
        this.digestAlgorithm = "SM3";
        this.status = FileStatus.UPLOADED;
        this.uploadedAt = LocalDateTime.now();
        markUpdated(this.uploadedBy != null ? this.uploadedBy : this.createdBy());
        registerDomainEvent(FileUploadedEvent.of(this));
    }

    // ============ 新增 verifyDownloadable ============
    public void verifyDownloadable() {
        if (this.status != FileStatus.UPLOADED) {
            throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
                .withLogDetail("文件当前状态不允许下载: " + this.status + ", fileId=" + id());
        }
        if (isExpired()) {
            throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
                .withLogDetail("文件已过期, fileId=" + id());
        }
    }

    // ============ 数据库重建（更新：含新字段）============
    public FileMetadata(FileId id, String originalName, Long size, String contentType, String md5,
                        String digest, String digestAlgorithm, FileAccessScope accessScope,
                        String targetId, StorageType storageType, String storageKey,
                        FileUsage usage, String bizType, String sourceApp, BatchId businessBatchId,
                        FileStatus status, UserNo uploadedBy, LocalDateTime uploadedAt, LocalDateTime expiresAt,
                        UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.originalName = originalName;
        this.size = size;
        this.contentType = contentType;
        this.md5 = md5;
        this.digest = digest;
        this.digestAlgorithm = digestAlgorithm;
        this.accessScope = accessScope;
        this.targetId = targetId;
        this.storageType = storageType;
        this.storageKey = storageKey;
        this.usage = usage;
        this.bizType = bizType;
        this.sourceApp = sourceApp;
        this.businessBatchId = businessBatchId;
        this.status = status;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.expiresAt = expiresAt;
    }

    public static FileMetadata reconstitute(FileId id, String originalName, Long size, String contentType, String md5,
                                             String digest, String digestAlgorithm, FileAccessScope accessScope,
                                             String targetId, StorageType storageType, String storageKey,
                                             FileUsage usage, String bizType, String sourceApp, BatchId businessBatchId,
                                             FileStatus status, UserNo uploadedBy, LocalDateTime uploadedAt, LocalDateTime expiresAt,
                                             UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new FileMetadata(id, originalName, size, contentType, md5, digest, digestAlgorithm,
            accessScope, targetId, storageType, storageKey, usage, bizType, sourceApp, businessBatchId,
            status, uploadedBy, uploadedAt, expiresAt, createdBy, updatedBy, createdAt, updatedAt, version);
    }

    // ============ 保留原有 create（向后兼容）============
    public static FileMetadata create(FileId id, String originalName, long size, String contentType,
                                       FileUsage usage, String bizType, String sourceApp, BatchId businessBatchId,
                                       String targetId, StorageType storageType,
                                       UserNo uploadedBy, LocalDateTime expiresAt) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        if (originalName == null || originalName.isBlank()) throw new IllegalArgumentException("originalName 不能为空");
        if (size < 0) throw new IllegalArgumentException("size 不能为负");
        if (usage == null) throw new IllegalArgumentException("usage 不能为空");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId 不能为空");
        if (storageType == null) throw new IllegalArgumentException("storageType 不能为空");
        if (uploadedBy == null) throw new IllegalArgumentException("uploadedBy 不能为空");
        return new FileMetadata(id, originalName, size, contentType, usage, bizType, sourceApp,
            businessBatchId, targetId, storageType, uploadedBy, expiresAt);
    }

    // ============ 保留原有 markUploaded ============
    public void markUploaded(String storageKey, String md5) {
        if (this.status != FileStatus.PENDING_UPLOAD) {
            throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
                .withLogDetail("当前状态: " + this.status + ", 期望状态: PENDING_UPLOAD, fileId=" + id());
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
                .withLogDetail("storageKey 不能为空, fileId=" + id());
        }
        this.storageKey = storageKey;
        this.md5 = md5;
        this.status = FileStatus.UPLOADED;
        this.uploadedAt = LocalDateTime.now();
        markUpdated(this.uploadedBy != null ? this.uploadedBy : this.createdBy());
        registerDomainEvent(FileUploadedEvent.of(this));
    }

    public void markDeleted(UserNo deletedBy) {
        if (this.status == FileStatus.DELETED) {
            return;
        }
        this.status = FileStatus.DELETED;
        if (deletedBy != null) {
            markUpdated(deletedBy);
        }
        registerDomainEvent(FileDeletedEvent.of(this, deletedBy));
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    @Override
    protected void validateInvariants() {
        if (size != null && size < 0) {
            throw new IllegalStateException("size 不能为负, fileId=" + id());
        }
        if (status == FileStatus.UPLOADED) {
            if (uploadedAt == null || storageKey == null) {
                throw new IllegalStateException("UPLOADED 状态下 uploadedAt 和 storageKey 不能为空, fileId=" + id());
            }
        }
    }

    // Getters
    public String originalName() { return originalName; }
    public Long size() { return size; }
    public String contentType() { return contentType; }
    public String md5() { return md5; }
    public String digest() { return digest; }
    public String digestAlgorithm() { return digestAlgorithm; }
    public FileAccessScope accessScope() { return accessScope; }
    public String targetId() { return targetId; }
    public StorageType storageType() { return storageType; }
    public String storageKey() { return storageKey; }
    public FileUsage usage() { return usage; }
    public String bizType() { return bizType; }
    public String sourceApp() { return sourceApp; }
    public BatchId businessBatchId() { return businessBatchId; }
    public FileStatus status() { return status; }
    public UserNo uploadedBy() { return uploadedBy; }
    public LocalDateTime uploadedAt() { return uploadedAt; }
    public LocalDateTime expiresAt() { return expiresAt; }
}
