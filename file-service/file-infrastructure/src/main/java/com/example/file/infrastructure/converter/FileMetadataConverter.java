package com.example.file.infrastructure.converter;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.infrastructure.entity.FileMetadataDO;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FileMetadataConverter {

    default FileMetadataDO toDO(FileMetadata file) {
        FileMetadataDO aDo = new FileMetadataDO();
        aDo.setId(file.id() != null ? file.id().value() : null);
        aDo.setOriginalName(file.originalName());
        aDo.setSize(file.size());
        aDo.setContentType(file.contentType());
        aDo.setMd5(file.md5());
        aDo.setTargetId(file.targetId());
        aDo.setStorageType(file.storageType() != null ? file.storageType().name() : null);
        aDo.setStorageKey(file.storageKey());
        aDo.setUsage(file.usage() != null ? file.usage().name() : null);
        aDo.setBizType(file.bizType());
        aDo.setSourceApp(file.sourceApp());
        aDo.setBusinessBatchId(file.businessBatchId() != null ? file.businessBatchId().value() : null);
        aDo.setStatus(file.status() != null ? file.status().name() : null);
        aDo.setUploadedBy(file.uploadedBy() != null ? file.uploadedBy().value() : null);
        aDo.setUploadedAt(file.uploadedAt());
        aDo.setExpiresAt(file.expiresAt());
        aDo.setCreatedBy(file.createdBy() != null ? file.createdBy().value() : null);
        aDo.setUpdatedBy(file.updatedBy() != null ? file.updatedBy().value() : null);
        aDo.setCreateTime(file.createdAt());
        aDo.setUpdateTime(file.updatedAt());
        aDo.setDeleted(false);
        aDo.setVersion(file.version() != null ? (int) file.version().value() : 0);
        return aDo;
    }

    default FileMetadata toDomain(FileMetadataDO aDo) {
        if (aDo == null) return null;
        return FileMetadata.reconstitute(
            new FileId(aDo.getId()),
            aDo.getOriginalName(),
            aDo.getSize() != null ? aDo.getSize() : 0L,
            aDo.getContentType(),
            aDo.getMd5(),
            aDo.getTargetId(),
            aDo.getStorageType() != null ? StorageType.valueOf(aDo.getStorageType()) : null,
            aDo.getStorageKey(),
            aDo.getUsage() != null ? FileUsage.valueOf(aDo.getUsage()) : null,
            aDo.getBizType(),
            aDo.getSourceApp(),
            aDo.getBusinessBatchId() != null ? BatchId.of(aDo.getBusinessBatchId()) : null,
            aDo.getStatus() != null ? FileStatus.valueOf(aDo.getStatus()) : null,
            aDo.getUploadedBy() != null ? UserNo.of(aDo.getUploadedBy()) : null,
            aDo.getUploadedAt(),
            aDo.getExpiresAt(),
            aDo.getCreatedBy() != null ? UserNo.of(aDo.getCreatedBy()) : null,
            aDo.getUpdatedBy() != null ? UserNo.of(aDo.getUpdatedBy()) : null,
            aDo.getCreateTime(),
            aDo.getUpdateTime(),
            aDo.getVersion() != null ? Version.of(aDo.getVersion().longValue()) : null
        );
    }
}
