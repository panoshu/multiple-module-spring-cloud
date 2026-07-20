package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.FileTokenGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencent.kona.crypto.KonaCryptoProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.security.Security;

@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@ConditionalOnClass(name = "com.tencent.kona.crypto.KonaCryptoProvider")
@ConditionalOnProperty(prefix = "file.token", name = "secret-key")
@EnableConfigurationProperties(FileTokenProperties.class)
public class KonaAutoConfiguration {

    private final FileTokenProperties properties;

    @PostConstruct
    public void registerProvider() {
        try {
            if (Security.getProvider("KonaCrypto") == null) {
                Security.addProvider(new KonaCryptoProvider());
                log.info("KonaCrypto Provider 已注册");
            } else {
                log.info("KonaCrypto Provider 已存在，跳过注册");
            }
        } catch (Exception e) {
            log.warn("KonaCrypto Provider 注册失败: {}", e.getMessage());
        }
    }

    @Bean
    @ConditionalOnMissingBean(FileTokenGateway.class)
    public FileTokenGateway fileTokenGateway(ObjectMapper objectMapper) {
        return new KonaFileTokenGateway(objectMapper, properties);
    }
}
