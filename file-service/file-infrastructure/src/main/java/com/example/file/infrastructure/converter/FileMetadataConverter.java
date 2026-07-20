package com.example.file.infrastructure.converter;

import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.infrastructure.entity.FileMetadataDO;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.SystemException;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FileMetadataConverter {

    /**
     * 共享 ObjectMapper 实例（线程安全），用于 FileAccessScope ↔ JSON 序列化。
     * 注册 JavaTimeModule 以支持 LocalDateTime 等时间类型（即使当前 scope 不直接含时间，
     * 也保持与项目其它 ObjectMapper 一致的配置）。
     */
    ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

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
        aDo.setAccessScope(scopeToJson(file.accessScope()));
        aDo.setDigest(file.digest());
        aDo.setDigestAlgorithm(file.digestAlgorithm());
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
            aDo.getSize(),
            aDo.getContentType(),
            aDo.getMd5(),
            aDo.getDigest(),
            aDo.getDigestAlgorithm(),
            jsonToScope(aDo.getAccessScope()),
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

    /**
     * FileAccessScope → JSON 字符串（持久化为 JSONB / VARCHAR）。
     * 使用共享 static ObjectMapper 避免每次调用创建新实例。
     */
    default String scopeToJson(FileAccessScope scope) {
        if (scope == null) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(scope);
        } catch (Exception e) {
            throw new SystemException(FileErrorCodes.FILE_STORAGE_CONFIG_INVALID)
                .withLogDetail("accessScope 序列化失败: " + e.getMessage());
        }
    }

    /**
     * JSON 字符串 → FileAccessScope。
     * 使用共享 static ObjectMapper 避免每次调用创建新实例。
     */
    default FileAccessScope jsonToScope(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return OBJECT_MAPPER.readValue(json, FileAccessScope.class);
        } catch (Exception e) {
            throw new SystemException(FileErrorCodes.FILE_STORAGE_CONFIG_INVALID)
                .withLogDetail("accessScope 反序列化失败: " + e.getMessage());
        }
    }
}
