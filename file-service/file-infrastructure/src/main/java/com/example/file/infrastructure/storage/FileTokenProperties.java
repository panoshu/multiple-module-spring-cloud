package com.example.file.infrastructure.storage;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@ConfigurationProperties(prefix = "file.token")
public class FileTokenProperties {

    @NotBlank
    private String secretKey;

    private Duration defaultUploadTtl = Duration.ofMinutes(15);
    private Duration defaultDownloadTtl = Duration.ofMinutes(15);

    private Redis redis = new Redis();

    @Data
    public static class Redis {
        private String keyPrefix = "file:token:used:";
        private Duration defaultTtl = Duration.ofMinutes(15);
    }
}
