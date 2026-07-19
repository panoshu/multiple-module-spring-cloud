package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class PropertiesBasedStorageTargetResolver implements StorageTargetResolver {

    private final StorageTargetProperties properties;
    private final Map<String, StorageTarget> targetMap;

    public PropertiesBasedStorageTargetResolver(StorageTargetProperties properties) {
        this.properties = properties;
        this.targetMap = properties.getTargets().stream()
            .collect(Collectors.toMap(
                StorageTargetProperties.StorageTargetConfig::getId,
                this::toStorageTarget,
                (a, b) -> a
            ));
        validate();
        log.info("存储目标已加载: {}", targetMap.keySet());
    }

    @Override
    public StorageTarget resolveByUsage(FileUsage usage, String bizType) {
        String targetId = switch (usage) {
            case SOURCE -> properties.getRouting().getSource();
            case PARSED -> properties.getRouting().getParsed();
            case EXPORT -> properties.getRouting().getExport();
            case ARCHIVE -> properties.getRouting().getArchive();
        };
        return resolveById(targetId);
    }

    @Override
    public StorageTarget resolveById(String targetId) {
        StorageTarget target = targetMap.get(targetId);
        if (target == null) {
            throw new IllegalStateException("存储目标不存在: targetId=" + targetId);
        }
        return target;
    }

    @Override
    public List<StorageTarget> listAll() {
        return List.copyOf(targetMap.values());
    }

    private StorageTarget toStorageTarget(StorageTargetProperties.StorageTargetConfig config) {
        return new StorageTarget(
            config.getId(),
            config.getType(),
            config.getEndpoint(),
            config.getBucket(),
            config.getBasePath(),
            config.getMountRoot(),
            config.getAccessKeyId(),
            config.getAccessKeySecret(),
            config.getOptions() != null ? Map.copyOf(config.getOptions()) : Map.of()
        );
    }

    private void validate() {
        StorageTargetProperties.RoutingConfig r = properties.getRouting();
        validateTargetExists(r.getSource(), "routing.source");
        validateTargetExists(r.getParsed(), "routing.parsed");
        validateTargetExists(r.getExport(), "routing.export");
        validateTargetExists(r.getArchive(), "routing.archive");

        for (StorageTarget target : targetMap.values()) {
            switch (target.type()) {
                case OSS -> {
                    requireNonBlank(target.endpoint(), "OSS endpoint", target.targetId());
                    requireNonBlank(target.bucket(), "OSS bucket", target.targetId());
                    requireNonBlank(target.accessKeyId(), "OSS accessKeyId", target.targetId());
                    requireNonBlank(target.accessKeySecret(), "OSS accessKeySecret", target.targetId());
                }
                case NAS -> {
                    requireNonBlank(target.bucket(), "NAS bucket(共享名)", target.targetId());
                    requireNonBlank(target.basePath(), "NAS basePath", target.targetId());
                }
                case LOCAL -> {
                    requireNonBlank(target.basePath(), "LOCAL basePath", target.targetId());
                }
            }
        }
    }

    private void validateTargetExists(String targetId, String configKey) {
        if (!targetMap.containsKey(targetId)) {
            throw new IllegalStateException(
                "存储路由配置错误: " + configKey + "=" + targetId + " 对应的 target 不存在"
            );
        }
    }

    private void requireNonBlank(String value, String fieldName, String targetId) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "存储目标配置错误: targetId=" + targetId + " 缺少必填字段 " + fieldName
            );
        }
    }
}
