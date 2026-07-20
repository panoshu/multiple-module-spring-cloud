# 文件服务 Token 访问机制设计

**版本**: v1.0
**日期**: 2026-07-20
**作者**: file-service 团队
**状态**: Draft

## 一、背景与目标

### 1.1 背景

当前文件服务（`feature/file-service-parse-engine` 分支）已完成存储引擎重构，支持 LOCAL/OSS/NAS 三种后端、StoreFileUseCase/OpenFileUseCase/CopyFileUseCase/DeleteFileUseCase 等基础用例。但缺少面向业务服务的前置授权机制：

- 业务服务无法安全地向文件服务发起上传/下载请求
- 前端无法直接访问文件服务（需通过业务服务中转，性能差）
- 缺少文件访问流水审计
- 未使用国密算法

### 1.2 目标

1. **业务服务申请 token**: 业务服务向文件服务传递业务类型、企业编号、产品编号、上传人等信息，文件服务创建 FileMetadata 聚合根（PENDING_UPLOAD 状态）并返回加密 token
2. **前端直接访问**: 前端用 token 直接调用文件服务上传/下载接口，文件服务校验 token 与会话用户一致性
3. **安全管控**: token 短时效 + 一次性使用 + 会话用户对比
4. **流水审计**: 双记录（APPLY + ACCESS）完整审计轨迹
5. **国密算法**: 使用腾讯 Kona 加密套件的 SM4（加密）和 SM3（摘要）

### 1.3 非目标

- 断点续传（YAGNI，当前业务场景文件通常 < 100MB）
- 多次性 token（上传/下载均为一次性）
- JWT 自包含认证（依赖网关注入 Header）
- 跨服务 token 共享（token 仅文件服务内部使用）

## 二、核心设计决策

| 决策项 | 选择 | 理由 |
|---|---|---|
| Token 生命周期 | 一次性 + 短时效（5-15 分钟） | 安全性高，泄露风险低 |
| Token 持久化 | 不存数据库，Redis 标记已使用 | 性能优，避免 DB 写入 |
| Token 载荷 | 完整载荷（含 fileId/customerNo/productNo/operator/allowedContentTypes/allowedMaxSize/expireAt） | 服务端无状态校验 |
| 企业/产品字段归属 | FileAccessScope 值对象（JSONB 存储） | 符合 DDD，便于扩展 |
| 流水记录时机 | 双记录（APPLY + ACCESS） | 完整审计轨迹 |
| 会话信息来源 | HTTP Header（X-User-No/X-Customer-No/X-Product-No） | 网关统一注入，性能优 |
| Header 注入策略 | 网关统一注入（网络隔离防伪造） | 零 RPC，性能最优 |
| 架构方案 | 方案 A：Token 作为领域服务 | 业务规则在 domain 层，便于测试 |
| 文件摘要算法 | SM3（国密） | 替代 MD5，符合国密要求 |
| Token 加密算法 | SM4/CBC/PKCS5Padding + 随机 IV | 国密标准模式 |
| 下载策略 | 流式下载（StreamingResponseBody，8KB 缓冲） | 内存友好，YAGNI 不做断点续传 |
| 文件存储命名 | 使用 fileId（不用原名） | 安全考虑 |

## 三、领域模型设计

### 3.1 FileMetadata 聚合根改造

**核心变更**: `create()` 方法简化 — 申请 token 时不要求文件本身信息（originalName/size/contentType/digest），这些在上传时才设置。

```java
public class FileMetadata extends AggregateRoot<FileId> {
    // 新增: 访问范围（企业/产品）
    private FileAccessScope accessScope;
    
    // 原有字段（部分改为可空，上传时才填充）
    private String originalName;      // 上传时才设置
    private Long size;                // 上传时才设置（改 long → Long，允许 null）
    private String contentType;       // 上传时才设置
    private String digest;            // 上传时才设置（SM3 摘要，替代 md5）
    private String digestAlgorithm;   // 新增: "SM3"
    
    // 原有字段保持
    private String targetId;
    private StorageType storageType;
    private String storageKey;        // 上传时才设置
    private FileUsage usage;
    private String bizType;
    private String sourceApp;
    private BatchId businessBatchId;
    private FileStatus status;        // PENDING_UPLOAD → UPLOADED → DELETED
    private UserNo uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime expiresAt;
    
    // 申请上传时调用（业务服务申请 token）
    public static FileMetadata createForUpload(FileId id, FileUsage usage, String bizType, 
                                                String sourceApp, BatchId businessBatchId,
                                                FileAccessScope accessScope,
                                                String targetId, StorageType storageType,
                                                UserNo uploader, LocalDateTime expiresAt) { ... }
    
    // 实际上传完成时调用
    public void completeUpload(String originalName, long size, String contentType, 
                                String storageKey, String digest) { ... }
    
    // 申请下载时调用（验证文件已上传且未过期）
    public void verifyDownloadable() { ... }
}
```

**状态机**:
```
createForUpload()    completeUpload()      markDeleted()
     │                    │                     │
     ▼                    ▼                     ▼
PENDING_UPLOAD  ──→  UPLOADED  ──→  DELETED
```

### 3.2 FileAccessScope 值对象（新建）

```java
public record FileAccessScope(CustomerNo customerNo, ProductNo productNo) 
    implements ValueObject {
    // 校验: customerNo 和 productNo 不能为 null
}
```

数据库用 JSONB 字段存储：`{"customerNo":"C001","productNo":"P001"}`。便于未来扩展（如增加 `departmentNo`）。

### 3.3 FileTokenPayload 值对象（新建，不持久化）

```java
public record FileTokenPayload(
    String tokenId,           // UUID
    FileId fileId,
    FileUsage usage,          // UPLOAD / DOWNLOAD
    String bizType,
    CustomerNo customerNo,
    ProductNo productNo,
    UserNo operator,          // uploader 或 downloader
    List<String> allowedContentTypes,  // 上传 token 专有，下载 token 为空
    Long allowedMaxSize,                // 上传 token 专有，下载 token 为 null
    LocalDateTime expireAt
) implements ValueObject {}
```

### 3.4 SessionUser 值对象（新建）

```java
public record SessionUser(UserNo userNo, CustomerNo customerNo, ProductNo productNo) 
    implements ValueObject {}
```

Adapter 层从 HTTP Header 读取（`X-User-No` / `X-Customer-No` / `X-Product-No`，由网关注入），组装为 `SessionUser` 传入 UseCase。

### 3.5 FileAccessLog 聚合根（新建，流水记录）

```java
public class FileAccessLog extends AggregateRoot<String> {  // id = ULID
    private FileId fileId;
    private FileAccessAction action;    // APPLY / ACCESS
    private FileUsage usage;            // UPLOAD / DOWNLOAD
    private CustomerNo customerNo;
    private ProductNo productNo;
    private UserNo operator;
    private String sourceApp;           // 申请方系统
    private String sourceIp;            // ACCESS 时记录
    private String tokenHash;           // token 的 SHA-256（关联 APPLY 和 ACCESS）
    private FileAccessResult result;    // SUCCESS / FAIL / EXPIRED / REJECTED
    private String failReason;          // 失败原因
    private LocalDateTime occurAt;
    
    public static FileAccessLog apply(...) { ... }   // 申请 token 时
    public static FileAccessLog access(...) { ... }  // 实际访问时
    public void markSuccess() { ... }
    public void markFail(String reason) { ... }
}
```

**枚举**:
```java
public enum FileAccessAction { APPLY, ACCESS }
public enum FileAccessResult { SUCCESS, FAIL, EXPIRED, REJECTED }
```

### 3.6 领域事件（新建）

```java
public record UploadTokenAppliedEvent(
    EventId eventId, LocalDateTime occurredAt,
    FileId fileId, CustomerNo customerNo, ProductNo productNo,
    UserNo uploader, String tokenHash, LocalDateTime expireAt
) implements DomainEvent { ... }

public record FileUploadedWithTokenEvent(
    EventId eventId, LocalDateTime occurredAt,
    FileId fileId, String originalName, long size, String digest, String tokenHash
) implements DomainEvent { ... }

public record DownloadTokenAppliedEvent(...) implements DomainEvent { ... }
public record FileDownloadedEvent(...) implements DomainEvent { ... }
```

事件包含 `tokenHash`（不含 token 明文），便于下游审计关联。

## 四、SPI 接口与领域服务

### 4.1 FileTokenGateway SPI（加密/解密）

```java
package com.example.file.domain.gateway;

public interface FileTokenGateway {
    /**
     * 加密 token 载荷，返回密文字符串。
     * 使用国密 SM4 算法（腾讯 kona 加密套件）。
     */
    String encrypt(FileTokenPayload payload);
    
    /**
     * 解密 token 字符串，返回载荷。
     * 解密失败或格式错误抛 SystemException(FILE_TOKEN_INVALID)。
     */
    FileTokenPayload decrypt(String token);
}
```

### 4.2 FileTokenStore SPI（一次性使用标记）

```java
package com.example.file.domain.gateway;

public interface FileTokenStore {
    /**
     * 标记 token 已使用。
     * key = "file:token:used:" + tokenId
     * TTL = token 剩余有效期
     * 若 key 已存在返回 false（重复使用），否则写入并返回 true。
     */
    boolean markUsed(String tokenId, java.time.Duration ttl);
    
    /**
     * 检查 token 是否已使用。
     */
    boolean isUsed(String tokenId);
}
```

### 4.3 FileTokenService 领域服务

```java
package com.example.file.domain.service;

@DomainService
public class FileTokenService {
    
    /**
     * 生成上传 token
     * 1. 基于 FileMetadata 组装 FileTokenPayload (usage=UPLOAD)
     * 2. 调用 FileTokenGateway.encrypt() 加密
     * 3. 返回密文 token
     */
    public String generateUploadToken(FileMetadata file, 
                                       List<String> allowedContentTypes,
                                       Long allowedMaxSize,
                                       Duration ttl) { ... }
    
    /**
     * 生成下载 token
     * 1. 校验 file.verifyDownloadable()
     * 2. 组装 FileTokenPayload (usage=DOWNLOAD)
     * 3. 加密返回
     */
    public String generateDownloadToken(FileMetadata file, Duration ttl) { ... }
    
    /**
     * 校验上传 token 并消费（标记已使用）
     * 1. decrypt(token) → FileTokenPayload
     * 2. 校验 expiry / usage=UPLOAD / 文件类型 / 文件大小 / customerNo / productNo / operator
     * 3. 与会话用户对比（uploader 必须等于会话用户）
     * 4. markUsed(tokenId, ttl) — 重复使用抛 SystemException(FILE_TOKEN_ALREADY_USED)
     * 5. 返回 FileTokenPayload 供 UseCase 完成上传
     */
    public FileTokenPayload verifyAndConsumeUploadToken(String token, 
                                                         SessionUser session,
                                                         FileMetadata file) { ... }
    
    /**
     * 校验下载 token 并消费
     * 同上，usage=DOWNLOAD，不校验文件类型/大小。
     */
    public FileTokenPayload verifyAndConsumeDownloadToken(String token,
                                                           SessionUser session,
                                                           FileMetadata file) { ... }
}
```

## 五、UseCase 与 API 设计

### 5.1 UseCase 列表

#### ApplyUploadTokenUseCase（业务服务申请上传 token）

```java
@Service @RequiredArgsConstructor
public class ApplyUploadTokenUseCase {
    private final FileMetadataRepository metadataRepository;
    private final StorageTargetResolver targetResolver;
    private final FileTokenService tokenService;
    private final FileAccessLogRepository logRepository;
    
    @Transactional
    public ApplyUploadTokenResponse apply(ApplyUploadTokenCommand cmd) {
        // 1. 创建 FileMetadata (PENDING_UPLOAD)
        FileId fileId = new FileId(UlidAlgorithm.generate());
        var target = targetResolver.resolveByUsage(FileUsage.SOURCE, cmd.bizType());
        FileMetadata file = FileMetadata.createForUpload(
            fileId, FileUsage.SOURCE, cmd.bizType(), cmd.sourceApp(),
            cmd.businessBatchId(), cmd.accessScope(),
            target.targetId(), target.type(), cmd.uploader(), cmd.expiresAt()
        );
        metadataRepository.save(file);
        
        // 2. 生成 token
        String token = tokenService.generateUploadToken(
            file, cmd.allowedContentTypes(), cmd.allowedMaxSize(), cmd.ttl()
        );
        
        // 3. 写 APPLY 流水
        FileAccessLog log = FileAccessLog.apply(
            fileId, FileUsage.SOURCE, cmd.accessScope(), cmd.uploader(),
            cmd.sourceApp(), tokenHashOf(token)
        );
        logRepository.save(log);
        
        return new ApplyUploadTokenResponse(token, fileId);
    }
}
```

#### UploadFileWithTokenUseCase（前端实际上传文件）

```java
@Service @RequiredArgsConstructor
public class UploadFileWithTokenUseCase {
    private final FileMetadataRepository metadataRepository;
    private final FileTokenService tokenService;
    private final FileStorageGateway storageGateway;
    private final FileAccessLogRepository logRepository;
    
    @Transactional
    public void upload(String token, SessionUser session, 
                       MultipartFile file, String clientIp) {
        FileTokenPayload payload;
        try {
            // 1. 解密 + 校验 + 消费 token
            payload = tokenService.verifyAndConsumeUploadToken(token, session, /* file_metadata */);
        } catch (SystemException e) {
            // token 校验失败 — 写流水用 tokenHash
            writeAccessLogFailed(token, session, clientIp, e);
            throw e;
        }
        
        try {
            // 2. 上传文件 (storageKey = fileId，避免原名)
            FileMetadata meta = metadataRepository.loadOrThrow(payload.fileId());
            StoreResult result = storageGateway.store(
                payload.fileId(), file.getInputStream(), file.getSize()
            );
            
            // 3. 更新 FileMetadata（设置文件本身信息）
            meta.completeUpload(
                file.getOriginalFilename(), file.getSize(), file.getContentType(),
                result.storageKey(), result.digest()  // SM3 摘要
            );
            metadataRepository.save(meta);
            writeAccessLogSuccess(payload, session, clientIp);
        } catch (Exception e) {
            writeAccessLogFailed(payload, session, clientIp, e);
            throw e;
        }
    }
}
```

#### ApplyDownloadTokenUseCase（业务服务申请下载 token）

```java
@Service @RequiredArgsConstructor
public class ApplyDownloadTokenUseCase {
    private final FileMetadataRepository metadataRepository;
    private final FileTokenService tokenService;
    private final FileAccessLogRepository logRepository;
    
    @Transactional
    public ApplyDownloadTokenResponse apply(ApplyDownloadTokenCommand cmd) {
        FileMetadata file = metadataRepository.loadOrThrow(cmd.fileId());
        file.verifyDownloadable();  // 校验已上传且未过期
        
        String token = tokenService.generateDownloadToken(file, cmd.ttl());
        
        logRepository.save(FileAccessLog.apply(
            cmd.fileId(), FileUsage.SOURCE, file.accessScope(), cmd.downloader(),
            cmd.sourceApp(), tokenHashOf(token)
        ));
        
        return new ApplyDownloadTokenResponse(token);
    }
}
```

#### DownloadFileWithTokenUseCase（前端实际下载文件）

```java
@Service @RequiredArgsConstructor
public class DownloadFileWithTokenUseCase {
    private final FileMetadataRepository metadataRepository;
    private final FileTokenService tokenService;
    private final FileStorageGateway storageGateway;
    private final FileAccessLogRepository logRepository;
    
    /**
     * 事务 1: token 校验 + 写流水 + 加载 FileMetadata
     * 返回 DownloadContext（含 storageKey、originalName 等）
     */
    @Transactional
    public DownloadContext prepareDownload(String token, SessionUser session, String clientIp) {
        FileTokenPayload payload = tokenService.verifyAndConsumeDownloadToken(...);
        writeAccessLogSuccess(payload, session, clientIp);
        FileMetadata meta = metadataRepository.loadOrThrow(payload.fileId());
        return new DownloadContext(meta);
    }
    
    /**
     * 事务 2: 打开 InputStream（事务外，避免长事务持有流）
     */
    public InputStream openStream(FileId fileId) {
        return storageGateway.open(fileId);
    }
}
```

**关键设计**: 下载分两阶段，事务内校验+流水，事务外流式返回，避免长事务持有 InputStream。

### 5.2 API 接口设计（@HttpExchange，在 file-api 层）

```java
// file-api/.../FileAccessApi.java
@HttpExchange(url = "/api/file/access")
public interface FileAccessApi {
    
    // 业务服务 → 文件服务：申请上传 token
    @PostExchange(url = "/upload-tokens")
    ApplyUploadTokenResponse applyUploadToken(@RequestBody ApplyUploadTokenRequest request);
    
    // 业务服务 → 文件服务：申请下载 token
    @PostExchange(url = "/download-tokens")
    ApplyDownloadTokenResponse applyDownloadToken(@RequestBody ApplyDownloadTokenRequest request);
    
    // 前端 → 文件服务：实际上传文件
    @PostExchange(url = "/upload")
    UploadFileResponse upload(
        @RequestHeader("X-File-Token") String token,
        @RequestPart("file") MultipartFile file
    );
    
    // 前端 → 文件服务：实际下载文件（流式）
    @GetExchange(url = "/download")
    ResponseEntity<StreamingResponseBody> download(@RequestHeader("X-File-Token") String token);
}
```

**Request/Response 命令对象**（file-api 层）:

```java
public record ApplyUploadTokenRequest(
    String bizType, String sourceApp, String businessBatchId,
    CustomerNo customerNo, ProductNo productNo,
    UserNo uploader, LocalDateTime expiresAt,
    List<String> allowedContentTypes, Long allowedMaxSize,
    Duration ttl  // 可选，默认从配置取
) {}

public record ApplyUploadTokenResponse(String token, FileId fileId) {}

public record ApplyDownloadTokenRequest(
    FileId fileId, String sourceApp,
    CustomerNo customerNo, ProductNo productNo,
    UserNo downloader, Duration ttl
) {}

public record ApplyDownloadTokenResponse(String token) {}
```

### 5.3 下载 Adapter 实现（流式）

```java
@RestController
public class FileAccessAdapter implements FileAccessApi {
    
    @GetMapping("/api/file/access/download")
    public ResponseEntity<StreamingResponseBody> download(
            @RequestHeader("X-File-Token") String token,
            @RequestHeader(value = "X-User-No", required = false) String userNo,
            @RequestHeader(value = "X-Customer-No", required = false) String customerNo,
            @RequestHeader(value = "X-Product-No", required = false) String productNo,
            HttpServletRequest request) {
        
        SessionUser session = new SessionUser(
            UserNo.of(userNo), CustomerNo.of(customerNo), ProductNo.of(productNo)
        );
        String clientIp = extractClientIp(request);
        
        // 事务 1: 校验 + 流水
        DownloadContext ctx = downloadFileWithTokenUseCase.prepareDownload(token, session, clientIp);
        
        // 事务 2: 流式打开
        InputStream stream = downloadFileWithTokenUseCase.openStream(ctx.fileId());
        
        StreamingResponseBody body = outputStream -> {
            try (InputStream in = stream) {
                byte[] buffer = new byte[8192];  // 8KB 缓冲区
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
        };
        
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(
                ctx.contentType() != null ? ctx.contentType() : "application/octet-stream"
            ))
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + URLEncoder.encode(ctx.originalName(), UTF_8) + "\"")
            .contentLength(ctx.size())
            .body(body);
    }
}
```

**设计要点**:
- **`StreamingResponseBody`**: Spring MVC 直接写入响应流，文件内容从不全部加载到内存
- **8KB 缓冲区**: 固定大小，内存占用恒定（~8KB/请求）
- **`contentLength`**: 从 `FileMetadata.size()` 设置，让客户端显示下载进度
- **`Content-Disposition`**: URL 编码处理中文文件名
- **`try-with-resources`**: 确保 InputStream 关闭

### 5.4 配置项

```yaml
file:
  token:
    secret-key: ${FILE_TOKEN_SECRET_KEY:}   # SM4 密钥（Base64），16 字节
    default-upload-ttl: 15m                  # 上传 token 默认有效期
    default-download-ttl: 15m                # 下载 token 默认有效期
    redis:
      key-prefix: "file:token:used:"
      default-ttl: 15m
```

## 六、数据库表设计

### 6.1 t_file_metadata 表改造

```sql
-- 新增字段（不改原有字段）
ALTER TABLE t_file_metadata ADD COLUMN access_scope JSONB;
ALTER TABLE t_file_metadata ADD COLUMN digest VARCHAR(128);
ALTER TABLE t_file_metadata ADD COLUMN digest_algorithm VARCHAR(20) DEFAULT 'SM3';

-- original_name / size / storage_key 允许 NULL（PENDING_UPLOAD 阶段不设置）
ALTER TABLE t_file_metadata ALTER COLUMN original_name DROP NOT NULL;
ALTER TABLE t_file_metadata ALTER COLUMN size DROP NOT NULL;
ALTER TABLE t_file_metadata ALTER COLUMN storage_key DROP NOT NULL;

COMMENT ON COLUMN t_file_metadata.access_scope IS '访问范围 JSON: {"customerNo":"C001","productNo":"P001"}';
COMMENT ON COLUMN t_file_metadata.digest IS '内容摘要（SM3）';
COMMENT ON COLUMN t_file_metadata.digest_algorithm IS '摘要算法: SM3';
```

完整字段清单（改造后）:

| 字段 | 类型 | 说明 | PENDING_UPLOAD | UPLOADED |
|---|---|---|---|---|
| id | VARCHAR(64) | FileId | ✓ | ✓ |
| access_scope | JSONB | 访问范围 | ✓ | ✓ |
| original_name | VARCHAR(512) | 原始文件名 | NULL | ✓ |
| size | BIGINT | 文件大小 | NULL | ✓ |
| content_type | VARCHAR(128) | MIME 类型 | NULL | ✓ |
| md5 | VARCHAR(64) | 旧字段，保留 | NULL | 旧数据 |
| digest | VARCHAR(128) | SM3 摘要 | NULL | ✓ |
| digest_algorithm | VARCHAR(20) | 'SM3' | NULL | 'SM3' |
| storage_key | VARCHAR(1024) | 后端存储 key | NULL | ✓ |
| status | VARCHAR(20) | 状态 | PENDING_UPLOAD | UPLOADED |
| 其他字段 | - | 同现有 | ✓ | ✓ |

### 6.2 t_file_access_log 表（新建）

```sql
CREATE TABLE IF NOT EXISTS t_file_access_log (
    id              VARCHAR(64)   NOT NULL,
    file_id         VARCHAR(64)   NOT NULL,
    action          VARCHAR(20)   NOT NULL,          -- APPLY / ACCESS
    usage           VARCHAR(20)   NOT NULL,          -- UPLOAD / DOWNLOAD
    customer_no     VARCHAR(64)   NOT NULL,
    product_no      VARCHAR(64)   NOT NULL,
    operator        VARCHAR(64)   NOT NULL,
    source_app      VARCHAR(64),
    source_ip       VARCHAR(64),
    token_hash      VARCHAR(128)  NOT NULL,
    result          VARCHAR(20)   NOT NULL,          -- SUCCESS / FAIL / EXPIRED / REJECTED
    fail_reason     VARCHAR(512),
    occur_at        TIMESTAMP     NOT NULL,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_access_log_file_id ON t_file_access_log(file_id);
CREATE INDEX IF NOT EXISTS idx_access_log_token_hash ON t_file_access_log(token_hash);
CREATE INDEX IF NOT EXISTS idx_access_log_action_time ON t_file_access_log(action, occur_at);
CREATE INDEX IF NOT EXISTS idx_access_log_customer_product ON t_file_access_log(customer_no, product_no, occur_at);

COMMENT ON TABLE t_file_access_log IS '文件访问流水表';
COMMENT ON COLUMN t_file_access_log.action IS 'APPLY=申请token, ACCESS=实际访问';
COMMENT ON COLUMN t_file_access_log.token_hash IS 'token SHA-256, 用于关联 APPLY 和 ACCESS 记录';
COMMENT ON COLUMN t_file_access_log.result IS 'SUCCESS/FAIL/EXPIRED/REJECTED';
```

**设计要点**:
- **`token_hash`**: 不存 token 明文（安全），存 SHA-256 用于关联 APPLY 和 ACCESS
- **`action`**: APPLY（业务服务申请）+ ACCESS（前端实际访问），双记录
- **不设 `updated_at` / `deleted`**: 流水记录不可修改、不可删除（审计要求）

### 6.3 错误码扩展

```java
// FileErrorCodes.java 新增
FILE_TOKEN_INVALID("FILE_TOKEN_INVALID", "文件访问 token 无效或已过期"),
FILE_TOKEN_EXPIRED("FILE_TOKEN_EXPIRED", "文件访问 token 已过期"),
FILE_TOKEN_ALREADY_USED("FILE_TOKEN_ALREADY_USED", "文件访问 token 已被使用"),
FILE_TOKEN_MISMATCH("FILE_TOKEN_MISMATCH", "文件访问 token 与当前用户不匹配"),
FILE_CONTENT_TYPE_NOT_ALLOWED("FILE_CONTENT_TYPE_NOT_ALLOWED", "文件类型不被允许"),
FILE_SIZE_EXCEEDED("FILE_SIZE_EXCEEDED", "文件大小超出限制"),
FILE_NOT_UPLOADABLE("FILE_NOT_UPLOADABLE", "文件当前状态不允许上传"),
FILE_NOT_DOWNLOADABLE("FILE_NOT_DOWNLOADABLE", "文件当前状态不允许下载"),
FILE_DIGEST_MISMATCH("FILE_DIGEST_MISMATCH", "文件摘要校验失败"),
FILE_TOKEN_SECRET_NOT_CONFIGURED("FILE_TOKEN_SECRET_NOT_CONFIGURED", "文件 token 密钥未配置"),
```

**HTTP 状态码映射**:

| 错误码 | HTTP Status | 说明 |
|---|---|---|
| FILE_TOKEN_INVALID | 401 Unauthorized | token 无效或解密失败 |
| FILE_TOKEN_EXPIRED | 401 Unauthorized | token 已过期 |
| FILE_TOKEN_ALREADY_USED | 409 Conflict | token 重复使用 |
| FILE_TOKEN_MISMATCH | 403 Forbidden | 用户/企业/产品不匹配 |
| FILE_CONTENT_TYPE_NOT_ALLOWED | 422 Unprocessable Entity | 文件类型不允许 |
| FILE_SIZE_EXCEEDED | 422 Unprocessable Entity | 文件大小超限 |
| FILE_NOT_UPLOADABLE | 409 Conflict | 文件状态不允许上传 |
| FILE_NOT_DOWNLOADABLE | 409 Conflict | 文件状态不允许下载 |
| FILE_TOKEN_SECRET_NOT_CONFIGURED | 500 Internal Server Error | 服务端配置错误 |

## 七、国密加密与 Redis 集成

### 7.1 腾讯 Kona 加密套件集成

**依赖**（file-infrastructure/pom.xml）:

```xml
<dependency>
    <groupId>com.tencent.kona</groupId>
    <artifactId>kona-crypto</artifactId>
    <version>1.0.15</version>
</dependency>
```

**配置**（application.yml）:

```yaml
file:
  token:
    secret-key: ${FILE_TOKEN_SECRET_KEY:}   # SM4 密钥（Base64），16 字节
    default-upload-ttl: 15m
    default-download-ttl: 15m
```

> **注意**: SM4 是分组密码，密钥长度固定 16 字节（128 位）。Base64 编码后约 24 字符。生产环境通过环境变量 `FILE_TOKEN_SECRET_KEY` 注入，不入仓库。

### 7.2 KonaFileTokenGateway 实现

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class KonaFileTokenGateway implements FileTokenGateway {
    
    private final ObjectMapper objectMapper;
    private final FileTokenProperties properties;
    
    @Override
    public String encrypt(FileTokenPayload payload) {
        try {
            // 1. JSON 序列化
            String json = objectMapper.writeValueAsString(payload);
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            
            // 2. SM4 加密 (CBC 模式 + 随机 IV)
            byte[] key = Base64.getDecoder().decode(properties.getSecretKey());
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS5Padding", "KonaCrypto");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "SM4"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(data);
            
            // 3. 拼接 IV + 密文，Base64 编码
            byte[] output = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(encrypted, 0, output, iv.length, encrypted.length);
            
            return Base64.getUrlEncoder().withoutPadding().encodeToString(output);
        } catch (Exception e) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_SECRET_NOT_CONFIGURED)
                .withLogDetail("加密失败: " + e.getMessage());
        }
    }
    
    @Override
    public FileTokenPayload decrypt(String token) {
        try {
            byte[] input = Base64.getUrlDecoder().decode(token);
            byte[] iv = Arrays.copyOf(input, 16);
            byte[] encrypted = Arrays.copyOfRange(input, 16, input.length);
            
            byte[] key = Base64.getDecoder().decode(properties.getSecretKey());
            Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS5Padding", "KonaCrypto");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "SM4"), new IvParameterSpec(iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            
            String json = new String(decrypted, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, FileTokenPayload.class);
        } catch (Exception e) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_INVALID)
                .withLogDetail("解密失败: " + e.getMessage());
        }
    }
}
```

**设计要点**:
- **SM4/CBC/PKCS5Padding**: 国密标准模式，每次加密生成随机 IV 防止重放
- **IV 拼接密文**: IV 无需保密，直接前置拼接，解密时切分
- **URL-safe Base64**: token 用于 HTTP Header，避免 `+`/`/` 字符
- **Provider 名称 `KonaCrypto`**: Kona 注册的 JCE Provider 名称

### 7.3 Redis 一次性 Token 标记

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisFileTokenStore implements FileTokenStore {
    
    private final RedissonClient redissonClient;
    private final FileTokenProperties properties;
    
    @Override
    public boolean markUsed(String tokenId, Duration ttl) {
        String key = properties.getRedis().getKeyPrefix() + tokenId;
        RBucket<String> bucket = redissonClient.getBucket(key);
        // SETNX + EXPIRE 原子操作
        boolean success = bucket.setIfAbsent("1", ttl);
        if (!success) {
            log.warn("token 重复使用: tokenId={}", tokenId);
        }
        return success;
    }
    
    @Override
    public boolean isUsed(String tokenId) {
        String key = properties.getRedis().getKeyPrefix() + tokenId;
        return redissonClient.getBucket(key).isExists();
    }
}
```

### 7.4 FileTokenProperties 配置类

```java
@Data
@Validated
@ConfigurationProperties(prefix = "file.token")
public class FileTokenProperties {
    
    @NotBlank
    private String secretKey;  // Base64 编码的 SM4 密钥
    
    private Duration defaultUploadTtl = Duration.ofMinutes(15);
    private Duration defaultDownloadTtl = Duration.ofMinutes(15);
    
    private Redis redis = new Redis();
    
    @Data
    public static class Redis {
        private String keyPrefix = "file:token:used:";
        private Duration defaultTtl = Duration.ofMinutes(15);
    }
}
```

### 7.5 SM3 摘要计算

**摘要替换**: 原 `LocalFileStorage.computeMd5()` / `AliyunOSSFileStorage.computeMd5()` 等方法改为计算 SM3。

```java
// file-infrastructure/.../storage/LocalFileStorage.java
@Override
public String computeDigest(StorageTarget target, String storageKey) {
    java.security.MessageDigest sm3 = java.security.MessageDigest.getInstance("SM3", "KonaCrypto");
    try (InputStream in = Files.newInputStream(Paths.get(target.basePath(), storageKey))) {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            sm3.update(buffer, 0, bytesRead);
        }
    }
    return Hex.encodeHexString(sm3.digest());
}
```

**SPI 改造**: `FileStorageBackend` / `FileStorageGateway` 将 `computeMd5` 重命名为 `computeDigest`，`StoreResult.md5` 改为 `StoreResult.digest`。

### 7.6 Kona Provider 注册

```java
@AutoConfiguration
@ConditionalOnClass(name = "com.tencent.kona.crypto.provider.SM4")
public class KonaAutoConfiguration {
    
    @PostConstruct
    public void registerProvider() {
        Security.addProvider(new KonaCryptoProvider());
        log.info("KonaCrypto Provider 已注册");
    }
}
```

## 八、安全校验与异常处理

### 8.1 安全校验流程（上传）

```
前端 POST /api/file/access/upload
  ├─ Header: X-File-Token, X-User-No, X-Customer-No, X-Product-No
  └─ Body: MultipartFile

Adapter 层:
  1. 从 Header 组装 SessionUser(userNo, customerNo, productNo)
  2. 从 X-Forwarded-For 提取 clientIp
  3. 调用 UploadFileWithTokenUseCase.upload(token, session, file, clientIp)

UseCase 层:
  1. tokenService.verifyAndConsumeUploadToken(token, session, file_metadata)
     ├─ decrypt(token) → FileTokenPayload
     │   └─ 失败 → SystemException(FILE_TOKEN_INVALID)
     ├─ 校验 payload.expireAt > now
     │   └─ 失败 → SystemException(FILE_TOKEN_EXPIRED)
     ├─ 校验 payload.usage == UPLOAD
     │   └─ 失败 → SystemException(FILE_TOKEN_MISMATCH)
     ├─ 校验 session.userNo == payload.operator
     │   └─ 失败 → SystemException(FILE_TOKEN_MISMATCH) — 安全会话对比
     ├─ 校验 session.customerNo == payload.customerNo
     │   └─ 失败 → SystemException(FILE_TOKEN_MISMATCH)
     ├─ 校验 session.productNo == payload.productNo
     │   └─ 失败 → SystemException(FILE_TOKEN_MISMATCH)
     ├─ 校验 file.contentType ∈ payload.allowedContentTypes
     │   └─ 失败 → SystemException(FILE_CONTENT_TYPE_NOT_ALLOWED)
     ├─ 校验 file.size ≤ payload.allowedMaxSize
     │   └─ 失败 → SystemException(FILE_SIZE_EXCEEDED)
     ├─ markUsed(tokenId, ttl)
     │   └─ 失败 → SystemException(FILE_TOKEN_ALREADY_USED)
     └─ 返回 FileTokenPayload
  2. storageGateway.store(fileId, stream, size)
  3. fileMetadata.completeUpload(originalName, size, contentType, storageKey, digest)
  4. metadataRepository.save(fileMetadata)
  5. 写 ACCESS 流水（SUCCESS）

异常分支:
  ├─ 任何 SystemException → 写 ACCESS 流水（FAIL/REJECTED）→ 抛出
  └─ storageGateway.store 失败 → 写 ACCESS 流水（FAIL）→ 抛出
```

### 8.2 安全校验流程（下载）

```
前端 GET /api/file/access/download
  └─ Header: X-File-Token, X-User-No, X-Customer-No, X-Product-No

UseCase 层:
  1. tokenService.verifyAndConsumeDownloadToken(token, session, file_metadata)
     ├─ decrypt + expiry + usage=DOWNLOAD 校验
     ├─ session.userNo == payload.operator
     ├─ session.customerNo == payload.customerNo
     ├─ session.productNo == payload.productNo
     ├─ fileMetadata.status == UPLOADED (FILE_NOT_DOWNLOADABLE)
     ├─ !fileMetadata.isExpired() (FILE_EXPIRED)
     └─ markUsed(tokenId, ttl)
  2. 写 ACCESS 流水（SUCCESS）
  3. 返回 InputStream（流式）
```

### 8.3 流水记录与异常处理

**关键设计**: 无论成功还是失败，都写 ACCESS 流水记录。流水记录使用 `REQUIRES_NEW` 传播，确保流水不被业务事务回滚。

```java
// UploadFileWithTokenUseCase.upload() 完整实现（含异常处理）
@Transactional
public void upload(String token, SessionUser session, MultipartFile file, String clientIp) {
    FileTokenPayload payload;
    try {
        payload = tokenService.verifyAndConsumeUploadToken(token, session, /* file */);
    } catch (SystemException e) {
        // token 校验失败 — 无法获取 fileId/tokenId，写流水用 tokenHash
        writeAccessLogFailed(token, session, clientIp, e);
        throw e;
    }
    
    try {
        FileMetadata meta = metadataRepository.loadOrThrow(payload.fileId());
        StoreResult result = storageGateway.store(payload.fileId(), 
            file.getInputStream(), file.getSize());
        meta.completeUpload(
            file.getOriginalFilename(), file.getSize(), file.getContentType(),
            result.storageKey(), result.digest()
        );
        metadataRepository.save(meta);
        writeAccessLogSuccess(payload, session, clientIp);
    } catch (Exception e) {
        writeAccessLogFailed(payload, session, clientIp, e);
        throw e;
    }
}

// 使用 REQUIRES_NEW 传播，确保流水不被业务事务回滚
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void writeAccessLogFailed(String token, SessionUser session, 
                                   String clientIp, Exception e) {
    FileAccessLog log = FileAccessLog.accessFailed(
        /* fileId */ null, FileUsage.UPLOAD, session, 
        clientIp, tokenHashOf(token), e.getMessage()
    );
    logRepository.save(log);
}
```

### 8.4 下载场景的事务处理

**问题**: 下载需在事务内读取 InputStream，但流式返回到客户端时事务已提交，若客户端读取过程中文件被删除会导致异常。

**解决方案**: 分两个事务
1. **事务 1（`@Transactional`）**: token 校验 + 写流水 + 加载 FileMetadata（获取 storageKey）
2. **事务 2（无事务）**: 打开 InputStream 返回给 Adapter

见 5.1 中的 `DownloadFileWithTokenUseCase` 实现。

### 8.5 审计日志查询能力

`t_file_access_log` 支持以下审计查询（通过 `FileAccessLogRepository`）:

```java
public interface FileAccessLogRepository extends Repository<FileAccessLog, String> {
    // 查询某文件的所有访问记录
    List<FileAccessLog> findByFileId(FileId fileId);
    
    // 查询某 token 的 APPLY + ACCESS 记录（通过 tokenHash 关联）
    List<FileAccessLog> findByTokenHash(String tokenHash);
    
    // 查询某企业某产品的访问记录（分页）
    Page<FileAccessLog> findByCustomerAndProduct(CustomerNo customerNo, ProductNo productNo, 
                                                    LocalDateTime from, LocalDateTime to, 
                                                    Pageable pageable);
    
    // 统计某时段的访问量
    long countByActionAndTimeRange(FileAccessAction action, LocalDateTime from, LocalDateTime to);
}
```

## 九、模块改动范围

### 9.1 file-types 层
**无改动**。`CustomerNo`/`ProductNo`/`FileId`/`UserNo` 已存在于 shared-types。

### 9.2 file-domain 层（核心改动）

**新建文件**:
| 文件 | 类型 | 说明 |
|---|---|---|
| `model/aggregate/valueobject/FileAccessScope.java` | 值对象 | customerNo + productNo |
| `model/aggregate/valueobject/FileTokenPayload.java` | 值对象 | token 明文载荷（不持久化） |
| `model/aggregate/valueobject/SessionUser.java` | 值对象 | 会话用户（从 Header 提取） |
| `model/aggregate/valueobject/FileAccessAction.java` | 枚举 | APPLY / ACCESS |
| `model/aggregate/valueobject/FileAccessResult.java` | 枚举 | SUCCESS / FAIL / EXPIRED / REJECTED |
| `model/aggregate/root/FileAccessLog.java` | 聚合根 | 流水记录 |
| `gateway/FileTokenGateway.java` | SPI | 加密/解密 |
| `gateway/FileTokenStore.java` | SPI | Redis 一次性标记 |
| `repository/FileAccessLogRepository.java` | Repository | 流水查询 |
| `service/FileTokenService.java` | 领域服务 | token 生成/校验 |
| `event/UploadTokenAppliedEvent.java` | 领域事件 | |
| `event/DownloadTokenAppliedEvent.java` | 领域事件 | |
| `event/FileUploadedWithTokenEvent.java` | 领域事件 | |
| `event/FileDownloadedEvent.java` | 领域事件 | |

**修改文件**:
| 文件 | 改动 |
|---|---|
| `model/aggregate/root/FileMetadata.java` | 新增 `accessScope`/`digest`/`digestAlgorithm` 字段；`create()` 简化为 `createForUpload()`；新增 `completeUpload()`/`verifyDownloadable()` |
| `gateway/FileStorageGateway.java` | `computeMd5` → `computeDigest`；`StoreResult.md5` → `StoreResult.digest` |
| `errorcode/FileErrorCodes.java` | 新增 10 个 token/访问相关错误码 |

### 9.3 file-api 层

**新建文件**:
| 文件 | 说明 |
|---|---|
| `FileAccessApi.java` | 4 个 HTTP 接口：applyUploadToken/applyDownloadToken/upload/download |
| `request/ApplyUploadTokenRequest.java` | |
| `request/ApplyDownloadTokenRequest.java` | |
| `response/ApplyUploadTokenResponse.java` | |
| `response/ApplyDownloadTokenResponse.java` | |
| `response/UploadFileResponse.java` | |
| `event/FileAccessLogEventDTO.java` | 集成事件 DTO（可选，供其他服务订阅） |

### 9.4 file-application 层

**新建文件**:
| 文件 | 说明 |
|---|---|
| `usecase/ApplyUploadTokenUseCase.java` | 业务服务申请上传 token |
| `usecase/UploadFileWithTokenUseCase.java` | 前端实际上传 |
| `usecase/ApplyDownloadTokenUseCase.java` | 业务服务申请下载 token |
| `usecase/DownloadFileWithTokenUseCase.java` | 前端实际下载 |
| `command/ApplyUploadTokenCommand.java` | |
| `command/ApplyDownloadTokenCommand.java` | |
| 对应的 4 个 UseCaseTest | |

**修改文件**:
| 文件 | 改动 |
|---|---|
| `usecase/StoreFileUseCase.java` | 适配 `computeDigest` 重命名；考虑保留作为内部 API（非 token 路径） |
| `usecase/CopyFileUseCase.java` | 适配 `digest` 重命名 |

### 9.5 file-adapter 层

**新建文件**:
| 文件 | 说明 |
|---|---|
| `FileAccessAdapter.java` | 实现 `FileAccessApi`，流式下载 + Header 解析 |
| `converter/FileAccessConverter.java` | MapStruct DTO ↔ Command |

### 9.6 file-infrastructure 层

**新建文件**:
| 文件 | 说明 |
|---|---|
| `storage/KonaFileTokenGateway.java` | SM4 加解密实现 |
| `storage/RedisFileTokenStore.java` | Redis 一次性标记实现 |
| `storage/FileTokenProperties.java` | 配置类 |
| `storage/KonaAutoConfiguration.java` | Kona Provider 注册 |
| `entity/FileAccessLogDO.java` | MyBatis-Flex DO |
| `mapper/FileAccessLogMapper.java` | Mapper |
| `repository/FileAccessLogRepositoryImpl.java` | Repository 实现 |
| `converter/FileAccessLogConverter.java` | MapStruct |

**修改文件**:
| 文件 | 改动 |
|---|---|
| `entity/FileMetadataDO.java` | 新增 `accessScope`/`digest`/`digestAlgorithm` 字段 |
| `converter/FileMetadataConverter.java` | 新增 `accessScope` JSONB ↔ record 转换 |
| `repository/FileMetadataRepositoryImpl.java` | 适配新字段 |
| `storage/LocalFileStorage.java` | `computeMd5` → `computeDigest`（SM3） |
| `storage/AliyunOSSFileStorage.java` | 同上（OSS ETag 保留，另算 SM3） |
| `storage/NASFileStorage.java` | 同上 |
| `storage/FileStorageBackend.java` | SPI 方法重命名 |
| `storage/FileStorageRouter.java` | 适配重命名 |
| `storage/StorageAutoConfiguration.java` | 注册新增 Bean |
| `pom.xml` | 新增 `kona-crypto` 依赖 |
| `resources/schema-pg.sql` | 新增 `t_file_access_log` 表 + `t_file_metadata` ALTER |

### 9.7 file-starter 层

**修改文件**:
| 文件 | 改动 |
|---|---|
| `resources/application.yml` | 新增 `file.token` 配置块 |
| `resources/application-local.yml` | 本地覆盖（密钥用测试值） |

## 十、测试策略

### 10.1 单元测试（file-domain）

| 测试类 | 覆盖范围 |
|---|---|
| `FileMetadataTest` | `createForUpload()` 初始状态、`completeUpload()` 状态转换、`verifyDownloadable()` 校验 |
| `FileAccessScopeTest` | 值对象校验 |
| `FileTokenPayloadTest` | 值对象校验 |
| `FileAccessLogTest` | `apply()`/`access()` 工厂方法、`markSuccess()`/`markFail()` |
| `FileTokenServiceTest` | 4 个核心方法，mock `FileTokenGateway`/`FileTokenStore`，验证所有校验分支 |

### 10.2 应用层测试（file-application）

| 测试类 | 覆盖范围 |
|---|---|
| `ApplyUploadTokenUseCaseTest` | 正常流程 + 流水记录 |
| `UploadFileWithTokenUseCaseTest` | 正常上传 + token 校验失败各分支 + 流水失败记录 |
| `ApplyDownloadTokenUseCaseTest` | 正常流程 + 文件不可下载 |
| `DownloadFileWithTokenUseCaseTest` | 正常下载 + token 校验失败 |

### 10.3 基础设施测试（file-infrastructure）

| 测试类 | 覆盖范围 |
|---|---|
| `KonaFileTokenGatewayTest` | SM4 加解密 round-trip、错误密钥、错误 token |
| `RedisFileTokenStoreTest` | `markUsed` 成功/重复返回 false、`isUsed` |
| `FileAccessLogRepositoryImplTest` | CRUD + 查询方法 |
| `FileStorageRouterTest` | 适配 `computeDigest` 重命名 |
| `LocalFileStorageTest` | SM3 摘要计算 |

### 10.4 集成测试（file-infrastructure）

| 测试类 | 覆盖范围 |
|---|---|
| `FileAccessIntegrationTest` | 端到端：apply upload token → upload file → apply download token → download file，全流程验证 |

### 10.5 测试数据与配置

```yaml
# file-infrastructure/src/test/resources/application-test.yml
file:
  token:
    secret-key: "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6"  # 测试用 Base64 密钥
    default-upload-ttl: 15m
    default-download-ttl: 15m
    redis:
      key-prefix: "test:file:token:used:"
```

**测试 SM4 密钥**: 16 字节固定值（`0123456789abcdefghijklmnopqrstuvwxyz` 的前 16 字节），不入生产。

## 十一、实施顺序

按依赖关系分阶段：

1. **阶段 1: domain 层基础**（值对象 + FileAccessLog + 错误码）
2. **阶段 2: domain 层核心**（FileMetadata 改造 + FileTokenService + SPI 接口）
3. **阶段 3: infrastructure 层**（Kona + Redis + Repository + SM3 改造）
4. **阶段 4: application 层**（4 个 UseCase）
5. **阶段 5: adapter 层**（FileAccessAdapter + 流式下载）
6. **阶段 6: api 层 + 配置**（API 接口 + application.yml）
7. **阶段 7: 集成测试**（端到端验证）

## 十二、风险与缓解

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| Kona SDK 兼容性 | JDK 25 preview 可能与 Kona 不兼容 | 早期验证（阶段 3 首先跑通加解密 round-trip） |
| Redis 不可用 | token 一次性标记失效，重复使用风险 | 启动时检查 Redis 连接，失败则拒绝启动 |
| SM4 密钥泄露 | token 可被解密伪造 | 密钥通过环境变量注入，不入仓库；定期轮换 |
| 网关绕过 | Header 可被伪造 | 网络隔离（文件服务不暴露公网），仅网关可访问 |
| FileMetadata 改造影响现有功能 | StoreFileUseCase 等已有用例可能 break | 保留 `create()` 重载方法向后兼容，`completeUpload()` 与 `markUploaded()` 并存 |

## 十三、验收标准

- [ ] 业务服务可申请上传/下载 token，token 用 SM4 加密
- [ ] 前端可用 token 上传文件，文件服务校验 token + 会话用户一致性
- [ ] 前端可用 token 流式下载文件（8KB 缓冲，内存恒定）
- [ ] token 一次性使用，重复使用抛 FILE_TOKEN_ALREADY_USED
- [ ] token 过期、用户不匹配、文件类型/大小超限等场景正确拒绝
- [ ] 流水记录双记录（APPLY + ACCESS），含 tokenHash 关联
- [ ] 失败也写流水（含失败原因）
- [ ] 文件摘要用 SM3 计算
- [ ] 所有单元测试 + 集成测试通过
- [ ] application.yml 含 `file.token` 配置块
