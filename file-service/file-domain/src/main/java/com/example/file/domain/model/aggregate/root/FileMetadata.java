package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.event.FileDeletedEvent;
import com.example.file.domain.event.FileMetadataCreatedEvent;
import com.example.file.domain.event.FileUploadedEvent;
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
    private long size;
    private String contentType;
    private String md5;

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

    // 业务创建
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

    // 数据库重建
    public FileMetadata(FileId id, String originalName, long size, String contentType, String md5,
                        String targetId, StorageType storageType, String storageKey,
                        FileUsage usage, String bizType, String sourceApp, BatchId businessBatchId,
                        FileStatus status, UserNo uploadedBy, LocalDateTime uploadedAt, LocalDateTime expiresAt,
                        UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.originalName = originalName;
        this.size = size;
        this.contentType = contentType;
        this.md5 = md5;
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

    public static FileMetadata reconstitute(FileId id, String originalName, long size, String contentType, String md5,
                                             String targetId, StorageType storageType, String storageKey,
                                             FileUsage usage, String bizType, String sourceApp, BatchId businessBatchId,
                                             FileStatus status, UserNo uploadedBy, LocalDateTime uploadedAt, LocalDateTime expiresAt,
                                             UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new FileMetadata(id, originalName, size, contentType, md5, targetId, storageType, storageKey,
            usage, bizType, sourceApp, businessBatchId, status, uploadedBy, uploadedAt, expiresAt,
            createdBy, updatedBy, createdAt, updatedAt, version);
    }

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
        if (size < 0) {
            throw new IllegalStateException("size 不能为负, fileId=" + id());
        }
        if (status == FileStatus.UPLOADED && (uploadedAt == null || storageKey == null)) {
            throw new IllegalStateException("UPLOADED 状态下 uploadedAt 和 storageKey 不能为空, fileId=" + id());
        }
    }

    // Getters
    public String originalName() { return originalName; }
    public long size() { return size; }
    public String contentType() { return contentType; }
    public String md5() { return md5; }
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
