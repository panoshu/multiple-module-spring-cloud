# file-service 模块说明

## 一、模块定位

file-service 是文件处理核心服务，负责文件上传/下载、Excel 模板化解析、子任务拆分与校验、数据导出以及基于 Token 的安全访问控制。采用 PostgreSQL 存储，大量使用 JSONB 保存嵌套结构（模板配置、解析数据、校验规则等）。

## 二、DDD 分层结构

```
file-types        → 领域原语（BizType, FileTaskId, SubTaskId, TemplateCode, TemplateConfigId）
file-domain       → 聚合根、值对象、实体、领域服务、SPI 网关接口、领域事件、Repository 接口、错误码
file-api          → @HttpExchange 接口、Request/Response DTO、集成事件 DTO
file-application  → UseCase 编排、Command/Query/Result、FileAccessLogWriter
file-adapter      → Controller 实现、MapStruct Converter
file-infrastructure → Repository 实现、DO/Mapper/Converter、存储后端、自动配置
file-starter      → Boot 类、application.yml
```

## 三、聚合根与核心领域模型

### 3.1 FileMetadata（文件元数据）

**生命周期**: `PENDING_UPLOAD → UPLOADED → DELETED`

两条创建路径：
- **遗留直传**: `create()` — 一次性传入所有字段，直接 `markUploaded()` 设置 MD5
- **Token 路径**: `createForUpload()` — 仅初始化元信息，`completeUpload()` 填充实际文件信息并设置 SM3 摘要

关键字段：
| 字段 | 说明 |
|------|------|
| `storageKey` | 内部存储路径（注释说"不对外暴露"但存在 getter） |
| `accessScope` | FileAccessScope 值对象 (customerNo + productNo)，JSONB 存储 |
| `digest` / `digestAlgorithm` | SM3 内容摘要 |
| `targetId` | 存储目标 ID，关联 StorageTarget |
| `storageType` | LOCAL / OSS / NAS |

### 3.2 TemplateConfig（模板配置）

**生命周期**: `DRAFT → ACTIVE → DEPRECATED`

核心结构：
```
TemplateConfig
├── BizType + templateVersion (唯一键)
├── ErrorPolicy (SKIP / ABORT / QUARANTINE)
├── CanonicalModelDef (属性定义 + 表格定义 schema)
├── List<ValidationRule> (校验规则，含 SpEL/QLExpress 表达式)
├── List<DerivationRule> (派生规则，从已有字段计算新字段)
├── SplitConfig (splitKey + maxRowsPerSubTask)
├── List<SourceTemplateDef> (源模板定义，含指纹用于自动识别)
├── targetTemplateRef (导出模板引用)
└── TargetMapping (导出字段映射)
```

模板自动识别: `autoIdentify(List<String> headers)` — 扫描前 10 行表头，与每个 SourceTemplateDef 的 fingerprint 比对，≥50% 匹配率且最高者胜出。

### 3.3 ParseTask（解析任务）

**生命周期**: `PENDING → PARSING → SPLITTING → VALIDATING → SUCCESS / PARTIAL_SUCCESS / FAILED`

渐进式记录子任务摘要（`recordSubTask()`），最终通过 `markSuccess()` / `markPartialSuccess()` / `markFailed()` 终结。

### 3.4 SubTaskData（子任务数据）

**生命周期**: `PENDING → VALID/INVALID → CONSUMED`

持有 `CanonicalData`（properties Map + tables Map），默认 30 天 TTL。`toSummary()` 为父 ParseTask 生成摘要。

### 3.5 FileAccessLog（访问审计流水）

不可变（仅 `markFail()` 可标记失败）。每条 Token 产生两条流水：APPLY（申请）+ ACCESS（实际访问），通过 `tokenHash` 关联。ID 使用 `UUID.randomUUID()`。

## 四、领域服务

| 服务 | 职责 |
|------|------|
| `FileTokenService` | Token 生成（SM4 加密）、验证、一次性消费（Redis SETNX） |
| `RegionStateMachine` | 驱动 RawRowStream 按有序 RegionDef 解析 Excel 区域 |
| `SourceTemplateIdentifier` | 自动识别源模板（扫描前 10 行表头指纹匹配） |
| `DataValidator` | 执行 ValidationRule（支持 FAIL_FAST / COLLECT_ALL） |
| `CanonicalModelBuilder` | 将 RegionParseResult 映射为 CanonicalData |
| `DataDeriver` | 执行 DerivationRule 派生新字段 |
| `TaskSplitter` | 按拆分键分组 + maxRowsPerSubTask 分块 |
| `ExportDecisionService` | 无状态，仅导出校验通过的 SplitUnit |
| `TableRegionParser` | TABLE 区域解析策略（header 别名规范化 + 数据行提取） |
| `KeyValueRegionParser` | KEY_VALUE 区域解析策略（label 别名匹配 + RIGHT 位置取值） |

## 五、SPI 网关接口

| 接口 | 实现类 | 说明 |
|------|--------|------|
| `FileStorageGateway` | `FileStorageRouter` | 按 StorageType 路由到后端，支持跨后端复制 |
| `FileTokenGateway` | `KonaFileTokenGateway` | SM4 加解密 Token（依赖 KonaCrypto） |
| `FileTokenStore` | `RedisFileTokenStore` | Redis SETNX 一次性消费 |
| `StorageTargetResolver` | `PropertiesBasedStorageTargetResolver` | YAML 属性路由 SOURCE/PARSED/EXPORT/ARCHIVE → 目标 |
| `ExcelParser` | `ExcelParserImpl` | Fesod 库双模式解析（stream / 直接 parse） |
| `ExpressionEvaluator` | `AviatorExpressionEvaluator` / `QLExpressExpressionEvaluator` | 条件表达式求值，通过 `file.expression.engine` 切换 |
| `ConfigLoader` | `YamlConfigLoader` | YAML → TemplateConfig 领域对象 |
| `ExcelExporter` | `FesodExcelExporter` | 模板填充导出（注意 fesod 2.0.2 占位符语法兼容问题） |

## 六、存储后端架构

```
FileStorageGateway (SPI)
    └── FileStorageRouter
            ├── LocalFileStorage   (LOCAL: 本地文件系统，无原子性保证)
            ├── NASFileStorage     (NAS: 临时文件 + atomic move，多节点安全)
            └── AliyunOSSFileStorage (OSS: 阿里云对象存储，@ConditionalOnClass)
```

**StorageKey 格式**: `{bizType}/{ISO_DATE}/{batchId}/{fileId}/{originalName}`

**跨后端复制**: `FileStorageRouter.copy()` — 源端开流 → 目标端存储，支持跨类型。

**摘要算法**: 所有后端统一使用 SM3（KonaCrypto Provider）。

## 七、自动配置

| 配置类 | 激活条件 | 说明 |
|--------|----------|------|
| `StorageAutoConfiguration` | `file.storage.enabled=true`（默认） | 注册 StorageTargetResolver + FileStorageRouter |
| `KonaAutoConfiguration` | `file.token.secret-key` 已配置 + KonaCrypto 在 classpath | 注册 KonaCrypto Provider + FileTokenGateway |
| `RedisFileTokenStoreAutoConfiguration` | `file.token.secret-key` + RedissonClient 在 classpath | 注册 RedisFileTokenStore |
| `FileDomainServiceConfiguration` | 始终 | 补充扫描 `com.example.file` 包下的 `@DomainService` |

## 八、核心流程

### 8.1 Token 上传流程

```
1. ApplyUploadTokenUseCase
   → FileMetadata.createForUpload() (status=PENDING_UPLOAD)
   → StorageTargetResolver.resolveByUsage(SOURCE)
   → FileTokenService.generateUploadToken() (SM4加密, 含 allowedContentTypes/allowedMaxSize)
   → FileAccessLog.apply() (APPLY 审计记录)
   → return (token, fileId)

2. UploadFileWithTokenUseCase
   → 解密 token → 加载 FileMetadata → 校验 content type + size
   → FileTokenService.verifyAndConsumeUploadToken() (Redis SETNX 一次性)
   → FileStorageGateway.store() (实际存储)
   → FileMetadata.completeUpload() (填充 metadata, SM3 digest)
   → FileAccessLogWriter.writeAccessLogSuccess() (REQUIRES_NEW 独立事务)
```

**关键设计**：校验在 Token 消费前，校验失败不消耗 Token；审计流水独立事务不受主事务回滚影响。

### 8.2 Token 下载流程

```
1. ApplyDownloadTokenUseCase
   → 加载 FileMetadata → verifyDownloadable()
   → FileTokenService.generateDownloadToken()
   → FileAccessLog.apply()

2. DownloadFileWithTokenUseCase
   → prepareDownload(): 解密 → 验证 → 消费 Token → 写 ACCESS 成功审计 → return DownloadContext
   → openStream(): FileStorageGateway.open() (事务外打开流，避免长事务)
   → Adapter: prepareDownload(tx内) → openStream(tx外) → StreamingResponseBody
```

### 8.3 文件解析流程（ParseFileUseCase — 最复杂的 UseCase）

```
1. 加载 ParseTask → markParsing()
2. 查找活跃 TemplateConfig
3. 打开文件流 → SourceTemplateIdentifier 识别模板
4. markSplitting() → 重新打开文件流
5. RegionStateMachine 驱动区域解析
6. CanonicalModelBuilder 构建标准数据
7. DataDeriver 执行派生规则
8. TaskSplitter 按拆分键拆分 + 分块
9. markValidating()
10. 对每个 SplitUnit：
    a. DataValidator 校验
    b. 创建 SubTaskData + applyValidationResult()
    c. 保存
    d. recordSubTask() 到 ParseTask
11. markSuccess / markPartialSuccess / markFailed
12. 保存 ParseTask → Repository 发布 FileParsedEvent
```

**注意**：文件流被打开两次（一次识别，一次解析）；CanonicalData 经过 Map → SplitUnit → Map 的双向转换，存在冗余。

### 8.4 数据导出流程

```
ExportDecisionService.filter() → 仅保留 ValidationResult.isValid() 的 SplitUnit
FesodExcelExporter.export() → 用目标模板填充数据写入输出流
```

**fesod 占位符**: `{regionName.field}` (非标准语法，因 fesod 2.0.2-incubating bug)

## 九、集成事件

### 9.1 发布的领域事件

| 事件 | 触发时机 | 消费方 |
|------|----------|--------|
| `FileParsedEvent` | ParseTask 完成解析 | kernel `FileParsedEventListener` → `FlowOrchestrationService.advanceByFileTaskId()` |
| `FileUploadedEvent` | 遗留直传完成 | 当前无消费方 |
| `FileUploadedWithTokenEvent` | Token 上传完成 | 当前无消费方 |
| `UploadTokenAppliedEvent` | 上传 Token 申请 | 当前无消费方 |
| `DownloadTokenAppliedEvent` | 下载 Token 申请 | 当前无消费方 |
| `FileDownloadedEvent` | 文件下载完成 | 当前无消费方 |
| `FileMetadataCreatedEvent` | 文件元数据创建 | 当前无消费方 |
| `FileDeletedEvent` | 文件删除 | 当前无消费方 |

### 9.2 事件传递机制

- **演示环境**: Repository `publishDomainEvents()` 通过 `ApplicationEventPublisher` 发布领域事件 → kernel `FileParsedEventListener` 监听
- **生产环境**: 领域事件 → `shared-event-starter` 转换为集成事件 → RocketMQ → 远程消费者

file-service 当前未发现集成事件发布适配器（没有 `@TransactionalEventListener` 将领域事件转为 `FileParsedEventDTO`），可能通过 `shared-event-starter` 的通用机制自动转换。

## 十、数据库表

| 表名 | 对应聚合根 | 主键类型 | 特殊字段 |
|------|-----------|---------|----------|
| `t_file_metadata` | FileMetadata | VARCHAR(64) | access_scope JSONB, digest, digest_algorithm |
| `t_file_template_config` | TemplateConfig | VARCHAR(64) | 6个 JSONB 字段: canonical_model, validation_rules, derivation_rules, split_config, source_templates, target_mapping |
| `t_file_parse_task` | ParseTask | VARCHAR(64) | sub_task_summaries JSONB, errors JSONB |
| `t_file_sub_task_data` | SubTaskData | VARCHAR(64) | context JSONB, properties JSONB, tables JSONB, validation_errors JSONB |
| `t_file_access_log` | FileAccessLog | VARCHAR(64) | token_hash (SHA-256), apply/access 双记录 |

**唯一约束**: `uk_file_template_config_biz_version` (biz_type, template_version)

## 十一、API 接口

### 11.1 FileAccessApi（/api/file/access）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /upload-token | 申请上传 Token |
| POST | /upload-with-token | Token 上传文件 |
| POST | /download-token | 申请下载 Token |
| POST | /download-with-token | Token 下载文件 |
| POST | /store | 遗留直传上传 |
| GET | /{fileId}/open | 打开文件流 |
| POST | /{fileId}/copy | 复制文件 |
| DELETE | /{fileId} | 删除文件 |

### 11.2 FileTaskApi（/api/file/task）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /upload | 上传文件并创建解析任务 |
| GET | /{fileTaskId} | 查询解析任务详情 |
| POST | /{fileTaskId}/cancel | 取消解析任务 |
| GET | /{fileTaskId}/sub-tasks | 查询子任务列表 |
| POST | /{fileTaskId}/sub-tasks/{subTaskId}/rows | 分页获取子任务行数据 |

### 11.3 TemplateConfigApi（/api/file/template-config）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /save | 保存模板配置 |
| POST | /activate | 激活模板配置 |
| GET | / | 查询模板配置 |
| GET | /{configId} | 按 ID 查询 |

### 11.4 ParsedDataApi（/api/file/parsed-data）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /{subTaskId}/fetch | 获取子任务解析数据 |

## 十二、配置属性

```yaml
file:
  token:
    secret-key: <SM4密钥>                    # 必填
    default-upload-ttl: 15m                  # 上传 Token 默认有效期
    default-download-ttl: 15m                # 下载 Token 默认有效期
    redis:
      key-prefix: "file:token:used:"         # Redis key 前缀
      default-ttl: 15m                       # 一次性标记 TTL
  storage:
    enabled: true
    targets:
      - id: local-dev
        type: LOCAL
        base-path: /tmp/file-storage
    routing:
      source: local-dev
      parsed: local-dev
      export: local-dev
      archive: local-dev
  expression:
    engine: aviator                           # aviator (默认) / qlexpress
```

## 十三、已知设计注意事项

1. **CanonicalData 是可变类**：使用 `setProperty()` 修改内部 Map，不符合值对象不可变原则，与项目中其他 record 类型值对象风格不一致。
2. **文件流打开两次**：`ParseFileUseCase` 先识别模板再完整解析，需要打开文件流两次，对大文件或远程存储效率有影响。
3. **Map 双向转换**：CanonicalData → flat Map（用于拆分）→ Map（用于校验），存在不必要的序列化/反序列化开销。
4. **YamlConfigLoader ID 生成**：使用 `"01HW-" + System.currentTimeMillis()`，与 approval-service 类似存在碰撞风险。
5. **FileMetadata.storageKey**：注释说"不对外暴露"但存在 public getter，存在信息泄露风险。
6. **FileAccessLog 删除保护**：`FileAccessLogRepositoryImpl.delete()` 和 `deleteById()` 抛 `UnsupportedOperationException`，保护审计完整性。
7. **Fesod 占位符语法**：因 fesod 2.0.2-incubating bug，模板需使用 `{regionName.field}` 而非标准的 `{.regionName.field}`。
8. **ExcelParserImpl 全量内存加载**：`openStream()` 将所有行读入 `ListBackedRawRowStream`，对大文件有 OOM 风险。
9. **集成事件发布缺口**：file-service 未显式将领域事件转换为 `FileParsedEventDTO` 发布到 MQ 的适配器代码，依赖 `shared-event-starter` 通用机制或尚未实现。
10. **LocalFileStorage vs NASFileStorage**：Local 无原子性保证（直接写入目标路径），NAS 使用临时文件 + atomic move。生产环境应使用 NAS 或 OSS。
