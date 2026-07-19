package com.example.file.infrastructure.storage;

import com.example.file.domain.repository.FileMetadataRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import static org.mockito.Mockito.mock;

/**
 * 存储集成测试专用配置。
 *
 * <p>设计要点：
 * <ul>
 *   <li>不使用 @EnableAutoConfiguration —— 避免 shared-id-starter / shared-cache-starter
 *       的自动配置级联触发 DataSource/DistributedLock 等不需要的依赖</li>
 *   <li>显式 @Import StorageAutoConfiguration 提供存储相关 bean
 *       (FileStorageGateway + StorageTargetResolver)</li>
 *   <li>@ComponentScan 加载 storage 包下 @Component 标注的 LocalFileStorage</li>
 *   <li>提供 mock FileMetadataRepository (FileStorageRouter 依赖项)</li>
 * </ul>
 *
 * <p>注意：存储配置（file.storage.*）通过测试类上的 @TestPropertySource 内联提供。
 * 不能在此 @Configuration 类上声明 @TestPropertySource —— 该注解由 Spring TestContext
 * 框架基于测试类层级处理，配置类上的声明不会被 @SpringBootTest 拾取。
 */
@SpringBootConfiguration
@Import(StorageAutoConfiguration.class)
@ComponentScan(basePackages = "com.example.file.infrastructure.storage")
public class StorageTestConfiguration {

    /**
     * Mock FileMetadataRepository 骨架。
     *
     * <p>具体 stubbing (loadOrThrow / load / save) 在测试类的 @BeforeEach 中
     * 通过 {@code reset(repository)} 后用 {@code when(...)} 设置，保证每个测试
     * 用例对 mock 的行为有完全控制。
     */
    @Bean
    public FileMetadataRepository fileMetadataRepository() {
        return mock(FileMetadataRepository.class);
    }
}
