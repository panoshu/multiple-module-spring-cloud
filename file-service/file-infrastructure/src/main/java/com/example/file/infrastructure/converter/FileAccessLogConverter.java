package com.example.file.infrastructure.converter;

import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.valueobject.FileAccessAction;
import com.example.file.domain.model.aggregate.valueobject.FileAccessResult;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.infrastructure.entity.FileAccessLogDO;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileAccessLogId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * FileAccessLog 聚合根与 DO 互转
 * <p>
 * 参考 {@link FileMetadataConverter} 的实现风格：使用 @Mapper(componentModel = "spring")
 * 让 MapStruct 生成 Spring Bean 实现类，转换逻辑用 default 方法手写以保持与项目惯例一致。
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FileAccessLogConverter {

    default FileAccessLogDO toDO(FileAccessLog log) {
        if (log == null) return null;
        FileAccessLogDO aDo = new FileAccessLogDO();
        aDo.setId(log.id() != null ? log.id().value() : null);
        aDo.setFileId(log.fileId() != null ? log.fileId().value() : null);
        aDo.setAction(log.action() != null ? log.action().name() : null);
        aDo.setUsage(log.usage() != null ? log.usage().name() : null);
        aDo.setCustomerNo(log.customerNo() != null ? log.customerNo().value() : null);
        aDo.setProductNo(log.productNo() != null ? log.productNo().value() : null);
        aDo.setOperator(log.operator() != null ? log.operator().value() : null);
        aDo.setSourceApp(log.sourceApp());
        aDo.setSourceIp(log.sourceIp());
        aDo.setTokenHash(log.tokenHash());
        aDo.setResult(log.result() != null ? log.result().name() : null);
        aDo.setFailReason(log.failReason());
        aDo.setOccurAt(log.occurAt());
        aDo.setCreatedBy(log.createdBy() != null ? log.createdBy().value() : null);
        aDo.setUpdatedBy(log.updatedBy() != null ? log.updatedBy().value() : null);
        aDo.setCreateTime(log.createdAt());
        aDo.setUpdateTime(log.updatedAt());
        aDo.setDeleted(false);
        aDo.setVersion(log.version() != null ? (int) log.version().value() : 0);
        return aDo;
    }

    default FileAccessLog toDomain(FileAccessLogDO aDo) {
        if (aDo == null) return null;
        return FileAccessLog.reconstitute(
            aDo.getId() != null ? FileAccessLogId.of(aDo.getId()) : null,
            aDo.getFileId() != null ? new FileId(aDo.getFileId()) : null,
            aDo.getAction() != null ? FileAccessAction.valueOf(aDo.getAction()) : null,
            aDo.getUsage() != null ? FileUsage.valueOf(aDo.getUsage()) : null,
            aDo.getCustomerNo() != null ? CustomerNo.of(aDo.getCustomerNo()) : null,
            aDo.getProductNo() != null ? ProductNo.of(aDo.getProductNo()) : null,
            aDo.getOperator() != null ? UserNo.of(aDo.getOperator()) : null,
            aDo.getSourceApp(),
            aDo.getSourceIp(),
            aDo.getTokenHash(),
            aDo.getResult() != null ? FileAccessResult.valueOf(aDo.getResult()) : null,
            aDo.getFailReason(),
            aDo.getOccurAt(),
            aDo.getCreatedBy() != null ? UserNo.of(aDo.getCreatedBy()) : null,
            aDo.getUpdatedBy() != null ? UserNo.of(aDo.getUpdatedBy()) : null,
            aDo.getCreateTime(),
            aDo.getUpdateTime(),
            aDo.getVersion() != null ? Version.of(aDo.getVersion().longValue()) : null
        );
    }
}
