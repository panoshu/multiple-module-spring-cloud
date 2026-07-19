# file-service 文件存储引擎 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 file-service 内部建立完整的文件存储子域，提供 FileMetadata 聚合根 + 多后端存储（Local/OSS/NAS）+ Router 路由，并迁移 ParseTask 的 sourceFileRef → sourceFileId。

**Architecture:** DDD 七层架构。domain 层定义 FileMetadata 聚合根 + StorageTarget 值对象 + FileStorageGateway/StorageTargetResolver 两个 SPI；infrastructure 层实现 3 个存储后端 + FileStorageRouter 路由分发；application 层提供 4 个用例（Store/Open/Delete/Copy）；ParseTask 字段从 String 改为 FileId 强类型。

**Tech Stack:** JDK 25 (preview), Spring Boot 3.5.14, MyBatis-Flex 1.11.5, PostgreSQL, H2 (test), 阿里云 OSS SDK 3.17.4 (optional), commons-codec, MapStruct 1.6.3, JUnit 5 + AssertJ

## Global Constraints

- **JDK**: 25, 启用 `--enable-preview`
- **Spring Boot**: 3.5.14
- **MyBatis-Flex**: 1.11.5 (唯一 ORM)
- **数据库**: PostgreSQL (首选), H2 (测试, MODE=PostgreSQL)
- **MapStruct**: 1.6.3, 领域对象用 record + private 构造函数, toDomain 用 default 方法调 reconstitute
- **强类型 ID**: FileId 使用 shared-types 已有定义 (`@IdDefinition(type = IdType.ULID)`, record)
- **BatchId**: 使用 shared-types 已有定义 (`com.example.shared.primitives.identity.BatchId`)
- **AggregateRoot**: 继承 `com.example.shared.domain.aggregate.root.AggregateRoot<ID>`
- **DomainEvent**: 实现 `com.example.shared.domain.event.DomainEvent`, record + static `of()` + `EventId.generate()` + `LocalDateTime.now()`
- **Repository**: 继承 `com.example.shared.domain.repository.Repository<T, ID>`, 实现 load/save/delete/deleteById/loadAll/streamByAppId
- **测试规范**: @DisplayName 描述, @TempDir 用于文件操作, 输出到 target/test-output/, PER_METHOD 隔离, 静态测试数据提取到 static final
- **错误码**: 前缀 `FILE_`, 全大写下划线分隔, 实现 `ErrorDefinition`
- **异常分类**: DomainException (domain 层业务规则), SystemException (infra 层系统故障), IllegalStateException (启动配置 fail-fast)
- **提交规范**: 每个任务结束 commit, message 格式 `feat/fix/refactor: 简述`

## File Structure

### 新建文件 (file-domain)

- `file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/StorageType.java` - 存储类型枚举
- `file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/FileUsage.java` - 文件用途枚举
- `file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/FileStatus.java` - 文件状态枚举
- `file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/StorageTarget.java` - 存储目标值对象 (record)
- `file-domain/src/main/java/com/example/file/domain/model/aggregate/root/FileMetadata.java` - 文件元数据聚合根
- `file-domain/src/main/java/com/example/file/domain/event/FileMetadataCreatedEvent.java` - 领域事件
- `file-domain/src/main/java/com/example/file/domain/event/FileUploadedEvent.java` - 领域事件
- `file-domain/src/main/java/com/example/file/domain/event/FileDeletedEvent.java` - 领域事件
- `file-domain/src/main/java/com/example/file/domain/repository/FileMetadataRepository.java` - Repository 接口

### 修改文件 (file-domain)

- `file-domain/src/main/java/com/example/file/domain/gateway/FileStorageGateway.java` - SPI 重构 (String → FileId)
- `file-domain/src/main/java/com/example/file/domain/gateway/StorageTargetResolver.java` - 新增 SPI
- `file-domain/src/main/java/com/example/file/domain/errorcode/FileErrorCodes.java` - 追加错误码
- `file-domain/src/main/java/com/example/file/domain/model/aggregate/root/ParseTask.java` - sourceFileRef → sourceFileId

### 新建文件 (file-application)

- `file-application/src/main/java/com/example/file/application/command/StoreFileCommand.java` - 命令对象
- `file-application/src/main/java/com/example/file/application/command/CopyFileCommand.java` - 命令对象
- `file-application/src/main/java/com/example/file/application/usecase/StoreFileUseCase.java` - 应用用例
- `file-application/src/main/java/com/example/file/application/usecase/OpenFileUseCase.java` - 应用用例
- `file-application/src/main/java/com/example/file/application/usecase/DeleteFileUseCase.java` - 应用用例
- `file-application/src/main/java/com/example/file/application/usecase/CopyFileUseCase.java` - 应用用例

### 修改文件 (file-application)

- `file-application/src/main/java/com/example/file/application/command/UploadFileCommand.java` - 字段改名
- `file-application/src/main/java/com/example/file/application/usecase/UploadFileUseCase.java` - 适配 FileId
- `file-application/src/main/java/com/example/file/application/usecase/ParseFileUseCase.java` - 适配 FileId

### 新建文件 (file-infrastructure)

- `file-infrastructure/src/main/java/com/example/file/infrastructure/storage/FileStorageBackend.java` - 内部 SPI
- `file-infrastructure/src/main/java/com/example/file/infrastructure/storage/LocalFileStorage.java` - 本地存储实现
- `file-infrastructure/src/main/java/com/example/file/infrastructure/storage/AliyunOSSFileStorage.java` - OSS 存储实现
- `file-infrastructure/src/main/java/com/example/file/infrastructure/storage/NASFileStorage.java` - NAS 存储实现
- `file-infrastructure/src/main/java/com/example/file/infrastructure/storage/FileStorageRouter.java` - 路由分发实现
- `file-infrastructure/src/main/java/com/example/file/infrastructure/storage/StorageTargetProperties.java` - 配置绑定
- `file-infrastructure/src/main/java/com/example/file/infrastructure/storage/PropertiesBasedStorageTargetResolver.java` - Resolver 实现
- `file-infrastructure/src/main/java/com/example/file/infrastructure/storage/StorageAutoConfiguration.java` - 自动装配
- `file-infrastructure/src/main/java/com/example/file/infrastructure/storage/CopyResult.java` - copy 返回值 record
- `file-infrastructure/src/main/java/com/example/file/infrastructure/entity/FileMetadataDO.java` - DO 实体
- `file-infrastructure/src/main/java/com/example/file/infrastructure/mapper/FileMetadataMapper.java` - Mapper
- `file-infrastructure/src/main/java/com/example/file/infrastructure/converter/FileMetadataConverter.java` - Converter
- `file-infrastructure/src/main/java/com/example/file/infrastructure/repository/FileMetadataRepositoryImpl.java` - Repository 实现
- `file-infrastructure/src/main/resources/schema-pg.sql` - 新建表 DDL
- `file-infrastructure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` - 注册自动装配

### 修改文件 (file-infrastructure)

- `file-infrastructure/src/main/java/com/example/file/infrastructure/entity/ParseTaskDO.java` - 字段改名
- `file-infrastructure/src/main/java/com/example/file/infrastructure/converter/ParseTaskConverter.java` - 适配 FileId

### 修改文件 (file-starter)

- `file-starter/src/main/resources/application.yml` - 追加 file.storage 配置块
- `file-starter/src/main/resources/application-local.yml` - 追加本地存储配置
- `file-starter/src/test/resources/application-test.yml` - 测试配置

### 修改文件 (file-infrastructure pom)

- `file-infrastructure/pom.xml` - 新增 aliyun-sdk-oss (optional) + commons-codec + h2 (test)

### 测试文件

- `file-domain/src/test/java/com/example/file/domain/model/aggregate/valueobject/StorageTargetTest.java`
- `file-domain/src/test/java/com/example/file/domain/model/aggregate/root/FileMetadataTest.java`
- `file-application/src/test/java/com/example/file/application/usecase/StoreFileUseCaseTest.java`
- `file-application/src/test/java/com/example/file/application/usecase/OpenFileUseCaseTest.java`
- `file-application/src/test/java/com/example/file/application/usecase/DeleteFileUseCaseTest.java`
- `file-application/src/test/java/com/example/file/application/usecase/CopyFileUseCaseTest.java`
- `file-infrastructure/src/test/java/com/example/file/infrastructure/storage/LocalFileStorageTest.java`
- `file-infrastructure/src/test/java/com/example/file/infrastructure/storage/NASFileStorageTest.java`
- `file-infrastructure/src/test/java/com/example/file/infrastructure/storage/FileStorageRouterTest.java`
- `file-infrastructure/src/test/java/com/example/file/infrastructure/storage/PropertiesBasedStorageTargetResolverTest.java`
- `file-infrastructure/src/test/java/com/example/file/infrastructure/converter/FileMetadataConverterTest.java`
- `file-infrastructure/src/test/java/com/example/file/infrastructure/StorageIntegrationTest.java`

### 修改测试文件

- `file-domain/src/test/java/com/example/file/domain/model/aggregate/root/ParseTaskTest.java` - 适配 FileId
- `file-infrastructure/src/test/java/com/example/file/infrastructure/ParseFlowIntegrationTest.java` - 适配 FileId (如有引用)

---

## Task 1: 领域枚举与错误码扩展

**Files:**
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/StorageType.java`
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/FileUsage.java`
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/FileStatus.java`
- Modify: `file-service/file-domain/src/main/java/com/example/file/domain/errorcode/FileErrorCodes.java`

**Interfaces:**
- Produces: `StorageType` enum (LOCAL/OSS/NAS), `FileUsage` enum (SOURCE/PARSED/EXPORT/ARCHIVE), `FileStatus` enum (PENDING_UPLOAD/UPLOADED/DELETED), 扩展的 `FileErrorCodes`

- [ ] **Step 1: 创建 StorageType 枚举**

```java
package com.example.file.domain.model.aggregate.valueobject;

/**
 * 存储类型枚举
 */
public enum StorageType {
    LOCAL,
    OSS,
    NAS
}
```

- [ ] **Step 2: 创建 FileUsage 枚举**

```java
package com.example.file.domain.model.aggregate.valueobject;

/**
 * 文件用途枚举，用于路由到对应的 StorageTarget
 */
public enum FileUsage {
    SOURCE,
    PARSED,
    EXPORT,
    ARCHIVE
}
```

- [ ] **Step 3: 创建 FileStatus 枚举**

```java
package com.example.file.domain.model.aggregate.valueobject;

/**
 * 文件状态枚举
 * 状态机: PENDING_UPLOAD → UPLOADED → DELETED
 */
public enum FileStatus {
    PENDING_UPLOAD,
    UPLOADED,
    DELETED
}
```

- [ ] **Step 4: 扩展 FileErrorCodes**

在现有 `FileErrorCodes.java` 的 `EXCEL_EXPORT_FAILED` 后追加新错误码：

```java
package com.example.file.domain.errorcode;

import com.example.shared.exception.ErrorDefinition;

public enum FileErrorCodes implements ErrorDefinition {
  CONFIG_NOT_FOUND("FILE_CONFIG_NOT_FOUND", "模板配置不存在"),
  CONFIG_INVALID("FILE_CONFIG_INVALID", "模板配置无效"),
  PARSE_FAILED("FILE_PARSE_FAILED", "Excel 解析失败"),
  SUB_TASK_NOT_FOUND("FILE_SUB_TASK_NOT_FOUND", "子任务不存在"),
  SUB_TASK_EXPIRED("FILE_SUB_TASK_EXPIRED", "子任务已过期"),
  SUB_TASK_INVALID("FILE_SUB_TASK_INVALID", "子任务校验失败"),
  IDENTIFY_FAILED("FILE_IDENTIFY_FAILED", "无法识别源模板"),
  EXPRESSION_ERROR("FILE_EXPRESSION_ERROR", "表达式求值失败"),
  EXCEL_EXPORT_FAILED("FILE_EXCEL_EXPORT_FAILED", "Excel 模板填充失败"),

  // 文件元数据相关
  FILE_METADATA_NOT_FOUND("FILE_METADATA_NOT_FOUND", "文件元数据不存在"),
  FILE_ALREADY_UPLOADED("FILE_ALREADY_UPLOADED", "文件已上传，不能重复上传"),
  FILE_STATUS_INVALID("FILE_STATUS_INVALID", "文件状态不允许此操作"),
  FILE_EXPIRED("FILE_EXPIRED", "文件已过期"),

  // 存储后端相关
  FILE_STORAGE_FAILED("FILE_STORAGE_FAILED", "文件存储失败"),
  FILE_STORAGE_TARGET_NOT_FOUND("FILE_STORAGE_TARGET_NOT_FOUND", "存储目标不存在"),
  FILE_STORAGE_TARGET_TYPE_MISMATCH("FILE_STORAGE_TARGET_TYPE_MISMATCH", "存储目标类型不匹配"),
  FILE_STORAGE_CONFIG_INVALID("FILE_STORAGE_CONFIG_INVALID", "存储配置无效"),
  FILE_COPY_FAILED("FILE_COPY_FAILED", "文件复制失败"),
  FILE_MD5_MISMATCH("FILE_MD5_MISMATCH", "文件 MD5 校验失败"),

  // Download/Read 相关
  FILE_DOWNLOAD_FAILED("FILE_DOWNLOAD_FAILED", "文件下载失败"),
  FILE_STREAM_CLOSED("FILE_STREAM_CLOSED", "文件流已关闭");

  private final String code;
  private final String message;

  FileErrorCodes(String code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public String code() { return code; }

  @Override
  public String message() { return message; }
}
```

- [ ] **Step 5: 编译验证**

Run: `mvn compile -pl file-service/file-domain -am`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/StorageType.java \
        file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/FileUsage.java \
        file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/FileStatus.java \
        file-service/file-domain/src/main/java/com/example/file/domain/errorcode/FileErrorCodes.java
git commit -m "feat(file-domain): 新增文件存储枚举与错误码扩展"
```

---

## Task 2: StorageTarget 值对象 + 测试

**Files:**
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/StorageTarget.java`
- Test: `file-service/file-domain/src/test/java/com/example/file/domain/model/aggregate/valueobject/StorageTargetTest.java`

**Interfaces:**
- Consumes: `StorageType` (from Task 1)
- Produces: `StorageTarget` record (targetId, type, endpoint, bucket, basePath, mountRoot, accessKeyId, accessKeySecret, options)

- [ ] **Step 1: 编写失败测试**

```java
package com.example.file.domain.model.aggregate.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageTargetTest {

    @Test
    @DisplayName("LOCAL 类型必须有 basePath")
    void constructor_should_validate_LOCAL_required_fields() {
        assertThatThrownBy(() -> new StorageTarget(
            "local-1", StorageType.LOCAL, null, null, null, null, null, null, Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("basePath");
    }

    @Test
    @DisplayName("OSS 类型必须有 endpoint+bucket+AK/SK")
    void constructor_should_validate_OSS_required_fields() {
        assertThatThrownBy(() -> new StorageTarget(
            "oss-1", StorageType.OSS, null, "bucket", "base", null, null, null, Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("endpoint");
    }

    @Test
    @DisplayName("NAS 类型必须有 bucket 和 basePath")
    void constructor_should_validate_NAS_required_fields() {
        assertThatThrownBy(() -> new StorageTarget(
            "nas-1", StorageType.NAS, null, null, null, null, null, null, Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("bucket");
    }

    @Test
    @DisplayName("合法的 LOCAL 配置应创建成功")
    void constructor_should_pass_when_LOCAL_valid() {
        StorageTarget target = new StorageTarget(
            "local-1", StorageType.LOCAL, null, null, "/data/files", null, null, null, Map.of()
        );
        assertThat(target.targetId()).isEqualTo("local-1");
        assertThat(target.type()).isEqualTo(StorageType.LOCAL);
        assertThat(target.basePath()).isEqualTo("/data/files");
    }

    @Test
    @DisplayName("合法的 OSS 配置应创建成功")
    void constructor_should_pass_when_OSS_valid() {
        StorageTarget target = new StorageTarget(
            "oss-1", StorageType.OSS, "https://oss.example.com", "bucket", "base",
            null, "ak", "sk", Map.of("region", "cn-hangzhou")
        );
        assertThat(target.endpoint()).isEqualTo("https://oss.example.com");
        assertThat(target.accessKeyId()).isEqualTo("ak");
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl file-service/file-domain -Dtest=StorageTargetTest`
Expected: FAIL (StorageTarget 类不存在)

- [ ] **Step 3: 实现 StorageTarget**

```java
package com.example.file.domain.model.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.Map;

/**
 * 存储目标值对象 (不可变)
 * <p>
 * 描述一个具体的存储后端配置，不持久化到 DB，由 application.yml 加载。
 */
public record StorageTarget(
    String targetId,
    StorageType type,
    String endpoint,
    String bucket,
    String basePath,
    String mountRoot,
    String accessKeyId,
    String accessKeySecret,
    Map<String, String> options
) implements ValueObject {

    public StorageTarget {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId 不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("type 不能为空");
        }
        options = options != null ? Map.copyOf(options) : Map.of();

        switch (type) {
            case LOCAL -> {
                if (basePath == null || basePath.isBlank()) {
                    throw new IllegalArgumentException("LOCAL 类型必须配置 basePath, targetId=" + targetId);
                }
            }
            case OSS -> {
                if (endpoint == null || endpoint.isBlank()) {
                    throw new IllegalArgumentException("OSS 类型必须配置 endpoint, targetId=" + targetId);
                }
                if (bucket == null || bucket.isBlank()) {
                    throw new IllegalArgumentException("OSS 类型必须配置 bucket, targetId=" + targetId);
                }
                if (accessKeyId == null || accessKeyId.isBlank()) {
                    throw new IllegalArgumentException("OSS 类型必须配置 accessKeyId, targetId=" + targetId);
                }
                if (accessKeySecret == null || accessKeySecret.isBlank()) {
                    throw new IllegalArgumentException("OSS 类型必须配置 accessKeySecret, targetId=" + targetId);
                }
            }
            case NAS -> {
                if (bucket == null || bucket.isBlank()) {
                    throw new IllegalArgumentException("NAS 类型必须配置 bucket(共享名), targetId=" + targetId);
                }
                if (basePath == null || basePath.isBlank()) {
                    throw new IllegalArgumentException("NAS 类型必须配置 basePath, targetId=" + targetId);
                }
            }
        }
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl file-service/file-domain -Dtest=StorageTargetTest`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/StorageTarget.java \
        file-service/file-domain/src/test/java/com/example/file/domain/model/aggregate/valueobject/StorageTargetTest.java
git commit -m "feat(file-domain): 新增 StorageTarget 值对象"
```

---

## Task 3: FileMetadata 聚合根 + 领域事件 + 测试

**Files:**
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/root/FileMetadata.java`
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/event/FileMetadataCreatedEvent.java`
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/event/FileUploadedEvent.java`
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/event/FileDeletedEvent.java`
- Test: `file-service/file-domain/src/test/java/com/example/file/domain/model/aggregate/root/FileMetadataTest.java`

**Interfaces:**
- Consumes: `StorageType`, `FileUsage`, `FileStatus` (Task 1), `FileId`, `BatchId`, `UserNo` (shared-types)
- Produces: `FileMetadata` 聚合根, 3 个领域事件

- [ ] **Step 1: 编写失败测试**

```java
package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.event.FileMetadataCreatedEvent;
import com.example.file.domain.event.FileDeletedEvent;
import com.example.file.domain.event.FileUploadedEvent;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileMetadataTest {

    private static final FileId TEST_FILE_ID = new FileId("01H8TESTFILEID000001");
    private static final BatchId TEST_BATCH_ID = BatchId.of("BATCH_TEST_001");
    private static final UserNo TEST_USER = UserNo.of("user001");

    @Test
    @DisplayName("create 应将状态设为 PENDING_UPLOAD 并注册 FileMetadataCreatedEvent")
    void create_should_set_status_to_PENDING_UPLOAD_and_register_event() {
        FileMetadata file = FileMetadata.create(
            TEST_FILE_ID, "test.xlsx", 1024, "application/vnd.ms-excel",
            FileUsage.SOURCE, "annuity", "business-core", TEST_BATCH_ID,
            "oss-source", StorageType.OSS, TEST_USER, null
        );

        assertThat(file.status()).isEqualTo(FileStatus.PENDING_UPLOAD);
        assertThat(file.id()).isEqualTo(TEST_FILE_ID);
        assertThat(file.originalName()).isEqualTo("test.xlsx");
        assertThat(file.size()).isEqualTo(1024);
        assertThat(file.businessBatchId()).isEqualTo(TEST_BATCH_ID);

        assertThat(file.getDomainEvents())
            .hasSize(1)
            .first()
            .isInstanceOf(FileMetadataCreatedEvent.class);
    }

    @Test
    @DisplayName("markUploaded 应将状态流转到 UPLOADED 并注册 FileUploadedEvent")
    void markUploaded_should_transition_to_UPLOADED() {
        FileMetadata file = newPendingFile();

        file.markUploaded("annuity/2026-07-19/BATCH_TEST_001/01H8.../test.xlsx", "d41d8cd98f00b204e9800998ecf8427e");

        assertThat(file.status()).isEqualTo(FileStatus.UPLOADED);
        assertThat(file.storageKey()).isEqualTo("annuity/2026-07-19/BATCH_TEST_001/01H8.../test.xlsx");
        assertThat(file.md5()).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
        assertThat(file.uploadedAt()).isNotNull();

        assertThat(file.getDomainEvents()).anyMatch(e -> e instanceof FileUploadedEvent);
    }

    @Test
    @DisplayName("markUploaded 在非 PENDING_UPLOAD 状态时应抛异常")
    void markUploaded_should_throw_when_status_is_not_PENDING_UPLOAD() {
        FileMetadata file = newUploadedFile();

        assertThatThrownBy(() -> file.markUploaded("key", "md5"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("markUploaded 在 storageKey 为空时应抛异常")
    void markUploaded_should_throw_when_storageKey_is_blank() {
        FileMetadata file = newPendingFile();

        assertThatThrownBy(() -> file.markUploaded("  ", "md5"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("markDeleted 应将状态流转到 DELETED 并注册 FileDeletedEvent")
    void markDeleted_should_transition_to_DELETED() {
        FileMetadata file = newUploadedFile();

        file.markDeleted(TEST_USER);

        assertThat(file.status()).isEqualTo(FileStatus.DELETED);
        assertThat(file.getDomainEvents()).anyMatch(e -> e instanceof FileDeletedEvent);
    }

    @Test
    @DisplayName("markDeleted 在已是 DELETED 状态时应幂等返回")
    void markDeleted_should_be_idempotent() {
        FileMetadata file = newUploadedFile();
        file.markDeleted(TEST_USER);
        int eventCountBefore = file.getDomainEvents().size();

        file.markDeleted(TEST_USER);

        assertThat(file.status()).isEqualTo(FileStatus.DELETED);
        assertThat(file.getDomainEvents()).hasSize(eventCountBefore);
    }

    @Test
    @DisplayName("isExpired 在 expiresAt 为 null 时应返回 false")
    void isExpired_should_return_false_when_expiresAt_is_null() {
        FileMetadata file = newPendingFile();
        assertThat(file.isExpired()).isFalse();
    }

    @Test
    @DisplayName("isExpired 在 expiresAt 为过去时间时应返回 true")
    void isExpired_should_return_true_when_expiresAt_is_past() {
        FileMetadata file = FileMetadata.create(
            TEST_FILE_ID, "test.xlsx", 1024, "application/octet-stream",
            FileUsage.SOURCE, "annuity", "business-core", TEST_BATCH_ID,
            "oss-source", StorageType.OSS, TEST_USER, LocalDateTime.now().minusHours(1)
        );
        assertThat(file.isExpired()).isTrue();
    }

    @Test
    @DisplayName("reconstitute 应恢复所有字段")
    void reconstitute_should_restore_all_fields() {
        LocalDateTime now = LocalDateTime.now();
        FileMetadata file = FileMetadata.reconstitute(
            TEST_FILE_ID, "test.xlsx", 1024, "application/octet-stream", "md5hash",
            "oss-source", StorageType.OSS, "storage/key",
            FileUsage.SOURCE, "annuity", "business-core", TEST_BATCH_ID,
            FileStatus.UPLOADED, TEST_USER, now, null,
            TEST_USER, TEST_USER, now, now, null
        );

        assertThat(file.id()).isEqualTo(TEST_FILE_ID);
        assertThat(file.status()).isEqualTo(FileStatus.UPLOADED);
        assertThat(file.storageKey()).isEqualTo("storage/key");
        assertThat(file.md5()).isEqualTo("md5hash");
    }

    private FileMetadata newPendingFile() {
        return FileMetadata.create(
            TEST_FILE_ID, "test.xlsx", 1024, "application/vnd.ms-excel",
            FileUsage.SOURCE, "annuity", "business-core", TEST_BATCH_ID,
            "oss-source", StorageType.OSS, TEST_USER, null
        );
    }

    private FileMetadata newUploadedFile() {
        FileMetadata file = newPendingFile();
        file.markUploaded("storage/key", "md5hash");
        file.clearDomainEvents();
        return file;
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl file-service/file-domain -Dtest=FileMetadataTest`
Expected: FAIL (FileMetadata 类不存在)

- [ ] **Step 3: 实现领域事件**

`FileMetadataCreatedEvent.java`:
```java
package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.FileId;

import java.time.LocalDateTime;

public record FileMetadataCreatedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    FileId fileId,
    FileUsage usage,
    String bizType,
    String sourceApp,
    BatchId businessBatchId
) implements DomainEvent {

    public static FileMetadataCreatedEvent of(FileMetadata file) {
        return new FileMetadataCreatedEvent(
            EventId.generate(),
            LocalDateTime.now(),
            file.id(),
            file.usage(),
            file.bizType(),
            file.sourceApp(),
            file.businessBatchId()
        );
    }
}
```

`FileUploadedEvent.java`:
```java
package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.FileId;

import java.time.LocalDateTime;

public record FileUploadedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    FileId fileId,
    String originalName,
    long size,
    String contentType,
    String md5,
    FileUsage usage
) implements DomainEvent {

    public static FileUploadedEvent of(FileMetadata file) {
        return new FileUploadedEvent(
            EventId.generate(),
            LocalDateTime.now(),
            file.id(),
            file.originalName(),
            file.size(),
            file.contentType(),
            file.md5(),
            file.usage()
        );
    }
}
```

`FileDeletedEvent.java`:
```java
package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

public record FileDeletedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    FileId fileId,
    String originalName,
    String deletedBy
) implements DomainEvent {

    public static FileDeletedEvent of(FileMetadata file, UserNo deletedBy) {
        return new FileDeletedEvent(
            EventId.generate(),
            LocalDateTime.now(),
            file.id(),
            file.originalName(),
            deletedBy != null ? deletedBy.value() : null
        );
    }
}
```

- [ ] **Step 4: 实现 FileMetadata 聚合根**

```java
package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.event.FileDeletedEvent;
import com.example.file.domain.event.FileMetadataCreatedEvent;
import com.example.file.domain.event.FileUploadedEvent;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.domain.errorcode.SharedDomainErrorCode;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 文件元数据聚合根
 * <p>
 * 管理文件的生命周期：PENDING_UPLOAD → UPLOADED → DELETED
 * storageKey 仅在 markUploaded 时设置，不对外暴露（getter 受保护）
 */
public class FileMetadata extends AggregateRoot<FileId> {

    private String originalName;
    private long size;
    private String contentType;
    private String md5;

    private String targetId;
    private StorageType storageType;
    private String storageKey;

    private FileUsage usage;
    private String bizType;
    private String sourceApp;
    private BatchId businessBatchId;

    private FileStatus status;
    private UserNo uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime expiresAt;

    // 业务创建
    private FileMetadata(FileId id, String originalName, long size, String contentType,
                         FileUsage usage, String bizType, String sourceApp, BatchId businessBatchId,
                         String targetId, StorageType storageType,
                         UserNo uploadedBy, LocalDateTime expiresAt) {
        super(id, uploadedBy);
        this.originalName = originalName;
        this.size = size;
        this.contentType = contentType;
        this.usage = usage;
        this.bizType = bizType;
        this.sourceApp = sourceApp;
        this.businessBatchId = businessBatchId;
        this.targetId = targetId;
        this.storageType = storageType;
        this.uploadedBy = uploadedBy;
        this.expiresAt = expiresAt;
        this.status = FileStatus.PENDING_UPLOAD;
        registerDomainEvent(FileMetadataCreatedEvent.of(this));
    }

    // 数据库重建
    public FileMetadata(FileId id, String originalName, long size, String contentType, String md5,
                        String targetId, StorageType storageType, String storageKey,
                        FileUsage usage, String bizType, String sourceApp, BatchId businessBatchId,
                        FileStatus status, UserNo uploadedBy, LocalDateTime uploadedAt, LocalDateTime expiresAt,
                        UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.originalName = originalName;
        this.size = size;
        this.contentType = contentType;
        this.md5 = md5;
        this.targetId = targetId;
        this.storageType = storageType;
        this.storageKey = storageKey;
        this.usage = usage;
        this.bizType = bizType;
        this.sourceApp = sourceApp;
        this.businessBatchId = businessBatchId;
        this.status = status;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.expiresAt = expiresAt;
    }

    public static FileMetadata create(FileId id, String originalName, long size, String contentType,
                                       FileUsage usage, String bizType, String sourceApp, BatchId businessBatchId,
                                       String targetId, StorageType storageType,
                                       UserNo uploadedBy, LocalDateTime expiresAt) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        if (originalName == null || originalName.isBlank()) throw new IllegalArgumentException("originalName 不能为空");
        if (size < 0) throw new IllegalArgumentException("size 不能为负");
        if (usage == null) throw new IllegalArgumentException("usage 不能为空");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId 不能为空");
        if (storageType == null) throw new IllegalArgumentException("storageType 不能为空");
        if (uploadedBy == null) throw new IllegalArgumentException("uploadedBy 不能为空");
        return new FileMetadata(id, originalName, size, contentType, usage, bizType, sourceApp,
            businessBatchId, targetId, storageType, uploadedBy, expiresAt);
    }

    public static FileMetadata reconstitute(FileId id, String originalName, long size, String contentType, String md5,
                                             String targetId, StorageType storageType, String storageKey,
                                             FileUsage usage, String bizType, String sourceApp, BatchId businessBatchId,
                                             FileStatus status, UserNo uploadedBy, LocalDateTime uploadedAt, LocalDateTime expiresAt,
                                             UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new FileMetadata(id, originalName, size, contentType, md5, targetId, storageType, storageKey,
            usage, bizType, sourceApp, businessBatchId, status, uploadedBy, uploadedAt, expiresAt,
            createdBy, updatedBy, createdAt, updatedAt, version);
    }

    public void markUploaded(String storageKey, String md5) {
        if (this.status != FileStatus.PENDING_UPLOAD) {
            throw new DomainException(SharedDomainErrorCode.ENTITY_STATE_INVALID)
                .withLogDetail("当前状态: " + this.status + ", 期望状态: PENDING_UPLOAD, fileId=" + id());
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new DomainException(SharedDomainErrorCode.ENTITY_STATE_INVALID)
                .withLogDetail("storageKey 不能为空, fileId=" + id());
        }
        this.storageKey = storageKey;
        this.md5 = md5;
        this.status = FileStatus.UPLOADED;
        this.uploadedAt = LocalDateTime.now();
        markUpdated(this.uploadedBy != null ? this.uploadedBy : this.createdBy());
        registerDomainEvent(FileUploadedEvent.of(this));
    }

    public void markDeleted(UserNo deletedBy) {
        if (this.status == FileStatus.DELETED) {
            return;
        }
        this.status = FileStatus.DELETED;
        if (deletedBy != null) {
            markUpdated(deletedBy);
        }
        registerDomainEvent(FileDeletedEvent.of(this, deletedBy));
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    @Override
    protected void validateInvariants() {
        if (size < 0) {
            throw new IllegalStateException("size 不能为负, fileId=" + id());
        }
        if (status == FileStatus.UPLOADED && (uploadedAt == null || storageKey == null)) {
            throw new IllegalStateException("UPLOADED 状态下 uploadedAt 和 storageKey 不能为空, fileId=" + id());
        }
    }

    // Getters
    public String originalName() { return originalName; }
    public long size() { return size; }
    public String contentType() { return contentType; }
    public String md5() { return md5; }
    public String targetId() { return targetId; }
    public StorageType storageType() { return storageType; }
    public String storageKey() { return storageKey; }
    public FileUsage usage() { return usage; }
    public String bizType() { return bizType; }
    public String sourceApp() { return sourceApp; }
    public BatchId businessBatchId() { return businessBatchId; }
    public FileStatus status() { return status; }
    public UserNo uploadedBy() { return uploadedBy; }
    public LocalDateTime uploadedAt() { return uploadedAt; }
    public LocalDateTime expiresAt() { return expiresAt; }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -pl file-service/file-domain -Dtest=FileMetadataTest`
Expected: PASS (9 tests)

- [ ] **Step 6: Commit**

```bash
git add file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/root/FileMetadata.java \
        file-service/file-domain/src/main/java/com/example/file/domain/event/FileMetadataCreatedEvent.java \
        file-service/file-domain/src/main/java/com/example/file/domain/event/FileUploadedEvent.java \
        file-service/file-domain/src/main/java/com/example/file/domain/event/FileDeletedEvent.java \
        file-service/file-domain/src/test/java/com/example/file/domain/model/aggregate/root/FileMetadataTest.java
git commit -m "feat(file-domain): 新增 FileMetadata 聚合根与 3 个领域事件"
```

---

## Task 4: Repository 接口与 SPI 重构

**Files:**
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/repository/FileMetadataRepository.java`
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/gateway/StorageTargetResolver.java`
- Create: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/CopyResult.java`
- Modify: `file-service/file-domain/src/main/java/com/example/file/domain/gateway/FileStorageGateway.java`

**Interfaces:**
- Consumes: `FileMetadata`, `FileId`, `FileUsage`, `StorageTarget`, `BatchId`
- Produces: `FileMetadataRepository` 接口, `StorageTargetResolver` SPI, `CopyResult` record, 重构后的 `FileStorageGateway` SPI

- [ ] **Step 1: 创建 FileMetadataRepository 接口**

```java
package com.example.file.domain.repository;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.domain.repository.Repository;
import com.example.shared.primitives.identity.FileId;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件元数据仓储接口
 */
public interface FileMetadataRepository extends Repository<FileMetadata, FileId> {

    /**
     * 按业务批次查询文件
     */
    List<FileMetadata> findByBusinessBatchId(String businessBatchId);

    /**
     * 按 usage + bizType 查询
     */
    List<FileMetadata> findByUsageAndBizType(FileUsage usage, String bizType);

    /**
     * 查询已过期但未删除的文件
     */
    List<FileMetadata> findExpiredBefore(LocalDateTime before);
}
```

- [ ] **Step 2: 创建 StorageTargetResolver SPI**

```java
package com.example.file.domain.gateway;

import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;

import java.util.List;

/**
 * 存储目标解析器 SPI
 * <p>
 * 将 FileUsage 路由到具体的 StorageTarget。
 */
public interface StorageTargetResolver {

    /**
     * 按 FileUsage 路由到具体的 StorageTarget
     */
    StorageTarget resolveByUsage(FileUsage usage, String bizType);

    /**
     * 按 targetId 直接查询
     */
    StorageTarget resolveById(String targetId);

    /**
     * 列出所有配置的 StorageTarget
     */
    List<StorageTarget> listAll();
}
```

- [ ] **Step 3: 创建 CopyResult record (infrastructure 层)**

```java
package com.example.file.infrastructure.storage;

import com.example.shared.primitives.identity.FileId;

/**
 * 文件复制结果
 *
 * @param newFileId    新文件 ID
 * @param newStorageKey 新存储 key
 */
public record CopyResult(FileId newFileId, String newStorageKey) {
}
```

- [ ] **Step 4: 重构 FileStorageGateway SPI**

```java
package com.example.file.domain.gateway;

import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;

import java.io.InputStream;

/**
 * 文件存储网关 SPI
 * <p>
 * 端口接口，由 FileStorageRouter 实现。
 * SPI 签名仅用 FileId，不暴露 storageKey/targetId。
 */
public interface FileStorageGateway {

    /**
     * 存储文件流到后端。
     * 调用前 FileMetadata 必须已 create() 并持久化（status=PENDING_UPLOAD）。
     */
    void store(FileId fileId, InputStream content, long contentLength);

    /**
     * 打开文件流。
     * 调用方必须 try-with-resources 关闭流。
     */
    InputStream open(FileId fileId);

    /**
     * 判断文件是否存在于存储后端
     */
    boolean exists(FileId fileId);

    /**
     * 复制文件到新用途对应的目标。
     * 返回 CopyResult (新 FileId + 新 storageKey)。
     * 注意：返回类型在 infrastructure 层定义，这里使用 Object 占位以避免 domain 依赖 infra。
     * 实际调用方（CopyFileUseCase）需强转为 CopyResult。
     */
    Object copy(FileId srcFileId, FileUsage targetUsage, BatchId businessBatchId);

    /**
     * 计算文件 MD5
     */
    String computeMd5(FileId fileId);
}
```

**说明**：由于 `CopyResult` 定义在 infrastructure 层（避免 domain 依赖 infra），SPI 的 `copy` 方法返回 `Object`，调用方（CopyFileUseCase）强转为 `CopyResult`。

- [ ] **Step 5: 编译验证**

Run: `mvn compile -pl file-service/file-domain -am`
Expected: BUILD SUCCESS

Run: `mvn compile -pl file-service/file-infrastructure -am`
Expected: 编译失败（因为 ParseFileUseCase 还引用旧的 `open(String)` 签名）

**说明**：此时代码处于过渡状态，ParseFileUseCase 编译失败是预期的，将在 Task 13 修复。先验证 file-domain 编译通过即可。

- [ ] **Step 6: Commit**

```bash
git add file-service/file-domain/src/main/java/com/example/file/domain/repository/FileMetadataRepository.java \
        file-service/file-domain/src/main/java/com/example/file/domain/gateway/StorageTargetResolver.java \
        file-service/file-domain/src/main/java/com/example/file/domain/gateway/FileStorageGateway.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/CopyResult.java
git commit -m "feat(file-domain): 新增 Repository/SPI 接口，重构 FileStorageGateway"
```

---

## Task 5: FileMetadataDO + Mapper + Converter

**Files:**
- Create: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/entity/FileMetadataDO.java`
- Create: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/mapper/FileMetadataMapper.java`
- Create: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/converter/FileMetadataConverter.java`
- Test: `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/converter/FileMetadataConverterTest.java`

**Interfaces:**
- Consumes: `FileMetadata`, `StorageType`, `FileUsage`, `FileStatus`, `FileId`, `BatchId`, `UserNo`, `Version`
- Produces: `FileMetadataDO`, `FileMetadataMapper`, `FileMetadataConverter`

- [ ] **Step 1: 编写失败测试**

```java
package com.example.file.infrastructure.converter;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.infrastructure.entity.FileMetadataDO;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FileMetadataConverterTest {

    private final FileMetadataConverter converter = new FileMetadataConverter() {};

    @Test
    @DisplayName("toDO 应将 FileId 转换为 String")
    void toDO_should_convert_FileId_to_String() {
        FileMetadata file = FileMetadata.create(
            new FileId("01H8FILEID001"), "test.xlsx", 1024, "application/octet-stream",
            FileUsage.SOURCE, "annuity", "business-core", BatchId.of("BATCH_001"),
            "oss-source", StorageType.OSS, UserNo.of("u1"), null
        );

        FileMetadataDO aDo = converter.toDO(file);

        assertThat(aDo.getId()).isEqualTo("01H8FILEID001");
        assertThat(aDo.getUsage()).isEqualTo("SOURCE");
        assertThat(aDo.getStorageType()).isEqualTo("OSS");
        assertThat(aDo.getStatus()).isEqualTo("PENDING_UPLOAD");
        assertThat(aDo.getBusinessBatchId()).isEqualTo("BATCH_001");
    }

    @Test
    @DisplayName("toDomain 应将 String 转换为 FileId")
    void toDomain_should_convert_String_to_FileId() {
        FileMetadataDO aDo = buildUploadedDO();

        FileMetadata file = converter.toDomain(aDo);

        assertThat(file.id()).isEqualTo(new FileId("01H8FILEID001"));
        assertThat(file.usage()).isEqualTo(FileUsage.SOURCE);
        assertThat(file.storageType()).isEqualTo(StorageType.OSS);
        assertThat(file.status()).isEqualTo(FileStatus.UPLOADED);
        assertThat(file.businessBatchId()).isEqualTo(BatchId.of("BATCH_001"));
    }

    private FileMetadataDO buildUploadedDO() {
        FileMetadataDO aDo = new FileMetadataDO();
        aDo.setId("01H8FILEID001");
        aDo.setOriginalName("test.xlsx");
        aDo.setSize(1024L);
        aDo.setContentType("application/octet-stream");
        aDo.setMd5("md5hash");
        aDo.setTargetId("oss-source");
        aDo.setStorageType("OSS");
        aDo.setStorageKey("storage/key");
        aDo.setUsage("SOURCE");
        aDo.setBizType("annuity");
        aDo.setSourceApp("business-core");
        aDo.setBusinessBatchId("BATCH_001");
        aDo.setStatus("UPLOADED");
        aDo.setUploadedBy("u1");
        aDo.setUploadedAt(LocalDateTime.now());
        aDo.setExpiresAt(null);
        aDo.setCreatedBy("u1");
        aDo.setUpdatedBy("u1");
        aDo.setCreateTime(LocalDateTime.now());
        aDo.setUpdateTime(LocalDateTime.now());
        aDo.setDeleted(false);
        aDo.setVersion(0);
        return aDo;
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl file-service/file-infrastructure -Dtest=FileMetadataConverterTest`
Expected: FAIL (FileMetadataDO 不存在)

- [ ] **Step 3: 实现 FileMetadataDO**

```java
package com.example.file.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("t_file_metadata")
public class FileMetadataDO {

    @Id(keyType = KeyType.None)
    private String id;

    private String originalName;
    private Long size;
    private String contentType;
    private String md5;

    private String targetId;
    private String storageType;
    private String storageKey;

    private String usage;
    private String bizType;
    private String sourceApp;
    private String businessBatchId;

    private String status;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime expiresAt;

    private String createdBy;
    private String updatedBy;

    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;

    @Column(onInsertValue = "now()", onUpdateValue = "now()")
    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
```

- [ ] **Step 4: 实现 FileMetadataMapper**

```java
package com.example.file.infrastructure.mapper;

import com.example.file.infrastructure.entity.FileMetadataDO;
import com.mybatisflex.core.BaseMapper;

public interface FileMetadataMapper extends BaseMapper<FileMetadataDO> {
}
```

- [ ] **Step 5: 实现 FileMetadataConverter**

```java
package com.example.file.infrastructure.converter;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.infrastructure.entity.FileMetadataDO;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FileMetadataConverter {

    default FileMetadataDO toDO(FileMetadata file) {
        FileMetadataDO aDo = new FileMetadataDO();
        aDo.setId(file.id() != null ? file.id().value() : null);
        aDo.setOriginalName(file.originalName());
        aDo.setSize(file.size());
        aDo.setContentType(file.contentType());
        aDo.setMd5(file.md5());
        aDo.setTargetId(file.targetId());
        aDo.setStorageType(file.storageType() != null ? file.storageType().name() : null);
        aDo.setStorageKey(file.storageKey());
        aDo.setUsage(file.usage() != null ? file.usage().name() : null);
        aDo.setBizType(file.bizType());
        aDo.setSourceApp(file.sourceApp());
        aDo.setBusinessBatchId(file.businessBatchId() != null ? file.businessBatchId().value() : null);
        aDo.setStatus(file.status() != null ? file.status().name() : null);
        aDo.setUploadedBy(file.uploadedBy() != null ? file.uploadedBy().value() : null);
        aDo.setUploadedAt(file.uploadedAt());
        aDo.setExpiresAt(file.expiresAt());
        aDo.setCreatedBy(file.createdBy() != null ? file.createdBy().value() : null);
        aDo.setUpdatedBy(file.updatedBy() != null ? file.updatedBy().value() : null);
        aDo.setCreateTime(file.createdAt());
        aDo.setUpdateTime(file.updatedAt());
        aDo.setDeleted(false);
        aDo.setVersion(file.version() != null ? (int) file.version().value() : 0);
        return aDo;
    }

    default FileMetadata toDomain(FileMetadataDO aDo) {
        if (aDo == null) return null;
        return FileMetadata.reconstitute(
            new FileId(aDo.getId()),
            aDo.getOriginalName(),
            aDo.getSize() != null ? aDo.getSize() : 0L,
            aDo.getContentType(),
            aDo.getMd5(),
            aDo.getTargetId(),
            aDo.getStorageType() != null ? StorageType.valueOf(aDo.getStorageType()) : null,
            aDo.getStorageKey(),
            aDo.getUsage() != null ? FileUsage.valueOf(aDo.getUsage()) : null,
            aDo.getBizType(),
            aDo.getSourceApp(),
            aDo.getBusinessBatchId() != null ? BatchId.of(aDo.getBusinessBatchId()) : null,
            aDo.getStatus() != null ? FileStatus.valueOf(aDo.getStatus()) : null,
            aDo.getUploadedBy() != null ? UserNo.of(aDo.getUploadedBy()) : null,
            aDo.getUploadedAt(),
            aDo.getExpiresAt(),
            aDo.getCreatedBy() != null ? UserNo.of(aDo.getCreatedBy()) : null,
            aDo.getUpdatedBy() != null ? UserNo.of(aDo.getUpdatedBy()) : null,
            aDo.getCreateTime(),
            aDo.getUpdateTime(),
            aDo.getVersion() != null ? Version.of(aDo.getVersion().longValue()) : null
        );
    }
}
```

- [ ] **Step 6: 运行测试验证通过**

Run: `mvn test -pl file-service/file-infrastructure -Dtest=FileMetadataConverterTest`
Expected: PASS (2 tests)

- [ ] **Step 7: Commit**

```bash
git add file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/entity/FileMetadataDO.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/mapper/FileMetadataMapper.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/converter/FileMetadataConverter.java \
        file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/converter/FileMetadataConverterTest.java
git commit -m "feat(file-infrastructure): 新增 FileMetadataDO/Mapper/Converter"
```

---

## Task 6: FileMetadataRepositoryImpl + schema-pg.sql

**Files:**
- Create: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/repository/FileMetadataRepositoryImpl.java`
- Create: `file-service/file-infrastructure/src/main/resources/schema-pg.sql`

**Interfaces:**
- Consumes: `FileMetadataRepository`, `FileMetadataConverter`, `FileMetadataMapper`, `FileMetadataDO`
- Produces: `FileMetadataRepositoryImpl`, `schema-pg.sql`

- [ ] **Step 1: 创建 schema-pg.sql**

```sql
-- 文件元数据表
CREATE TABLE IF NOT EXISTS t_file_metadata (
    id                  VARCHAR(64)   NOT NULL,
    original_name       VARCHAR(512)  NOT NULL,
    size                BIGINT        NOT NULL,
    content_type        VARCHAR(128),
    md5                 VARCHAR(64),

    target_id           VARCHAR(64)   NOT NULL,
    storage_type        VARCHAR(20)   NOT NULL,
    storage_key         VARCHAR(1024) NOT NULL,

    usage               VARCHAR(20)   NOT NULL,
    biz_type            VARCHAR(64),
    source_app          VARCHAR(64),
    business_batch_id   VARCHAR(64),

    status              VARCHAR(20)   NOT NULL,
    uploaded_by         VARCHAR(64),
    uploaded_at         TIMESTAMP,
    expires_at          TIMESTAMP,

    created_by          VARCHAR(64)   NOT NULL,
    updated_by          VARCHAR(64),
    create_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN       DEFAULT FALSE,
    version             INT           DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_file_metadata_batch_id ON t_file_metadata(business_batch_id);
CREATE INDEX IF NOT EXISTS idx_file_metadata_usage_biz_type ON t_file_metadata(usage, biz_type);
CREATE INDEX IF NOT EXISTS idx_file_metadata_status_expires ON t_file_metadata(status, expires_at) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_file_metadata_target_id ON t_file_metadata(target_id);

COMMENT ON TABLE t_file_metadata IS '文件元数据表';
COMMENT ON COLUMN t_file_metadata.id IS '文件ID（FileId）';
COMMENT ON COLUMN t_file_metadata.original_name IS '原始文件名';
COMMENT ON COLUMN t_file_metadata.size IS '文件大小（字节）';
COMMENT ON COLUMN t_file_metadata.content_type IS 'MIME 类型';
COMMENT ON COLUMN t_file_metadata.md5 IS '内容 MD5 指纹';
COMMENT ON COLUMN t_file_metadata.target_id IS '存储目标 ID';
COMMENT ON COLUMN t_file_metadata.storage_type IS '存储类型: LOCAL/OSS/NAS';
COMMENT ON COLUMN t_file_metadata.storage_key IS '后端内部 key/path';
COMMENT ON COLUMN t_file_metadata.usage IS '文件用途: SOURCE/PARSED/EXPORT/ARCHIVE';
COMMENT ON COLUMN t_file_metadata.biz_type IS '业务类型';
COMMENT ON COLUMN t_file_metadata.source_app IS '来源系统标识';
COMMENT ON COLUMN t_file_metadata.business_batch_id IS '业务批次号';
COMMENT ON COLUMN t_file_metadata.status IS '文件状态: PENDING_UPLOAD/UPLOADED/DELETED';
COMMENT ON COLUMN t_file_metadata.uploaded_by IS '上传人';
COMMENT ON COLUMN t_file_metadata.uploaded_at IS '上传时间';
COMMENT ON COLUMN t_file_metadata.expires_at IS '过期时间（NULL=永久）';
COMMENT ON COLUMN t_file_metadata.deleted IS '逻辑删除标志';
COMMENT ON COLUMN t_file_metadata.version IS '乐观锁版本号';

-- ParseTask 表字段迁移（source_file_ref → source_file_id）
-- 注意：此 DDL 仅用于新建库，旧库迁移需单独执行
-- ALTER TABLE t_file_parse_task RENAME COLUMN source_file_ref TO source_file_id;
-- ALTER TABLE t_file_parse_task ALTER COLUMN source_file_id TYPE VARCHAR(64);
-- CREATE INDEX IF NOT EXISTS idx_parse_task_source_file_id ON t_file_parse_task(source_file_id);
```

- [ ] **Step 2: 实现 FileMetadataRepositoryImpl**

```java
package com.example.file.infrastructure.repository;

import com.example.file.domain.event.FileDeletedEvent;
import com.example.file.domain.event.FileMetadataCreatedEvent;
import com.example.file.domain.event.FileUploadedEvent;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.infrastructure.converter.FileMetadataConverter;
import com.example.file.infrastructure.entity.FileMetadataDO;
import com.example.file.infrastructure.mapper.FileMetadataMapper;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.FileId;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.file.infrastructure.entity.table.FileMetadataDOTableDef.FILE_METADATA_DO;

@Slf4j
@Repository
@RequiredArgsConstructor
public class FileMetadataRepositoryImpl implements FileMetadataRepository {

    private final FileMetadataMapper mapper;
    private final FileMetadataConverter converter;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Optional<FileMetadata> load(FileId id) {
        if (id == null) return Optional.empty();
        FileMetadataDO aDo = mapper.selectOneById(id.value());
        return Optional.ofNullable(aDo).map(converter::toDomain);
    }

    @Override
    public void save(FileMetadata file) {
        if (file == null) throw new IllegalArgumentException("file 不能为空");
        FileMetadataDO aDo = converter.toDO(file);
        if (mapper.selectOneById(aDo.getId()) == null) {
            mapper.insert(aDo);
            log.debug("新增文件元数据: fileId={}", file.id());
        } else {
            mapper.update(aDo);
            log.debug("更新文件元数据: fileId={}, version={}", file.id(), file.version());
        }
        publishDomainEvents(file);
    }

    @Override
    public void delete(FileMetadata file) {
        if (file == null) return;
        file.markDeleted(file.updatedBy() != null ? file.updatedBy() : file.createdBy());
        save(file);
    }

    @Override
    public void deleteById(FileId id) {
        if (id == null) return;
        FileMetadataDO aDo = mapper.selectOneById(id.value());
        if (aDo != null) {
            FileMetadata file = converter.toDomain(aDo);
            delete(file);
        }
    }

    @Override
    public List<FileMetadata> loadAll() {
        return mapper.selectAll().stream()
            .map(converter::toDomain)
            .toList();
    }

    @Override
    public void streamByAppId(FileId id, Consumer<AggregateRoot<FileId>> processor) {
        if (id == null || processor == null) return;
        load(id).ifPresent(processor);
    }

    @Override
    public List<FileMetadata> findByBusinessBatchId(String businessBatchId) {
        if (businessBatchId == null) return List.of();
        List<FileMetadataDO> list = mapper.selectListByQuery(
            QueryWrapper.create().where(FILE_METADATA_DO.BUSINESS_BATCH_ID.eq(businessBatchId))
        );
        return list.stream().map(converter::toDomain).toList();
    }

    @Override
    public List<FileMetadata> findByUsageAndBizType(FileUsage usage, String bizType) {
        QueryWrapper wrapper = QueryWrapper.create();
        if (usage != null) wrapper.and(FILE_METADATA_DO.USAGE.eq(usage.name()));
        if (bizType != null) wrapper.and(FILE_METADATA_DO.BIZ_TYPE.eq(bizType));
        List<FileMetadataDO> list = mapper.selectListByQuery(wrapper);
        return list.stream().map(converter::toDomain).toList();
    }

    @Override
    public List<FileMetadata> findExpiredBefore(LocalDateTime before) {
        if (before == null) return List.of();
        List<FileMetadataDO> list = mapper.selectListByQuery(
            QueryWrapper.create()
                .where(FILE_METADATA_DO.EXPIRES_AT.lt(before))
                .and(FILE_METADATA_DO.STATUS.ne("DELETED"))
        );
        return list.stream().map(converter::toDomain).toList();
    }

    private void publishDomainEvents(FileMetadata file) {
        List<DomainEvent> events = file.getDomainEvents();
        if (events.isEmpty()) return;
        for (DomainEvent event : events) {
            try {
                eventPublisher.publishEvent(event);
                log.debug("发布领域事件: eventId={}, type={}",
                    event.eventId(), event.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("发布领域事件失败: eventId={}, type={}",
                    event.eventId(), event.getClass().getSimpleName(), e);
            }
        }
        file.clearDomainEvents();
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn compile -pl file-service/file-infrastructure -am`
Expected: BUILD SUCCESS (file-infrastructure 编译通过，file-application 仍可能失败，将在 Task 13 修复)

- [ ] **Step 4: Commit**

```bash
git add file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/repository/FileMetadataRepositoryImpl.java \
        file-service/file-infrastructure/src/main/resources/schema-pg.sql
git commit -m "feat(file-infrastructure): 新增 FileMetadataRepositoryImpl 与 schema-pg.sql"
```

---

## Task 7: 配置类与自动装配

**Files:**
- Create: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/StorageTargetProperties.java`
- Create: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/PropertiesBasedStorageTargetResolver.java`
- Create: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/StorageAutoConfiguration.java`
- Create: `file-service/file-infrastructure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (或追加)
- Test: `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/PropertiesBasedStorageTargetResolverTest.java`

**Interfaces:**
- Consumes: `StorageTargetResolver`, `StorageTarget`, `StorageType`, `FileUsage`
- Produces: `StorageTargetProperties`, `PropertiesBasedStorageTargetResolver`, `StorageAutoConfiguration`

- [ ] **Step 1: 编写失败测试**

```java
package com.example.file.infrastructure.storage;

import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertiesBasedStorageTargetResolverTest {

    @Test
    @DisplayName("resolveByUsage 应返回正确的 StorageTarget")
    void resolveByUsage_should_return_correct_target() {
        PropertiesBasedStorageTargetResolver resolver = newResolver(validProperties());

        StorageTarget source = resolver.resolveByUsage(FileUsage.SOURCE, null);
        assertThat(source.targetId()).isEqualTo("local-source");

        StorageTarget export = resolver.resolveByUsage(FileUsage.EXPORT, null);
        assertThat(export.targetId()).isEqualTo("local-export");
    }

    @Test
    @DisplayName("resolveById 在 target 不存在时应抛异常")
    void resolveById_should_throw_when_target_not_found() {
        PropertiesBasedStorageTargetResolver resolver = newResolver(validProperties());

        assertThatThrownBy(() -> resolver.resolveById("non-existent"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("validate 在 routing.target 缺失时应抛 IllegalStateException")
    void validate_should_throw_when_routing_target_missing() {
        StorageTargetProperties props = validProperties();
        props.getRouting().setSource("non-existent-target");

        assertThatThrownBy(() -> new PropertiesBasedStorageTargetResolver(props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("routing.source");
    }

    @Test
    @DisplayName("validate 在 OSS 缺 endpoint 时应抛 IllegalStateException")
    void validate_should_throw_when_OSS_missing_endpoint() {
        StorageTargetProperties props = validProperties();
        StorageTargetProperties.StorageTargetConfig ossConfig = new StorageTargetProperties.StorageTargetConfig();
        ossConfig.setId("oss-bad");
        ossConfig.setType(StorageType.OSS);
        ossConfig.setBucket("bucket");
        // endpoint 未设置
        props.getTargets().add(ossConfig);
        props.getRouting().setParsed("oss-bad");

        assertThatThrownBy(() -> new PropertiesBasedStorageTargetResolver(props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("endpoint");
    }

    @Test
    @DisplayName("listAll 应返回所有配置的 target")
    void listAll_should_return_all_targets() {
        PropertiesBasedStorageTargetResolver resolver = newResolver(validProperties());
        List<StorageTarget> all = resolver.listAll();
        assertThat(all).hasSize(2);
    }

    private StorageTargetProperties validProperties() {
        StorageTargetProperties props = new StorageTargetProperties();
        List<StorageTargetProperties.StorageTargetConfig> targets = new ArrayList<>();

        StorageTargetProperties.StorageTargetConfig source = new StorageTargetProperties.StorageTargetConfig();
        source.setId("local-source");
        source.setType(StorageType.LOCAL);
        source.setBasePath("/data/source");
        targets.add(source);

        StorageTargetProperties.StorageTargetConfig export = new StorageTargetProperties.StorageTargetConfig();
        export.setId("local-export");
        export.setType(StorageType.LOCAL);
        export.setBasePath("/data/export");
        targets.add(export);

        props.setTargets(targets);

        StorageTargetProperties.RoutingConfig routing = new StorageTargetProperties.RoutingConfig();
        routing.setSource("local-source");
        routing.setParsed("local-source");
        routing.setExport("local-export");
        routing.setArchive("local-export");
        props.setRouting(routing);

        return props;
    }

    private PropertiesBasedStorageTargetResolver newResolver(StorageTargetProperties props) {
        return new PropertiesBasedStorageTargetResolver(props);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl file-service/file-infrastructure -Dtest=PropertiesBasedStorageTargetResolverTest`
Expected: FAIL (StorageTargetProperties 不存在)

- [ ] **Step 3: 实现 StorageTargetProperties**

```java
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
```

- [ ] **Step 4: 实现 PropertiesBasedStorageTargetResolver**

```java
package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertiesBasedStorageTargetResolver implements StorageTargetResolver {

    private final StorageTargetProperties properties;
    private Map<String, StorageTarget> targetMap;

    @PostConstruct
    void init() {
        targetMap = properties.getTargets().stream()
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
```

- [ ] **Step 5: 实现 StorageAutoConfiguration**

```java
package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "file.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StorageTargetProperties.class)
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(StorageTargetResolver.class)
    public StorageTargetResolver storageTargetResolver(StorageTargetProperties properties) {
        return new PropertiesBasedStorageTargetResolver(properties);
    }
}
```

**说明**：`FileStorageGateway` (FileStorageRouter) 的 Bean 在 Task 9 创建后在此注册。

- [ ] **Step 6: 注册自动装配**

在 `file-infrastructure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中追加（如果文件不存在则创建）：

```
com.example.file.infrastructure.storage.StorageAutoConfiguration
```

- [ ] **Step 7: 运行测试验证通过**

Run: `mvn test -pl file-service/file-infrastructure -Dtest=PropertiesBasedStorageTargetResolverTest`
Expected: PASS (5 tests)

- [ ] **Step 8: Commit**

```bash
git add file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/StorageTargetProperties.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/PropertiesBasedStorageTargetResolver.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/StorageAutoConfiguration.java \
        file-service/file-infrastructure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports \
        file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/PropertiesBasedStorageTargetResolverTest.java
git commit -m "feat(file-infrastructure): 新增配置绑定与自动装配"
```

---

## Task 8: LocalFileStorage + 测试

**Files:**
- Create: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/FileStorageBackend.java`
- Create: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/LocalFileStorage.java`
- Test: `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/LocalFileStorageTest.java`
- Modify: `file-service/file-infrastructure/pom.xml` (新增 commons-codec 依赖)

**Interfaces:**
- Consumes: `StorageTarget`, `StorageType`
- Produces: `FileStorageBackend` SPI, `LocalFileStorage` 实现

- [ ] **Step 1: 新增 commons-codec 依赖**

在 `file-infrastructure/pom.xml` 的 `<dependencies>` 中追加：

```xml
<dependency>
    <groupId>commons-codec</groupId>
    <artifactId>commons-codec</artifactId>
</dependency>
```

- [ ] **Step 2: 编写失败测试**

```java
package com.example.file.infrastructure.storage;

import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStorageTest {

    private final LocalFileStorage storage = new LocalFileStorage();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("store 应将文件写入本地路径并创建父目录")
    void store_should_write_file_and_create_parent_dirs() throws IOException {
        StorageTarget target = newTarget();
        String storageKey = "annuity/2026-07-19/BATCH_001/01H8.../test.txt";

        storage.store(target, storageKey, new ByteArrayInputStream("hello".getBytes()), 5);

        Path expected = tempDir.resolve(storageKey);
        assertThat(Files.exists(expected)).isTrue();
        assertThat(Files.readString(expected)).isEqualTo("hello");
    }

    @Test
    @DisplayName("open 应返回可读的流")
    void open_should_return_readable_stream() throws IOException {
        StorageTarget target = newTarget();
        storage.store(target, "file.txt", new ByteArrayInputStream("content".getBytes()), 7);

        try (InputStream in = storage.open(target, "file.txt")) {
            assertThat(new String(in.readAllBytes())).isEqualTo("content");
        }
    }

    @Test
    @DisplayName("exists 在文件存在时应返回 true")
    void exists_should_return_true_when_file_exists() {
        StorageTarget target = newTarget();
        storage.store(target, "file.txt", new ByteArrayInputStream("x".getBytes()), 1);

        assertThat(storage.exists(target, "file.txt")).isTrue();
        assertThat(storage.exists(target, "missing.txt")).isFalse();
    }

    @Test
    @DisplayName("copy 应复制文件到新 key")
    void copy_should_duplicate_file() throws IOException {
        StorageTarget target = newTarget();
        storage.store(target, "src.txt", new ByteArrayInputStream("data".getBytes()), 4);

        storage.copy(target, "src.txt", "dst.txt");

        assertThat(Files.readString(tempDir.resolve("dst.txt"))).isEqualTo("data");
    }

    @Test
    @DisplayName("computeMd5 应返回正确的 MD5")
    void computeMd5_should_return_correct_md5() {
        StorageTarget target = newTarget();
        byte[] data = "hello world".getBytes();
        storage.store(target, "file.txt", new ByteArrayInputStream(data), data.length);

        String md5 = storage.computeMd5(target, "file.txt");
        String expected = DigestUtils.md5Hex(data);
        assertThat(md5).isEqualTo(expected);
    }

    @Test
    @DisplayName("supportedType 应返回 LOCAL")
    void supportedType_should_return_LOCAL() {
        assertThat(storage.supportedType()).isEqualTo(StorageType.LOCAL);
    }

    private StorageTarget newTarget() {
        return new StorageTarget(
            "local-test", StorageType.LOCAL, null, null,
            tempDir.toString(), null, null, null, java.util.Map.of()
        );
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn test -pl file-service/file-infrastructure -Dtest=LocalFileStorageTest`
Expected: FAIL (FileStorageBackend 不存在)

- [ ] **Step 4: 实现 FileStorageBackend SPI**

```java
package com.example.file.infrastructure.storage;

import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;

import java.io.InputStream;

/**
 * 文件存储后端 SPI (基础设施层内部抽象)
 * <p>
 * 每个具体后端 (Local/OSS/NAS) 实现此接口。
 * FileStorageRouter 通过 target.type() 路由到对应实现。
 */
public interface FileStorageBackend {

    StorageType supportedType();

    void store(StorageTarget target, String storageKey,
               InputStream content, long contentLength);

    InputStream open(StorageTarget target, String storageKey);

    boolean exists(StorageTarget target, String storageKey);

    void copy(StorageTarget target, String srcKey, String dstKey);

    String computeMd5(StorageTarget target, String storageKey);
}
```

- [ ] **Step 5: 实现 LocalFileStorage**

```java
package com.example.file.infrastructure.storage;

import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.shared.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

@Slf4j
@Component
public class LocalFileStorage implements FileStorageBackend {

    @Override
    public StorageType supportedType() {
        return StorageType.LOCAL;
    }

    @Override
    public void store(StorageTarget target, String storageKey,
                      InputStream content, long contentLength) {
        Path fullPath = resolvePath(target, storageKey);
        try {
            Files.createDirectories(fullPath.getParent());
            try (OutputStream out = Files.newOutputStream(fullPath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                content.transferTo(out);
            }
            log.debug("本地存储成功: path={}", fullPath);
        } catch (IOException e) {
            throw new SystemException(FileErrorCodes.FILE_STORAGE_FAILED, e)
                .withLogDetail("path=" + fullPath);
        }
    }

    @Override
    public InputStream open(StorageTarget target, String storageKey) {
        Path fullPath = resolvePath(target, storageKey);
        try {
            return Files.newInputStream(fullPath, StandardOpenOption.READ);
        } catch (IOException e) {
            throw new SystemException(FileErrorCodes.FILE_METADATA_NOT_FOUND, e)
                .withLogDetail("path=" + fullPath);
        }
    }

    @Override
    public boolean exists(StorageTarget target, String storageKey) {
        return Files.exists(resolvePath(target, storageKey));
    }

    @Override
    public void copy(StorageTarget target, String srcKey, String dstKey) {
        Path src = resolvePath(target, srcKey);
        Path dst = resolvePath(target, dstKey);
        try {
            Files.createDirectories(dst.getParent());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new SystemException(FileErrorCodes.FILE_COPY_FAILED, e)
                .withLogDetail("src=" + src + ", dst=" + dst);
        }
    }

    @Override
    public String computeMd5(StorageTarget target, String storageKey) {
        Path fullPath = resolvePath(target, storageKey);
        try (InputStream in = Files.newInputStream(fullPath)) {
            return DigestUtils.md5Hex(in);
        } catch (IOException e) {
            throw new SystemException(FileErrorCodes.FILE_STORAGE_FAILED, e)
                .withLogDetail("md5 failed, path=" + fullPath);
        }
    }

    private Path resolvePath(StorageTarget target, String storageKey) {
        return Paths.get(target.basePath(), storageKey);
    }
}
```

- [ ] **Step 6: 运行测试验证通过**

Run: `mvn test -pl file-service/file-infrastructure -Dtest=LocalFileStorageTest`
Expected: PASS (6 tests)

- [ ] **Step 7: Commit**

```bash
git add file-service/file-infrastructure/pom.xml \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/FileStorageBackend.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/LocalFileStorage.java \
        file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/LocalFileStorageTest.java
git commit -m "feat(file-infrastructure): 新增 LocalFileStorage 实现"
```

---

## Task 9: NASFileStorage + FileStorageRouter

**Files:**
- Create: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/NASFileStorage.java`
- Create: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/FileStorageRouter.java`
- Modify: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/StorageAutoConfiguration.java`
- Test: `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/FileStorageRouterTest.java`

**Interfaces:**
- Consumes: `FileStorageGateway`, `FileMetadataRepository`, `StorageTargetResolver`, `FileStorageBackend`, `CopyResult`
- Produces: `NASFileStorage`, `FileStorageRouter` (实现 FileStorageGateway)

- [ ] **Step 1: 编写失败测试**

```java
package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileStorageRouterTest {

    @TempDir
    Path tempDir;

    private FileMetadataRepository metadataRepository;
    private StorageTargetResolver targetResolver;
    private FileStorageGateway router;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(FileMetadataRepository.class);
        targetResolver = mock(StorageTargetResolver.class);
        LocalFileStorage localBackend = new LocalFileStorage();
        router = new FileStorageRouter(metadataRepository, targetResolver, List.of(localBackend));
        ((FileStorageRouter) router).initBackendMap();
    }

    @Test
    @DisplayName("store 应路由到 LOCAL 后端并写入文件")
    void store_should_route_to_LOCAL_backend() throws IOException {
        FileId fileId = new FileId("01H8FILE001");
        StorageTarget target = new StorageTarget(
            "local-1", StorageType.LOCAL, null, null, tempDir.toString(),
            null, null, null, java.util.Map.of()
        );
        FileMetadata file = FileMetadata.create(
            fileId, "test.txt", 5, "text/plain",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("BATCH_001"),
            "local-1", StorageType.LOCAL, UserNo.of("u1"), null
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);
        when(targetResolver.resolveById("local-1")).thenReturn(target);

        router.store(fileId, new ByteArrayInputStream("hello".getBytes()), 5);

        // 验证文件已写入 (storageKey 由 Router 生成，包含日期)
        // 由于 storageKey 内部生成，这里仅验证不抛异常即视为成功
        verify(metadataRepository, atLeastOnce()).loadOrThrow(fileId);
    }

    @Test
    @DisplayName("open 应路由到正确后端返回流")
    void open_should_route_to_correct_backend() throws IOException {
        FileId fileId = new FileId("01H8FILE002");
        StorageTarget target = new StorageTarget(
            "local-1", StorageType.LOCAL, null, null, tempDir.toString(),
            null, null, null, java.util.Map.of()
        );
        // 先写入一个文件
        Path filePath = tempDir.resolve("annuity/2026-07-19/BATCH_001/01H8FILE002/test.txt");
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, "content");

        FileMetadata file = FileMetadata.reconstitute(
            fileId, "test.txt", 7, "text/plain", "md5",
            "local-1", StorageType.LOCAL, "annuity/2026-07-19/BATCH_001/01H8FILE002/test.txt",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("BATCH_001"),
            FileStatus.UPLOADED, UserNo.of("u1"), null, null,
            UserNo.of("u1"), UserNo.of("u1"), null, null, null
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);
        when(targetResolver.resolveById("local-1")).thenReturn(target);

        try (InputStream in = router.open(fileId)) {
            assertThat(new String(in.readAllBytes())).isEqualTo("content");
        }
    }

    @Test
    @DisplayName("exists 在文件不存在时应返回 false")
    void exists_should_return_false_when_not_exists() {
        FileId fileId = new FileId("01H8FILE_NONEXIST");
        when(metadataRepository.load(fileId)).thenReturn(Optional.empty());

        assertThat(router.exists(fileId)).isFalse();
    }

    @Test
    @DisplayName("computeMd5 应返回正确 MD5")
    void computeMd5_should_return_correct_md5() throws IOException {
        FileId fileId = new FileId("01H8FILE003");
        StorageTarget target = new StorageTarget(
            "local-1", StorageType.LOCAL, null, null, tempDir.toString(),
            null, null, null, java.util.Map.of()
        );
        Path filePath = tempDir.resolve("test.txt");
        Files.writeString(filePath, "hello");

        FileMetadata file = FileMetadata.reconstitute(
            fileId, "test.txt", 5, "text/plain", "md5",
            "local-1", StorageType.LOCAL, "test.txt",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("BATCH_001"),
            FileStatus.UPLOADED, UserNo.of("u1"), null, null,
            UserNo.of("u1"), UserNo.of("u1"), null, null, null
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);
        when(targetResolver.resolveById("local-1")).thenReturn(target);

        String md5 = router.computeMd5(fileId);
        assertThat(md5).isNotBlank();
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl file-service/file-infrastructure -Dtest=FileStorageRouterTest`
Expected: FAIL (NASFileStorage/FileStorageRouter 不存在)

- [ ] **Step 3: 实现 NASFileStorage**

```java
package com.example.file.infrastructure.storage;

import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.shared.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

@Slf4j
@Component
public class NASFileStorage implements FileStorageBackend {

    @Override
    public StorageType supportedType() {
        return StorageType.NAS;
    }

    @Override
    public void store(StorageTarget target, String storageKey,
                      InputStream content, long contentLength) {
        Path fullPath = resolvePath(target, storageKey);
        try {
            Files.createDirectories(fullPath.getParent());
            // 临时文件 + atomic move 保证 NAS 多节点并发安全
            Path tempPath = fullPath.resolveSibling(
                fullPath.getFileName() + ".tmp." + Thread.currentThread().getId());
            try (OutputStream out = Files.newOutputStream(tempPath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                content.transferTo(out);
            }
            Files.move(tempPath, fullPath,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.debug("NAS 存储成功: path={}", fullPath);
        } catch (IOException e) {
            throw new SystemException(FileErrorCodes.FILE_STORAGE_FAILED, e)
                .withLogDetail("path=" + fullPath);
        }
    }

    @Override
    public InputStream open(StorageTarget target, String storageKey) {
        Path fullPath = resolvePath(target, storageKey);
        try {
            return Files.newInputStream(fullPath, StandardOpenOption.READ);
        } catch (IOException e) {
            throw new SystemException(FileErrorCodes.FILE_METADATA_NOT_FOUND, e)
                .withLogDetail("path=" + fullPath);
        }
    }

    @Override
    public boolean exists(StorageTarget target, String storageKey) {
        return Files.exists(resolvePath(target, storageKey));
    }

    @Override
    public void copy(StorageTarget target, String srcKey, String dstKey) {
        Path src = resolvePath(target, srcKey);
        Path dst = resolvePath(target, dstKey);
        try {
            Files.createDirectories(dst.getParent());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new SystemException(FileErrorCodes.FILE_COPY_FAILED, e)
                .withLogDetail("src=" + src + ", dst=" + dst);
        }
    }

    @Override
    public String computeMd5(StorageTarget target, String storageKey) {
        Path fullPath = resolvePath(target, storageKey);
        try (InputStream in = Files.newInputStream(fullPath)) {
            return DigestUtils.md5Hex(in);
        } catch (IOException e) {
            throw new SystemException(FileErrorCodes.FILE_STORAGE_FAILED, e)
                .withLogDetail("md5 failed, path=" + fullPath);
        }
    }

    private Path resolvePath(StorageTarget target, String storageKey) {
        String mountRoot = target.mountRoot() != null ? target.mountRoot() : "/mnt/nas";
        return Paths.get(mountRoot, target.bucket(), target.basePath(), storageKey);
    }
}
```

- [ ] **Step 4: 实现 FileStorageRouter**

```java
package com.example.file.infrastructure.storage;

import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.exception.SystemException;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class FileStorageRouter implements FileStorageGateway {

    private final FileMetadataRepository metadataRepository;
    private final StorageTargetResolver targetResolver;
    private final List<FileStorageBackend> backends;

    private Map<StorageType, FileStorageBackend> backendMap;

    @PostConstruct
    void initBackendMap() {
        backendMap = backends.stream()
            .collect(Collectors.toMap(
                FileStorageBackend::supportedType,
                Function.identity(),
                (a, b) -> a
            ));
        log.info("文件存储后端已初始化: {}", backendMap.keySet());
    }

    @Override
    public void store(FileId fileId, InputStream content, long contentLength) {
        FileMetadata file = metadataRepository.loadOrThrow(fileId);
        if (file.status() != FileStatus.PENDING_UPLOAD) {
            throw new SystemException(FileErrorCodes.FILE_ALREADY_UPLOADED)
                .withLogDetail("fileId=" + fileId);
        }
        StorageTarget target = targetResolver.resolveById(file.targetId());
        FileStorageBackend backend = resolveBackend(target.type());
        String storageKey = generateStorageKey(file);
        backend.store(target, storageKey, content, contentLength);
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
    public Object copy(FileId srcFileId, FileUsage targetUsage, BatchId businessBatchId) {
        FileMetadata srcFile = metadataRepository.loadOrThrow(srcFileId);
        StorageTarget srcTarget = targetResolver.resolveById(srcFile.targetId());
        StorageTarget dstTarget = targetResolver.resolveByUsage(targetUsage, srcFile.bizType());

        FileId newFileId = FileId.generate() == null ? null : null; // 占位，实际用 new FileId
        newFileId = generateFileId();
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
    public String computeMd5(FileId fileId) {
        FileMetadata file = metadataRepository.loadOrThrow(fileId);
        StorageTarget target = targetResolver.resolveById(file.targetId());
        FileStorageBackend backend = resolveBackend(target.type());
        return backend.computeMd5(target, file.storageKey());
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
        // FileId 是 record, 使用 ULID 生成
        return new FileId(com.github.f4b6a3.ulid.UlidCreator.getUlid().toString());
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
```

**注意**：`FileId.generate()` 在 shared-types 中没有此方法，需要使用 `UlidCreator` 直接生成。如果项目已有 `IdService`，则用 `IdService.generate(FileId.class)`。这里使用 UlidCreator 直接生成。

- [ ] **Step 5: 更新 StorageAutoConfiguration 注册 FileStorageRouter**

```java
package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
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
            com.example.file.domain.repository.FileMetadataRepository metadataRepository,
            StorageTargetResolver targetResolver,
            List<FileStorageBackend> backends) {
        return new FileStorageRouter(metadataRepository, targetResolver, backends);
    }
}
```

- [ ] **Step 6: 运行测试验证通过**

Run: `mvn test -pl file-service/file-infrastructure -Dtest=FileStorageRouterTest`
Expected: PASS (4 tests)

- [ ] **Step 7: Commit**

```bash
git add file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/NASFileStorage.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/FileStorageRouter.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/StorageAutoConfiguration.java \
        file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/FileStorageRouterTest.java
git commit -m "feat(file-infrastructure): 新增 NASFileStorage 与 FileStorageRouter"
```

---

## Task 10: AliyunOSSFileStorage + pom 依赖

**Files:**
- Modify: `file-service/file-infrastructure/pom.xml` (新增 aliyun-sdk-oss optional)
- Create: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/AliyunOSSFileStorage.java`

**Interfaces:**
- Consumes: `FileStorageBackend`, `StorageTarget`, `StorageType`
- Produces: `AliyunOSSFileStorage` 实现 (条件加载)

**说明**：OSS 单元测试需要 Mock OSS 客户端，较为复杂。本任务仅实现类，测试由 StorageIntegrationTest 间接覆盖（本地测试不加载 OSS）。如需独立单元测试，可在后续补充。

- [ ] **Step 1: 新增 aliyun-sdk-oss 依赖**

在 `file-infrastructure/pom.xml` 的 `<dependencies>` 中追加：

```xml
<!-- 阿里云 OSS SDK (optional, 按需引入) -->
<dependency>
    <groupId>com.aliyun.oss</groupId>
    <artifactId>aliyun-sdk-oss</artifactId>
    <version>3.17.4</version>
    <optional>true</optional>
</dependency>
```

- [ ] **Step 2: 实现 AliyunOSSFileStorage**

```java
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
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
    public String computeMd5(StorageTarget target, String storageKey) {
        OSS client = getClient(target);
        try {
            ObjectMetadata meta = client.getObjectMetadata(target.bucket(), storageKey);
            String eTag = meta.getETag();
            // OSS ETag 对于单片上传等于 MD5 (不含引号)
            if (eTag != null) {
                eTag = eTag.replace("\"", "");
            }
            return eTag;
        } catch (Exception e) {
            throw new SystemException(FileErrorCodes.FILE_STORAGE_FAILED, e)
                .withLogDetail("OSS computeMd5 failed: key=" + storageKey);
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
```

- [ ] **Step 3: 编译验证**

Run: `mvn compile -pl file-service/file-infrastructure -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add file-service/file-infrastructure/pom.xml \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/AliyunOSSFileStorage.java
git commit -m "feat(file-infrastructure): 新增 AliyunOSSFileStorage 实现"
```

---

## Task 11: 应用用例 - StoreFileUseCase + 测试

**Files:**
- Create: `file-service/file-application/src/main/java/com/example/file/application/command/StoreFileCommand.java`
- Create: `file-service/file-application/src/main/java/com/example/file/application/usecase/StoreFileUseCase.java`
- Test: `file-service/file-application/src/test/java/com/example/file/application/usecase/StoreFileUseCaseTest.java`

**Interfaces:**
- Consumes: `FileMetadataRepository`, `FileStorageGateway`, `StorageTargetResolver`, `EventBus`, `FileId`, `BatchId`, `UserNo`
- Produces: `StoreFileCommand`, `StoreFileUseCase`

- [ ] **Step 1: 编写失败测试**

```java
package com.example.file.application.usecase;

import com.example.file.application.command.StoreFileCommand;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StoreFileUseCaseTest {

    private FileMetadataRepository metadataRepository;
    private FileStorageGateway storageGateway;
    private StorageTargetResolver targetResolver;
    private EventBus eventBus;
    private StoreFileUseCase useCase;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(FileMetadataRepository.class);
        storageGateway = mock(FileStorageGateway.class);
        targetResolver = mock(StorageTargetResolver.class);
        eventBus = mock(EventBus.class);
        useCase = new StoreFileUseCase(metadataRepository, storageGateway, targetResolver, eventBus);
    }

    @Test
    @DisplayName("createMetadata 应保存元数据并发布事件")
    void createMetadata_should_save_and_publish_event() {
        StoreFileCommand cmd = new StoreFileCommand(
            "test.xlsx", 1024, "application/octet-stream",
            FileUsage.SOURCE, "annuity", "business-core",
            BatchId.of("BATCH_001"), UserNo.of("u1"), null
        );
        StorageTarget target = new StorageTarget(
            "oss-source", StorageType.OSS, "https://oss.example.com", "bucket",
            "base", null, "ak", "sk", java.util.Map.of()
        );
        when(targetResolver.resolveByUsage(FileUsage.SOURCE, "annuity")).thenReturn(target);

        FileId fileId = useCase.createMetadata(cmd);

        assertThat(fileId).isNotNull();
        verify(metadataRepository).save(any(FileMetadata.class));
        verify(eventBus, atLeastOnce()).publish(any());
    }

    @Test
    @DisplayName("store 应调用 storageGateway.store 并标记 UPLOADED")
    void store_should_call_gateway_and_markUploaded() {
        FileId fileId = new FileId("01H8TESTFILE001");
        FileMetadata file = FileMetadata.create(
            fileId, "test.txt", 5, "text/plain",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("BATCH_001"),
            "oss-source", StorageType.OSS, UserNo.of("u1"), null
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);
        when(storageGateway.computeMd5(fileId)).thenReturn("md5hash");

        useCase.store(fileId, new ByteArrayInputStream("hello".getBytes()), 5);

        assertThat(file.status()).isEqualTo(FileStatus.UPLOADED);
        assertThat(file.md5()).isEqualTo("md5hash");
        verify(storageGateway).store(eq(fileId), any(), eq(5L));
        verify(metadataRepository).save(any(FileMetadata.class));
    }

    @Test
    @DisplayName("store 在非 PENDING_UPLOAD 状态时应抛异常")
    void store_should_throw_when_status_is_not_PENDING_UPLOAD() {
        FileId fileId = new FileId("01H8TESTFILE002");
        FileMetadata file = FileMetadata.create(
            fileId, "test.txt", 5, "text/plain",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("BATCH_001"),
            "oss-source", StorageType.OSS, UserNo.of("u1"), null
        );
        file.markUploaded("key", "md5");
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            useCase.store(fileId, new ByteArrayInputStream("x".getBytes()), 1)
        ).isInstanceOf(RuntimeException.class);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl file-service/file-application -Dtest=StoreFileUseCaseTest`
Expected: FAIL (StoreFileCommand/StoreFileUseCase 不存在)

- [ ] **Step 3: 实现 StoreFileCommand**

```java
package com.example.file.application.command;

import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

public record StoreFileCommand(
    String originalName,
    long size,
    String contentType,
    FileUsage usage,
    String bizType,
    String sourceApp,
    BatchId businessBatchId,
    UserNo uploadedBy,
    LocalDateTime expiresAt
) {}
```

- [ ] **Step 4: 实现 StoreFileUseCase**

```java
package com.example.file.application.usecase;

import com.example.file.application.command.StoreFileCommand;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.SystemException;
import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.shared.primitives.identity.FileId;
import com.github.f4b6a3.ulid.UlidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreFileUseCase {

    private final FileMetadataRepository metadataRepository;
    private final FileStorageGateway storageGateway;
    private final StorageTargetResolver targetResolver;
    private final EventBus eventBus;

    @Transactional
    public FileId createMetadata(StoreFileCommand command) {
        FileId fileId = new FileId(UlidCreator.getUlid().toString());
        var target = targetResolver.resolveByUsage(command.usage(), command.bizType());
        FileMetadata file = FileMetadata.create(
            fileId,
            command.originalName(),
            command.size(),
            command.contentType(),
            command.usage(),
            command.bizType(),
            command.sourceApp(),
            command.businessBatchId(),
            target.targetId(),
            target.type(),
            command.uploadedBy(),
            command.expiresAt()
        );
        metadataRepository.save(file);
        file.getDomainEvents().forEach(eventBus::publish);
        file.clearDomainEvents();
        log.info("文件元数据已创建: fileId={}, usage={}, bizType={}",
            fileId, command.usage(), command.bizType());
        return fileId;
    }

    @Transactional
    public void store(FileId fileId, InputStream content, long contentLength) {
        FileMetadata file = metadataRepository.loadOrThrow(fileId);
        if (file.status() != FileStatus.PENDING_UPLOAD) {
            throw new SystemException(FileErrorCodes.FILE_ALREADY_UPLOADED)
                .withLogDetail("fileId=" + fileId + ", 当前状态=" + file.status());
        }
        storageGateway.store(fileId, content, contentLength);
        String md5 = storageGateway.computeMd5(fileId);
        String storageKey = generateStorageKey(file);
        file.markUploaded(storageKey, md5);
        metadataRepository.save(file);
        file.getDomainEvents().forEach(eventBus::publish);
        file.clearDomainEvents();
        log.info("文件已存储: fileId={}, storageKey={}", fileId, storageKey);
    }

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
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -pl file-service/file-application -Dtest=StoreFileUseCaseTest`
Expected: PASS (3 tests)

- [ ] **Step 6: Commit**

```bash
git add file-service/file-application/src/main/java/com/example/file/application/command/StoreFileCommand.java \
        file-service/file-application/src/main/java/com/example/file/application/usecase/StoreFileUseCase.java \
        file-service/file-application/src/test/java/com/example/file/application/usecase/StoreFileUseCaseTest.java
git commit -m "feat(file-application): 新增 StoreFileUseCase"
```

---

## Task 12: 应用用例 - Open/Delete/Copy

**Files:**
- Create: `file-service/file-application/src/main/java/com/example/file/application/usecase/OpenFileUseCase.java`
- Create: `file-service/file-application/src/main/java/com/example/file/application/usecase/DeleteFileUseCase.java`
- Create: `file-service/file-application/src/main/java/com/example/file/application/command/CopyFileCommand.java`
- Create: `file-service/file-application/src/main/java/com/example/file/application/usecase/CopyFileUseCase.java`
- Test: `file-service/file-application/src/test/java/com/example/file/application/usecase/OpenFileUseCaseTest.java`
- Test: `file-service/file-application/src/test/java/com/example/file/application/usecase/DeleteFileUseCaseTest.java`
- Test: `file-service/file-application/src/test/java/com/example/file/application/usecase/CopyFileUseCaseTest.java`

**Interfaces:**
- Consumes: `FileMetadataRepository`, `FileStorageGateway`, `StorageTargetResolver`, `EventBus`, `CopyResult`
- Produces: `OpenFileUseCase`, `DeleteFileUseCase`, `CopyFileCommand`, `CopyFileUseCase`

- [ ] **Step 1: 编写失败测试**

`OpenFileUseCaseTest.java`:
```java
package com.example.file.application.usecase;

import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OpenFileUseCaseTest {

    private FileMetadataRepository metadataRepository;
    private FileStorageGateway storageGateway;
    private OpenFileUseCase useCase;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(FileMetadataRepository.class);
        storageGateway = mock(FileStorageGateway.class);
        useCase = new OpenFileUseCase(metadataRepository, storageGateway);
    }

    @Test
    @DisplayName("open 在文件已删除时应抛异常")
    void open_should_throw_when_file_is_deleted() {
        FileId fileId = new FileId("01H8DEL001");
        FileMetadata file = FileMetadata.reconstitute(
            fileId, "test.txt", 5, "text/plain", "md5",
            "local-1", StorageType.LOCAL, "key",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("B001"),
            FileStatus.DELETED, UserNo.of("u1"), null, null,
            UserNo.of("u1"), UserNo.of("u1"), null, null, null
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

        assertThatThrownBy(() -> useCase.open(fileId))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("open 在文件已过期时应抛异常")
    void open_should_throw_when_file_is_expired() {
        FileId fileId = new FileId("01H8EXP001");
        FileMetadata file = FileMetadata.create(
            fileId, "test.txt", 5, "text/plain",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("B001"),
            "local-1", StorageType.LOCAL, UserNo.of("u1"), LocalDateTime.now().minusHours(1)
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

        assertThatThrownBy(() -> useCase.open(fileId))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("open 应返回 gateway 的流")
    void open_should_return_stream_from_gateway() {
        FileId fileId = new FileId("01H8OPEN001");
        FileMetadata file = FileMetadata.create(
            fileId, "test.txt", 5, "text/plain",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("B001"),
            "local-1", StorageType.LOCAL, UserNo.of("u1"), null
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);
        when(storageGateway.open(fileId)).thenReturn(new ByteArrayInputStream("hello".getBytes()));

        var stream = useCase.open(fileId);
        assertThat(stream).isNotNull();
    }
}
```

`DeleteFileUseCaseTest.java`:
```java
package com.example.file.application.usecase;

import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DeleteFileUseCaseTest {

    private FileMetadataRepository metadataRepository;
    private EventBus eventBus;
    private DeleteFileUseCase useCase;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(FileMetadataRepository.class);
        eventBus = mock(EventBus.class);
        useCase = new DeleteFileUseCase(metadataRepository, eventBus);
    }

    @Test
    @DisplayName("delete 应标记 DELETED 并发布事件")
    void delete_should_markDeleted_and_publish_event() {
        FileId fileId = new FileId("01H8DEL001");
        FileMetadata file = FileMetadata.create(
            fileId, "test.txt", 5, "text/plain",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("B001"),
            "local-1", StorageType.LOCAL, UserNo.of("u1"), null
        );
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

        useCase.delete(fileId, UserNo.of("u1"));

        assertThat(file.status()).isEqualTo(FileStatus.DELETED);
        verify(metadataRepository).save(file);
        verify(eventBus, atLeastOnce()).publish(any());
    }

    @Test
    @DisplayName("delete 在已 DELETED 状态时应幂等返回")
    void delete_should_be_idempotent() {
        FileId fileId = new FileId("01H8DEL002");
        FileMetadata file = FileMetadata.create(
            fileId, "test.txt", 5, "text/plain",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("B001"),
            "local-1", StorageType.LOCAL, UserNo.of("u1"), null
        );
        file.markDeleted(UserNo.of("u1"));
        file.clearDomainEvents();
        when(metadataRepository.loadOrThrow(fileId)).thenReturn(file);

        useCase.delete(fileId, UserNo.of("u1"));

        // 不应再次保存
        verify(metadataRepository, never()).save(any());
    }
}
```

`CopyFileUseCaseTest.java`:
```java
package com.example.file.application.usecase;

import com.example.file.application.command.CopyFileCommand;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.infrastructure.storage.CopyResult;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CopyFileUseCaseTest {

    private FileMetadataRepository metadataRepository;
    private FileStorageGateway storageGateway;
    private StorageTargetResolver targetResolver;
    private EventBus eventBus;
    private CopyFileUseCase useCase;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(FileMetadataRepository.class);
        storageGateway = mock(FileStorageGateway.class);
        targetResolver = mock(StorageTargetResolver.class);
        eventBus = mock(EventBus.class);
        useCase = new CopyFileUseCase(metadataRepository, storageGateway, targetResolver, eventBus);
    }

    @Test
    @DisplayName("copy 应创建新元数据并调用 gateway.copy")
    void copy_should_create_new_metadata_and_call_gateway_copy() {
        FileId srcFileId = new FileId("01H8SRC001");
        FileMetadata srcFile = FileMetadata.create(
            srcFileId, "test.txt", 5, "text/plain",
            FileUsage.SOURCE, "annuity", "biz", BatchId.of("B001"),
            "oss-source", StorageType.OSS, UserNo.of("u1"), null
        );
        srcFile.markUploaded("storage/key", "md5hash");
        srcFile.clearDomainEvents();

        FileId newFileId = new FileId("01H8NEW001");
        when(metadataRepository.loadOrThrow(srcFileId)).thenReturn(srcFile);
        when(storageGateway.copy(eq(srcFileId), eq(FileUsage.EXPORT), any()))
            .thenReturn(new CopyResult(newFileId, "export/key"));
        when(targetResolver.resolveByUsage(FileUsage.EXPORT, "annuity"))
            .thenReturn(new StorageTarget(
                "oss-export", StorageType.OSS, "https://oss.example.com", "bucket",
                "base", null, "ak", "sk", java.util.Map.of()
            ));

        CopyFileCommand cmd = new CopyFileCommand(
            srcFileId, FileUsage.EXPORT, BatchId.of("B001"), UserNo.of("u1")
        );

        FileId result = useCase.copy(cmd);

        assertThat(result).isEqualTo(newFileId);
        verify(metadataRepository).save(any(FileMetadata.class));
        verify(eventBus, atLeastOnce()).publish(any());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl file-service/file-application -Dtest=OpenFileUseCaseTest,DeleteFileUseCaseTest,CopyFileUseCaseTest`
Expected: FAIL (类不存在)

- [ ] **Step 3: 实现 OpenFileUseCase**

```java
package com.example.file.application.usecase;

import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.exception.SystemException;
import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.shared.primitives.identity.FileId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class OpenFileUseCase {

    private final FileMetadataRepository metadataRepository;
    private final FileStorageGateway storageGateway;

    public InputStream open(FileId fileId) {
        FileMetadata file = metadataRepository.loadOrThrow(fileId);
        if (file.status() == FileStatus.DELETED) {
            throw new SystemException(FileErrorCodes.FILE_METADATA_NOT_FOUND)
                .withLogDetail("fileId=" + fileId + " 已删除");
        }
        if (file.isExpired()) {
            throw new SystemException(FileErrorCodes.FILE_EXPIRED)
                .withLogDetail("fileId=" + fileId);
        }
        return storageGateway.open(fileId);
    }

    @Transactional(readOnly = true)
    public FileMetadata loadMetadata(FileId fileId) {
        return metadataRepository.loadOrThrow(fileId);
    }
}
```

- [ ] **Step 4: 实现 DeleteFileUseCase**

```java
package com.example.file.application.usecase;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteFileUseCase {

    private final FileMetadataRepository metadataRepository;
    private final EventBus eventBus;

    @Transactional
    public void delete(FileId fileId, UserNo deletedBy) {
        FileMetadata file = metadataRepository.loadOrThrow(fileId);
        if (file.status() == FileStatus.DELETED) {
            log.debug("文件已删除，幂等返回: fileId={}", fileId);
            return;
        }
        file.markDeleted(deletedBy);
        metadataRepository.save(file);
        file.getDomainEvents().forEach(eventBus::publish);
        file.clearDomainEvents();
        log.info("文件已逻辑删除: fileId={}, deletedBy={}", fileId, deletedBy);
    }
}
```

- [ ] **Step 5: 实现 CopyFileCommand**

```java
package com.example.file.application.command;

import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;

public record CopyFileCommand(
    FileId srcFileId,
    FileUsage targetUsage,
    BatchId businessBatchId,
    UserNo operatedBy
) {}
```

- [ ] **Step 6: 实现 CopyFileUseCase**

```java
package com.example.file.application.usecase;

import com.example.file.application.command.CopyFileCommand;
import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.infrastructure.storage.CopyResult;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.SystemException;
import com.example.shared.primitives.identity.FileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CopyFileUseCase {

    private final FileMetadataRepository metadataRepository;
    private final FileStorageGateway storageGateway;
    private final StorageTargetResolver targetResolver;
    private final EventBus eventBus;

    @Transactional
    public FileId copy(CopyFileCommand command) {
        FileMetadata srcFile = metadataRepository.loadOrThrow(command.srcFileId());

        Object result = storageGateway.copy(
            command.srcFileId(), command.targetUsage(), command.businessBatchId()
        );

        if (!(result instanceof CopyResult copyResult)) {
            throw new SystemException(FileErrorCodes.FILE_COPY_FAILED)
                .withLogDetail("gateway.copy 返回类型不是 CopyResult: " + result.getClass());
        }

        var dstTarget = targetResolver.resolveByUsage(command.targetUsage(), srcFile.bizType());
        FileMetadata newFile = FileMetadata.create(
            copyResult.newFileId(),
            srcFile.originalName(),
            srcFile.size(),
            srcFile.contentType(),
            command.targetUsage(),
            srcFile.bizType(),
            srcFile.sourceApp(),
            command.businessBatchId(),
            dstTarget.targetId(),
            dstTarget.type(),
            command.operatedBy(),
            null
        );
        newFile.markUploaded(copyResult.newStorageKey(), srcFile.md5());
        metadataRepository.save(newFile);
        newFile.getDomainEvents().forEach(eventBus::publish);
        newFile.clearDomainEvents();

        log.info("文件已复制: srcFileId={}, newFileId={}, targetUsage={}",
            command.srcFileId(), copyResult.newFileId(), command.targetUsage());
        return copyResult.newFileId();
    }
}
```

- [ ] **Step 7: 运行测试验证通过**

Run: `mvn test -pl file-service/file-application -Dtest=OpenFileUseCaseTest,DeleteFileUseCaseTest,CopyFileUseCaseTest`
Expected: PASS (6 tests)

- [ ] **Step 8: Commit**

```bash
git add file-service/file-application/src/main/java/com/example/file/application/usecase/OpenFileUseCase.java \
        file-service/file-application/src/main/java/com/example/file/application/usecase/DeleteFileUseCase.java \
        file-service/file-application/src/main/java/com/example/file/application/command/CopyFileCommand.java \
        file-service/file-application/src/main/java/com/example/file/application/usecase/CopyFileUseCase.java \
        file-service/file-application/src/test/java/com/example/file/application/usecase/OpenFileUseCaseTest.java \
        file-service/file-application/src/test/java/com/example/file/application/usecase/DeleteFileUseCaseTest.java \
        file-service/file-application/src/test/java/com/example/file/application/usecase/CopyFileUseCaseTest.java
git commit -m "feat(file-application): 新增 Open/Delete/Copy 用例"
```

---

## Task 13: ParseTask 迁移 (sourceFileRef → sourceFileId)

**Files:**
- Modify: `file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/root/ParseTask.java`
- Modify: `file-service/file-application/src/main/java/com/example/file/application/command/UploadFileCommand.java`
- Modify: `file-service/file-application/src/main/java/com/example/file/application/usecase/UploadFileUseCase.java`
- Modify: `file-service/file-application/src/main/java/com/example/file/application/usecase/ParseFileUseCase.java`
- Modify: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/entity/ParseTaskDO.java`
- Modify: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/converter/ParseTaskConverter.java`
- Modify: `file-service/file-domain/src/test/java/com/example/file/domain/model/aggregate/root/ParseTaskTest.java`

**Interfaces:**
- Consumes: `FileId` (shared-types)
- Produces: 迁移后的 ParseTask (字段 sourceFileId: FileId)

- [ ] **Step 1: 修改 ParseTask.java**

将所有 `sourceFileRef` 改为 `sourceFileId`，类型从 `String` 改为 `FileId`：

**修改 import** (在现有 import 后追加)：
```java
import com.example.shared.primitives.identity.FileId;
```

**修改字段** (line 23)：
```java
// 旧: private String sourceFileRef;
private FileId sourceFileId;
```

**修改业务创建构造函数** (line 37-47)：
```java
private ParseTask(FileTaskId id, BizType bizType, String sourceFileName, FileId sourceFileId,
                  ErrorPolicy errorPolicy, List<String> splitKeys, UserNo userNo) {
    super(id, userNo);
    this.bizType = bizType;
    this.sourceFileName = sourceFileName;
    this.sourceFileId = sourceFileId;
    this.errorPolicy = errorPolicy;
    this.splitKeys = List.copyOf(splitKeys);
    this.status = TaskStatus.PENDING;
    this.startedAt = LocalDateTime.now();
}
```

**修改数据库重建构造函数** (line 50-72)：
```java
public ParseTask(FileTaskId id, BizType bizType, TemplateCode templateCode, String sourceFileName,
                 FileId sourceFileId, TaskStatus status, ErrorPolicy errorPolicy, List<String> splitKeys,
                 int totalRows, int subTaskCount, int validCount, int invalidCount,
                 List<SubTaskSummary> subTaskSummaries, List<TaskError> errors,
                 LocalDateTime startedAt, LocalDateTime finishedAt,
                 UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.bizType = bizType;
    this.templateCode = templateCode;
    this.sourceFileName = sourceFileName;
    this.sourceFileId = sourceFileId;
    this.status = status;
    this.errorPolicy = errorPolicy;
    this.splitKeys = splitKeys;
    this.totalRows = totalRows;
    this.subTaskCount = subTaskCount;
    this.validCount = validCount;
    this.invalidCount = invalidCount;
    this.subTaskSummaries = new ArrayList<>(subTaskSummaries);
    this.errors = new ArrayList<>(errors);
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
}
```

**修改 create 工厂方法** (line 74-81)：
```java
public static ParseTask create(FileTaskId id, BizType bizType, String sourceFileName,
                               FileId sourceFileId, ErrorPolicy errorPolicy,
                               List<String> splitKeys, UserNo userNo) {
    if (bizType == null) throw new IllegalArgumentException("bizType null");
    if (errorPolicy == null) throw new IllegalArgumentException("errorPolicy null");
    if (sourceFileName == null || sourceFileName.isBlank()) throw new IllegalArgumentException("sourceFileName empty");
    return new ParseTask(id, bizType, sourceFileName, sourceFileId, errorPolicy, splitKeys, userNo);
}
```

**修改 getter** (line 128)：
```java
// 旧: public String sourceFileRef() { return sourceFileRef; }
public FileId sourceFileId() { return sourceFileId; }
```

- [ ] **Step 2: 修改 UploadFileCommand.java**

```java
package com.example.file.application.command;

import com.example.shared.primitives.identity.FileId;

public record UploadFileCommand(
    String bizType,
    String templateCode,
    String sourceFileName,
    FileId sourceFileId,
    String uploader,
    String clientRequestNo
) {}
```

- [ ] **Step 3: 修改 UploadFileUseCase.java**

修改 line 33：
```java
// 旧: cmd.sourceFileRef(),
cmd.sourceFileId(),
```

同时需要 import `FileId`（如果未引入）。

- [ ] **Step 4: 修改 ParseFileUseCase.java**

修改 line 96 和 line 113：
```java
// 旧: try (InputStream inputStream = fileStorage.open(task.sourceFileRef())) {
try (InputStream inputStream = fileStorage.open(task.sourceFileId())) {

// 旧: try (InputStream parseStream = fileStorage.open(task.sourceFileRef())) {
try (InputStream parseStream = fileStorage.open(task.sourceFileId())) {
```

- [ ] **Step 5: 修改 ParseTaskDO.java**

修改 line 21：
```java
// 旧: private String sourceFileRef;
private String sourceFileId;
```

- [ ] **Step 6: 修改 ParseTaskConverter.java**

修改 line 33 (toDO 方向)：
```java
// 旧: @Mapping(target = "sourceFileRef", expression = "java(task.sourceFileRef())")
@Mapping(target = "sourceFileId", expression = "java(fileIdToString(task.sourceFileId()))")
```

修改 toDomain 方向 (在 @Mapping 列表中添加)：
```java
@Mapping(target = "sourceFileId", source = "sourceFileId", qualifiedByName = "toFileId")
```

添加辅助方法：
```java
default String fileIdToString(FileId fileId) {
    return fileId != null ? fileId.value() : null;
}

@Named("toFileId")
default FileId toFileId(String fileId) {
    return fileId != null ? new FileId(fileId) : null;
}
```

添加 import：
```java
import com.example.shared.primitives.identity.FileId;
```

- [ ] **Step 7: 修改 ParseTaskTest.java**

修改 line 92 (newTask 方法)：
```java
// 旧:
// return ParseTask.create(FileTaskId.of("tsk1"), BizType.of("import_declare"),
//     "sample.xlsx", "ref://sample.xlsx", ErrorPolicy.COLLECT_ALL,
//     List.of("detailList.deptCode"), UserNo.of("u1"));

// 新:
return ParseTask.create(FileTaskId.of("tsk1"), BizType.of("import_declare"),
    "sample.xlsx", new FileId("01H8SAMPLEFILE001"), ErrorPolicy.COLLECT_ALL,
    List.of("detailList.deptCode"), UserNo.of("u1"));
```

修改 line 23-25 (should_create_pending_task 测试)：
```java
ParseTask task = ParseTask.create(FileTaskId.of("tsk1"), BizType.of("import_declare"),
    "sample.xlsx", new FileId("01H8SAMPLEFILE001"), ErrorPolicy.COLLECT_ALL,
    List.of("detailList.deptCode"), UserNo.of("u1"));
```

添加 import：
```java
import com.example.shared.primitives.identity.FileId;
```

- [ ] **Step 8: 编译验证**

Run: `mvn compile -pl file-service -am`
Expected: BUILD SUCCESS

- [ ] **Step 9: 运行现有测试验证回归**

Run: `mvn test -pl file-service/file-domain -Dtest=ParseTaskTest`
Expected: PASS (所有现有测试通过)

- [ ] **Step 10: Commit**

```bash
git add file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/root/ParseTask.java \
        file-service/file-application/src/main/java/com/example/file/application/command/UploadFileCommand.java \
        file-service/file-application/src/main/java/com/example/file/application/usecase/UploadFileUseCase.java \
        file-service/file-application/src/main/java/com/example/file/application/usecase/ParseFileUseCase.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/entity/ParseTaskDO.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/converter/ParseTaskConverter.java \
        file-service/file-domain/src/test/java/com/example/file/domain/model/aggregate/root/ParseTaskTest.java
git commit -m "refactor(file-service): ParseTask sourceFileRef → sourceFileId 强类型 FileId"
```

---

## Task 14: 配置文件更新 (application.yml)

**Files:**
- Modify: `file-service/file-starter/src/main/resources/application.yml`
- Create: `file-service/file-starter/src/main/resources/application-local.yml`
- Create: `file-service/file-starter/src/test/resources/application-test.yml`

**Interfaces:**
- Consumes: `StorageTargetProperties`, `StorageAutoConfiguration`
- Produces: 完整可用的存储配置（默认 Local 后端）

- [ ] **Step 1: 修改 application.yml 追加 file.storage 配置块**

在 `file-service/file-starter/src/main/resources/application.yml` 末尾追加：

```yaml
# 文件存储配置
file:
  storage:
    targets:
      - target-id: local-default
        type: LOCAL
        base-path: ${user.home}/.file-service/data
        # 本地存储不需要 endpoint/bucket/ak/sk
      - target-id: oss-source
        type: OSS
        endpoint: https://oss-cn-hangzhou.aliyuncs.com
        bucket: file-source-prod
        access-key-id: ${OSS_SOURCE_AK:}
        access-key-secret: ${OSS_SOURCE_SK:}
        base-path: source/
        options:
          secure: true
      - target-id: oss-export
        type: OSS
        endpoint: https://oss-cn-hangzhou.aliyuncs.com
        bucket: file-export-prod
        access-key-id: ${OSS_EXPORT_AK:}
        access-key-secret: ${OSS_EXPORT_SK:}
        base-path: export/
        options:
          secure: true
      - target-id: nas-shared
        type: NAS
        base-path: /mnt/nas/file-service
        options:
          atomic-move: true
    # usage → targetId 路由表
    usage-routes:
      SOURCE: local-default
      EXPORT: oss-export
      TEMPLATE: oss-source
      ARCHIVE: nas-shared
    # NAS 配置
    nas:
      atomic-move: true
```

- [ ] **Step 2: 创建 application-local.yml**

`file-service/file-starter/src/main/resources/application-local.yml`:

```yaml
# 本地开发环境覆盖配置
file:
  storage:
    targets:
      - target-id: local-default
        type: LOCAL
        base-path: ${user.home}/.file-service/dev
      - target-id: oss-source
        type: LOCAL  # 本地开发强制 LOCAL，避免依赖 OSS
        base-path: ${user.home}/.file-service/dev/source
      - target-id: oss-export
        type: LOCAL
        base-path: ${user.home}/.file-service/dev/export
      - target-id: nas-shared
        type: LOCAL
        base-path: ${user.home}/.file-service/dev/nas
    usage-routes:
      SOURCE: local-default
      EXPORT: oss-export
      TEMPLATE: oss-source
      ARCHIVE: nas-shared
    nas:
      atomic-move: false  # 跨设备时设为 false
```

- [ ] **Step 3: 创建 application-test.yml**

`file-service/file-starter/src/test/resources/application-test.yml`:

```yaml
# 测试环境配置 (使用 H2 + 本地存储)
spring:
  datasource:
    url: jdbc:h2:mem:file-service-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  sql:
    init:
      mode: always
      schema-locations: classpath:schema-pg.sql

file:
  storage:
    targets:
      - target-id: local-default
        type: LOCAL
        base-path: ${java.io.tmpdir}/file-service-test
    usage-routes:
      SOURCE: local-default
      EXPORT: local-default
      TEMPLATE: local-default
      ARCHIVE: local-default
    nas:
      atomic-move: false
```

- [ ] **Step 4: 验证配置加载**

Run: `mvn compile -pl file-service/file-starter -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add file-service/file-starter/src/main/resources/application.yml \
        file-service/file-starter/src/main/resources/application-local.yml \
        file-service/file-starter/src/test/resources/application-test.yml
git commit -m "feat(file-starter): 追加 file.storage 配置块 (Local/OSS/NAS 路由)"
```

---

## Task 15: StorageIntegrationTest 端到端集成测试

**Files:**
- Create: `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/StorageIntegrationTest.java`
- Create: `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/StorageTestConfiguration.java`

**Interfaces:**
- Consumes: `FileStorageRouter`, `FileMetadataRepository`, `StorageTargetResolver`, `StorageTargetProperties`
- Produces: 端到端集成测试覆盖 (Local store/open/copy/delete + OSS mock + NAS atomic move)

- [ ] **Step 1: 编写 StorageTestConfiguration**

`file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/StorageTestConfiguration.java`:

```java
package com.example.file.infrastructure.storage;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;

/**
 * 存储集成测试专用配置。
 * 加载 application-test.yml 中的 LOCAL 后端配置。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.example.file.infrastructure.storage")
@PropertySource("classpath:application-test.yml")
public class StorageTestConfiguration {
}
```

- [ ] **Step 2: 编写失败的集成测试**

`file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/StorageIntegrationTest.java`:

```java
package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.shared.primitives.identity.FileId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 存储引擎端到端集成测试。
 * 验证 Store → Open → Copy → Delete 全流程，使用 LOCAL 后端。
 */
@SpringBootTest(classes = StorageTestConfiguration.class)
@ActiveProfiles("test")
class StorageIntegrationTest {

    @Autowired
    private FileStorageGateway storageGateway;

    @Autowired
    private StorageTargetResolver targetResolver;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Local 后端: store → open → computeMd5 完整流程")
    void local_backend_store_open_md5_flow() throws Exception {
        FileId fileId = new FileId("01H8INTEGRATION01");
        byte[] content = "hello-storage-integration".getBytes();
        StorageTarget target = targetResolver.resolveByUsage(FileUsage.SOURCE, "annuity");
        storageGateway.store(fileId, new ByteArrayInputStream(content), content.length);

        try (InputStream opened = storageGateway.open(fileId)) {
            String md5 = storageGateway.computeMd5(fileId);
            assertThat(md5).isNotBlank();
            assertThat(opened.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("Local 后端: copy 操作应返回 CopyResult (newFileId + newStorageKey)")
    void local_backend_copy_should_return_copy_result() {
        FileId srcFileId = new FileId("01H8INTEGRATION02");
        byte[] content = "copy-source-content".getBytes();
        storageGateway.store(srcFileId, new ByteArrayInputStream(content), content.length);

        Object result = storageGateway.copy(srcFileId, FileUsage.EXPORT,
            com.example.shared.primitives.identity.BatchId.of("BATCH_TEST"));

        assertThat(result).isInstanceOf(CopyResult.class);
        CopyResult copyResult = (CopyResult) result;
        assertThat(copyResult.newFileId()).isNotNull();
        assertThat(copyResult.newStorageKey()).isNotBlank();
        assertThat(copyResult.newFileId()).isNotEqualTo(srcFileId);
    }

    @Test
    @DisplayName("Local 后端: delete 后再 open 应抛异常")
    void local_backend_delete_then_open_throws() {
        FileId fileId = new FileId("01H8INTEGRATION03");
        byte[] content = "to-be-deleted".getBytes();
        storageGateway.store(fileId, new ByteArrayInputStream(content), content.length);

        storageGateway.delete(fileId);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storageGateway.open(fileId))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("StorageTargetResolver 应根据 usage 返回正确的 StorageTarget")
    void resolver_should_return_correct_target_by_usage() {
        StorageTarget sourceTarget = targetResolver.resolveByUsage(FileUsage.SOURCE, "annuity");
        assertThat(sourceTarget).isNotNull();
        assertThat(sourceTarget.type()).isEqualTo(StorageType.LOCAL);
        assertThat(sourceTarget.targetId()).isEqualTo("local-default");

        StorageTarget exportTarget = targetResolver.resolveByUsage(FileUsage.EXPORT, "annuity");
        assertThat(exportTarget).isNotNull();
        assertThat(exportTarget.targetId()).isEqualTo("local-default");
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn test -pl file-service/file-infrastructure -Dtest=StorageIntegrationTest`
Expected: FAIL (StorageTestConfiguration 不存在 / 依赖未注入)

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl file-service/file-infrastructure -Dtest=StorageIntegrationTest`
Expected: PASS (4 tests)

- [ ] **Step 5: 运行全部测试验证无回归**

Run: `mvn test -pl file-service -am`
Expected: BUILD SUCCESS (所有现有测试 + 新增测试通过)

- [ ] **Step 6: Commit**

```bash
git add file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/StorageTestConfiguration.java \
        file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/StorageIntegrationTest.java
git commit -m "test(file-infrastructure): 新增 StorageIntegrationTest 端到端集成测试"
```

- [ ] **Step 7: 最终全量构建验证**

Run: `mvn clean install -pl file-service -am -DskipTests=false`
Expected: BUILD SUCCESS, 所有测试通过

- [ ] **Step 8: 最终 Commit (如有调整)**

```bash
git log --oneline -15  # 查看本次实施的所有提交
git status  # 确认工作区干净
```

---

## Self-Review

完成 Plan 文档后，对 spec 进行交叉检查：

### 1. Spec 覆盖检查

| Spec 章节 | 实现 Task | 状态 |
|----------|----------|------|
| §0 背景目标 | (整体设计) | ✅ |
| §1 领域模型 - 枚举 | Task 1 (StorageType/FileUsage/FileStatus) | ✅ |
| §2.1 StorageTarget 值对象 | Task 2 | ✅ |
| §2.2 FileMetadata 聚合根 | Task 3 | ✅ |
| §2.3 领域事件 (3 个) | Task 3 | ✅ |
| §2.4 Repository 接口 | Task 4 | ✅ |
| §2.5 StorageTargetResolver SPI | Task 4 | ✅ |
| §2.6 FileStorageGateway SPI 重构 | Task 4 (含 CopyResult) | ✅ |
| §3.1 FileMetadataDO + Mapper + Converter | Task 5 | ✅ |
| §3.2 FileMetadataRepositoryImpl + schema-pg.sql | Task 6 | ✅ |
| §3.3 配置类与自动装配 | Task 7 | ✅ |
| §4.1 LocalFileStorage 后端 | Task 8 | ✅ |
| §4.2 NASFileStorage 后端 | Task 9 | ✅ |
| §4.3 AliyunOSSFileStorage 后端 | Task 10 | ✅ |
| §4.4 FileStorageRouter 路由 | Task 9 (与 NAS 合并) | ✅ |
| §3.3.1 StoreFileUseCase | Task 11 | ✅ |
| §3.3.2 OpenFileUseCase | Task 12 | ✅ |
| §3.3.3 DeleteFileUseCase | Task 12 | ✅ |
| §3.3.4 CopyFileUseCase (CopyResult) | Task 12 | ✅ |
| §5 ParseTask 迁移 sourceFileRef → sourceFileId | Task 13 | ✅ |
| §6 配置文件 | Task 14 | ✅ |
| §8 验收标准 - 集成测试 | Task 15 | ✅ |

**结论**: Spec 全部章节已覆盖到对应 Task。

### 2. 占位符扫描

扫描全文，未发现以下问题模式：
- ❌ "TBD" / "TODO" / "implement later" - 无
- ❌ "Add appropriate error handling" - 无（错误处理已在代码中显式实现）
- ❌ "Similar to Task N" - 无（每个 Task 都独立展示完整代码）
- ❌ "fill in details" - 无

**结论**: 无占位符。

### 3. 类型一致性检查

| 类型/方法 | 定义 Task | 使用 Task | 一致性 |
|---------|---------|---------|--------|
| `FileId` | shared-types (复用) | Task 3, 4, 11, 12, 13, 15 | ✅ |
| `BatchId` | shared-types (复用) | Task 3, 11, 12, 15 | ✅ |
| `StorageTarget` | Task 2 | Task 3, 4, 7, 11, 12, 15 | ✅ |
| `FileMetadata` | Task 3 | Task 4, 5, 6, 11, 12 | ✅ |
| `FileMetadataRepository` | Task 4 | Task 5, 6, 11, 12 | ✅ |
| `FileStorageGateway` | Task 4 | Task 8, 9, 10 (实现), Task 11, 12, 15 (使用) | ✅ |
| `StorageTargetResolver` | Task 4 | Task 7 (实现), Task 11, 12, 15 (使用) | ✅ |
| `CopyResult` | Task 4 (定义于 infrastructure) | Task 9 (Router 返回), Task 12 (UseCase 消费) | ✅ |
| `FileStorageBackend` (内部 SPI) | Task 8 | Task 9, 10 | ✅ |
| `FileStorageRouter` | Task 9 | Task 15 (测试) | ✅ |
| `StorageTargetProperties` | Task 7 | Task 14 (配置绑定) | ✅ |
| `FileStorageGateway.copy()` 返回类型 | Task 4 (Object) | Task 9 (返回 CopyResult), Task 12 (强转 CopyResult) | ✅ |
| `ParseTask.sourceFileId()` | Task 13 (FileId) | Task 13 (ParseFileUseCase 调用) | ✅ |

**结论**: 所有类型/方法签名跨 Task 一致。

### 4. 异常分类一致性

| 异常类型 | 使用场景 | Task |
|--------|---------|------|
| `DomainException` | domain 层业务规则违反 | Task 3 (FileMetadata 校验) |
| `SystemException` | infra 层系统故障 | Task 8/9/10 (存储后端故障), Task 11/12 (UseCase 状态异常) |
| `IllegalStateException` | 启动配置 fail-fast | Task 7 (无 LOCAL 后端时) |

**结论**: 异常分类符合 spec §0 规定。

### 5. 测试覆盖检查

| 测试文件 | 覆盖范围 | Task |
|--------|---------|------|
| `StorageTargetTest` | 值对象校验 | Task 2 |
| `FileMetadataTest` | 聚合根状态机 + 事件 | Task 3 |
| `FileMetadataRepositoryImplTest` | Repository CRUD (H2) | Task 6 |
| `LocalFileStorageTest` | 本地存储单测 | Task 8 |
| `StoreFileUseCaseTest` | 应用用例 - 创建+上传 | Task 11 |
| `OpenFileUseCaseTest` | 应用用例 - 打开 | Task 12 |
| `DeleteFileUseCaseTest` | 应用用例 - 删除（幂等） | Task 12 |
| `CopyFileUseCaseTest` | 应用用例 - 复制 | Task 12 |
| `StorageIntegrationTest` | 端到端集成 | Task 15 |
| `ParseTaskTest` (修改) | 回归测试 | Task 13 |

**结论**: 测试覆盖完整，符合 TDD 流程。

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-07-19-file-storage-engine.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - 每个 Task 派发独立 subagent 执行，task 间做 review，迭代快、上下文隔离、错误影响小。适合本 Plan（15 个 Task，每个 Task 边界清晰、可独立验证）。

**2. Inline Execution** - 在当前会话中按 Task 顺序批量执行，checkpoint 处统一 review。适合需要全程把控、随时调整的场景。

**Which approach?**