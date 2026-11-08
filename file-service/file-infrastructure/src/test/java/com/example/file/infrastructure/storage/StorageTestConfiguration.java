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

  /**
   * 测试桩 IdService：FileStorageRouter 收编至 shared-id 门面后需要（固定 FileId）.
   */
  @Bean
  public com.example.shared.identifier.contract.IdService idService() {
    return new com.example.shared.identifier.contract.IdService() {
      @Override
      public <T extends com.example.shared.identifier.contract.Identifier<?>> T nextId(Class<T> idClass) {
        try {
          return idClass.getDeclaredConstructor(String.class).newInstance("01H8TESTFILE00");
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      }

      @Override
      public <T extends com.example.shared.identifier.contract.Identifier<?>> T nextId(Class<T> idClass, String bizContext) {
        return nextId(idClass);
      }

      @Override
      public String nextId(com.example.shared.identifier.contract.IdType type) {
        return "01H8TESTFILE00";
      }

      @Override
      public String nextId(com.example.shared.identifier.contract.IdType type, String bizType) {
        return "01H8TESTFILE00";
      }

      @Override
      public <T extends com.example.shared.identifier.contract.Identifier<Long>> T nextLongId(Class<T> idClass, String bizContext) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Long nextLongId(String bizType) {
        throw new UnsupportedOperationException();
      }
    };
  }

  @org.springframework.context.annotation.Bean
  com.example.shared.cache.core.ICacheTemplate testCacheTemplate() {
    com.example.shared.cache.core.ICacheTemplate mock =
      org.mockito.Mockito.mock(com.example.shared.cache.core.ICacheTemplate.class);
    // getOrLoad 直通 loader
    org.mockito.Mockito.when(mock.getOrLoad(
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.<Class<Object>>any()))
      .thenAnswer(inv -> ((java.util.function.Function<String, Object>) inv.getArgument(2)).apply(inv.getArgument(1)));
    return mock;
  }
}
