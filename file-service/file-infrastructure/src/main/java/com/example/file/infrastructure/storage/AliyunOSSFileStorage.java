package com.example.file.infrastructure.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.shared.exception.SystemException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnClass(name = "com.aliyun.oss.OSS")
public class AliyunOSSFileStorage implements FileStorageBackend {

    private final Map<String, OSS> clientCache = new ConcurrentHashMap<>();

    @Override
    public StorageType supportedType() {
        return StorageType.OSS;
    }

    @Override
    public void store(StorageTarget target, String storageKey,
                      InputStream content, long contentLength) {
        OSS client = getClient(target);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            if (contentLength > 0) {
                metadata.setContentLength(contentLength);
            }
            PutObjectRequest request = new PutObjectRequest(target.bucket(), storageKey, content, metadata);
            client.putObject(request);
            log.debug("OSS 存储成功: bucket={}, key={}", target.bucket(), storageKey);
        } catch (Exception e) {
            throw new SystemException(FileErrorCodes.FILE_STORAGE_FAILED, e)
                .withLogDetail("OSS store failed: bucket=" + target.bucket() + ", key=" + storageKey);
        }
    }

    @Override
    public InputStream open(StorageTarget target, String storageKey) {
        OSS client = getClient(target);
        try {
            OSSObject object = client.getObject(new GetObjectRequest(target.bucket(), storageKey));
            return object.getObjectContent();
        } catch (Exception e) {
            throw new SystemException(FileErrorCodes.FILE_METADATA_NOT_FOUND, e)
                .withLogDetail("OSS open failed: bucket=" + target.bucket() + ", key=" + storageKey);
        }
    }

    @Override
    public boolean exists(StorageTarget target, String storageKey) {
        OSS client = getClient(target);
        try {
            return client.doesObjectExist(target.bucket(), storageKey);
        } catch (Exception e) {
            log.warn("OSS exists 检查失败: bucket={}, key={}", target.bucket(), storageKey, e);
            return false;
        }
    }

    @Override
    public void copy(StorageTarget target, String srcKey, String dstKey) {
        OSS client = getClient(target);
        try {
            client.copyObject(target.bucket(), srcKey, target.bucket(), dstKey);
            log.debug("OSS 复制成功: src={}, dst={}", srcKey, dstKey);
        } catch (Exception e) {
            throw new SystemException(FileErrorCodes.FILE_COPY_FAILED, e)
                .withLogDetail("OSS copy failed: src=" + srcKey + ", dst=" + dstKey);
        }
    }

    @Override
    public String computeDigest(StorageTarget target, String storageKey) {
        OSS client = getClient(target);
        try {
            ObjectMetadata meta = client.getObjectMetadata(target.bucket(), storageKey);
            String eTag = meta.getETag();
            // OSS ETag 对于单片上传等于 MD5 (不含引号)
            // 注意：分片上传的 ETag 不是 MD5，此处假设单片上传场景
            if (eTag != null) {
                eTag = eTag.replace("\"", "");
            }
            return eTag;
        } catch (Exception e) {
            throw new SystemException(FileErrorCodes.FILE_STORAGE_FAILED, e)
                .withLogDetail("OSS computeDigest failed: key=" + storageKey);
        }
    }

    private OSS getClient(StorageTarget target) {
        return clientCache.computeIfAbsent(target.targetId(), k -> {
            CredentialsProvider credProvider = new DefaultCredentialProvider(
                target.accessKeyId(), target.accessKeySecret()
            );
            return new OSSClientBuilder()
                .build(target.endpoint(), credProvider);
        });
    }

    @PreDestroy
    void shutdown() {
        clientCache.values().forEach(client -> {
            try {
                client.shutdown();
            } catch (Exception e) {
                log.warn("OSS 客户端关闭失败", e);
            }
        });
        log.info("OSS 客户端已关闭");
    }
}
