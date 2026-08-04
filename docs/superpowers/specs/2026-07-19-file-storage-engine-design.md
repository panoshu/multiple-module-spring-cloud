# file-service 文件存储引擎设计

**项目**: multiple-module-spring-cloud / file-service **作者**: Trae (基于用户需求 brainstorming 产出)
**日期**: 2026-07-19 **状态**: 已确认，待生成实施计划 **关联**: 11.2 中期补全 / 子项目 A（共 5 个子项目 A/B/C/D/E）

---

## 0. 背景与目标

### 0.1 背景

业务要求 file-service 提供完整的文件上传/存储/下载能力，并支持 OSS/NAS 多后端存储，为后续 Token 机制、跨服务事件链路、外部表单导出上传打下基础。

当前 file-service 仅有 `FileStorageGateway.open(String fileRef)` 单一方法，文件以路径字符串形式引用，缺乏统一的文件元数据管理。

### 0.2 总体目标

1. 在 file-service 内部建立 **完整的文件存储子域**
2. 将 `FileId` 提升为正式领域概念
3. 提供多后端（Local / 阿里云 OSS / NAS）统一抽象
4. 重构 `FileStorageGateway` SPI，封装存储细节
5. 迁移 `ParseTask.sourceFileRef: String` → `sourceFileId: FileId`

### 0.3 子项目分解（5 个）

| 子项目           | 内容                                                      | 状态   |
|------------------|-----------------------------------------------------------|--------|
| **A（本 spec）** | 文件存储子域（元数据 + 多后端 + Router + ParseTask 迁移） | 进行中 |
| B                | Token 机制（上传 token 申请/校验、API Controller）        | 待启动 |
| C                | 跨服务事件发布（FileUploadedEvent → MQ → 业务服务）       | 待启动 |
| D                | 外部表单导出上传（导出后复制到 EXPORT target）            | 待启动 |
| E                | business-core-kernel 侧 FileIntegrationGateway 实现       | 待启动 |

---

## 1. 架构与范围

### 1.1 设计目标

在 file-service 内部建立 **完整的文件存储子域**，将 `FileId` 提升为正式领域概念，提供多后端（Local / 阿里云 OSS /
NAS）统一抽象，为后续 Token 机制、跨服务事件链路、外部表单导出上传打下基础。

### 1.2 在整体架构中的位置

```
┌──────────────────────────────────────────────────────────────┐
│  business-core-kernel                                        │
│    FileIntegrationGateway (SPI, 待 B 子项目重构)              │
│    - 当前 4 方法保留                                          │
└──────────────────────┬───────────────────────────────────────┘
                       │ HTTP 调用 file-service API
                       ▼
┌──────────────────────────────────────────────────────────────┐
│  file-service                                                │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ file-api      FileStorageApi (新增, B 子项目)           │  │
│  │                - applyUploadToken                      │  │
│  │                - applyDownloadToken                    │  │
│  │                - downloadByToken                       │  │
│  └────────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ file-domain   FileMetadata (聚合根, 新增)              │  │
│  │                StorageTarget (值对象, 新增)             │  │
│  │                StorageType (枚举: LOCAL/OSS/NAS)        │  │
│  │                FileUsage (枚举: SOURCE/PARSED/EXPORT)   │  │
│  │                FileStorageGateway (SPI, 重构)           │  │
│  │                FileMetadataRepository (新增)            │  │
│  └────────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ file-app      StoreFileUseCase (新增)                  │  │
│  │                OpenFileUseCase (新增)                  │  │
│  │                DeleteFileUseCase (新增)                │  │
│  │                CopyFileUseCase (新增)                  │  │
│  └────────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ file-infra    LocalFileStorage (新增)                  │  │
│  │                AliyunOSSFileStorage (新增)              │  │
│  │                NASFileStorage (新增)                   │  │
│  │                FileStorageRouter (新增, 路由分发)        │  │
│  │                FileMetadataRepositoryImpl (新增)        │  │
│  │                FileMetadataDO + Mapper (新增)           │  │
│  │                t_file_metadata 表 (新增)                │  │
│  │                StorageTargetProperties (配置绑定)       │  │
│  └────────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ file-adapter  FileStorageAdapter (新增, B 子项目)       │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
                       ▲
                       │ OSS SDK / NAS 挂载 / 本地 FS
                       ▼
                  物理存储后端
```

### 1.3 子项目 A 范围

**包含**：

- file-domain：FileMetadata 聚合根、StorageTarget 值对象、FileStorageGateway SPI 重构、FileMetadataRepository 接口
- file-application：StoreFileUseCase、OpenFileUseCase、DeleteFileUseCase、CopyFileUseCase
- file-infrastructure：3 个存储后端实现 + Router + Repository + DO + Mapper + 配置类
- file-service/application.yml：新增 `file.storage` 配置块
- 数据库：新增 `t_file_metadata` 表 schema-pg.sql
- ParseTask 迁移：sourceFileRef (String) → sourceFileId (FileId)
- 单元测试 + 集成测试

**不包含**：

- Token 机制（B 子项目）
- 跨服务事件发布（C 子项目）
- FileIntegrationGateway 业务侧实现（E 子项目）
- 外部表单导出上传（D 子项目）
- FileStorageApi 的 Controller 实现（B 子项目）
- 阿里云 OSS SDK 之外的 SDK（如 S3/Minio）—— 未来按需扩展

### 1.4 关键设计原则

1. **FileId 唯一标识**：对外不暴露 storageKey/targetId 等内部细节
2. **多目标配置**：每个 type 可有多个 target，业务按 usage 路由
3. **凭据隔离**：OSS AK/SK 通过 `${ENV_VAR}` 占位 + 环境变量注入
4. **Router 模式**：FileStorageGateway 接口的实现统一走 FileStorageRouter，Router 根据 FileMetadata.targetId 分发到具体后端
5. **不破坏现有解析流程**：ParseFileUseCase 通过 `fileStorageGateway.open(fileId)` 读取文件，迁移后行为不变

---

## 2. 领域模型

### 2.1 FileMetadata 聚合根

**位置**：`file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/root/FileMetadata.java`

**继承**：`AggregateRoot<FileId>`，遵循 03-领域模型约束

**字段**：

```java
public class FileMetadata extends AggregateRoot<FileId> {
    // 基础属性
    private String originalName;       // 原始文件名（含扩展名）
    private long size;                 // 字节数
    private String contentType;        // MIME 类型
    private String md5;                // 内容指纹（可选，上传时计算）

    // 存储路由（仅记录"路由到了哪里"，具体配置在 StorageTarget）
    private String targetId;           // 关联 StorageTarget.targetId
    private StorageType storageType;   // LOCAL / OSS / NAS
    private String storageKey;         // 后端内部 key/path

    // 业务上下文
    private FileUsage usage;
    private String bizType;
    private String sourceApp;
    private BatchId businessBatchId;   // 来自 com.example.shared.primitives.identity.BatchId

    // 生命周期
    private FileStatus status;
    private UserNo uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime expiresAt;

    @Override
    protected void validateInvariants() {
        if (size < 0) throw new DomainException(...);
        if (status == FileStatus.UPLOADED && uploadedAt == null) throw ...;
        ...
    }
}
```

### 2.2 FileStatus 状态机

```
                                    ┌─────────────┐
                  create()          │ PENDING_     │
                  ──────────────►    │ UPLOAD       │
                                    └──────┬───────┘
                                           │ markUploaded(storageKey, md5)
                                           ▼
                                    ┌─────────────┐
                                    │ UPLOADED     │
                                    └──────┬───────┘
                                           │ markDeleted()
                                           ▼
                                    ┌─────────────┐
                                    │ DELETED      │ (终态)
                                    └─────────────┘
```

**约束**：

- `create()` → PENDING_UPLOAD：仅记录元数据，文件未上传
- `markUploaded(storageKey, md5)` → UPLOADED：文件已落存储后端
- `markDeleted()` → DELETED：逻辑删除（不物理删除）
- `reconstitute(...)` 静态方法用于 Repository 重建
- DELETED 状态下 `open()` 抛 `FILE_METADATA_NOT_FOUND`

### 2.3 FileUsage 枚举（路由关键）

```java
public enum FileUsage {
    SOURCE,     // 用户上传的原始表单
    PARSED,     // 解析后的 JSON 数据
    EXPORT,     // 导出的外部 Excel
    ARCHIVE     // 归档文件
}
```

**作用**：当业务层申请上传 token 时，仅需指定 `FileUsage`，由 `FileStorageRouter` 根据 `application.yml` 的
`file.storage.routing` 配置路由到具体 `StorageTarget`。 **业务层不感知具体目标**。

### 2.4 StorageType 枚举

```java
public enum StorageType {
    LOCAL,  // 本地文件系统
    OSS,    // 阿里云 OSS（含私有化部署）
    NAS     // NAS 挂载盘
}
```

### 2.5 StorageTarget 值对象

**位置**：`file-domain/model/aggregate/valueobject/StorageTarget.java`

```java
public record StorageTarget(
    String targetId,
    StorageType type,
    String endpoint,           // OSS only
    String bucket,             // OSS / NAS
    String basePath,
    String mountRoot,          // NAS only, 默认 /mnt/nas
    String accessKeyId,        // OSS only
    String accessKeySecret,    // OSS only
    Map<String, String> options  // OSS: {region=cn-hangzhou, ...}
) implements ValueObject {
    // 构造校验：OSS 必须有 endpoint+bucket+AK/SK；NAS 必须有 bucket(share名) + basePath；LOCAL basePath 必填
}
```

**关键**：这是值对象（record + 不可变），不属于聚合根。生命周期由 `StorageTargetProperties` 配置加载管理，不持久化到 DB。

### 2.6 FileStorageGateway SPI 重构

**位置**：`file-domain/gateway/FileStorageGateway.java`

```java
public interface FileStorageGateway {

    /**
     * 存储文件流。由 FileStorageRouter 根据 FileMetadata.targetId 路由到具体后端。
     * 调用前 FileMetadata 必须已 create() 并持久化（status=PENDING_UPLOAD）。
     * 调用后由应用层调 markUploaded() 完成状态流转。
     */
    void store(FileId fileId, InputStream content, long contentLength);

    /**
     * 打开文件流。按 FileId 查 FileMetadata 后路由到具体后端。
     * 调用方必须 try-with-resources 关闭流。
     */
    InputStream open(FileId fileId);

    /**
     * 判断文件是否存在于存储后端。
     */
    boolean exists(FileId fileId);

    /**
     * 复制文件到新用途对应的目标。返回 CopyResult（包含新 FileId 和新 storageKey）。
     * 用于跨后端迁移（如 EXPORT → ARCHIVE）。
     * 新 FileMetadata 由调用方（CopyFileUseCase）基于 CopyResult 创建并持久化。
     */
    CopyResult copy(FileId srcFileId, FileUsage targetUsage, BatchId businessBatchId);

    /**
     * 计算文件 MD5（用于上传完整性校验）。
     */
    String computeMd5(FileId fileId);
}
```

**重要约束**：

- SPI 签名仅用 `FileId`， **不暴露** `storageKey` / `targetId` / `StorageTarget` 给应用层
- `FileMetadata` 的 `targetId` / `storageKey` 由 `FileStorageRouter` 内部填充并持久化
- **不暴露 delete**：删除文件采用逻辑删除，由 `DeleteFileUseCase` 调 `file.markDeleted()` 完成

### 2.7 FileMetadataRepository 接口

**位置**：`file-domain/repository/FileMetadataRepository.java`

```java
public interface FileMetadataRepository extends Repository<FileMetadata, FileId> {
    // 继承基础契约：load/save/delete/deleteById/loadAll/streamByAppId

    /** 按业务批次查询文件 */
    List<FileMetadata> findByBusinessBatchId(String businessBatchId);

    /** 按 usage + bizType 查询（用于清理过期文件） */
    List<FileMetadata> findByUsageAndBizType(FileUsage usage, String bizType);

    /** 查询已过期但未删除的文件（定时清理任务用） */
    List<FileMetadata> findExpiredBefore(LocalDateTime before);
}
```

### 2.8 领域事件

**新增 3 个领域事件**（位于 `file-domain/event/`）：

```java
// 文件元数据已创建（PENDING_UPLOAD 状态）
public record FileMetadataCreatedEvent(
    EventId eventId, LocalDateTime occurredOn,
    FileId fileId, FileUsage usage, String bizType,
    String sourceApp, BatchId businessBatchId
) implements DomainEvent {
    static FileMetadataCreatedEvent of(FileMetadata file) { ... }
}

// 文件已上传（UPLOADED 状态，含 storageKey、md5）
public record FileUploadedEvent(
    EventId eventId, LocalDateTime occurredOn,
    FileId fileId, String originalName, long size,
    String contentType, String md5, FileUsage usage
) implements DomainEvent {
    static FileUploadedEvent of(FileMetadata file) { ... }
}

// 文件已删除
public record FileDeletedEvent(
    EventId eventId, LocalDateTime occurredOn,
    FileId fileId, String originalName, String deletedBy
) implements DomainEvent {
    static FileDeletedEvent of(FileMetadata file, UserNo deletedBy) { ... }
}
```

**事件触发时机**：

- `FileMetadata.create()` → 注册 `FileMetadataCreatedEvent`
- `FileMetadata.markUploaded()` → 注册 `FileUploadedEvent`
- `FileMetadata.markDeleted()` → 注册 `FileDeletedEvent`

**注意**：这些事件当前仅在进程内发布（C 子项目之前），为 C 子项目启用 shared-event-starter 后自动转为 MQ 消息。

### 2.9 错误码

**位置**：`file-domain/errorcode/FileErrorCodes.java`（已存在，需补充）

```java
public enum FileErrorCodes implements ErrorDefinition {
    // 已有错误码保持不变...

    // 新增：文件元数据相关
    FILE_METADATA_NOT_FOUND("FILE_METADATA_NOT_FOUND", "文件元数据不存在"),
    FILE_ALREADY_UPLOADED("FILE_ALREADY_UPLOADED", "文件已上传，不能重复上传"),
    FILE_STATUS_INVALID("FILE_STATUS_INVALID", "文件状态不允许此操作"),
    FILE_EXPIRED("FILE_EXPIRED", "文件已过期"),

    // 新增：存储后端相关
    FILE_STORAGE_FAILED("FILE_STORAGE_FAILED", "文件存储失败"),
    FILE_STORAGE_TARGET_NOT_FOUND("FILE_STORAGE_TARGET_NOT_FOUND", "存储目标不存在"),
    FILE_STORAGE_TARGET_TYPE_MISMATCH("FILE_STORAGE_TARGET_TYPE_MISMATCH", "存储目标类型不匹配"),
    FILE_STORAGE_CONFIG_INVALID("FILE_STORAGE_CONFIG_INVALID", "存储配置无效"),
    FILE_COPY_FAILED("FILE_COPY_FAILED", "文件复制失败"),
    FILE_MD5_MISMATCH("FILE_MD5_MISMATCH", "文件 MD5 校验失败"),

    // 新增：Download/Read 相关
    FILE_DOWNLOAD_FAILED("FILE_DOWNLOAD_FAILED", "文件下载失败"),
    FILE_STREAM_CLOSED("FILE_STREAM_CLOSED", "文件流已关闭");
}
```

### 2.10 不变式总结

| 聚合根        | 不变式                                                                                                              |
|---------------|---------------------------------------------------------------------------------------------------------------------|
| FileMetadata  | `size >= 0`；`UPLOADED` 状态下 `uploadedAt != null && storageKey != null`；`storageKey` 不对外暴露（getter 不返回） |
| StorageTarget | `OSS` 类型必须 endpoint+bucket+AK/SK 齐全；`NAS` 类型必须有 bucket(共享名) + basePath；`LOCAL` 类型 basePath 必填   |

### 2.11 与现有 ParseTask 的关系

```
ParseTask (聚合根)
├── sourceFileId: FileId      ← 新字段（原 sourceFileRef: String）
├── sourceFileName: String    ← 保留（便于直接展示）
└── 1 个 ParseTask 对应 1 个 FileMetadata (SOURCE 类型)

SubTaskData (聚合根)
├── parsedJsonFileId: FileId  ← 新字段（如果有）
└── 1 个 SubTaskData 对应 0/1 个 FileMetadata (PARSED 类型)
```

**关键**：FileMetadata 与 ParseTask/SubTaskData 是 **关联关系**（通过 FileId 引用），不是聚合内包含。FileMetadata
是独立聚合根，可独立加载/删除。

---

## 3. SPI 接口与应用用例

### 3.1 FileStorageGateway SPI 最终签名

详见 §2.6。

**与原接口对比**：

| 原签名                             | 新签名                                            | 变化                              |
|------------------------------------|---------------------------------------------------|-----------------------------------|
| `InputStream open(String fileRef)` | `InputStream open(FileId fileId)`                 | String → FileId                   |
| -                                  | `store(...)`                                      | 新增                              |
| -                                  | `exists(FileId)`                                  | 新增                              |
| -                                  | `copy(...)` 返回 `CopyResult(fileId, storageKey)` | 新增                              |
| -                                  | `computeMd5(FileId)`                              | 新增                              |
| -                                  | ~~`delete(FileId)`~~                              | 不暴露（逻辑删除由 UseCase 完成） |

### 3.2 StorageTargetResolver（独立 SPI）

**位置**：`file-domain/gateway/StorageTargetResolver.java`

```java
public interface StorageTargetResolver {
    /**
     * 按 FileUsage 路由到具体的 StorageTarget。
     * 实现基于 application.yml 的 file.storage.routing 配置。
     */
    StorageTarget resolveByUsage(FileUsage usage, String bizType);

    /**
     * 按 targetId 直接查询（用于 FileStorageRouter 内部分发）。
     */
    StorageTarget resolveById(String targetId);

    /**
     * 列出所有配置的 StorageTarget（管理用）。
     */
    List<StorageTarget> listAll();
}
```

**作用**：将 `FileUsage` → `StorageTarget` 的映射逻辑封装为独立 SPI，避免 `FileStorageGateway` 承担过多职责。实现位于
infrastructure 层，基于 `StorageTargetProperties` 配置。

### 3.3 应用用例

#### 3.3.1 StoreFileUseCase（拆分为 createMetadata + store 两个方法）

**设计理由**：

- **业务交互分离**：createMetadata 由业务服务调用（短事务），store 由前端凭 token 调用（文件流事务）
- **事务边界清晰**：元数据登记 + 文件流存储独立事务
- **Token 机制契合**：createMetadata 后申请 token，前端凭 token 调 store

```java
@RequiredArgsConstructor
@Service
public class StoreFileUseCase {
    private final FileMetadataRepository metadataRepository;
    private final FileStorageGateway storageGateway;
    private final StorageTargetResolver targetResolver;
    private final EventBus eventBus;

    /**
     * 创建文件元数据（PENDING_UPLOAD 状态），返回 fileId。
     * 不实际存储，仅占位。
     */
    @Transactional
    public FileId createMetadata(StoreFileCommand command) {
        FileId fileId = FileId.generate();
        StorageTarget target = targetResolver.resolveByUsage(command.usage(), command.bizType());
        FileMetadata file = FileMetadata.create(
            fileId, command.originalName(), command.size(),
            command.contentType(), command.usage(), command.bizType(),
            command.sourceApp(), command.businessBatchId(),
            target.targetId(), target.type(),
            command.uploadedBy(), command.expiresAt()
        );
        metadataRepository.save(file);
        file.getDomainEvents().forEach(eventBus::publish);
        file.clearDomainEvents();
        return fileId;
    }

    /**
     * 实际存储文件流到后端，并标记 UPLOADED。
     */
    @Transactional
    public void store(FileId fileId, InputStream content, long contentLength) {
        FileMetadata file = metadataRepository.loadOrThrow(fileId);
        if (file.status() != FileStatus.PENDING_UPLOAD) {
            throw new SystemException(FileErrorCodes.FILE_ALREADY_UPLOADED)
                .withLogDetail("fileId=" + fileId);
        }
        storageGateway.store(fileId, content, contentLength);
        String md5 = storageGateway.computeMd5(fileId);
        file.markUploaded(generateStorageKey(file), md5);
        metadataRepository.save(file);
        file.getDomainEvents().forEach(eventBus::publish);
        file.clearDomainEvents();
    }

    private String generateStorageKey(FileMetadata file) {
        // 详见 §5.6
    }
}
```

#### 3.3.2 OpenFileUseCase

```java
@RequiredArgsConstructor
@Service
public class OpenFileUseCase {
    private final FileMetadataRepository metadataRepository;
    private final FileStorageGateway storageGateway;

    /**
     * 打开文件流。调用方必须 try-with-resources。
     * 不在事务内（流必须保持打开）。
     */
    public InputStream open(FileId fileId) {
        FileMetadata file = metadataRepository.loadOrThrow(fileId);
        if (file.status() == FileStatus.DELETED) {
            throw new SystemException(FileErrorCodes.FILE_METADATA_NOT_FOUND)
                .withLogDetail("fileId=" + fileId + " 已删除");
        }
        if (file.isExpired()) {
            throw new SystemException(FileErrorCodes.FILE_EXPIRED);
        }
        return storageGateway.open(fileId);
    }

    @Transactional(readOnly = true)
    public FileMetadata loadMetadata(FileId fileId) {
        return metadataRepository.loadOrThrow(fileId);
    }
}
```

#### 3.3.3 DeleteFileUseCase（逻辑删除）

```java
@RequiredArgsConstructor
@Service
public class DeleteFileUseCase {
    private final FileMetadataRepository metadataRepository;
    private final EventBus eventBus;

    @Transactional
    public void delete(FileId fileId, UserNo deletedBy) {
        FileMetadata file = metadataRepository.loadOrThrow(fileId);
        if (file.status() == FileStatus.DELETED) {
            return;  // 幂等
        }
        file.markDeleted(deletedBy);   // 仅标记逻辑删除，不调 storageGateway.delete()
        metadataRepository.save(file);
        file.getDomainEvents().forEach(eventBus::publish);
        file.clearDomainEvents();
    }
}
```

#### 3.3.4 CopyFileUseCase（纳入 A 子项目）

**设计说明**：copy 操作的物理文件复制由 `FileStorageGateway.copy()` 完成（内部生成新 storageKey）。但 `markUploaded` 需要
storageKey 入参——因此 `FileStorageGateway.copy()` 的返回值需要包含 storageKey（不只是 FileId）。

**调整方案**：将 `FileStorageGateway.copy()` 返回值改为 `CopyResult` record，包含新 FileId 和新 storageKey。

```java
// 新增返回值类型
public record CopyResult(FileId newFileId, String newStorageKey) {}

@RequiredArgsConstructor
@Service
public class CopyFileUseCase {
    private final FileMetadataRepository metadataRepository;
    private final FileStorageGateway storageGateway;
    private final StorageTargetResolver targetResolver;
    private final EventBus eventBus;

    @Transactional
    public FileId copy(CopyFileCommand command) {
        FileMetadata srcFile = metadataRepository.loadOrThrow(command.srcFileId());
        CopyResult copyResult = storageGateway.copy(
            command.srcFileId(), command.targetUsage(), command.businessBatchId()
        );
        StorageTarget dstTarget = targetResolver.resolveByUsage(
            command.targetUsage(), srcFile.bizType()
        );
        FileMetadata newFile = FileMetadata.create(
            copyResult.newFileId(), srcFile.originalName(), srcFile.size(),
            srcFile.contentType(), command.targetUsage(), srcFile.bizType(),
            srcFile.sourceApp(), command.businessBatchId(),
            dstTarget.targetId(), dstTarget.type(),
            command.operatedBy(), null
        );
        newFile.markUploaded(copyResult.newStorageKey(), srcFile.md5());
        metadataRepository.save(newFile);
        newFile.getDomainEvents().forEach(eventBus::publish);
        newFile.clearDomainEvents();
        return copyResult.newFileId();
    }
}
```

**同步调整 §2.6 FileStorageGateway.copy 签名**：

```java
CopyResult copy(FileId srcFileId, FileUsage targetUsage, BatchId businessBatchId);
```

### 3.4 命令对象（Command）

```java
public record StoreFileCommand(
    String originalName,
    long size,
    String contentType,
    FileUsage usage,
    String bizType,
    String sourceApp,
    BatchId businessBatchId,
    UserNo uploadedBy,
    LocalDateTime expiresAt     // null = 永久
) {}

public record CopyFileCommand(
    FileId srcFileId,
    FileUsage targetUsage,
    BatchId businessBatchId,
    UserNo operatedBy
) {}
```

### 3.5 应用服务编排顺序

**完整文件上传流程**（B 子项目将在此流程上叠加 Token）：

```
1. 业务服务调用 file-service API: createMetadata(StoreFileCommand)
   ↓ StoreFileUseCase.createMetadata()
   ↓ - 查 StorageTarget by usage
   ↓ - FileMetadata.create() → PENDING_UPLOAD + 注册 FileMetadataCreatedEvent
   ↓ - metadataRepository.save()
   ↓ - eventBus.publish(FileMetadataCreatedEvent)
   ↓ 返回 fileId

2. (B 子项目) 申请上传 token，前端直传到 file-service

3. file-service 接收文件流，调用 store(fileId, stream, length)
   ↓ StoreFileUseCase.store()
   ↓ - storageGateway.store() → Router 分发到具体后端
   ↓ - storageGateway.computeMd5()
   ↓ - file.markUploaded(storageKey, md5) → UPLOADED + 注册 FileUploadedEvent
   ↓ - metadataRepository.save()
   ↓ - eventBus.publish(FileUploadedEvent)
```

### 3.6 现有 ParseFileUseCase 集成点

```java
// 迁移前
try (InputStream stream = fileStorage.open(task.sourceFileRef())) { ... }

// 迁移后
try (InputStream stream = fileStorage.open(task.sourceFileId())) { ... }
```

唯一变化：`sourceFileRef` (String) → `sourceFileId` (FileId)。SPI 签名变化已隔离差异。

### 3.7 应用用例清单

| 用例                | 文件                     | 用途                       |
|---------------------|--------------------------|----------------------------|
| `StoreFileUseCase`  | `StoreFileUseCase.java`  | 创建元数据 + 存储文件流    |
| `OpenFileUseCase`   | `OpenFileUseCase.java`   | 打开文件流 + 查询元数据    |
| `DeleteFileUseCase` | `DeleteFileUseCase.java` | 逻辑删除文件               |
| `CopyFileUseCase`   | `CopyFileUseCase.java`   | 跨后端复制（D 子项目复用） |

### 3.8 关键决策表

| 项目                  | 决策                          | 理由                                        |
|-----------------------|-------------------------------|---------------------------------------------|
| 文件删除              | 逻辑删除，SPI 不暴露 delete   | 审计需要保留物理文件                        |
| StorageTargetResolver | 独立 SPI                      | 单一职责，避免 FileStorageGateway 过重      |
| CopyFileUseCase       | 纳入 A 子项目                 | SPI 完整性，D 子项目复用                    |
| StoreFileUseCase      | 拆分为 createMetadata + store | 业务交互分离，事务边界清晰，契合 Token 机制 |

---

## 4. 配置模型与数据库设计

### 4.1 配置模型

**位置**：`file-infrastructure/configuration/StorageTargetProperties.java`

```java
@ConfigurationProperties(prefix = "file.storage")
@Validated
public class StorageTargetProperties {

    @NotEmpty
    private List<StorageTargetConfig> targets = new ArrayList<>();

    @NotNull
    private RoutingConfig routing;

    /** 单个存储目标配置 */
    public static class StorageTargetConfig {
        @NotBlank
        private String id;              // 如 "oss-source"

        @NotNull
        private StorageType type;       // LOCAL / OSS / NAS

        private String endpoint;        // OSS: https://oss.private.example.com

        private String bucket;          // OSS: bucket; NAS: 共享名

        private String basePath;        // 子目录前缀

        private String mountRoot;       // NAS only, 默认 /mnt/nas

        private String accessKeyId;     // OSS: ${OSS_AK}

        private String accessKeySecret; // OSS: ${OSS_SK}

        private Map<String, String> options = new HashMap<>();
    }

    /** 用途路由配置 */
    public static class RoutingConfig {
        @NotBlank
        private String source;          // SOURCE → targetId
        @NotBlank
        private String parsed;          // PARSED → targetId
        @NotBlank
        private String export;          // EXPORT → targetId
        @NotBlank
        private String archive;         // ARCHIVE → targetId
    }
}
```

### 4.2 application.yml 配置示例

**位置**：`file-service/file-starter/src/main/resources/application.yml`（追加）

```yaml
file:
  storage:
    enabled: true
    targets:
      # 本地开发用
      - id: local-dev
        type: LOCAL
        base-path: /data/files

      # 阿里云 OSS - 原始上传与解析结果
      - id: oss-source
        type: OSS
        endpoint: ${OSS_ENDPOINT:https://oss.private.example.com}
        bucket: forms-source
        base-path: forms/2026
        access-key-id: ${OSS_AK}
        access-key-secret: ${OSS_SK}
        options:
          region: cn-private

      # 阿里云 OSS - 导出的外部 Excel
      - id: oss-export
        type: OSS
        endpoint: ${OSS_ENDPOINT:https://oss.private.example.com}
        bucket: forms-export
        base-path: exports/2026
        access-key-id: ${OSS_AK}
        access-key-secret: ${OSS_SK}
        options:
          region: cn-private

      # NAS - 归档
      - id: nas-archive
        type: NAS
        bucket: nas-share              # 挂载的共享名
        base-path: /archive/forms
        mount-root: /mnt/nas

    routing:
      source: oss-source
      parsed: oss-source
      export: oss-export
      archive: nas-archive
```

**环境变量约定**：

- `OSS_ENDPOINT`：私有化 OSS 地址
- `OSS_AK` / `OSS_SK`：访问凭据
- 生产环境通过 K8s Secret 或环境变量注入，不写入代码

**本地开发 profile**（application-local.yml）：

```yaml
file:
  storage:
    targets:
      - id: local-dev
        type: LOCAL
        base-path: ./target/test-files
    routing:
      source: local-dev
      parsed: local-dev
      export: local-dev
      archive: local-dev
```

### 4.3 数据库表设计

**新增表**：`t_file_metadata`

```sql
-- 文件元数据表
CREATE TABLE IF NOT EXISTS t_file_metadata (
    id                  VARCHAR(64)   NOT NULL,
    original_name       VARCHAR(512)  NOT NULL,
    size                BIGINT        NOT NULL,
    content_type        VARCHAR(128),
    md5                 VARCHAR(64),

    -- 存储路由
    target_id           VARCHAR(64)   NOT NULL,
    storage_type        VARCHAR(20)   NOT NULL,
    storage_key         VARCHAR(1024) NOT NULL,

    -- 业务上下文
    usage               VARCHAR(20)   NOT NULL,
    biz_type            VARCHAR(64),
    source_app          VARCHAR(64),
    business_batch_id   VARCHAR(64),

    -- 生命周期
    status              VARCHAR(20)   NOT NULL,
    uploaded_by         VARCHAR(64),
    uploaded_at         TIMESTAMP,
    expires_at          TIMESTAMP,

    -- 审计字段（继承 BaseEntity）
    created_by          VARCHAR(64)   NOT NULL,
    updated_by          VARCHAR(64),
    created_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN       DEFAULT FALSE,
    version             INT           DEFAULT 0,

    PRIMARY KEY (id)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_file_metadata_batch_id ON t_file_metadata(business_batch_id);
CREATE INDEX IF NOT EXISTS idx_file_metadata_usage_biz_type ON t_file_metadata(usage, biz_type);
CREATE INDEX IF NOT EXISTS idx_file_metadata_status_expires ON t_file_metadata(status, expires_at) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_file_metadata_target_id ON t_file_metadata(target_id);

-- 注释
COMMENT ON TABLE t_file_metadata IS '文件元数据表';
COMMENT ON COLUMN t_file_metadata.id IS '文件ID（FileId）';
COMMENT ON COLUMN t_file_metadata.original_name IS '原始文件名';
COMMENT ON COLUMN t_file_metadata.size IS '文件大小（字节）';
COMMENT ON COLUMN t_file_metadata.content_type IS 'MIME 类型';
COMMENT ON COLUMN t_file_metadata.md5 IS '内容 MD5 指纹';
COMMENT ON COLUMN t_file_metadata.target_id IS '存储目标 ID（关联 StorageTarget）';
COMMENT ON COLUMN t_file_metadata.storage_type IS '存储类型: LOCAL/OSS/NAS';
COMMENT ON COLUMN t_file_metadata.storage_key IS '后端内部 key/path（不对外暴露）';
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
```

**SQL 文件位置**：

- `file-service/file-infrastructure/src/main/resources/schema-pg.sql`（新建）

### 4.4 ParseTask 表结构变更

```sql
-- 修改 source_file_ref → source_file_id
-- 旧字段：source_file_ref VARCHAR(1024)
-- 新字段：source_file_id  VARCHAR(64)

ALTER TABLE t_file_parse_task
    RENAME COLUMN source_file_ref TO source_file_id;

ALTER TABLE t_file_parse_task
    ALTER COLUMN source_file_id TYPE VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_parse_task_source_file_id
    ON t_file_parse_task(source_file_id);
```

**不提供数据迁移脚本**（用户已确认）。

### 4.5 DO 实体设计

**位置**：`file-infrastructure/entity/FileMetadataDO.java`

```java
@Table("t_file_metadata")
@Data
public class FileMetadataDO {
    @Id
    private String id;                  // FileId.value()
    private String originalName;
    private Long size;
    private String contentType;
    private String md5;

    // 存储路由
    private String targetId;
    private String storageType;         // StorageType.name()
    private String storageKey;

    // 业务上下文
    private String usage;               // FileUsage.name()
    private String bizType;
    private String sourceApp;
    private String businessBatchId;     // BatchId.value()

    // 生命周期
    private String status;              // FileStatus.name()
    private String uploadedBy;          // UserNo.value()
    private LocalDateTime uploadedAt;
    private LocalDateTime expiresAt;

    // 审计字段
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Column(isLogicDelete = true)
    private Boolean deleted;
    @Version
    private Integer version;
}
```

### 4.6 Mapper 设计

**位置**：`file-infrastructure/mapper/FileMetadataMapper.java`

```java
public interface FileMetadataMapper extends BaseMapper<FileMetadataDO> {
    List<FileMetadataDO> selectByBusinessBatchId(@Param("businessBatchId") String batchId);
    List<FileMetadataDO> selectByUsageAndBizType(
        @Param("usage") String usage,
        @Param("bizType") String bizType
    );
    List<FileMetadataDO> selectExpiredBefore(@Param("before") LocalDateTime before);
}
```

### 4.7 Converter 设计

采用与 `ParseTaskConverter` 相同的模式：

- `toDomain(DO)` → `default` 方法，手动调用 `FileMetadata.reconstitute(...)`
- `toDO(FileMetadata)` → `default` 方法，手动构造 DO 并赋值
- 枚举/ID 类型转换通过 `@Named` 辅助方法或 `expression = "java(...)"`

### 4.8 配置加载与校验

```java
@Configuration
@EnableConfigurationProperties(StorageTargetProperties.class)
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StorageTargetResolver storageTargetResolver(StorageTargetProperties props) {
        return new PropertiesBasedStorageTargetResolver(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public FileStorageGateway fileStorageGateway(
            FileMetadataRepository metadataRepository,
            StorageTargetResolver targetResolver,
            List<FileStorageBackend> backends
    ) {
        return new FileStorageRouter(metadataRepository, targetResolver, backends);
    }
}
```

**校验逻辑**（fail-fast）：

- 校验 `routing.source/parsed/export/archive` 对应的 targetId 都存在
- 校验 OSS 类型的 target 都有 endpoint + bucket + AK/SK
- 校验 NAS 类型的 target 都有 bucket（共享名）+ basePath
- 校验 LOCAL 类型的 target 都有 basePath
- targetId 全局唯一

**启动失败策略**：校验不通过抛 `IllegalStateException`，应用启动失败。

---

## 5. 存储后端实现与 Router

### 5.1 类层次结构

```
file-domain/gateway/
├── FileStorageGateway.java          (端口)
└── StorageTargetResolver.java       (端口)

file-infrastructure/storage/
├── FileStorageBackend.java          (新增 SPI, 后端抽象)
│   ├── LocalFileStorage.java        (实现: 本地文件系统)
│   ├── AliyunOSSFileStorage.java    (实现: 阿里云 OSS)
│   └── NASFileStorage.java          (实现: NAS 挂载盘)
├── FileStorageRouter.java           (端口实现: 路由分发)
├── StorageTargetProperties.java     (配置绑定)
├── PropertiesBasedStorageTargetResolver.java  (Resolver 实现)
└── StorageAutoConfiguration.java    (自动装配)
```

### 5.2 FileStorageBackend SPI（新增，基础设施层内部抽象）

**位置**：`file-infrastructure/storage/FileStorageBackend.java`

```java
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

**设计要点**：

- 这是 **基础设施层内部抽象**，不放到 domain 层（避免 domain 感知存储后端细节）
- `supportedType()` 用于 Router 启动时建立 `Map<StorageType, FileStorageBackend>` 分发映射
- 后端实现只关心 `StorageTarget` + `storageKey`，不感知 FileId/FileMetadata

### 5.3 LocalFileStorage 实现

```java
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

### 5.4 AliyunOSSFileStorage 实现

```java
@Slf4j
@Component
@ConditionalOnClass(name = "com.aliyun.oss.OSS")
public class AliyunOSSFileStorage implements FileStorageBackend, DisposableBean {

    private final Map<String, OSS> clientCache = new ConcurrentHashMap<>();

    @Override
    public StorageType supportedType() {
        return StorageType.OSS;
    }

    @Override
    public void store(StorageTarget target, String storageKey,
                      InputStream content, long contentLength) {
        OSS client = getClient(target);
        ObjectMetadata metadata = new ObjectMetadata();
        if (contentLength > 0) {
            metadata.setContentLength(contentLength);
        }
        if (contentLength > 100 * 1024 * 1024) {
            storeByMultipart(client, target, storageKey, content, contentLength, metadata);
        } else {
            client.putObject(target.bucket(), storageKey, content, metadata);
        }
    }

    @Override
    public InputStream open(StorageTarget target, String storageKey) {
        OSS client = getClient(target);
        OSSObject object = client.getObject(target.bucket(), storageKey);
        return object.getObjectContent();
    }

    @Override
    public boolean exists(StorageTarget target, String storageKey) {
        OSS client = getClient(target);
        return client.doesObjectExist(target.bucket(), storageKey);
    }

    @Override
    public void copy(StorageTarget target, String srcKey, String dstKey) {
        OSS client = getClient(target);
        client.copyObject(target.bucket(), srcKey, target.bucket(), dstKey);
    }

    @Override
    public String computeMd5(StorageTarget target, String storageKey) {
        OSS client = getClient(target);
        ObjectMetadata meta = client.getObjectMetadata(target.bucket(), storageKey);
        return meta.getETag();
    }

    private OSS getClient(StorageTarget target) {
        return clientCache.computeIfAbsent(target.targetId(), k -> {
            OSSClientBuilder builder = new OSSClientBuilder();
            return builder.build(target.endpoint(), target.accessKeyId(), target.accessKeySecret());
        });
    }

    private void storeByMultipart(OSS client, StorageTarget target, String storageKey,
                                  InputStream content, long contentLength, ObjectMetadata metadata) {
        UploadFileRequest request = new UploadFileRequest(target.bucket(), storageKey);
        request.setInputStream(content);
        request.setObjectMetadata(metadata);
        request.setPartSize(10 * 1024 * 1024);
        request.setTaskNum(4);
        try {
            client.uploadFile(request);
        } catch (Throwable t) {
            throw new SystemException(FileErrorCodes.FILE_STORAGE_FAILED, t)
                .withLogDetail("multipart upload failed, key=" + storageKey);
        }
    }

    @Override
    public void destroy() {
        clientCache.values().forEach(OSS::shutdown);
    }
}
```

**关键点**：

- `@ConditionalOnClass` 确保未引入 OSS SDK 时不加载
- OSS 客户端按 targetId 缓存
- 大文件（>100MB）自动走分片上传
- 实现 `DisposableBean` 在应用关闭时释放 OSS 连接

**Maven 依赖**（file-infrastructure/pom.xml 新增）：

```xml
<dependency>
    <groupId>com.aliyun.oss</groupId>
    <artifactId>aliyun-sdk-oss</artifactId>
    <version>3.17.4</version>
    <optional>true</optional>
</dependency>
```

### 5.5 NASFileStorage 实现

```java
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
            // 使用临时文件 + rename 保证原子性（NAS 多节点并发安全）
            Path tempPath = fullPath.resolveSibling(
                fullPath.getFileName() + ".tmp." + Thread.currentThread().getId());
            try (OutputStream out = Files.newOutputStream(tempPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                content.transferTo(out);
            }
            Files.move(tempPath, fullPath,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new SystemException(FileErrorCodes.FILE_STORAGE_FAILED, e)
                .withLogDetail("path=" + fullPath);
        }
    }

    // open/exists/copy/computeMd5 与 LocalFileStorage 完全相同
    // 建议提取到 AbstractFileSystemFileStorage 抽象基类复用

    private Path resolvePath(StorageTarget target, String storageKey) {
        // mountRoot + bucket + basePath + storageKey
        String mountRoot = target.mountRoot() != null ? target.mountRoot() : "/mnt/nas";
        return Paths.get(mountRoot, target.bucket(), target.basePath(), storageKey);
    }
}
```

**重构建议**：提取 `AbstractFileSystemFileStorage` 抽象基类，Local/NAS 继承，只覆写 `resolvePath` 和 `store`（NAS 需要原子
move）。

### 5.6 FileStorageRouter 实现

```java
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
        FileMetadata file = loadMetadataOrThrow(fileId);
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
        FileMetadata file = loadMetadataOrThrow(fileId);
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
    public CopyResult copy(FileId srcFileId, FileUsage targetUsage, BatchId businessBatchId) {
        FileMetadata srcFile = loadMetadataOrThrow(srcFileId);
        StorageTarget srcTarget = targetResolver.resolveById(srcFile.targetId());
        StorageTarget dstTarget = targetResolver.resolveByUsage(targetUsage, srcFile.bizType());
        FileStorageBackend srcBackend = resolveBackend(srcTarget.type());

        FileId newFileId = FileId.generate();
        String newStorageKey = generateStorageKeyForCopy(srcFile, newFileId);

        if (srcTarget.type() == dstTarget.type()) {
            FileStorageBackend backend = resolveBackend(dstTarget.type());
            backend.copy(dstTarget, srcFile.storageKey(), newStorageKey);
        } else {
            crossBackendCopy(srcBackend, srcTarget, srcFile.storageKey(),
                             resolveBackend(dstTarget.type()), dstTarget, newStorageKey);
        }

        return new CopyResult(newFileId, newStorageKey);
    }

    @Override
    public String computeMd5(FileId fileId) {
        FileMetadata file = loadMetadataOrThrow(fileId);
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

    private FileMetadata loadMetadataOrThrow(FileId fileId) {
        return metadataRepository.loadOrThrow(fileId);
    }

    /**
     * storageKey 生成规范:
     * {bizType}/{date}/{batchId}/{fileId}/{originalName}
     * 例: annuity/2026-07-19/BATCH_2026_001/01H8.../report.xlsx
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

### 5.7 PropertiesBasedStorageTargetResolver 实现

```java
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
            throw new SystemException(FileErrorCodes.FILE_STORAGE_TARGET_NOT_FOUND)
                .withLogDetail("targetId=" + targetId);
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

    /** fail-fast 校验 */
    private void validate() {
        RoutingConfig r = properties.getRouting();
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

### 5.8 StorageAutoConfiguration 自动装配

```java
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
```

**注册到自动装配 imports 文件**：
`file-infrastructure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### 5.9 关键依赖

**新增 Maven 依赖**（file-infrastructure/pom.xml）：

```xml
<!-- 阿里云 OSS SDK -->
<dependency>
    <groupId>com.aliyun.oss</groupId>
    <artifactId>aliyun-sdk-oss</artifactId>
    <version>3.17.4</version>
    <optional>true</optional>
</dependency>

<!-- MD5 工具 -->
<dependency>
    <groupId>commons-codec</groupId>
    <artifactId>commons-codec</artifactId>
</dependency>
```

### 5.10 关键设计决策总结

| 决策            | 选择                                                 | 理由                               |
|-----------------|------------------------------------------------------|------------------------------------|
| 后端抽象位置    | infra 层（非 domain）                                | domain 不感知存储后端细节          |
| 后端分发机制    | `Map<StorageType, Backend>` + `@PostConstruct`       | 启动时建立映射，O(1) 分发          |
| OSS 客户端管理  | 按 targetId 缓存 + `DisposableBean` 关闭             | 避免重复创建，确保资源释放         |
| 大文件处理      | >100MB 自动分片上传                                  | OSS 分片上传断点续传               |
| storageKey 规范 | `{bizType}/{date}/{batchId}/{fileId}/{originalName}` | 按业务可读 + 日期分区              |
| 跨后端 copy     | 源读 + 目标写流                                      | 不依赖后端原生跨域复制             |
| NAS 并发写入    | 临时文件 + atomic move                               | 多节点并发安全                     |
| NAS 路径        | 配置化 mountRoot                                     | 适配不同部署环境                   |
| 配置校验        | fail-fast                                            | 启动时发现配置错误，避免运行时崩溃 |

---

## 6. ParseTask 迁移与向后兼容

### 6.1 影响范围分析

通过 Grep 找到 6 个涉及 `sourceFileRef` 的文件：

| 文件                      | 层             | 影响点                                   |
|---------------------------|----------------|------------------------------------------|
| `ParseTask.java`          | domain         | 字段定义 + 构造函数 + getter             |
| `UploadFileCommand.java`  | application    | Command 字段                             |
| `UploadFileUseCase.java`  | application    | 创建 ParseTask 时传入                    |
| `ParseFileUseCase.java`   | application    | `fileStorage.open(task.sourceFileRef())` |
| `ParseTaskConverter.java` | infrastructure | DO ↔ Domain 字段映射                     |
| `ParseTaskDO.java`        | infrastructure | DO 字段                                  |

### 6.2 字段迁移策略

**字段更名 + 类型变更**：

```java
// 旧
private String sourceFileRef;

// 新
private FileId sourceFileId;
```

**为什么改类型为 `FileId`**：

- 与 §2 设计的 `FileMetadata` 聚合根建立引用关系
- 强类型化，避免 magic string
- SPI `FileStorageGateway.open(FileId)` 签名一致

### 6.3 ParseTask 改造（domain 层）

```java
public class ParseTask extends AggregateRoot<FileTaskId> {
  private String sourceFileName;
  private FileId sourceFileId;   // ← 类型从 String 改为 FileId

  // 业务创建构造函数
  private ParseTask(FileTaskId id, BizType bizType, String sourceFileName,
                    FileId sourceFileId,
                    ErrorPolicy errorPolicy, List<String> splitKeys, UserNo userNo) {
    super(id, userNo);
    this.sourceFileId = sourceFileId;
    // ...
  }

  // 数据库重建构造函数
  public ParseTask(FileTaskId id, BizType bizType, TemplateCode templateCode, String sourceFileName,
                   FileId sourceFileId,
                   TaskStatus status, ErrorPolicy errorPolicy, /* ... */) {
    // ...
  }

  // create 工厂方法
  public static ParseTask create(FileTaskId id, BizType bizType, String sourceFileName,
                                  FileId sourceFileId,
                                  ErrorPolicy errorPolicy, List<String> splitKeys, UserNo userNo) {
    // ...
  }

  public FileId sourceFileId() { return sourceFileId; }
  // 删除 public String sourceFileRef()
}
```

### 6.4 UploadFileCommand 改造

```java
public record UploadFileCommand(
    String bizType,
    String sourceFileName,
    FileId sourceFileId,        // ← 从 String sourceFileRef 改为 FileId
    String templateCode,
    String uploader
) {}
```

### 6.5 UploadFileUseCase 改造

```java
@Transactional
public UploadFileResult execute(UploadFileCommand cmd) {
    FileTaskId taskId = FileTaskId.generate();
    ParseTask task = ParseTask.create(
        taskId,
        BizType.of(cmd.bizType()),
        cmd.sourceFileName(),
        cmd.sourceFileId(),         // ← 直接传 FileId
        ErrorPolicy.COLLECT_ALL,
        List.of(),
        UserNo.of(cmd.uploader())
    );
    // ...
}
```

### 6.6 ParseFileUseCase 改造

```java
// 旧
try (InputStream inputStream = fileStorage.open(task.sourceFileRef())) { ... }
try (InputStream parseStream = fileStorage.open(task.sourceFileRef())) { ... }

// 新
try (InputStream inputStream = fileStorage.open(task.sourceFileId())) { ... }
try (InputStream parseStream = fileStorage.open(task.sourceFileId())) { ... }
```

### 6.7 ParseTaskDO 改造

**DO 字段类型保持 String**，仅在 Converter 中做转换：

```java
@Table("t_file_parse_task")
@Data
public class ParseTaskDO {
    private String sourceFileName;
    private String sourceFileId;   // ← 字段名改（DO 层仍是 String）
}
```

### 6.8 ParseTaskConverter 改造

```java
@Mapper(componentModel = "spring")
public interface ParseTaskConverter {

    @Mapping(target = "sourceFileId", expression = "java(fileIdToString(task.sourceFileId()))")
    ParseTaskDO toDO(ParseTask task);

    @Mapping(target = "sourceFileId", source = "sourceFileId", qualifiedByName = "toFileId")
    ParseTask toDomain(ParseTaskDO aDo);

    default String fileIdToString(FileId fileId) {
        return fileId != null ? fileId.value() : null;
    }

    @Named("toFileId")
    default FileId toFileId(String fileId) {
        return fileId != null ? new FileId(fileId) : null;
    }
}
```

### 6.9 数据库 schema 迁移

```sql
-- ParseTask 表字段重命名
ALTER TABLE t_file_parse_task
    RENAME COLUMN source_file_ref TO source_file_id;

ALTER TABLE t_file_parse_task
    ALTER COLUMN source_file_id TYPE VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_parse_task_source_file_id
    ON t_file_parse_task(source_file_id);
```

### 6.10 向后兼容性分析

| 场景                                                      | 兼容性        | 处理                             |
|-----------------------------------------------------------|---------------|----------------------------------|
| 现有代码调用 `task.sourceFileRef()`                       | ❌ 编译失败   | 改为 `task.sourceFileId()`       |
| 现有代码调用 `ParseTask.create(..., String fileRef, ...)` | ❌ 编译失败   | 改为传 `FileId`                  |
| 数据库历史数据 `source_file_ref` 字段值                   | ⚠️ 数据丢失   | 用户已确认不迁移                 |
| 调用 `fileStorage.open(String)`                           | ❌ 编译失败   | SPI 签名已改                     |
| UploadFileApi 接口接收 `sourceFileRef: String`            | ⚠️ API 不兼容 | 同步改 API DTO 为 `sourceFileId` |

### 6.11 迁移步骤顺序

```
1. file-domain: ParseTask 字段改造 + create/reconstitute 工厂调整
2. file-application: UploadFileCommand + UploadFileUseCase + ParseFileUseCase 改造
3. file-infrastructure: ParseTaskDO + ParseTaskConverter 改造
4. file-infrastructure: schema-pg.sql 新建表用 source_file_id
5. 测试代码: 全量替换 sourceFileRef → sourceFileId
6. 编译验证: mvn compile -pl file-service -am
7. 测试验证: mvn test -pl file-service
```

### 6.12 与 FileMetadata 的关联

**完整引用链**：

```
UploadFileCommand.sourceFileId (FileId)
    ↓
ParseTask.sourceFileId (FileId)
    ↓ fileStorage.open(task.sourceFileId())
    ↓ Router 查询 FileMetadata by FileId
    ↓
FileMetadata (聚合根, 含 storageKey/targetId)
    ↓ Router 路由
    ↓
StorageTarget + FileStorageBackend
    ↓
物理存储
```

**重要**：A 子项目里，`ParseTask` 创建时 **不强制要求**对应的 `FileMetadata` 已存在。但 `ParseFileUseCase.execute()` 调用
`fileStorage.open(fileId)` 时，Router 会查询 `FileMetadata`，若不存在会抛 `FILE_METADATA_NOT_FOUND`。

**这意味着**：在 A 子项目完成后，调用方必须先调 `StoreFileUseCase.createMetadata()` 创建 FileMetadata，再调
`UploadFileUseCase.execute()` 创建 ParseTask，最后调 `ParseFileUseCase.execute()` 执行解析。 **应用层编排保证**，domain
层不校验，做好日志记录即可。

---

## 7. 错误处理与测试策略

### 7.1 错误码扩展

详见 §2.9。

**错误码命名规范**：

- 前缀 `FILE_` 表示文件服务
- 中段表示模块（METADATA/STORAGE/DOWNLOAD）
- 后段表示具体错误
- 全大写 + 下划线分隔

### 7.2 异常分类与使用

| 异常类型                   | 使用场景                      | 示例                                   |
|----------------------------|-------------------------------|----------------------------------------|
| `DomainException`          | 业务规则违反（domain 层抛出） | `FileMetadata.markUploaded()` 状态非法 |
| `SystemException`          | 系统级故障（infra 层抛出）    | OSS 上传失败、文件 IO 错误             |
| `IllegalArgumentException` | 编程错误（参数校验）          | `null` 入参、空字符串                  |
| `IllegalStateException`    | 启动配置错误                  | `StorageTargetProperties` 校验失败     |

**关键原则**：

- domain 层只抛 `DomainException`，不感知基础设施故障
- infra 层捕获底层异常（IOException/OSSException）后包装为 `SystemException`
- 应用层不捕获异常，让全局异常处理器统一处理

### 7.3 异常处理示例

#### 7.3.1 Domain 层异常

```java
public void markUploaded(String storageKey, String md5) {
    if (this.status != FileStatus.PENDING_UPLOAD) {
        throw new DomainException(FileErrorCodes.FILE_STATUS_INVALID)
            .withLogDetail("当前状态: " + this.status + ", 期望状态: PENDING_UPLOAD");
    }
    if (storageKey == null || storageKey.isBlank()) {
        throw new DomainException(FileErrorCodes.FILE_STATUS_INVALID)
            .withLogDetail("storageKey 不能为空");
    }
    this.storageKey = storageKey;
    this.md5 = md5;
    this.status = FileStatus.UPLOADED;
    this.uploadedAt = LocalDateTime.now();
    registerDomainEvent(FileUploadedEvent.of(this));
}
```

#### 7.3.2 Infrastructure 层异常包装

```java
@Override
public void store(StorageTarget target, String storageKey,
                  InputStream content, long contentLength) {
    try {
        OSS client = getClient(target);
        // ... OSS 上传逻辑
    } catch (OSSException e) {
        throw new SystemException(FileErrorCodes.FILE_STORAGE_FAILED, e)
            .withLogDetail("OSS 存储失败: bucket=" + target.bucket()
                + ", key=" + storageKey + ", errorCode=" + e.getErrorCode());
    } catch (ClientException e) {
        throw new SystemException(FileErrorCodes.FILE_STORAGE_FAILED, e)
            .withLogDetail("OSS 客户端异常: " + e.getMessage());
    }
}
```

### 7.4 测试策略总览

```
┌──────────────────────────────────────────────────────────────┐
│ 测试金字塔                                                    │
├──────────────────────────────────────────────────────────────┤
│                   ┌──────────────┐                           │
│                   │ E2E (少)      │  ← 不在 A 子项目范围     │
│                  ┌┴──────────────┴┐                         │
│                  │ 集成测试 (中)    │  ← StorageIntegrationTest│
│                 ┌┴─────────────────┴┐                       │
│                 │ 单元测试 (多)       │  ← 各组件独立测试      │
│                └────────────────────┘                       │
└──────────────────────────────────────────────────────────────┘
```

### 7.5 单元测试清单

#### 7.5.1 Domain 层单元测试

**FileMetadataTest**（新增）

- `create_should_register_FileMetadataCreatedEvent`
- `create_should_set_status_to_PENDING_UPLOAD`
- `markUploaded_should_transition_to_UPLOADED`
- `markUploaded_should_register_FileUploadedEvent`
- `markUploaded_should_throw_when_status_is_not_PENDING_UPLOAD`
- `markUploaded_should_throw_when_storageKey_is_blank`
- `markDeleted_should_transition_to_DELETED`
- `markDeleted_should_register_FileDeletedEvent`
- `markDeleted_should_be_idempotent_when_already_DELETED`
- `isExpired_should_return_true_when_expiresAt_is_past`
- `isExpired_should_return_false_when_expiresAt_is_null`
- `reconstitute_should_restore_all_fields`

**StorageTargetTest**（新增）

- `constructor_should_validate_OSS_required_fields`
- `constructor_should_validate_NAS_required_fields`
- `constructor_should_validate_LOCAL_required_fields`
- `constructor_should_allow_empty_options`

#### 7.5.2 Application 层单元测试

**StoreFileUseCaseTest**（新增）

- `createMetadata_should_save_and_publish_event`
- `createMetadata_should_resolve_target_by_usage`
- `store_should_call_storageGateway_store`
- `store_should_compute_md5_and_markUploaded`
- `store_should_throw_when_status_is_not_PENDING_UPLOAD`
- `store_should_publish_FileUploadedEvent`

**OpenFileUseCaseTest**（新增）

- `open_should_return_inputstream_from_gateway`
- `open_should_throw_when_file_is_deleted`
- `open_should_throw_when_file_is_expired`
- `loadMetadata_should_return_metadata`

**DeleteFileUseCaseTest**（新增）

- `delete_should_markDeleted_and_publish_event`
- `delete_should_be_idempotent`
- `delete_should_throw_when_file_not_found`

**CopyFileUseCaseTest**（新增）

- `copy_should_create_new_metadata_and_call_gateway_copy`
- `copy_should_publish_FileMetadataCreatedEvent`

#### 7.5.3 Infrastructure 层单元测试

**LocalFileStorageTest**（新增，使用 `@TempDir`）

- `store_should_write_file_to_local_path`
- `store_should_create_parent_directories`
- `open_should_return_readable_stream`
- `exists_should_return_true_when_file_exists`
- `exists_should_return_false_when_file_not_exists`
- `copy_should_duplicate_file`
- `computeMd5_should_return_correct_md5`

**NASFileStorageTest**（新增，使用 `@TempDir` 模拟 NAS 挂载）

- 与 LocalFileStorageTest 类似
- 额外：`store_should_use_atomic_move`

**FileStorageRouterTest**（新增，Mock 后端）

- `store_should_route_to_correct_backend_by_target_type`
- `open_should_route_to_correct_backend`
- `copy_should_use_same_backend_when_src_dst_type_match`
- `copy_should_use_cross_backend_copy_when_type_mismatch`
- `store_should_throw_when_backend_not_found`
- `generateStorageKey_should_include_date_partition`

**PropertiesBasedStorageTargetResolverTest**（新增）

- `resolveByUsage_should_return_correct_target`
- `resolveById_should_throw_when_target_not_found`
- `validate_should_throw_when_routing_target_missing`
- `validate_should_throw_when_OSS_missing_endpoint`
- `validate_should_throw_when_NAS_missing_bucket`
- `validate_should_pass_when_all_configs_valid`

**FileMetadataConverterTest**（新增）

- `toDO_should_convert_FileId_to_String`
- `toDomain_should_convert_String_to_FileId`
- `toDO_should_convert_enums_to_String`
- `toDomain_should_convert_String_to_enums`

**AliyunOSSFileStorageTest**（可选）

- 集成测试覆盖即可，单元测试不强制

#### 7.5.4 ParseTask 迁移回归测试

**ParseTaskTest**（修改现有测试）

- 字段从 `sourceFileRef: String` 改为 `sourceFileId: FileId`
- 验证所有现有测试用例通过

**ParseFlowIntegrationTest**（修改现有集成测试）

- 验证完整解析流程仍能跑通
- 入口从 `sourceFileRef` 改为 `sourceFileId`

### 7.6 集成测试清单

#### 7.6.1 StorageIntegrationTest（新增）

**位置**：`file-infrastructure/src/test/java/com/example/file/infrastructure/StorageIntegrationTest.java`

```java
@SpringBootTest
@ActiveProfiles("test")
class StorageIntegrationTest {

    @Autowired StoreFileUseCase storeFileUseCase;
    @Autowired OpenFileUseCase openFileUseCase;
    @Autowired DeleteFileUseCase deleteFileUseCase;
    @Autowired FileMetadataRepository metadataRepository;

    @Test
    @DisplayName("完整文件生命周期：创建→存储→打开→删除")
    void testFileLifecycle() throws IOException {
        // 1. 创建元数据
        StoreFileCommand cmd = new StoreFileCommand(
            "test.xlsx", 1024, "application/vnd.ms-excel",
            FileUsage.SOURCE, "annuity", "business-core",
            new BatchId("BATCH_TEST_001"), UserNo.of("user001"), null
        );
        FileId fileId = storeFileUseCase.createMetadata(cmd);

        // 2. 存储文件流
        try (InputStream content = new ByteArrayInputStream("test content".getBytes())) {
            storeFileUseCase.store(fileId, content, 12);
        }

        // 3. 验证元数据状态
        FileMetadata metadata = metadataRepository.loadOrThrow(fileId);
        assertThat(metadata.status()).isEqualTo(FileStatus.UPLOADED);
        assertThat(metadata.md5()).isNotBlank();

        // 4. 打开文件
        try (InputStream in = openFileUseCase.open(fileId)) {
            String content = new String(in.readAllBytes());
            assertThat(content).isEqualTo("test content");
        }

        // 5. 删除
        deleteFileUseCase.delete(fileId, UserNo.of("user001"));
        FileMetadata deleted = metadataRepository.loadOrThrow(fileId);
        assertThat(deleted.status()).isEqualTo(FileStatus.DELETED);
    }
}
```

#### 7.6.2 RouterRoutingIntegrationTest（新增）

- 多后端共存场景（Local + NAS）
- 验证 Router 根据 targetId 正确路由
- 验证跨后端 copy

#### 7.6.3 StorageConfigValidationTest（新增）

- 加载完整 `application-test.yml`
- 验证 `StorageTargetProperties` 正确绑定
- 验证 `PropertiesBasedStorageTargetResolver` 校验通过
- 验证 fail-fast：故意配置错误，期望启动失败

### 7.7 测试配置

**位置**：`file-starter/src/test/resources/application-test.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:file-service;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:

mybatis-flex:
  global-config:
    logic-delete:
      field: deleted
      logic-value: 'false'
      logic-not-value: 'true'

file:
  storage:
    enabled: true
    targets:
      - id: local-test
        type: LOCAL
        base-path: ${java.io.tmpdir}/file-storage-test
      - id: local-archive
        type: LOCAL
        base-path: ${java.io.tmpdir}/file-archive-test
    routing:
      source: local-test
      parsed: local-test
      export: local-test
      archive: local-archive
```

### 7.8 测试覆盖目标

| 模块                                      | 目标覆盖率 | 关键路径覆盖       |
|-------------------------------------------|------------|--------------------|
| file-domain (FileMetadata, StorageTarget) | 90%+       | 状态机、不变式     |
| file-application (UseCase)                | 85%+       | 编排流程、异常路径 |
| file-infrastructure (Router, Backends)    | 80%+       | 路由分发、后端操作 |
| ParseTask 迁移                            | 100% 回归  | 所有现有测试通过   |

### 7.9 测试数据管理

**遵循项目规范**：

- 测试输出到 `target/test-output/`（不写入源码目录）
- 测试资源使用 `@TempDir` 或 `target/test-classes`
- 测试方法使用 `@DisplayName` 描述目的
- 测试实例默认 `PER_METHOD` 隔离
- 静态测试数据提取到 `static final` 字段

### 7.10 关键测试场景清单

| #  | 场景                     | 测试类                                   | 期望结果                     |
|----|--------------------------|------------------------------------------|------------------------------|
| 1  | 创建文件元数据成功       | StoreFileUseCaseTest                     | fileId 返回，事件发布        |
| 2  | 创建时 usage 路由正确    | StoreFileUseCaseTest                     | targetId 匹配配置            |
| 3  | 存储文件流成功           | StoreFileUseCaseTest                     | status=UPLOADED, md5 非空    |
| 4  | 重复上传抛异常           | StoreFileUseCaseTest                     | FILE_ALREADY_UPLOADED        |
| 5  | 打开已删除文件抛异常     | OpenFileUseCaseTest                      | FILE_METADATA_NOT_FOUND      |
| 6  | 打开已过期文件抛异常     | OpenFileUseCaseTest                      | FILE_EXPIRED                 |
| 7  | 删除已删除文件幂等       | DeleteFileUseCaseTest                    | 不抛异常                     |
| 8  | 路由到正确后端           | FileStorageRouterTest                    | LOCAL/OSS/NAS 各自调用       |
| 9  | 跨后端复制成功           | FileStorageRouterTest                    | 新 FileId 生成               |
| 10 | storageKey 含日期分区    | FileStorageRouterTest                    | 路径包含 `2026-07-19`        |
| 11 | 配置校验 fail-fast       | PropertiesBasedStorageTargetResolverTest | 启动抛 IllegalStateException |
| 12 | OSS 缺 endpoint 启动失败 | PropertiesBasedStorageTargetResolverTest | 异常消息含 "endpoint"        |
| 13 | 完整生命周期             | StorageIntegrationTest                   | 创建→存储→打开→删除全部通过  |
| 14 | ParseTask 迁移回归       | ParseTaskTest                            | 所有现有测试通过             |
| 15 | 解析流程集成回归         | ParseFlowIntegrationTest                 | 完整解析链路通过             |

### 7.11 测试依赖

**file-infrastructure/pom.xml 新增测试依赖**：

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 8. 验收标准

### 8.1 功能验收

- [ ] `mvn compile -pl file-service -am` 全量编译通过
- [ ] `mvn test -pl file-service` 所有测试通过
- [ ] 新增 `t_file_metadata` 表 DDL 可在 PostgreSQL 执行
- [ ] ParseTask 表字段成功迁移：`source_file_ref` → `source_file_id`
- [ ] application.yml 新增 `file.storage` 配置块
- [ ] 3 个存储后端（Local/OSS/NAS）可独立工作
- [ ] Router 能根据 FileUsage 正确路由到 StorageTarget
- [ ] 完整文件生命周期：创建 → 存储 → 打开 → 删除 全部通过

### 8.2 架构验收

- [ ] domain 层不依赖 infra 层（依赖倒置）
- [ ] FileStorageGateway SPI 签名仅用 FileId，不暴露 storageKey
- [ ] FileMetadata 聚合根不变式完整
- [ ] 3 个领域事件正确注册与发布
- [ ] 配置校验 fail-fast 生效

### 8.3 测试验收

- [ ] file-domain 覆盖率 ≥ 90%
- [ ] file-application 覆盖率 ≥ 85%
- [ ] file-infrastructure 覆盖率 ≥ 80%
- [ ] ParseTask 迁移后所有现有测试通过（100% 回归）
- [ ] StorageIntegrationTest 集成测试通过

### 8.4 文档验收

- [ ] CLAUDE.md 同步更新（新增 file-service 文件存储子域章节）
- [ ] spec 文档归档到 `docs/superpowers/specs/`

---

## 9. 后续子项目预告

### 9.1 子项目 B：Token 机制

- FileStorageApi 新增 `applyUploadToken` / `applyDownloadToken` / `downloadByToken`
- FileStorageAdapter 实现 API
- Token 存储（Redis）+ 有效期 + 一次性使用
- 前端直传文件流到 file-service

### 9.2 子项目 C：跨服务事件发布

- 启用 shared-event-starter
- FileUploadedEvent 转换为集成事件 DTO
- 通过 RocketMQ 发布
- business-core-kernel 监听并创建业务表单聚合

### 9.3 子项目 D：外部表单导出上传

- 复用 CopyFileUseCase
- 导出后复制到 EXPORT target
- 通知业务服务获取下载链接

### 9.4 子项目 E：FileIntegrationGateway 业务侧实现

- business-core-kernel 的 FileIntegrationGateway 接口重构
- 对接 file-service API
- 实现表单上传 → 解析 → 校验 → 拆分完整链路

---

**Spec 结束**
