package com.example.file.infrastructure.storage;

import com.example.file.domain.model.aggregate.valueobject.StorageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Validated
@ConfigurationProperties(prefix = "file.storage")
public class StorageTargetProperties {

    private boolean enabled = true;

    @NotEmpty
    @Valid
    private List<StorageTargetConfig> targets = new ArrayList<>();

    @NotNull
    @Valid
    private RoutingConfig routing = new RoutingConfig();

    @Data
    public static class StorageTargetConfig {
        @NotBlank
        private String id;

        @NotNull
        private StorageType type;

        private String endpoint;
        private String bucket;
        private String basePath;
        private String mountRoot;
        private String accessKeyId;
        private String accessKeySecret;

        private Map<String, String> options = new HashMap<>();
    }

    @Data
    public static class RoutingConfig {
        @NotBlank
        private String source = "local-dev";
        @NotBlank
        private String parsed = "local-dev";
        @NotBlank
        private String export = "local-dev";
        @NotBlank
        private String archive = "local-dev";
    }
}
