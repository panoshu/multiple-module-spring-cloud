package com.example.file.infrastructure.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "shared.file.provider", havingValue = "s3") // 只有开启 s3 时才加载
public class MinioConfiguration {

  @Value("${shared.file.s3.endpoint}")
  private String endpoint;

  @Value("${shared.file.s3.access-key}")
  private String accessKey;

  @Value("${shared.file.s3.secret-key}")
  private String secretKey;

  @Bean
  public MinioClient minioClient() {
    return MinioClient.builder()
      .endpoint(endpoint)
      .credentials(accessKey, secretKey)
      .build();
  }
}
