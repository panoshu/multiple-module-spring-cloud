package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.repository.FileMetadataRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@ConditionalOnProperty(prefix = "file.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StorageTargetProperties.class)
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(StorageTargetResolver.class)
    public StorageTargetResolver storageTargetResolver(StorageTargetProperties properties) {
        return new PropertiesBasedStorageTargetResolver(properties);
    }

    @Bean
    @ConditionalOnMissingBean(FileStorageGateway.class)
    public FileStorageGateway fileStorageGateway(
            FileMetadataRepository metadataRepository,
            StorageTargetResolver targetResolver,
            List<FileStorageBackend> backends) {
        return new FileStorageRouter(metadataRepository, targetResolver, backends);
    }
}
