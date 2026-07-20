package com.example.file.infrastructure.storage;

import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.CopyResult;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.gateway.StoreResult;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.exception.SystemException;
import com.example.shared.id.algorithm.UlidAlgorithm;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class FileStorageRouter implements FileStorageGateway {

    private final FileMetadataRepository metadataRepository;
    private final StorageTargetResolver targetResolver;
    private final List<FileStorageBackend> backends;
    private final Map<StorageType, FileStorageBackend> backendMap;

    public FileStorageRouter(FileMetadataRepository metadataRepository,
                              StorageTargetResolver targetResolver,
                              List<FileStorageBackend> backends) {
        this.metadataRepository = metadataRepository;
        this.targetResolver = targetResolver;
        this.backends = backends;
        this.backendMap = backends.stream()
            .collect(Collectors.toMap(
                FileStorageBackend::supportedType,
                Function.identity(),
                (a, b) -> a
            ));
        log.info("文件存储后端已初始化: {}", backendMap.keySet());
    }

    @Override
    public StoreResult store(FileId fileId, InputStream content, long contentLength) {
        FileMetadata file = metadataRepository.loadOrThrow(fileId);
        if (file.status() != FileStatus.PENDING_UPLOAD) {
            throw new SystemException(FileErrorCodes.FILE_ALREADY_UPLOADED)
                .withLogDetail("fileId=" + fileId);
        }
        StorageTarget target = targetResolver.resolveById(file.targetId());
        FileStorageBackend backend = resolveBackend(target.type());
        String storageKey = generateStorageKey(file);
        backend.store(target, storageKey, content, contentLength);
        String md5 = backend.computeDigest(target, storageKey);
        return new StoreResult(storageKey, md5);
    }

    @Override
    public InputStream open(FileId fileId) {
        FileMetadata file = metadataRepository.loadOrThrow(fileId);
        StorageTarget target = targetResolver.resolveById(file.targetId());
        FileStorageBackend backend = resolveBackend(target.type());
        return backend.open(target, file.storageKey());
    }

    @Override
    public boolean exists(FileId fileId) {
        return metadataRepository.load(fileId)
            .map(file -> {
                StorageTarget target = targetResolver.resolveById(file.targetId());
                FileStorageBackend backend = resolveBackend(target.type());
                return backend.exists(target, file.storageKey());
            })
            .orElse(false);
    }

    @Override
    public CopyResult copy(FileId srcFileId, FileUsage targetUsage, BatchId businessBatchId) {
        FileMetadata srcFile = metadataRepository.loadOrThrow(srcFileId);
        StorageTarget srcTarget = targetResolver.resolveById(srcFile.targetId());
        StorageTarget dstTarget = targetResolver.resolveByUsage(targetUsage, srcFile.bizType());

        FileId newFileId = generateFileId();
        String newStorageKey = generateStorageKeyForCopy(srcFile, newFileId);

        if (srcTarget.type() == dstTarget.type()) {
            FileStorageBackend backend = resolveBackend(dstTarget.type());
            backend.copy(dstTarget, srcFile.storageKey(), newStorageKey);
        } else {
            crossBackendCopy(
                resolveBackend(srcTarget.type()), srcTarget, srcFile.storageKey(),
                resolveBackend(dstTarget.type()), dstTarget, newStorageKey
            );
        }

        return new CopyResult(newFileId, newStorageKey);
    }

    @Override
    public String computeDigest(FileId fileId) {
        FileMetadata file = metadataRepository.loadOrThrow(fileId);
        StorageTarget target = targetResolver.resolveById(file.targetId());
        FileStorageBackend backend = resolveBackend(target.type());
        return backend.computeDigest(target, file.storageKey());
    }

    private FileStorageBackend resolveBackend(StorageType type) {
        FileStorageBackend backend = backendMap.get(type);
        if (backend == null) {
            throw new SystemException(FileErrorCodes.FILE_STORAGE_TARGET_TYPE_MISMATCH)
                .withLogDetail("未找到存储类型对应的后端实现: " + type);
        }
        return backend;
    }

    /**
     * storageKey 生成规范:
     * {bizType}/{date}/{batchId}/{fileId}/{originalName}
     */
    private String generateStorageKey(FileMetadata file) {
        String datePartition = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return String.join("/",
            file.bizType() != null ? file.bizType() : "default",
            datePartition,
            file.businessBatchId() != null ? file.businessBatchId().value() : "no-batch",
            file.id().value(),
            file.originalName()
        );
    }

    private String generateStorageKeyForCopy(FileMetadata srcFile, FileId newFileId) {
        String datePartition = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return String.join("/",
            srcFile.bizType() != null ? srcFile.bizType() : "default",
            datePartition,
            srcFile.businessBatchId() != null ? srcFile.businessBatchId().value() : "no-batch",
            newFileId.value(),
            srcFile.originalName()
        );
    }

    private FileId generateFileId() {
        return new FileId(UlidAlgorithm.generate());
    }

    private void crossBackendCopy(FileStorageBackend srcBackend, StorageTarget srcTarget, String srcKey,
                                   FileStorageBackend dstBackend, StorageTarget dstTarget, String dstKey) {
        try (InputStream in = srcBackend.open(srcTarget, srcKey)) {
            dstBackend.store(dstTarget, dstKey, in, -1);
        } catch (IOException e) {
            throw new SystemException(FileErrorCodes.FILE_COPY_FAILED, e)
                .withLogDetail("cross-backend copy failed");
        }
    }
}
