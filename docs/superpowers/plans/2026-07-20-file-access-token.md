# 文件服务 Token 访问机制实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为文件服务实现基于国密 SM4 加密的 Token 访问机制，支持业务服务申请 token、前端直接上传/下载、流水审计、一次性使用等功能。

**Architecture:** DDD 七层架构（types → domain → api → application → adapter → infrastructure → starter）。Token
作为领域服务（FileTokenService），加密/Redis 通过 SPI 隔离。FileMetadata 改造支持两阶段创建（createForUpload +
completeUpload），新增 FileAccessLog 聚合根记录审计流水。

**Tech Stack:** JDK 25 (preview), Spring Boot 3.5.14, MyBatis-Flex 1.11.5, PostgreSQL (JSONB), MapStruct 1.6.3, Lombok,
Redisson, 腾讯 Kona 1.0.15 (SM4/SM3 国密算法)

## Global Constraints

- JDK 25 启用 `--enable-preview`
- DDD 七层架构依赖规则严格遵守：domain 不依赖 application/infrastructure
- 强类型 ID：FileId (ULID record)、CustomerNo、ProductNo、UserNo 均 record + implements Identifier
- 领域服务用 `@DomainService` 注解，无状态
- API 接口用 `@HttpExchange` 定义在 file-api 层，Adapter 层实现
- MapStruct Converter 处理所有 DTO ↔ Domain 转换
- 单元测试用 JUnit 5 + AssertJ + Mockito
- 集成测试用 H2 + MyBatis-Flex (PostgreSQL 兼容模式)
- 测试文件不写入源码目录，用 `@TempDir` 或 `target/test-output/`
- 测试方法用 `@DisplayName` 描述
- 国密算法：SM4/CBC/PKCS5Padding + 随机 IV（加密），SM3（摘要）
- Token 一次性使用，Redis SETNX + TTL 标记
- 流水记录用 `REQUIRES_NEW` 传播，确保不被业务事务回滚
- 下载分两阶段事务：校验+流水在事务内，流式返回在事务外
- 现有 `FileMetadata.create()` 保留向后兼容，新增 `createForUpload()` 方法

---

### Task 1: file-domain 基础值对象与枚举

**Files:**

- Create:
  `file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/FileAccessScope.java`
- Create:
  `file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/FileTokenPayload.java`
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/SessionUser.java`
- Create:
  `file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/FileAccessAction.java`
- Create:
  `file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/FileAccessResult.java`
- Test:
  `file-service/file-domain/src/test/java/com/example/file/domain/model/aggregate/valueobject/FileAccessScopeTest.java`
- Test:
  `file-service/file-domain/src/test/java/com/example/file/domain/model/aggregate/valueobject/FileTokenPayloadTest.java`
- Test:
  `file-service/file-domain/src/test/java/com/example/file/domain/model/aggregate/valueobject/SessionUserTest.java`

**Interfaces:**

- Consumes: `com.example.shared.primitives.identity.FileId/UserNo`, `com.example.shared.domain.mark.ValueObject`,
  `com.example.file.domain.model.aggregate.valueobject.FileUsage`
- Produces: `FileAccessScope(CustomerNo, ProductNo)`,
  `FileTokenPayload(tokenId, fileId, usage, bizType, customerNo, productNo, operator, allowedContentTypes, allowedMaxSize, expireAt)`,
  `SessionUser(userNo, customerNo, productNo)`, `FileAccessAction.{APPLY, ACCESS}`,
  `FileAccessResult.{SUCCESS, FAIL, EXPIRED, REJECTED}`

- [ ] **Step 1: 写 FileAccessScope 失败测试**

```java
package com.example.file.domain.model.aggregate.valueobject;

import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.ProductNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FileAccessScope 值对象")
class FileAccessScopeTest {

    @Test
    @DisplayName("合法参数创建成功")
    void should_create_with_valid_params() {
        FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
        assertThat(scope.customerNo()).isEqualTo(CustomerNo.of("C001"));
        assertThat(scope.productNo()).isEqualTo(ProductNo.of("P001"));
    }

    @Test
    @DisplayName("customerNo 为 null 抛异常")
    void should_throw_when_customerNo_null() {
        assertThatThrownBy(() -> new FileAccessScope(null, ProductNo.of("P001")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("productNo 为 null 抛异常")
    void should_throw_when_productNo_null() {
        assertThatThrownBy(() -> new FileAccessScope(CustomerNo.of("C001"), null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl file-service/file-domain test -Dtest=FileAccessScopeTest`
Expected: FAIL (FileAccessScope 不存在)

- [ ] **Step 3: 实现 FileAccessScope**

```java
package com.example.file.domain.model.aggregate.valueobject;

import com.example.shared.domain.mark.ValueObject;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.ProductNo;

/**
 * 文件访问范围值对象（企业 + 产品）
 */
public record FileAccessScope(CustomerNo customerNo, ProductNo productNo) implements ValueObject {
    public FileAccessScope {
        if (customerNo == null) throw new IllegalArgumentException("customerNo 不能为空");
        if (productNo == null) throw new IllegalArgumentException("productNo 不能为空");
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl file-service/file-domain test -Dtest=FileAccessScopeTest`
Expected: PASS (3 tests)

- [ ] **Step 5: 写 FileTokenPayload 失败测试**

```java
package com.example.file.domain.model.aggregate.valueobject;

import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FileTokenPayload 值对象")
class FileTokenPayloadTest {

    @Test
    @DisplayName("上传 token 创建成功")
    void should_create_upload_token() {
        FileTokenPayload payload = new FileTokenPayload(
            "tok-001", new FileId("f001"), FileUsage.SOURCE, "import_declare",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            List.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            10L * 1024 * 1024, LocalDateTime.now().plusMinutes(15)
        );
        assertThat(payload.tokenId()).isEqualTo("tok-001");
        assertThat(payload.allowedContentTypes()).hasSize(1);
    }

    @Test
    @DisplayName("下载 token allowedContentTypes 可为空")
    void should_create_download_token_with_empty_content_types() {
        FileTokenPayload payload = new FileTokenPayload(
            "tok-002", new FileId("f001"), FileUsage.EXPORT, "export",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            null, null, LocalDateTime.now().plusMinutes(15)
        );
        assertThat(payload.allowedContentTypes()).isNull();
        assertThat(payload.allowedMaxSize()).isNull();
    }

    @Test
    @DisplayName("tokenId 为 null 抛异常")
    void should_throw_when_tokenId_null() {
        assertThatThrownBy(() -> new FileTokenPayload(
            null, new FileId("f001"), FileUsage.SOURCE, "biz",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            null, null, LocalDateTime.now().plusMinutes(15)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("expireAt 为 null 抛异常")
    void should_throw_when_expireAt_null() {
        assertThatThrownBy(() -> new FileTokenPayload(
            "tok-001", new FileId("f001"), FileUsage.SOURCE, "biz",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            null, null, null
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 6: 实现 FileTokenPayload**

```java
package com.example.file.domain.model.aggregate.valueobject;

import com.example.shared.domain.mark.ValueObject;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Token 明文载荷（不持久化）
 *
 * @param tokenId              token 唯一 ID（UUID）
 * @param fileId               文件 ID
 * @param usage                用途（SOURCE 上传 / EXPORT 下载等）
 * @param bizType              业务类型
 * @param customerNo           企业编号
 * @param productNo            产品编号
 * @param operator             操作人（uploader 或 downloader）
 * @param allowedContentTypes  允许的文件类型（上传 token 专有，下载为 null）
 * @param allowedMaxSize       允许的最大文件大小（上传 token 专有，下载为 null）
 * @param expireAt             过期时间
 */
public record FileTokenPayload(
    String tokenId,
    FileId fileId,
    FileUsage usage,
    String bizType,
    CustomerNo customerNo,
    ProductNo productNo,
    UserNo operator,
    List<String> allowedContentTypes,
    Long allowedMaxSize,
    LocalDateTime expireAt
) implements ValueObject {
    public FileTokenPayload {
        if (tokenId == null || tokenId.isBlank()) throw new IllegalArgumentException("tokenId 不能为空");
        if (fileId == null) throw new IllegalArgumentException("fileId 不能为空");
        if (usage == null) throw new IllegalArgumentException("usage 不能为空");
        if (customerNo == null) throw new IllegalArgumentException("customerNo 不能为空");
        if (productNo == null) throw new IllegalArgumentException("productNo 不能为空");
        if (operator == null) throw new IllegalArgumentException("operator 不能为空");
        if (expireAt == null) throw new IllegalArgumentException("expireAt 不能为空");
    }
}
```

- [ ] **Step 7: 写 SessionUser 失败测试**

```java
package com.example.file.domain.model.aggregate.valueobject;

import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SessionUser 值对象")
class SessionUserTest {

    @Test
    @DisplayName("合法参数创建成功")
    void should_create_with_valid_params() {
        SessionUser user = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));
        assertThat(user.userNo()).isEqualTo(UserNo.of("u1"));
        assertThat(user.customerNo()).isEqualTo(CustomerNo.of("C001"));
        assertThat(user.productNo()).isEqualTo(ProductNo.of("P001"));
    }

    @Test
    @DisplayName("userNo 为 null 抛异常")
    void should_throw_when_userNo_null() {
        assertThatThrownBy(() -> new SessionUser(null, CustomerNo.of("C001"), ProductNo.of("P001")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 8: 实现 SessionUser + 枚举**

```java
// SessionUser.java
package com.example.file.domain.model.aggregate.valueobject;

import com.example.shared.domain.mark.ValueObject;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;

/**
 * 会话用户值对象（从 HTTP Header 提取）
 */
public record SessionUser(UserNo userNo, CustomerNo customerNo, ProductNo productNo) implements ValueObject {
    public SessionUser {
        if (userNo == null) throw new IllegalArgumentException("userNo 不能为空");
        if (customerNo == null) throw new IllegalArgumentException("customerNo 不能为空");
        if (productNo == null) throw new IllegalArgumentException("productNo 不能为空");
    }
}

// FileAccessAction.java
package com.example.file.domain.model.aggregate.valueobject;

public enum FileAccessAction {
    APPLY,   // 申请 token
    ACCESS   // 实际访问
}

// FileAccessResult.java
package com.example.file.domain.model.aggregate.valueobject;

public enum FileAccessResult {
    SUCCESS,
    FAIL,
    EXPIRED,
    REJECTED
}
```

- [ ] **Step 9: 运行所有测试验证通过**

Run: `mvn -pl file-service/file-domain test -Dtest=FileAccessScopeTest,FileTokenPayloadTest,SessionUserTest`
Expected: PASS (8 tests)

- [ ] **Step 10: Commit**

```bash
git add file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/FileAccessScope.java \
        file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/FileTokenPayload.java \
        file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/SessionUser.java \
        file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/FileAccessAction.java \
        file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/valueobject/FileAccessResult.java \
        file-service/file-domain/src/test/java/com/example/file/domain/model/aggregate/valueobject/
git commit -m "feat(file-domain): 新增 Token 访问相关值对象与枚举"
```

---

### Task 2: FileErrorCodes 扩展

**Files:**

- Modify: `file-service/file-domain/src/main/java/com/example/file/domain/errorcode/FileErrorCodes.java`

**Interfaces:**

- Consumes: `com.example.shared.exception.ErrorDefinition`
- Produces: 10 个新错误码：FILE_TOKEN_INVALID / FILE_TOKEN_EXPIRED / FILE_TOKEN_ALREADY_USED / FILE_TOKEN_MISMATCH /
  FILE_CONTENT_TYPE_NOT_ALLOWED / FILE_SIZE_EXCEEDED / FILE_NOT_UPLOADABLE / FILE_NOT_DOWNLOADABLE /
  FILE_DIGEST_MISMATCH / FILE_TOKEN_SECRET_NOT_CONFIGURED

- [ ] **Step 1: 修改 FileErrorCodes 添加 10 个错误码**

在 `FILE_STREAM_CLOSED` 后追加：

```java
  FILE_STREAM_CLOSED("FILE_STREAM_CLOSED", "文件流已关闭"),

  // Token 访问相关
  FILE_TOKEN_INVALID("FILE_TOKEN_INVALID", "文件访问 token 无效或已过期"),
  FILE_TOKEN_EXPIRED("FILE_TOKEN_EXPIRED", "文件访问 token 已过期"),
  FILE_TOKEN_ALREADY_USED("FILE_TOKEN_ALREADY_USED", "文件访问 token 已被使用"),
  FILE_TOKEN_MISMATCH("FILE_TOKEN_MISMATCH", "文件访问 token 与当前用户不匹配"),
  FILE_CONTENT_TYPE_NOT_ALLOWED("FILE_CONTENT_TYPE_NOT_ALLOWED", "文件类型不被允许"),
  FILE_SIZE_EXCEEDED("FILE_SIZE_EXCEEDED", "文件大小超出限制"),
  FILE_NOT_UPLOADABLE("FILE_NOT_UPLOADABLE", "文件当前状态不允许上传"),
  FILE_NOT_DOWNLOADABLE("FILE_NOT_DOWNLOADABLE", "文件当前状态不允许下载"),
  FILE_DIGEST_MISMATCH("FILE_DIGEST_MISMATCH", "文件摘要校验失败"),
  FILE_TOKEN_SECRET_NOT_CONFIGURED("FILE_TOKEN_SECRET_NOT_CONFIGURED", "文件 token 密钥未配置");
```

- [ ] **Step 2: 运行编译验证**

Run: `mvn -pl file-service/file-domain compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add file-service/file-domain/src/main/java/com/example/file/domain/errorcode/FileErrorCodes.java
git commit -m "feat(file-domain): 扩展 10 个 Token 访问相关错误码"
```

---

### Task 3: FileAccessLog 聚合根 + Repository 接口

**Files:**

- Create: `file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/root/FileAccessLog.java`
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/repository/FileAccessLogRepository.java`
- Test: `file-service/file-domain/src/test/java/com/example/file/domain/model/aggregate/root/FileAccessLogTest.java`

**Interfaces:**

- Consumes: `com.example.shared.domain.aggregate.root.AggregateRoot`,
  `com.example.shared.primitives.identity.{FileId,UserNo,CustomerNo,ProductNo}`, `FileAccessScope`, `FileAccessAction`,
  `FileAccessResult`, `FileUsage`
- Produces: `FileAccessLog` 聚合根（apply/access 工厂方法 + markSuccess/markFail），`FileAccessLogRepository`
  接口（save/findById/findByFileId/findByTokenHash）

- [ ] **Step 1: 写 FileAccessLog 失败测试**

```java
package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.model.aggregate.valueobject.FileAccessAction;
import com.example.file.domain.model.aggregate.valueobject.FileAccessResult;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FileAccessLog 聚合根")
class FileAccessLogTest {

  @Test
  @DisplayName("apply 工厂方法创建 APPLY 记录")
  void should_create_apply_log() {
    FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
    FileAccessLog log = FileAccessLog.apply(
        new FileId("f001"), FileUsage.SOURCE, scope, UserNo.of("u1"),
        "approval-service", "hash-001"
    );
    assertThat(log.action()).isEqualTo(FileAccessAction.APPLY);
    assertThat(log.result()).isEqualTo(FileAccessResult.SUCCESS);
    assertThat(log.fileId()).isEqualTo(new FileId("f001"));
    assertThat(log.tokenHash()).isEqualTo("hash-001");
    assertThat(log.occurAt()).isNotNull();
  }

  @Test
  @DisplayName("access 工厂方法创建 ACCESS 记录（成功）")
  void should_create_access_success_log() {
    FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
    FileAccessLog log = FileAccessLog.access(
        new FileId("f001"), FileUsage.SOURCE, scope, UserNo.of("u1"),
        "approval-service", "192.168.1.1", "hash-001",
        FileAccessResult.SUCCESS, null
    );
    assertThat(log.action()).isEqualTo(FileAccessAction.ACCESS);
    assertThat(log.result()).isEqualTo(FileAccessResult.SUCCESS);
    assertThat(log.sourceIp()).isEqualTo("192.168.1.1");
  }

  @Test
  @DisplayName("access 工厂方法创建 ACCESS 记录（失败）")
  void should_create_access_failed_log() {
    FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
    FileAccessLog log = FileAccessLog.access(
        new FileId("f001"), FileUsage.SOURCE, scope, UserNo.of("u1"),
        "approval-service", "192.168.1.1", "hash-001",
        FileAccessResult.FAIL, "token 校验失败"
    );
    assertThat(log.result()).isEqualTo(FileAccessResult.FAIL);
    assertThat(log.failReason()).isEqualTo("token 校验失败");
  }

  @Test
  @DisplayName("markFail 修改 result 和 failReason")
  void should_mark_fail() {
    FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
    FileAccessLog log = FileAccessLog.apply(
        new FileId("f001"), FileUsage.SOURCE, scope, UserNo.of("u1"),
        "approval-service", "hash-001"
    );
    log.markFail("存储失败");
    assertThat(log.result()).isEqualTo(FileAccessResult.FAIL);
    assertThat(log.failReason()).isEqualTo("存储失败");
  }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl file-service/file-domain test -Dtest=FileAccessLogTest`
Expected: FAIL (FileAccessLog 不存在)

- [ ] **Step 3: 实现 FileAccessLog**

```java
package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.model.aggregate.valueobject.FileAccessAction;
import com.example.file.domain.model.aggregate.valueobject.FileAccessResult;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 文件访问流水聚合根
 * <p>
 * 记录 token 申请 (APPLY) 和实际访问 (ACCESS) 的双记录，用于审计。
 * 流水记录不可修改、不可删除。
 */
public class FileAccessLog extends AggregateRoot<String> {

    private final FileId fileId;
    private final FileAccessAction action;
    private final FileUsage usage;
    private final CustomerNo customerNo;
    private final ProductNo productNo;
    private final UserNo operator;
    private final String sourceApp;
    private final String sourceIp;
    private final String tokenHash;
    private FileAccessResult result;
    private String failReason;
    private final LocalDateTime occurAt;

    private FileAccessLog(String id, FileId fileId, FileAccessAction action, FileUsage usage,
                          CustomerNo customerNo, ProductNo productNo, UserNo operator,
                          String sourceApp, String sourceIp, String tokenHash,
                          FileAccessResult result, String failReason, LocalDateTime occurAt) {
        super(id, operator);
        this.fileId = fileId;
        this.action = action;
        this.usage = usage;
        this.customerNo = customerNo;
        this.productNo = productNo;
        this.operator = operator;
        this.sourceApp = sourceApp;
        this.sourceIp = sourceIp;
        this.tokenHash = tokenHash;
        this.result = result;
        this.failReason = failReason;
        this.occurAt = occurAt;
    }

    /**
     * 申请 token 时记录
     */
    public static FileAccessLog apply(FileId fileId, FileUsage usage, FileAccessScope scope,
                                       UserNo operator, String sourceApp, String tokenHash) {
        validateCommon(fileId, usage, scope, operator, tokenHash);
        return new FileAccessLog(
            java.util.UUID.randomUUID().toString(), fileId, FileAccessAction.APPLY, usage,
            scope.customerNo(), scope.productNo(), operator, sourceApp, null, tokenHash,
            FileAccessResult.SUCCESS, null, LocalDateTime.now()
        );
    }

    /**
     * 实际访问时记录（成功或失败）
     */
    public static FileAccessLog access(FileId fileId, FileUsage usage, FileAccessScope scope,
                                        UserNo operator, String sourceApp, String sourceIp,
                                        String tokenHash, FileAccessResult result, String failReason) {
        validateCommon(fileId, usage, scope, operator, tokenHash);
        if (result == null) throw new IllegalArgumentException("result 不能为空");
        return new FileAccessLog(
            java.util.UUID.randomUUID().toString(), fileId, FileAccessAction.ACCESS, usage,
            scope.customerNo(), scope.productNo(), operator, sourceApp, sourceIp, tokenHash,
            result, failReason, LocalDateTime.now()
        );
    }

    private static void validateCommon(FileId fileId, FileUsage usage, FileAccessScope scope,
                                        UserNo operator, String tokenHash) {
        if (fileId == null) throw new IllegalArgumentException("fileId 不能为空");
        if (usage == null) throw new IllegalArgumentException("usage 不能为空");
        if (scope == null) throw new IllegalArgumentException("scope 不能为空");
        if (operator == null) throw new IllegalArgumentException("operator 不能为空");
        if (tokenHash == null || tokenHash.isBlank()) throw new IllegalArgumentException("tokenHash 不能为空");
    }

    public void markFail(String failReason) {
        this.result = FileAccessResult.FAIL;
        this.failReason = failReason;
        markUpdated(operator);
    }

    @Override
    protected void validateInvariants() {
        if (action == null) throw new IllegalStateException("action 不能为空");
        if (result == null) throw new IllegalStateException("result 不能为空");
        if (occurAt == null) throw new IllegalStateException("occurAt 不能为空");
    }

    // Getters
    public FileId fileId() { return fileId; }
    public FileAccessAction action() { return action; }
    public FileUsage usage() { return usage; }
    public CustomerNo customerNo() { return customerNo; }
    public ProductNo productNo() { return productNo; }
    public UserNo operator() { return operator; }
    public String sourceApp() { return sourceApp; }
    public String sourceIp() { return sourceIp; }
    public String tokenHash() { return tokenHash; }
    public FileAccessResult result() { return result; }
    public String failReason() { return failReason; }
    public LocalDateTime occurAt() { return occurAt; }
}
```

- [ ] **Step 4: 实现 FileAccessLogRepository 接口**

```java
package com.example.file.domain.repository;

import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.valueobject.FileAccessAction;
import com.example.shared.domain.repository.Repository;
import com.example.shared.primitives.identity.FileId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FileAccessLogRepository extends Repository<FileAccessLog, String> {

    List<FileAccessLog> findByFileId(FileId fileId);

    List<FileAccessLog> findByTokenHash(String tokenHash);

    long countByActionAndTimeRange(FileAccessAction action, LocalDateTime from, LocalDateTime to);

    Optional<FileAccessLog> findById(String id);
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -pl file-service/file-domain test -Dtest=FileAccessLogTest`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/root/FileAccessLog.java \
        file-service/file-domain/src/main/java/com/example/file/domain/repository/FileAccessLogRepository.java \
        file-service/file-domain/src/test/java/com/example/file/domain/model/aggregate/root/FileAccessLogTest.java
git commit -m "feat(file-domain): 新增 FileAccessLog 聚合根和 Repository 接口"
```

---

### Task 4: 领域事件

**Files:**

- Create: `file-service/file-domain/src/main/java/com/example/file/domain/event/UploadTokenAppliedEvent.java`
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/event/DownloadTokenAppliedEvent.java`
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/event/FileUploadedWithTokenEvent.java`
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/event/FileDownloadedEvent.java`
- Test: `file-service/file-domain/src/test/java/com/example/file/domain/event/FileAccessEventTest.java`

**Interfaces:**

- Consumes: `com.example.shared.domain.event.DomainEvent`,
  `com.example.shared.primitives.identity.{EventId,FileId,UserNo,CustomerNo,ProductNo}`, `FileMetadata`
- Produces: 4 个领域事件，均含 `tokenHash` 字段

- [ ] **Step 1: 写事件失败测试**

```java
package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("文件访问领域事件")
class FileAccessEventTest {

    @Test
    @DisplayName("UploadTokenAppliedEvent.of 创建成功")
    void should_create_upload_token_applied_event() {
        FileMetadata file = newFileMetadata();
        UploadTokenAppliedEvent event = UploadTokenAppliedEvent.of(file, "hash-001", LocalDateTime.now().plusMinutes(15));
        assertThat(event.fileId()).isEqualTo(file.id());
        assertThat(event.tokenHash()).isEqualTo("hash-001");
        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("FileUploadedWithTokenEvent.of 创建成功")
    void should_create_file_uploaded_with_token_event() {
        FileMetadata file = newFileMetadata();
        file.markUploaded("storage-key-001", "digest-001");
        FileUploadedWithTokenEvent event = FileUploadedWithTokenEvent.of(file, "hash-001");
        assertThat(event.fileId()).isEqualTo(file.id());
        assertThat(event.tokenHash()).isEqualTo("hash-001");
        assertThat(event.digest()).isEqualTo("digest-001");
    }

    @Test
    @DisplayName("DownloadTokenAppliedEvent.of 创建成功")
    void should_create_download_token_applied_event() {
        FileMetadata file = newUploadedMetadata();
        DownloadTokenAppliedEvent event = DownloadTokenAppliedEvent.of(file, "hash-002", LocalDateTime.now().plusMinutes(15));
        assertThat(event.fileId()).isEqualTo(file.id());
        assertThat(event.tokenHash()).isEqualTo("hash-002");
    }

    @Test
    @DisplayName("FileDownloadedEvent.of 创建成功")
    void should_create_file_downloaded_event() {
        FileMetadata file = newUploadedMetadata();
        FileDownloadedEvent event = FileDownloadedEvent.of(file, "hash-003");
        assertThat(event.fileId()).isEqualTo(file.id());
        assertThat(event.tokenHash()).isEqualTo("hash-003");
    }

    private FileMetadata newFileMetadata() {
        return FileMetadata.create(
            new FileId("f001"), "sample.xlsx", 100L, "application/xlsx",
            FileUsage.SOURCE, "import_declare", "approval-service",
            new BatchId("b001"), "target-001", StorageType.LOCAL,
            UserNo.of("u1"), LocalDateTime.now().plusDays(7)
        );
    }

    private FileMetadata newUploadedMetadata() {
        FileMetadata file = newFileMetadata();
        file.markUploaded("storage-key-001", "digest-001");
        return file;
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl file-service/file-domain test -Dtest=FileAccessEventTest`
Expected: FAIL (4 个事件类不存在)

- [ ] **Step 3: 实现 4 个领域事件**

```java
// UploadTokenAppliedEvent.java
package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

public record UploadTokenAppliedEvent(
    EventId eventId,
    LocalDateTime occurredAt,
    FileId fileId,
    CustomerNo customerNo,
    ProductNo productNo,
    UserNo uploader,
    String tokenHash,
    LocalDateTime expireAt
) implements DomainEvent {
    public static UploadTokenAppliedEvent of(FileMetadata file, String tokenHash, LocalDateTime expireAt) {
        return new UploadTokenAppliedEvent(
            EventId.generate(), LocalDateTime.now(),
            file.id(), file.accessScope().customerNo(), file.accessScope().productNo(),
            file.uploadedBy(), tokenHash, expireAt
        );
    }
}

// FileUploadedWithTokenEvent.java
package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.FileId;

import java.time.LocalDateTime;

public record FileUploadedWithTokenEvent(
    EventId eventId,
    LocalDateTime occurredAt,
    FileId fileId,
    String originalName,
    long size,
    String digest,
    String tokenHash
) implements DomainEvent {
    public static FileUploadedWithTokenEvent of(FileMetadata file, String tokenHash) {
        return new FileUploadedWithTokenEvent(
            EventId.generate(), LocalDateTime.now(),
            file.id(), file.originalName(), file.size(), file.digest(), tokenHash
        );
    }
}

// DownloadTokenAppliedEvent.java
package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

public record DownloadTokenAppliedEvent(
    EventId eventId,
    LocalDateTime occurredAt,
    FileId fileId,
    CustomerNo customerNo,
    ProductNo productNo,
    UserNo downloader,
    String tokenHash,
    LocalDateTime expireAt
) implements DomainEvent {
    public static DownloadTokenAppliedEvent of(FileMetadata file, String tokenHash, LocalDateTime expireAt) {
        return new DownloadTokenAppliedEvent(
            EventId.generate(), LocalDateTime.now(),
            file.id(), file.accessScope().customerNo(), file.accessScope().productNo(),
            file.uploadedBy(), tokenHash, expireAt
        );
    }
}

// FileDownloadedEvent.java
package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

public record FileDownloadedEvent(
    EventId eventId,
    LocalDateTime occurredAt,
    FileId fileId,
    CustomerNo customerNo,
    ProductNo productNo,
    UserNo downloader,
    String tokenHash
) implements DomainEvent {
    public static FileDownloadedEvent of(FileMetadata file, String tokenHash) {
        return new FileDownloadedEvent(
            EventId.generate(), LocalDateTime.now(),
            file.id(), file.accessScope().customerNo(), file.accessScope().productNo(),
            file.uploadedBy(), tokenHash
        );
    }
}
```

- [ ] **Step 4: 运行测试验证失败（依赖 accessScope () 字段，Task 5 才实现）**

Run: `mvn -pl file-service/file-domain test -Dtest=FileAccessEventTest`
Expected: FAIL (`file.accessScope()` 方法不存在 — 这是预期的，Task 5 实现)

> **Note:** 此 Task 的事件类引用了 `file.accessScope()` 和 `file.digest()`，这两个方法在 Task 5
> 实现。事件类本身可编译，但测试会失败。这是预期行为，Task 5 完成后测试会通过。

- [ ] **Step 5: Commit**

```bash
git add file-service/file-domain/src/main/java/com/example/file/domain/event/UploadTokenAppliedEvent.java \
        file-service/file-domain/src/main/java/com/example/file/domain/event/FileUploadedWithTokenEvent.java \
        file-service/file-domain/src/main/java/com/example/file/domain/event/DownloadTokenAppliedEvent.java \
        file-service/file-domain/src/main/java/com/example/file/domain/event/FileDownloadedEvent.java \
        file-service/file-domain/src/test/java/com/example/file/domain/event/FileAccessEventTest.java
git commit -m "feat(file-domain): 新增 4 个文件访问领域事件

注意: 事件引用 file.accessScope() 和 file.digest()，
这两个方法在 Task 5 FileMetadata 改造后实现。"
```

---

### Task 5: FileMetadata 聚合根改造

**Files:**

- Modify: `file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/root/FileMetadata.java`
- Modify: `file-service/file-domain/src/test/java/com/example/file/domain/model/aggregate/root/FileMetadataTest.java`
  (若已存在)
- Test: `file-service/file-domain/src/test/java/com/example/file/domain/model/aggregate/root/FileMetadataTokenTest.java`

**Interfaces:**

- Consumes: `FileAccessScope`, `FileStatus`, `FileUsage`, `StorageType`, `AggregateRoot`
- Produces: `FileMetadata.createForUpload()` / `completeUpload()` / `verifyDownloadable()` / `accessScope()` /
  `digest()` / `digestAlgorithm()`

- [ ] **Step 1: 写 FileMetadata Token 改造测试**

```java
package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FileMetadata Token 访问改造")
class FileMetadataTokenTest {

    @Test
    @DisplayName("createForUpload 创建 PENDING_UPLOAD 状态，文件信息为 null")
    void should_create_for_upload_with_pending_status() {
        FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
        FileMetadata file = FileMetadata.createForUpload(
            new FileId("f001"), FileUsage.SOURCE, "import_declare", "approval-service",
            new BatchId("b001"), scope, "target-001", StorageType.LOCAL,
            UserNo.of("u1"), LocalDateTime.now().plusDays(7)
        );
        assertThat(file.status()).isEqualTo(FileStatus.PENDING_UPLOAD);
        assertThat(file.originalName()).isNull();
        assertThat(file.size()).isNull();
        assertThat(file.contentType()).isNull();
        assertThat(file.storageKey()).isNull();
        assertThat(file.accessScope()).isEqualTo(scope);
    }

    @Test
    @DisplayName("completeUpload 设置文件信息并转 UPLOADED 状态")
    void should_complete_upload() {
        FileMetadata file = newPendingFile();
        file.completeUpload(
            "report.xlsx", 1024L, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "storage-key-001", "sm3-digest-001"
        );
        assertThat(file.status()).isEqualTo(FileStatus.UPLOADED);
        assertThat(file.originalName()).isEqualTo("report.xlsx");
        assertThat(file.size()).isEqualTo(1024L);
        assertThat(file.digest()).isEqualTo("sm3-digest-001");
        assertThat(file.digestAlgorithm()).isEqualTo("SM3");
    }

    @Test
    @DisplayName("completeUpload 在非 PENDING_UPLOAD 状态抛异常")
    void should_throw_when_complete_upload_in_wrong_status() {
        FileMetadata file = newPendingFile();
        file.completeUpload("n.xlsx", 1L, "text/plain", "k", "d");
        assertThatThrownBy(() -> file.completeUpload("n.xlsx", 1L, "text/plain", "k", "d"))
            .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("verifyDownloadable 在 UPLOADED 状态通过")
    void should_verify_downloadable_when_uploaded() {
        FileMetadata file = newUploadedFile();
        file.verifyDownloadable();  // 不抛异常
    }

    @Test
    @DisplayName("verifyDownloadable 在 PENDING_UPLOAD 状态抛异常")
    void should_throw_when_verify_downloadable_pending() {
        FileMetadata file = newPendingFile();
        assertThatThrownBy(file::verifyDownloadable).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("verifyDownloadable 在过期时抛异常")
    void should_throw_when_verify_downloadable_expired() {
        FileMetadata file = FileMetadata.createForUpload(
            new FileId("f001"), FileUsage.SOURCE, "biz", "app",
            new BatchId("b001"),
            new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001")),
            "target-001", StorageType.LOCAL, UserNo.of("u1"),
            LocalDateTime.now().minusDays(1)  // 已过期
        );
        assertThatThrownBy(file::verifyDownloadable).isInstanceOf(DomainException.class);
    }

    private FileMetadata newPendingFile() {
        return FileMetadata.createForUpload(
            new FileId("f001"), FileUsage.SOURCE, "import_declare", "approval-service",
            new BatchId("b001"),
            new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001")),
            "target-001", StorageType.LOCAL, UserNo.of("u1"),
            LocalDateTime.now().plusDays(7)
        );
    }

    private FileMetadata newUploadedFile() {
        FileMetadata file = newPendingFile();
        file.completeUpload("report.xlsx", 1024L,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "storage-key-001", "sm3-digest-001");
        return file;
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl file-service/file-domain test -Dtest=FileMetadataTokenTest`
Expected: FAIL (createForUpload/completeUpload/verifyDownloadable/accessScope/digest 方法不存在)

- [ ] **Step 3: 修改 FileMetadata 类**

在现有 FileMetadata 基础上：

1. 新增字段 `accessScope`、`digest`、`digestAlgorithm`，将 `size` 改为 `Long`（允许 null）
2. 保留现有 `create()` 方法（向后兼容）
3. 新增 `createForUpload()` 方法
4. 新增 `completeUpload()` 方法
5. 新增 `verifyDownloadable()` 方法
6. 新增 getters: `accessScope()`, `digest()`, `digestAlgorithm()`

完整修改后的 FileMetadata.java（保留原有 create/reconstitute/markUploaded/markDeleted，新增
createForUpload/completeUpload/verifyDownloadable）:

```java
package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.event.FileDeletedEvent;
import com.example.file.domain.event.FileMetadataCreatedEvent;
import com.example.file.domain.event.FileUploadedEvent;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
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

public class FileMetadata extends AggregateRoot<FileId> {

    private String originalName;
    private Long size;              // 改为 Long 允许 null（PENDING_UPLOAD 时为 null）
    private String contentType;
    private String md5;             // 旧字段保留
    private String digest;          // 新字段：SM3 摘要
    private String digestAlgorithm; // 新字段：摘要算法

    private FileAccessScope accessScope;  // 新字段

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

    // ============ 原有 create（保留向后兼容）============
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

    // ============ 新增 createForUpload（Token 路径）============
    private FileMetadata(FileId id, FileUsage usage, String bizType, String sourceApp,
                         BatchId businessBatchId, FileAccessScope accessScope,
                         String targetId, StorageType storageType,
                         UserNo uploader, LocalDateTime expiresAt) {
        super(id, uploader);
        this.usage = usage;
        this.bizType = bizType;
        this.sourceApp = sourceApp;
        this.businessBatchId = businessBatchId;
        this.accessScope = accessScope;
        this.targetId = targetId;
        this.storageType = storageType;
        this.uploadedBy = uploader;
        this.expiresAt = expiresAt;
        this.status = FileStatus.PENDING_UPLOAD;
        // originalName/size/contentType/storageKey/digest 留空，completeUpload 时填充
        registerDomainEvent(FileMetadataCreatedEvent.of(this));
    }

    public static FileMetadata createForUpload(FileId id, FileUsage usage, String bizType,
                                                String sourceApp, BatchId businessBatchId,
                                                FileAccessScope accessScope,
                                                String targetId, StorageType storageType,
                                                UserNo uploader, LocalDateTime expiresAt) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        if (usage == null) throw new IllegalArgumentException("usage 不能为空");
        if (accessScope == null) throw new IllegalArgumentException("accessScope 不能为空");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId 不能为空");
        if (storageType == null) throw new IllegalArgumentException("storageType 不能为空");
        if (uploader == null) throw new IllegalArgumentException("uploader 不能为空");
        return new FileMetadata(id, usage, bizType, sourceApp, businessBatchId,
            accessScope, targetId, storageType, uploader, expiresAt);
    }

    // ============ 新增 completeUpload（Token 路径）============
    public void completeUpload(String originalName, long size, String contentType,
                                String storageKey, String digest) {
        if (this.status != FileStatus.PENDING_UPLOAD) {
            throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
                .withLogDetail("当前状态: " + this.status + ", 期望状态: PENDING_UPLOAD, fileId=" + id());
        }
        if (originalName == null || originalName.isBlank()) {
            throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
                .withLogDetail("originalName 不能为空, fileId=" + id());
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
                .withLogDetail("storageKey 不能为空, fileId=" + id());
        }
        this.originalName = originalName;
        this.size = size;
        this.contentType = contentType;
        this.storageKey = storageKey;
        this.digest = digest;
        this.digestAlgorithm = "SM3";
        this.status = FileStatus.UPLOADED;
        this.uploadedAt = LocalDateTime.now();
        markUpdated(this.uploadedBy != null ? this.uploadedBy : this.createdBy());
        registerDomainEvent(FileUploadedEvent.of(this));
    }

    // ============ 新增 verifyDownloadable ============
    public void verifyDownloadable() {
        if (this.status != FileStatus.UPLOADED) {
            throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
                .withLogDetail("文件当前状态不允许下载: " + this.status + ", fileId=" + id());
        }
        if (isExpired()) {
            throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
                .withLogDetail("文件已过期, fileId=" + id());
        }
    }

    // ============ 数据库重建（更新：含新字段）============
    public FileMetadata(FileId id, String originalName, Long size, String contentType, String md5,
                        String digest, String digestAlgorithm, FileAccessScope accessScope,
                        String targetId, StorageType storageType, String storageKey,
                        FileUsage usage, String bizType, String sourceApp, BatchId businessBatchId,
                        FileStatus status, UserNo uploadedBy, LocalDateTime uploadedAt, LocalDateTime expiresAt,
                        UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.originalName = originalName;
        this.size = size;
        this.contentType = contentType;
        this.md5 = md5;
        this.digest = digest;
        this.digestAlgorithm = digestAlgorithm;
        this.accessScope = accessScope;
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

    public static FileMetadata reconstitute(FileId id, String originalName, Long size, String contentType, String md5,
                                             String digest, String digestAlgorithm, FileAccessScope accessScope,
                                             String targetId, StorageType storageType, String storageKey,
                                             FileUsage usage, String bizType, String sourceApp, BatchId businessBatchId,
                                             FileStatus status, UserNo uploadedBy, LocalDateTime uploadedAt, LocalDateTime expiresAt,
                                             UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new FileMetadata(id, originalName, size, contentType, md5, digest, digestAlgorithm,
            accessScope, targetId, storageType, storageKey, usage, bizType, sourceApp, businessBatchId,
            status, uploadedBy, uploadedAt, expiresAt, createdBy, updatedBy, createdAt, updatedAt, version);
    }

    // ============ 保留原有 create（向后兼容）============
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

    // ============ 保留原有 markUploaded ============
    public void markUploaded(String storageKey, String md5) {
        if (this.status != FileStatus.PENDING_UPLOAD) {
            throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
                .withLogDetail("当前状态: " + this.status + ", 期望状态: PENDING_UPLOAD, fileId=" + id());
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new DomainException(SharedDomainErrorCode.INVALID_OPERATION)
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
        if (size != null && size < 0) {
            throw new IllegalStateException("size 不能为负, fileId=" + id());
        }
        if (status == FileStatus.UPLOADED) {
            if (uploadedAt == null || storageKey == null) {
                throw new IllegalStateException("UPLOADED 状态下 uploadedAt 和 storageKey 不能为空, fileId=" + id());
            }
        }
    }

    // Getters
    public String originalName() { return originalName; }
    public Long size() { return size; }
    public String contentType() { return contentType; }
    public String md5() { return md5; }
    public String digest() { return digest; }
    public String digestAlgorithm() { return digestAlgorithm; }
    public FileAccessScope accessScope() { return accessScope; }
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

- [ ] **Step 4: 运行所有 domain 测试验证通过**

Run: `mvn -pl file-service/file-domain test`
Expected: PASS (含原有测试 + 新增 FileMetadataTokenTest + Task 4 事件测试)

- [ ] **Step 5: Commit**

```bash
git add file-service/file-domain/src/main/java/com/example/file/domain/model/aggregate/root/FileMetadata.java \
        file-service/file-domain/src/test/java/com/example/file/domain/model/aggregate/root/FileMetadataTokenTest.java
git commit -m "feat(file-domain): FileMetadata 新增 createForUpload/completeUpload/verifyDownloadable 方法

- 新增 accessScope/digest/digestAlgorithm 字段
- size 改为 Long 允许 null（PENDING_UPLOAD 时为 null）
- 保留原 create() 和 markUploaded() 向后兼容
- 数据库重建构造函数签名更新（含新字段）"
```

---

### Task 6: FileTokenGateway + FileTokenStore SPI

**Files:**

- Create: `file-service/file-domain/src/main/java/com/example/file/domain/gateway/FileTokenGateway.java`
- Create: `file-service/file-domain/src/main/java/com/example/file/domain/gateway/FileTokenStore.java`

**Interfaces:**

- Consumes: `FileTokenPayload`
- Produces: `FileTokenGateway.encrypt/decrypt`, `FileTokenStore.markUsed/isUsed`

- [ ] **Step 1: 实现 FileTokenGateway SPI**

```java
package com.example.file.domain.gateway;

import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;

/**
 * 文件 Token 加密网关 SPI
 * <p>
 * 由 KonaFileTokenGateway 实现，使用国密 SM4 算法。
 */
public interface FileTokenGateway {

    /**
     * 加密 token 载荷，返回密文字符串
     */
    String encrypt(FileTokenPayload payload);

    /**
     * 解密 token 字符串，返回载荷
     * 解密失败或格式错误抛 SystemException(FILE_TOKEN_INVALID)
     */
    FileTokenPayload decrypt(String token);
}
```

- [ ] **Step 2: 实现 FileTokenStore SPI**

```java
package com.example.file.domain.gateway;

import java.time.Duration;

/**
 * 文件 Token 一次性使用标记 SPI
 * <p>
 * 由 RedisFileTokenStore 实现，基于 Redis SETNX + TTL。
 */
public interface FileTokenStore {

    /**
     * 标记 token 已使用
     * @return true=首次标记成功, false=已存在（重复使用）
     */
    boolean markUsed(String tokenId, Duration ttl);

    /**
     * 检查 token 是否已使用
     */
    boolean isUsed(String tokenId);
}
```

- [ ] **Step 3: 运行编译验证**

Run: `mvn -pl file-service/file-domain compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add file-service/file-domain/src/main/java/com/example/file/domain/gateway/FileTokenGateway.java \
        file-service/file-domain/src/main/java/com/example/file/domain/gateway/FileTokenStore.java
git commit -m "feat(file-domain): 新增 FileTokenGateway 和 FileTokenStore SPI 接口"
```

---

### Task 7: FileTokenService 领域服务

**Files:**

- Create: `file-service/file-domain/src/main/java/com/example/file/domain/service/FileTokenService.java`
- Test: `file-service/file-domain/src/test/java/com/example/file/domain/service/FileTokenServiceTest.java`

**Interfaces:**

- Consumes: `FileTokenGateway`, `FileTokenStore`, `FileTokenPayload`, `SessionUser`, `FileMetadata`, `FileErrorCodes`,
  `@DomainService`
- Produces: `FileTokenService.generateUploadToken()`, `generateDownloadToken()`, `verifyAndConsumeUploadToken()`,
  `verifyAndConsumeDownloadToken()`

- [ ] **Step 1: 写 FileTokenService 失败测试**

```java
package com.example.file.domain.service;

import com.example.file.domain.gateway.FileTokenGateway;
import com.example.file.domain.gateway.FileTokenStore;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.SessionUser;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.shared.exception.SystemException;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("FileTokenService 领域服务")
class FileTokenServiceTest {

    private FileTokenGateway tokenGateway;
    private FileTokenStore tokenStore;
    private FileTokenService service;

    @BeforeEach
    void setUp() {
        tokenGateway = mock(FileTokenGateway.class);
        tokenStore = mock(FileTokenStore.class);
        service = new FileTokenService(tokenGateway, tokenStore);
    }

    @Test
    @DisplayName("generateUploadToken 调用 gateway.encrypt 返回密文")
    void should_generate_upload_token() {
        FileMetadata file = newPendingFile();
        when(tokenGateway.encrypt(any())).thenReturn("encrypted-token");

        String token = service.generateUploadToken(file,
            List.of("application/xlsx"), 10L * 1024 * 1024, Duration.ofMinutes(15));

        assertThat(token).isEqualTo("encrypted-token");
        verify(tokenGateway).encrypt(argThat(p -> p.usage() == FileUsage.SOURCE
            && p.allowedContentTypes().contains("application/xlsx")));
    }

    @Test
    @DisplayName("generateDownloadToken 文件未上传抛异常")
    void should_throw_when_generate_download_token_for_pending_file() {
        FileMetadata file = newPendingFile();
        assertThatThrownBy(() -> service.generateDownloadToken(file, Duration.ofMinutes(15)))
            .isInstanceOf(SystemException.class);
    }

    @Test
    @DisplayName("verifyAndConsumeUploadToken 成功返回 payload")
    void should_verify_and_consume_upload_token_success() {
        FileMetadata file = newUploadedFile();
        FileTokenPayload payload = new FileTokenPayload(
            "tok-001", file.id(), FileUsage.SOURCE, "biz",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(10)
        );
        when(tokenGateway.decrypt("encrypted-token")).thenReturn(payload);
        when(tokenStore.markUsed(eq("tok-001"), any())).thenReturn(true);

        SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));
        FileTokenPayload result = service.verifyAndConsumeUploadToken("encrypted-token", session, file);

        assertThat(result.tokenId()).isEqualTo("tok-001");
        verify(tokenStore).markUsed(eq("tok-001"), any());
    }

    @Test
    @DisplayName("verifyAndConsumeUploadToken 用户不匹配抛异常")
    void should_throw_when_user_mismatch() {
        FileMetadata file = newUploadedFile();
        FileTokenPayload payload = new FileTokenPayload(
            "tok-001", file.id(), FileUsage.SOURCE, "biz",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(10)
        );
        when(tokenGateway.decrypt("encrypted-token")).thenReturn(payload);

        SessionUser session = new SessionUser(UserNo.of("u2"), CustomerNo.of("C001"), ProductNo.of("P001"));
        assertThatThrownBy(() -> service.verifyAndConsumeUploadToken("encrypted-token", session, file))
            .isInstanceOf(SystemException.class);
    }

    @Test
    @DisplayName("verifyAndConsumeUploadToken 企业不匹配抛异常")
    void should_throw_when_customer_mismatch() {
        FileMetadata file = newUploadedFile();
        FileTokenPayload payload = new FileTokenPayload(
            "tok-001", file.id(), FileUsage.SOURCE, "biz",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(10)
        );
        when(tokenGateway.decrypt("encrypted-token")).thenReturn(payload);

        SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C002"), ProductNo.of("P001"));
        assertThatThrownBy(() -> service.verifyAndConsumeUploadToken("encrypted-token", session, file))
            .isInstanceOf(SystemException.class);
    }

    @Test
    @DisplayName("verifyAndConsumeUploadToken token 已使用抛异常")
    void should_throw_when_token_already_used() {
        FileMetadata file = newUploadedFile();
        FileTokenPayload payload = new FileTokenPayload(
            "tok-001", file.id(), FileUsage.SOURCE, "biz",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(10)
        );
        when(tokenGateway.decrypt("encrypted-token")).thenReturn(payload);
        when(tokenStore.markUsed(eq("tok-001"), any())).thenReturn(false);

        SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));
        assertThatThrownBy(() -> service.verifyAndConsumeUploadToken("encrypted-token", session, file))
            .isInstanceOf(SystemException.class);
    }

    @Test
    @DisplayName("verifyAndConsumeUploadToken 过期抛异常")
    void should_throw_when_token_expired() {
        FileMetadata file = newUploadedFile();
        FileTokenPayload payload = new FileTokenPayload(
            "tok-001", file.id(), FileUsage.SOURCE, "biz",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().minusMinutes(1)
        );
        when(tokenGateway.decrypt("encrypted-token")).thenReturn(payload);

        SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));
        assertThatThrownBy(() -> service.verifyAndConsumeUploadToken("encrypted-token", session, file))
            .isInstanceOf(SystemException.class);
    }

    @Test
    @DisplayName("verifyAndConsumeDownloadToken 成功返回 payload")
    void should_verify_and_consume_download_token_success() {
        FileMetadata file = newUploadedFile();
        FileTokenPayload payload = new FileTokenPayload(
            "tok-002", file.id(), FileUsage.EXPORT, "biz",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            null, null, LocalDateTime.now().plusMinutes(10)
        );
        when(tokenGateway.decrypt("encrypted-token")).thenReturn(payload);
        when(tokenStore.markUsed(eq("tok-002"), any())).thenReturn(true);

        SessionUser session = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));
        FileTokenPayload result = service.verifyAndConsumeDownloadToken("encrypted-token", session, file);

        assertThat(result.tokenId()).isEqualTo("tok-002");
    }

    private FileMetadata newPendingFile() {
        return FileMetadata.createForUpload(
            new FileId("f001"), FileUsage.SOURCE, "biz", "approval-service",
            new BatchId("b001"),
            new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001")),
            "target-001", StorageType.LOCAL, UserNo.of("u1"),
            LocalDateTime.now().plusDays(7)
        );
    }

    private FileMetadata newUploadedFile() {
        FileMetadata file = newPendingFile();
        file.completeUpload("report.xlsx", 1024L, "application/xlsx", "storage-key", "sm3-digest");
        return file;
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl file-service/file-domain test -Dtest=FileTokenServiceTest`
Expected: FAIL (FileTokenService 不存在)

- [ ] **Step 3: 实现 FileTokenService**

```java
package com.example.file.domain.service;

import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.FileTokenGateway;
import com.example.file.domain.gateway.FileTokenStore;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.SessionUser;
import com.example.shared.exception.SystemException;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import com.example.shared.domain.mark.DomainService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 文件 Token 领域服务
 * <p>
 * 负责 token 的生成、校验、消费（一次性使用标记）。
 * 业务规则（用户对比、文件类型/大小限制、过期判断）在此层实现。
 */
@DomainService
public class FileTokenService {

    private final FileTokenGateway tokenGateway;
    private final FileTokenStore tokenStore;

    public FileTokenService(FileTokenGateway tokenGateway, FileTokenStore tokenStore) {
        this.tokenGateway = tokenGateway;
        this.tokenStore = tokenStore;
    }

    /**
     * 生成上传 token
     */
    public String generateUploadToken(FileMetadata file, List<String> allowedContentTypes,
                                       Long allowedMaxSize, Duration ttl) {
        FileTokenPayload payload = new FileTokenPayload(
            UUID.randomUUID().toString(),
            file.id(),
            file.usage(),
            file.bizType(),
            file.accessScope().customerNo(),
            file.accessScope().productNo(),
            file.uploadedBy(),
            allowedContentTypes,
            allowedMaxSize,
            LocalDateTime.now().plus(ttl)
        );
        return tokenGateway.encrypt(payload);
    }

    /**
     * 生成下载 token
     */
    public String generateDownloadToken(FileMetadata file, Duration ttl) {
        file.verifyDownloadable();
        FileTokenPayload payload = new FileTokenPayload(
            UUID.randomUUID().toString(),
            file.id(),
            file.usage(),
            file.bizType(),
            file.accessScope().customerNo(),
            file.accessScope().productNo(),
            file.uploadedBy(),
            null,
            null,
            LocalDateTime.now().plus(ttl)
        );
        return tokenGateway.encrypt(payload);
    }

    /**
     * 校验上传 token 并消费（一次性使用）
     */
    public FileTokenPayload verifyAndConsumeUploadToken(String token, SessionUser session,
                                                         FileMetadata file) {
        FileTokenPayload payload = decryptAndVerify(token, session, file);

        if (payload.usage() != file.usage()) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_MISMATCH)
                .withLogDetail("token usage: " + payload.usage() + ", file usage: " + file.usage());
        }

        // 一次性使用标记
        Duration remainingTtl = Duration.between(LocalDateTime.now(), payload.expireAt());
        if (!tokenStore.markUsed(payload.tokenId(), remainingTtl)) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_ALREADY_USED)
                .withLogDetail("tokenId: " + payload.tokenId());
        }

        return payload;
    }

    /**
     * 校验下载 token 并消费
     */
    public FileTokenPayload verifyAndConsumeDownloadToken(String token, SessionUser session,
                                                           FileMetadata file) {
        FileTokenPayload payload = decryptAndVerify(token, session, file);
        file.verifyDownloadable();

        Duration remainingTtl = Duration.between(LocalDateTime.now(), payload.expireAt());
        if (!tokenStore.markUsed(payload.tokenId(), remainingTtl)) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_ALREADY_USED)
                .withLogDetail("tokenId: " + payload.tokenId());
        }

        return payload;
    }

    private FileTokenPayload decryptAndVerify(String token, SessionUser session, FileMetadata file) {
        FileTokenPayload payload = tokenGateway.decrypt(token);

        // 过期校验
        if (payload.expireAt().isBefore(LocalDateTime.now())) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_EXPIRED)
                .withLogDetail("tokenId: " + payload.tokenId());
        }

        // 会话用户对比
        if (!payload.operator().equals(session.userNo())) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_MISMATCH)
                .withLogDetail("token operator: " + payload.operator() + ", session: " + session.userNo());
        }
        if (!payload.customerNo().equals(session.customerNo())) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_MISMATCH)
                .withLogDetail("token product: " + payload.customerNo() + ", session: " + session.customerNo());
        }
        if (!payload.productNo().equals(session.productNo())) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_MISMATCH)
                .withLogDetail("token product: " + payload.productNo() + ", session: " + session.productNo());
        }

        // 文件 ID 对比
        if (!payload.fileId().equals(file.id())) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_MISMATCH)
                .withLogDetail("token fileId: " + payload.fileId() + ", file: " + file.id());
        }

        return payload;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl file-service/file-domain test -Dtest=FileTokenServiceTest`
Expected: PASS (8 tests)

- [ ] **Step 5: Commit**

```bash
git add file-service/file-domain/src/main/java/com/example/file/domain/service/FileTokenService.java \
        file-service/file-domain/src/test/java/com/example/file/domain/service/FileTokenServiceTest.java
git commit -m "feat(file-domain): 新增 FileTokenService 领域服务"
```

---

### Task 8: FileStorageGateway SPI 改造（computeMd5 → computeDigest）

**Files:**

- Modify: `file-service/file-domain/src/main/java/com/example/file/domain/gateway/FileStorageGateway.java`
- Modify: `file-service/file-domain/src/main/java/com/example/file/domain/gateway/StoreResult.java`
- Modify:
  `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/FileStorageBackend.java`
- Modify:
  `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/FileStorageRouter.java`
- Modify: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/LocalFileStorage.java`
- Modify: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/NASFileStorage.java`
- Modify:
  `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/AliyunOSSFileStorage.java`
- Modify: `file-service/file-application/src/main/java/com/example/file/application/usecase/StoreFileUseCase.java`
- Modify: `file-service/file-application/src/main/java/com/example/file/application/usecase/CopyFileUseCase.java`
- Modify: `file-service/file-application/src/test/java/com/example/file/application/usecase/StoreFileUseCaseTest.java`

**Interfaces:**

- Consumes: 现有 `FileStorageGateway.computeMd5()`, `StoreResult.md5()`
- Produces: `FileStorageGateway.computeDigest()`, `StoreResult.digest()`

- [ ] **Step 1: 修改 StoreResult**

```java
package com.example.file.domain.gateway;

/**
 * 文件存储结果
 *
 * @param storageKey 实际存储 key
 * @param digest     内容摘要（SM3）
 */
public record StoreResult(String storageKey, String digest) {
}
```

- [ ] **Step 2: 修改 FileStorageGateway**

将 `computeMd5` 改为 `computeDigest`：

```java
package com.example.file.domain.gateway;

import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;

import java.io.InputStream;

public interface FileStorageGateway {

    StoreResult store(FileId fileId, InputStream content, long contentLength);

    InputStream open(FileId fileId);

    boolean exists(FileId fileId);

    CopyResult copy(FileId srcFileId, FileUsage targetUsage, BatchId businessBatchId);

    /**
     * 计算文件摘要（SM3）
     */
    String computeDigest(FileId fileId);
}
```

- [ ] **Step 3: 修改 FileStorageBackend SPI**

将 `computeMd5(StorageTarget, String)` 改为 `computeDigest(StorageTarget, String)`。

- [ ] **Step 4: 修改 FileStorageRouter**

将 `computeMd5` 方法重命名为 `computeDigest`，调用 backend 的 `computeDigest`。

- [ ] **Step 5: 修改三个 FileStorage 实现**

LocalFileStorage/NASFileStorage/AliyunOSSFileStorage 中将 `computeMd5` 方法重命名为 `computeDigest`，方法体保持原有 MD5
实现（Task 11 会改为 SM3）。

- [ ] **Step 6: 修改 StoreFileUseCase**

将 `String md5 = storageGateway.computeMd5(fileId)` 改为 `String digest = storageGateway.computeDigest(fileId)`，调用
`file.markUploaded(storageKey, digest)`（markUploaded 仍用 md5 参数名，传 digest 值）。

- [ ] **Step 7: 修改 CopyFileUseCase**

适配 `StoreResult.digest()` 替代 `md5()`。

- [ ] **Step 8: 修改 StoreFileUseCaseTest**

适配新方法名。

- [ ] **Step 9: 运行所有测试验证通过**

Run: `mvn -pl file-service/file-domain,file-service/file-application,file-service/file-infrastructure test`
Expected: PASS (所有原有测试)

- [ ] **Step 10: Commit**

```bash
git add file-service/file-domain/src/main/java/com/example/file/domain/gateway/FileStorageGateway.java \
        file-service/file-domain/src/main/java/com/example/file/domain/gateway/StoreResult.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/FileStorageBackend.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/FileStorageRouter.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/LocalFileStorage.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/NASFileStorage.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/AliyunOSSFileStorage.java \
        file-service/file-application/src/main/java/com/example/file/application/usecase/StoreFileUseCase.java \
        file-service/file-application/src/main/java/com/example/file/application/usecase/CopyFileUseCase.java \
        file-service/file-application/src/test/java/com/example/file/application/usecase/StoreFileUseCaseTest.java
git commit -m "refactor(file-service): computeMd5 重命名为 computeDigest

- FileStorageGateway.computeMd5 → computeDigest
- StoreResult.md5 → digest
- FileStorageBackend/Router/Local/NAS/OSS 同步重命名
- StoreFileUseCase/CopyFileUseCase 适配
- 摘要算法仍为 MD5，Task 11 改为 SM3"
```

---

### Task 9: file-infrastructure Kona 加密集成

**Files:**

- Modify: `file-service/file-infrastructure/pom.xml` (新增 kona-crypto 依赖)
- Create:
  `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/FileTokenProperties.java`
- Create:
  `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/KonaAutoConfiguration.java`
- Create:
  `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/KonaFileTokenGateway.java`
- Test:
  `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/KonaFileTokenGatewayTest.java`

**Interfaces:**

- Consumes: `FileTokenGateway`, `FileTokenPayload`, `FileErrorCodes`, `SystemException`
- Produces: `KonaFileTokenGateway` (SM4 加解密实现), `FileTokenProperties` (配置类), `KonaAutoConfiguration` (Provider
  注册)

- [ ] **Step 1: 添加 kona-crypto 依赖**

在 `file-service/file-infrastructure/pom.xml` 的 `<dependencies>` 中添加：

```xml
<!-- 腾讯 Kona 国密加密套件 -->
<dependency>
    <groupId>com.tencent.kona</groupId>
    <artifactId>kona-crypto</artifactId>
    <version>1.0.15</version>
</dependency>
```

- [ ] **Step 2: 实现 FileTokenProperties**

```java
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
```

- [ ] **Step 3: 实现 KonaAutoConfiguration**

```java
package com.example.file.infrastructure.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import java.security.Security;

@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "com.tencent.kona.crypto.provider.SM4")
public class KonaAutoConfiguration {

    @PostConstruct
    public void registerProvider() {
        try {
            // Kona 自动注册 Provider，这里仅打日志确认
            log.info("KonaCrypto Provider 已就绪: {}",
                Security.getProvider("KonaCrypto") != null ? "已注册" : "未注册（将使用 BouncyCastle 兜底）");
        } catch (Exception e) {
            log.warn("KonaCrypto Provider 检查失败: {}", e.getMessage());
        }
    }
}
```

- [ ] **Step 4: 写 KonaFileTokenGateway 失败测试**

```java
package com.example.file.infrastructure.storage;

import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KonaFileTokenGateway SM4 加解密")
class KonaFileTokenGatewayTest {

    private KonaFileTokenGateway gateway;

    @BeforeEach
    void setUp() {
        FileTokenProperties props = new FileTokenProperties();
        // 测试密钥：16 字节 "0123456789abcdef" 的 Base64
        props.setSecretKey("MDEyMzQ1Njc4OWFiY2RlZg==");
        // Kona 默认会自动注册 Provider，无需手动注册
        try {
            java.security.Security.addProvider(new com.tencent.kona.crypto.provider.KonaCryptoProvider());
        } catch (Exception ignored) {}
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.datatype.jsr310.JavaTimeModule javaTimeModule = new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule();
        objectMapper.registerModule(javaTimeModule);
        gateway = new KonaFileTokenGateway(objectMapper, props);
    }

    @Test
    @DisplayName("加解密 round-trip 成功")
    void should_encrypt_and_decrypt() {
        FileTokenPayload payload = new FileTokenPayload(
            "tok-001", new FileId("f001"), FileUsage.SOURCE, "biz",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(15)
        );

        String token = gateway.encrypt(payload);
        assertThat(token).isNotBlank();

        FileTokenPayload decrypted = gateway.decrypt(token);
        assertThat(decrypted.tokenId()).isEqualTo("tok-001");
        assertThat(decrypted.fileId()).isEqualTo(new FileId("f001"));
    }

    @Test
    @DisplayName("错误 token 解密抛异常")
    void should_throw_when_decrypt_invalid_token() {
        assertThatThrownBy(() -> gateway.decrypt("invalid-token-string"))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("每次加密生成不同密文（随机 IV）")
    void should_produce_different_ciphertext_each_time() {
        FileTokenPayload payload = new FileTokenPayload(
            "tok-001", new FileId("f001"), FileUsage.SOURCE, "biz",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(15)
        );

        String token1 = gateway.encrypt(payload);
        String token2 = gateway.encrypt(payload);
        assertThat(token1).isNotEqualTo(token2);  // 因 IV 随机
    }
}
```

- [ ] **Step 5: 实现 KonaFileTokenGateway**

```java
package com.example.file.infrastructure.storage;

import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.FileTokenGateway;
import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;
import com.example.shared.exception.SystemException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class KonaFileTokenGateway implements FileTokenGateway {

    private final ObjectMapper objectMapper;
    private final FileTokenProperties properties;

    @Override
    public String encrypt(FileTokenPayload payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            byte[] data = json.getBytes(StandardCharsets.UTF_8);

            byte[] key = Base64.getDecoder().decode(properties.getSecretKey());
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS5Padding", "KonaCrypto");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "SM4"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(data);

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

- [ ] **Step 6: 运行测试验证通过**

Run: `mvn -pl file-service/file-infrastructure test -Dtest=KonaFileTokenGatewayTest`
Expected: PASS (3 tests)

> **Note:** 若 Kona SDK 与 JDK 25 preview 不兼容，需先验证 Kona Provider 是否可用。若
> `Cipher.getInstance("SM4/CBC/PKCS5Padding", "KonaCrypto")` 抛 `NoSuchProviderException`，需在 `KonaAutoConfiguration`
> 中显式调用 `Security.addProvider(new KonaCryptoProvider())`。

- [ ] **Step 7: Commit**

```bash
git add file-service/file-infrastructure/pom.xml \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/FileTokenProperties.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/KonaAutoConfiguration.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/KonaFileTokenGateway.java \
        file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/KonaFileTokenGatewayTest.java
git commit -m "feat(file-infrastructure): 集成腾讯 Kona SM4 加密套件"
```

---

### Task 10: RedisFileTokenStore 实现

**Files:**

- Create:
  `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/RedisFileTokenStore.java`
- Test:
  `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/RedisFileTokenStoreTest.java`

**Interfaces:**

- Consumes: `FileTokenStore`, `FileTokenProperties`, `RedissonClient`
- Produces: `RedisFileTokenStore` (基于 Redis SETNX + TTL)

- [ ] **Step 1: 写 RedisFileTokenStore 失败测试**

```java
package com.example.file.infrastructure.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("RedisFileTokenStore 一次性 token 标记")
class RedisFileTokenStoreTest {

    private RedissonClient redissonClient;
    private FileTokenProperties properties;
    private RedisFileTokenStore store;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        properties = new FileTokenProperties();
        store = new RedisFileTokenStore(redissonClient, properties);
    }

    @Test
    @DisplayName("markUsed 首次调用返回 true")
    void should_return_true_when_first_mark() {
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.getBucket("file:token:used:tok-001")).thenReturn(bucket);
        when(bucket.setIfAbsent(eq("1"), any(Duration.class))).thenReturn(true);

        boolean result = store.markUsed("tok-001", Duration.ofMinutes(15));
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("markUsed 重复调用返回 false")
    void should_return_false_when_repeat_mark() {
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.getBucket("file:token:used:tok-001")).thenReturn(bucket);
        when(bucket.setIfAbsent(eq("1"), any(Duration.class))).thenReturn(false);

        boolean result = store.markUsed("tok-001", Duration.ofMinutes(15));
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isUsed 检查 key 是否存在")
    void should_check_is_used() {
        RBucket<String> bucket = mock(RBucket.class);
        when(redissonClient.getBucket("file:token:used:tok-001")).thenReturn(bucket);
        when(bucket.isExists()).thenReturn(true);

        assertThat(store.isUsed("tok-001")).isTrue();
        verify(bucket).isExists();
    }
}
```

- [ ] **Step 2: 实现 RedisFileTokenStore**

```java
package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.FileTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

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

- [ ] **Step 3: 运行测试验证通过**

Run: `mvn -pl file-service/file-infrastructure test -Dtest=RedisFileTokenStoreTest`
Expected: PASS (3 tests)

- [ ] **Step 4: Commit**

```bash
git add file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/RedisFileTokenStore.java \
        file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/RedisFileTokenStoreTest.java
git commit -m "feat(file-infrastructure): 新增 RedisFileTokenStore 一次性 token 标记"
```

---

### Task 11: SM3 摘要改造

**Files:**

- Modify: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/LocalFileStorage.java`
- Modify: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/NASFileStorage.java`
- Modify:
  `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/AliyunOSSFileStorage.java`
- Test:
  `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/LocalFileStorageDigestTest.java`

**Interfaces:**

- Consumes: `FileStorageBackend.computeDigest()`, Kona `MessageDigest.getInstance("SM3", "KonaCrypto")`
- Produces: SM3 摘要算法（替代 MD5）

- [ ] **Step 1: 写 LocalFileStorage SM3 摘要测试**

```java
package com.example.file.infrastructure.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalFileStorage SM3 摘要")
class LocalFileStorageDigestTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("计算文件 SM3 摘要")
    void should_compute_sm3_digest() throws Exception {
        // 准备：注册 Kona Provider
        try {
            java.security.Security.addProvider(new com.tencent.kona.crypto.provider.KonaCryptoProvider());
        } catch (Exception ignored) {}

        // 准备测试文件
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");

        StorageTargetProperties.StorageTargetConfig config = new StorageTargetProperties.StorageTargetConfig();
        config.setId("local-test");
        config.setType(com.example.file.domain.model.aggregate.valueobject.StorageType.LOCAL);
        config.setBasePath(tempDir.toString());
        StorageTarget target = StorageTarget.from(config);

        LocalFileStorage storage = new LocalFileStorage();
        String digest = storage.computeDigest(target, "test.txt");

        assertThat(digest).isNotNull().hasSize(64);  // SM3 输出 32 字节 = 64 hex 字符
    }
}
```

- [ ] **Step 2: 修改 LocalFileStorage.computeDigest 改用 SM3**

```java
@Override
public String computeDigest(StorageTarget target, String storageKey) {
    try {
        java.security.MessageDigest sm3 = java.security.MessageDigest.getInstance("SM3", "KonaCrypto");
        try (InputStream in = Files.newInputStream(Paths.get(target.basePath(), storageKey))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                sm3.update(buffer, 0, bytesRead);
            }
        }
        return Hex.encodeHexString(sm3.digest());
    } catch (Exception e) {
        throw new SystemException(FileErrorCodes.FILE_STORAGE_FAILED)
            .withLogDetail("SM3 摘要计算失败: " + e.getMessage());
    }
}
```

> **Note:** `Hex.encodeHexString` 来自 commons-codec，确认 file-infrastructure 已有此依赖（Task 4 时已添加）。

- [ ] **Step 3: 同步修改 NASFileStorage 和 AliyunOSSFileStorage**

将三个实现的 `computeDigest` 方法体从 MD5 改为 SM3（如上代码）。

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl file-service/file-infrastructure test -Dtest=LocalFileStorageDigestTest`
Expected: PASS (1 test)

- [ ] **Step 5: 运行所有 infrastructure 测试验证无回归**

Run: `mvn -pl file-service/file-infrastructure test`
Expected: PASS (所有测试)

- [ ] **Step 6: Commit**

```bash
git add file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/LocalFileStorage.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/NASFileStorage.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/storage/AliyunOSSFileStorage.java \
        file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/LocalFileStorageDigestTest.java
git commit -m "feat(file-infrastructure): 摘要算法从 MD5 改为 SM3 国密"
```

---

### Task 12: FileAccessLog 持久层

**Files:**

- Create: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/entity/FileAccessLogDO.java`
- Create:
  `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/mapper/FileAccessLogMapper.java`
- Create:
  `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/converter/FileAccessLogConverter.java`
- Create:
  `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/repository/FileAccessLogRepositoryImpl.java`
- Modify: `file-service/file-infrastructure/src/main/resources/schema-pg.sql` (新增 t_file_access_log 表)
- Test:
  `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/repository/FileAccessLogRepositoryImplTest.java`

**Interfaces:**

- Consumes: `FileAccessLog`, `FileAccessLogRepository`, `FileAccessAction`, `FileAccessResult`, `FileUsage`,
  MyBatis-Flex `@Table`
- Produces: `FileAccessLogDO`, `FileAccessLogMapper`, `FileAccessLogConverter`, `FileAccessLogRepositoryImpl`

- [ ] **Step 1: 在 schema-pg.sql 新增 t_file_access_log 表**

```sql
CREATE TABLE IF NOT EXISTS t_file_access_log (
    id              VARCHAR(64)   NOT NULL,
    file_id         VARCHAR(64)   NOT NULL,
    action          VARCHAR(20)   NOT NULL,
    usage           VARCHAR(20)   NOT NULL,
    customer_no     VARCHAR(64)   NOT NULL,
    product_no      VARCHAR(64)   NOT NULL,
    operator        VARCHAR(64)   NOT NULL,
    source_app      VARCHAR(64),
    source_ip       VARCHAR(64),
    token_hash      VARCHAR(128)  NOT NULL,
    result          VARCHAR(20)   NOT NULL,
    fail_reason     VARCHAR(512),
    occur_at        TIMESTAMP     NOT NULL,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_access_log_file_id ON t_file_access_log(file_id);
CREATE INDEX IF NOT EXISTS idx_access_log_token_hash ON t_file_access_log(token_hash);
CREATE INDEX IF NOT EXISTS idx_access_log_action_time ON t_file_access_log(action, occur_at);
CREATE INDEX IF NOT EXISTS idx_access_log_customer_product ON t_file_access_log(customer_no, product_no, occur_at);
```

- [ ] **Step 2: 实现 FileAccessLogDO**

```java
package com.example.file.infrastructure.entity;

import com.mybatisflex.annotation.Table;
import com.mybatisflex.annotation.Column;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("t_file_access_log")
public class FileAccessLogDO {
    @Column(pk = true)
    private String id;
    private String fileId;
    private String action;
    private String usage;
    private String customerNo;
    private String productNo;
    private String operator;
    private String sourceApp;
    private String sourceIp;
    private String tokenHash;
    private String result;
    private String failReason;
    private LocalDateTime occurAt;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: 实现 FileAccessLogMapper**

```java
package com.example.file.infrastructure.mapper;

import com.example.file.infrastructure.entity.FileAccessLogDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileAccessLogMapper extends BaseMapper<FileAccessLogDO> {
}
```

- [ ] **Step 4: 实现 FileAccessLogConverter (MapStruct)**

```java
package com.example.file.infrastructure.converter;

import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.valueobject.FileAccessAction;
import com.example.file.domain.model.aggregate.valueobject.FileAccessResult;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.infrastructure.entity.FileAccessLogDO;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface FileAccessLogConverter {

    @Mapping(target = "fileId", source = "fileId", qualifiedByName = "fileIdToString")
    @Mapping(target = "customerNo", source = "customerNo", qualifiedByName = "customerNoToString")
    @Mapping(target = "productNo", source = "productNo", qualifiedByName = "productNoToString")
    @Mapping(target = "operator", source = "operator", qualifiedByName = "userNoToString")
    @Mapping(target = "action", source = "action", qualifiedByName = "actionToString")
    @Mapping(target = "result", source = "result", qualifiedByName = "resultToString")
    @Mapping(target = "usage", source = "usage", qualifiedByName = "usageToString")
    FileAccessLogDO toDO(FileAccessLog log);

    @Named("fileIdToString")
    default String fileIdToString(FileId fileId) { return fileId != null ? fileId.value() : null; }
    @Named("customerNoToString")
    default String customerNoToString(CustomerNo no) { return no != null ? no.value() : null; }
    @Named("productNoToString")
    default String productNoToString(ProductNo no) { return no != null ? no.value() : null; }
    @Named("userNoToString")
    default String userNoToString(UserNo no) { return no != null ? no.value() : null; }
    @Named("actionToString")
    default String actionToString(FileAccessAction action) { return action != null ? action.name() : null; }
    @Named("resultToString")
    default String resultToString(FileAccessResult result) { return result != null ? result.name() : null; }
    @Named("usageToString")
    default String usageToString(FileUsage usage) { return usage != null ? usage.name() : null; }
}
```

- [ ] **Step 5: 实现 FileAccessLogRepositoryImpl**

```java
package com.example.file.infrastructure.repository;

import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.valueobject.FileAccessAction;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.infrastructure.converter.FileAccessLogConverter;
import com.example.file.infrastructure.entity.FileAccessLogDO;
import com.example.file.infrastructure.mapper.FileAccessLogMapper;
import com.example.shared.primitives.identity.FileId;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FileAccessLogRepositoryImpl implements FileAccessLogRepository {

    private final FileAccessLogMapper mapper;
    private final FileAccessLogConverter converter;

    @Override
    public FileAccessLog save(FileAccessLog log) {
        FileAccessLogDO DO = converter.toDO(log);
        mapper.insert(DO);
        return log;
    }

    @Override
    public Optional<FileAccessLog> findById(String id) {
        FileAccessLogDO DO = mapper.selectOneById(id);
        return Optional.ofNullable(DO).map(converter::toDomain);
    }

    @Override
    public List<FileAccessLog> findByFileId(FileId fileId) {
        QueryWrapper wrapper = QueryWrapper.create()
            .eq("file_id", fileId.value())
            .orderBy("occur_at", false);
        return mapper.selectListByQuery(wrapper).stream()
            .map(converter::toDomain).toList();
    }

    @Override
    public List<FileAccessLog> findByTokenHash(String tokenHash) {
        QueryWrapper wrapper = QueryWrapper.create()
            .eq("token_hash", tokenHash)
            .orderBy("occur_at", true);
        return mapper.selectListByQuery(wrapper).stream()
            .map(converter::toDomain).toList();
    }

    @Override
    public long countByActionAndTimeRange(FileAccessAction action, LocalDateTime from, LocalDateTime to) {
        QueryWrapper wrapper = QueryWrapper.create()
            .eq("action", action.name())
            .between("occur_at", from, to);
        return mapper.selectCountByQuery(wrapper);
    }
}
```

> **Note:** `FileAccessLogConverter` 需补 `toDomain(FileAccessLogDO)` 方法（反向映射，含 String → 强类型 ID 的转换）。参考现有
> `FileMetadataConverter` 模式。

- [ ] **Step 6: 在 FileAccessLogConverter 补 toDomain 方法**

```java
@Mapping(target = "fileId", source = "fileId", qualifiedByName = "toFileId")
@Mapping(target = "customerNo", source = "customerNo", qualifiedByName = "toCustomerNo")
@Mapping(target = "productNo", source = "productNo", qualifiedByName = "toProductNo")
@Mapping(target = "operator", source = "operator", qualifiedByName = "toUserNo")
@Mapping(target = "action", source = "action", qualifiedByName = "toAction")
@Mapping(target = "result", source = "result", qualifiedByName = "toResult")
@Mapping(target = "usage", source = "usage", qualifiedByName = "toUsage")
FileAccessLog toDomain(FileAccessLogDO DO);

@Named("toFileId")
default FileId toFileId(String s) { return s != null ? new FileId(s) : null; }
@Named("toCustomerNo")
default CustomerNo toCustomerNo(String s) { return s != null ? CustomerNo.of(s) : null; }
@Named("toProductNo")
default ProductNo toProductNo(String s) { return s != null ? ProductNo.of(s) : null; }
@Named("toUserNo")
default UserNo toUserNo(String s) { return s != null ? UserNo.of(s) : null; }
@Named("toAction")
default FileAccessAction toAction(String s) { return s != null ? FileAccessAction.valueOf(s) : null; }
@Named("toResult")
default FileAccessResult toResult(String s) { return s != null ? FileAccessResult.valueOf(s) : null; }
@Named("toUsage")
default FileUsage toUsage(String s) { return s != null ? FileUsage.valueOf(s) : null; }
```

- [ ] **Step 7: 写 Repository 集成测试**

```java
package com.example.file.infrastructure.repository;

import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.valueobject.FileAccessAction;
import com.example.file.domain.model.aggregate.valueobject.FileAccessResult;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("FileAccessLogRepositoryImpl 集成测试")
class FileAccessLogRepositoryImplTest {

    @Autowired
    private FileAccessLogRepository repository;

    @Test
    @DisplayName("save 和 findByFileId 成功")
    void should_save_and_find_by_file_id() {
        FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
        FileAccessLog log = FileAccessLog.apply(
            new FileId("f-test-001"), FileUsage.SOURCE, scope, UserNo.of("u1"),
            "test-app", "hash-test-001"
        );
        repository.save(log);

        List<FileAccessLog> found = repository.findByFileId(new FileId("f-test-001"));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).tokenHash()).isEqualTo("hash-test-001");
    }
}
```

- [ ] **Step 8: 运行测试验证通过**

Run: `mvn -pl file-service/file-infrastructure test -Dtest=FileAccessLogRepositoryImplTest`
Expected: PASS (1 test)

- [ ] **Step 9: Commit**

```bash
git add file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/entity/FileAccessLogDO.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/mapper/FileAccessLogMapper.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/converter/FileAccessLogConverter.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/repository/FileAccessLogRepositoryImpl.java \
        file-service/file-infrastructure/src/main/resources/schema-pg.sql \
        file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/repository/FileAccessLogRepositoryImplTest.java
git commit -m "feat(file-infrastructure): 新增 FileAccessLog 持久层（DO/Mapper/Converter/Repository）"
```

---

### Task 13: FileMetadata 持久层适配

**Files:**

- Modify: `file-service/file-infrastructure/src/main/resources/schema-pg.sql` (ALTER t_file_metadata)
- Modify: `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/entity/FileMetadataDO.java`
- Modify:
  `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/converter/FileMetadataConverter.java`
- Modify:
  `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/repository/FileMetadataRepositoryImpl.java`
- Test:
  `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/repository/FileMetadataTokenRepositoryTest.java`

**Interfaces:**

- Consumes: Task 5 改造后的 `FileMetadata`（含 accessScope/digest/digestAlgorithm）
- Produces: 适配后的 FileMetadataDO/Converter/RepositoryImpl（支持新字段读写）

- [ ] **Step 1: 在 schema-pg.sql 追加 ALTER 语句**

```sql
-- Token 访问机制扩展
ALTER TABLE t_file_metadata ADD COLUMN IF NOT EXISTS access_scope JSONB;
ALTER TABLE t_file_metadata ADD COLUMN IF NOT EXISTS digest VARCHAR(128);
ALTER TABLE t_file_metadata ADD COLUMN IF NOT EXISTS digest_algorithm VARCHAR(20) DEFAULT 'SM3';
ALTER TABLE t_file_metadata ALTER COLUMN original_name DROP NOT NULL;
ALTER TABLE t_file_metadata ALTER COLUMN size DROP NOT NULL;
ALTER TABLE t_file_metadata ALTER COLUMN storage_key DROP NOT NULL;

COMMENT ON COLUMN t_file_metadata.access_scope IS '访问范围 JSON: {"customerNo":"C001","productNo":"P001"}';
COMMENT ON COLUMN t_file_metadata.digest IS '内容摘要（SM3）';
COMMENT ON COLUMN t_file_metadata.digest_algorithm IS '摘要算法: SM3';
```

- [ ] **Step 2: 修改 FileMetadataDO 添加新字段**

```java
// 在 FileMetadataDO 中添加：
private String accessScope;       // JSON 字符串
private String digest;
private String digestAlgorithm;

// 同时将 size 字段类型从 long 改为 Long（允许 null）
// private Long size;  // 原 long 改为 Long
```

- [ ] **Step 3: 修改 FileMetadataConverter 支持 accessScope JSONB 转换**

```java
// 新增依赖：ObjectMapper
private final ObjectMapper objectMapper;

// toDO 方向
@Mapping(target = "accessScope", source = "accessScope", qualifiedByName = "scopeToJson")
@Mapping(target = "digest", source = "digest")
@Mapping(target = "digestAlgorithm", source = "digestAlgorithm")
FileMetadataDO toDO(FileMetadata file);

// toDomain 方向
@Mapping(target = "accessScope", source = "accessScope", qualifiedByName = "jsonToScope")
@Mapping(target = "digest", source = "digest")
@Mapping(target = "digestAlgorithm", source = "digestAlgorithm")
FileMetadata toDomain(FileMetadataDO DO);

@Named("scopeToJson")
default String scopeToJson(FileAccessScope scope) {
    if (scope == null) return null;
    try {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(scope);
    } catch (Exception e) {
        throw new SystemException(FileErrorCodes.FILE_STORAGE_CONFIG_INVALID)
            .withLogDetail("accessScope 序列化失败: " + e.getMessage());
    }
}

@Named("jsonToScope")
default FileAccessScope jsonToScope(String json) {
    if (json == null || json.isBlank()) return null;
    try {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, FileAccessScope.class);
    } catch (Exception e) {
        throw new SystemException(FileErrorCodes.FILE_STORAGE_CONFIG_INVALID)
            .withLogDetail("accessScope 反序列化失败: " + e.getMessage());
    }
}
```

> **Note:** `FileMetadataConverter` 需从 interface 改为 abstract class（或注入 ObjectMapper bean），以支持 JSON 转换。参考项目现有
> Converter 模式。

- [ ] **Step 4: 修改 FileMetadataRepositoryImpl 适配新字段**

确认 `load`/`save`/`loadOrThrow` 等方法能正确处理新字段（Converter 自动处理，Repository 实现层无需大改动）。

- [ ] **Step 5: 写 Repository 测试验证新字段读写**

```java
package com.example.file.infrastructure.repository;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.model.aggregate.valueobject.FileStatus;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("FileMetadataRepository Token 字段读写")
class FileMetadataTokenRepositoryTest {

    @Autowired
    private FileMetadataRepository repository;

    @Test
    @DisplayName("createForUpload 后保存并加载，accessScope 正确")
    void should_save_and_load_with_access_scope() {
        FileId fileId = new FileId("f-token-test-001");
        FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
        FileMetadata file = FileMetadata.createForUpload(
            fileId, FileUsage.SOURCE, "biz", "test-app",
            new BatchId("b001"), scope, "target-001", StorageType.LOCAL,
            UserNo.of("u1"), LocalDateTime.now().plusDays(7)
        );
        repository.save(file);

        Optional<FileMetadata> loaded = repository.findById(fileId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().accessScope()).isEqualTo(scope);
        assertThat(loaded.get().status()).isEqualTo(FileStatus.PENDING_UPLOAD);
        assertThat(loaded.get().originalName()).isNull();
    }

    @Test
    @DisplayName("completeUpload 后保存，digest 字段正确")
    void should_save_with_digest_after_complete_upload() {
        FileId fileId = new FileId("f-token-test-002");
        FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
        FileMetadata file = FileMetadata.createForUpload(
            fileId, FileUsage.SOURCE, "biz", "test-app",
            new BatchId("b001"), scope, "target-001", StorageType.LOCAL,
            UserNo.of("u1"), LocalDateTime.now().plusDays(7)
        );
        file.completeUpload("report.xlsx", 1024L, "application/xlsx",
            "storage-key-001", "sm3-digest-001");
        repository.save(file);

        Optional<FileMetadata> loaded = repository.findById(fileId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().digest()).isEqualTo("sm3-digest-001");
        assertThat(loaded.get().digestAlgorithm()).isEqualTo("SM3");
        assertThat(loaded.get().originalName()).isEqualTo("report.xlsx");
    }
}
```

- [ ] **Step 6: 运行测试验证通过**

Run: `mvn -pl file-service/file-infrastructure test -Dtest=FileMetadataTokenRepositoryTest`
Expected: PASS (2 tests)

- [ ] **Step 7: 运行全量测试验证无回归**

Run: `mvn -pl file-service/file-infrastructure test`
Expected: PASS (所有测试)

- [ ] **Step 8: Commit**

```bash
git add file-service/file-infrastructure/src/main/resources/schema-pg.sql \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/entity/FileMetadataDO.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/converter/FileMetadataConverter.java \
        file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/repository/FileMetadataRepositoryImpl.java \
        file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/repository/FileMetadataTokenRepositoryTest.java
git commit -m "feat(file-infrastructure): FileMetadata 持久层适配 Token 新字段

- t_file_metadata 新增 access_scope/digest/digest_algorithm 字段
- original_name/size/storage_key 允许 NULL
- FileMetadataDO/Converter 适配新字段
- accessScope 用 JSONB 存储"
```

---

### Task 14: file-api 层 API 接口

**Files:**

- Create: `file-service/file-api/src/main/java/com/example/file/api/FileAccessApi.java`
- Create: `file-service/file-api/src/main/java/com/example/file/api/request/ApplyUploadTokenRequest.java`
- Create: `file-service/file-api/src/main/java/com/example/file/api/request/ApplyDownloadTokenRequest.java`
- Create: `file-service/file-api/src/main/java/com/example/file/api/response/ApplyUploadTokenResponse.java`
- Create: `file-service/file-api/src/main/java/com/example/file/api/response/ApplyDownloadTokenResponse.java`
- Create: `file-service/file-api/src/main/java/com/example/file/api/response/UploadFileResponse.java`

**Interfaces:**

- Consumes: `@HttpExchange` (Spring Framework), `FileId`, `CustomerNo`, `ProductNo`, `UserNo`
- Produces: `FileAccessApi` 接口 + 6 个 DTO

- [ ] **Step 1: 实现 Request/Response DTO**

```java
// ApplyUploadTokenRequest.java
package com.example.file.api.request;

import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public record ApplyUploadTokenRequest(
    String bizType,
    String sourceApp,
    String businessBatchId,
    CustomerNo customerNo,
    ProductNo productNo,
    UserNo uploader,
    LocalDateTime expiresAt,
    List<String> allowedContentTypes,
    Long allowedMaxSize,
    Duration ttl
) {}

// ApplyDownloadTokenRequest.java
package com.example.file.api.request;

import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;

import java.time.Duration;

public record ApplyDownloadTokenRequest(
    FileId fileId,
    String sourceApp,
    CustomerNo customerNo,
    ProductNo productNo,
    UserNo downloader,
    Duration ttl
) {}

// ApplyUploadTokenResponse.java
package com.example.file.api.response;

import com.example.shared.primitives.identity.FileId;

public record ApplyUploadTokenResponse(String token, FileId fileId) {}

// ApplyDownloadTokenResponse.java
package com.example.file.api.response;

public record ApplyDownloadTokenResponse(String token) {}

// UploadFileResponse.java
package com.example.file.api.response;

import com.example.shared.primitives.identity.FileId;

public record UploadFileResponse(FileId fileId, String originalName, long size, String digest) {}
```

- [ ] **Step 2: 实现 FileAccessApi 接口**

```java
package com.example.file.api;

import com.example.file.api.request.ApplyDownloadTokenRequest;
import com.example.file.api.request.ApplyUploadTokenRequest;
import com.example.file.api.response.ApplyDownloadTokenResponse;
import com.example.file.api.response.ApplyUploadTokenResponse;
import com.example.file.api.response.UploadFileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@HttpExchange(url = "/api/file/access")
public interface FileAccessApi {

    @PostExchange(url = "/upload-tokens")
    ApplyUploadTokenResponse applyUploadToken(@RequestBody ApplyUploadTokenRequest request);

    @PostExchange(url = "/download-tokens")
    ApplyDownloadTokenResponse applyDownloadToken(@RequestBody ApplyDownloadTokenRequest request);

    @PostExchange(url = "/upload")
    UploadFileResponse upload(
        @RequestHeader("X-File-Token") String token,
        @RequestPart("file") MultipartFile file
    );

    @GetExchange(url = "/download")
    ResponseEntity<StreamingResponseBody> download(@RequestHeader("X-File-Token") String token);
}
```

- [ ] **Step 3: 运行编译验证**

Run: `mvn -pl file-service/file-api compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add file-service/file-api/src/main/java/com/example/file/api/
git commit -m "feat(file-api): 新增 FileAccessApi 接口和 6 个 DTO"
```

---

### Task 15: file-application 层 UseCase

**Files:**

- Create:
  `file-service/file-application/src/main/java/com/example/file/application/command/ApplyUploadTokenCommand.java`
- Create:
  `file-service/file-application/src/main/java/com/example/file/application/command/ApplyDownloadTokenCommand.java`
- Create:
  `file-service/file-application/src/main/java/com/example/file/application/usecase/ApplyUploadTokenUseCase.java`
- Create:
  `file-service/file-application/src/main/java/com/example/file/application/usecase/UploadFileWithTokenUseCase.java`
- Create:
  `file-service/file-application/src/main/java/com/example/file/application/usecase/ApplyDownloadTokenUseCase.java`
- Create:
  `file-service/file-application/src/main/java/com/example/file/application/usecase/DownloadFileWithTokenUseCase.java`
- Test:
  `file-service/file-application/src/test/java/com/example/file/application/usecase/ApplyUploadTokenUseCaseTest.java`
- Test:
  `file-service/file-application/src/test/java/com/example/file/application/usecase/UploadFileWithTokenUseCaseTest.java`
- Test:
  `file-service/file-application/src/test/java/com/example/file/application/usecase/ApplyDownloadTokenUseCaseTest.java`
- Test:
  `file-service/file-application/src/test/java/com/example/file/application/usecase/DownloadFileWithTokenUseCaseTest.java`

**Interfaces:**

- Consumes: `FileTokenService`, `FileMetadataRepository`, `FileAccessLogRepository`, `StorageTargetResolver`,
  `FileStorageGateway`, `FileTokenProperties`
- Produces: 4 个 UseCase + 2 个 Command

- [ ] **Step 1: 实现 Command 对象**

```java
// ApplyUploadTokenCommand.java
package com.example.file.application.command;

import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.UserNo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public record ApplyUploadTokenCommand(
    String bizType,
    String sourceApp,
    BatchId businessBatchId,
    FileAccessScope accessScope,
    UserNo uploader,
    LocalDateTime expiresAt,
    List<String> allowedContentTypes,
    Long allowedMaxSize,
    Duration ttl
) {}

// ApplyDownloadTokenCommand.java
package com.example.file.application.command;

import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;

import java.time.Duration;

public record ApplyDownloadTokenCommand(
    FileId fileId,
    String sourceApp,
    FileAccessScope accessScope,
    UserNo downloader,
    Duration ttl
) {}
```

- [ ] **Step 2: 实现 ApplyUploadTokenUseCase**

```java
package com.example.file.application.usecase;

import com.example.file.application.command.ApplyUploadTokenCommand;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.domain.service.FileTokenService;
import com.example.file.infrastructure.storage.FileTokenProperties;
import com.example.shared.id.algorithm.UlidAlgorithm;
import com.example.shared.primitives.identity.FileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyUploadTokenUseCase {

    private final FileMetadataRepository metadataRepository;
    private final StorageTargetResolver targetResolver;
    private final FileTokenService tokenService;
    private final FileAccessLogRepository logRepository;
    private final FileTokenProperties tokenProperties;

    @Transactional
    public ApplyUploadTokenResult apply(ApplyUploadTokenCommand cmd) {
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
        Duration ttl = cmd.ttl() != null ? cmd.ttl() : tokenProperties.getDefaultUploadTtl();
        String token = tokenService.generateUploadToken(
            file, cmd.allowedContentTypes(), cmd.allowedMaxSize(), ttl
        );

        // 3. 写 APPLY 流水
        FileAccessLog log = FileAccessLog.apply(
            fileId, FileUsage.SOURCE, cmd.accessScope(), cmd.uploader(),
            cmd.sourceApp(), sha256(token)
        );
        logRepository.save(log);

        return new ApplyUploadTokenResult(token, fileId);
    }

    public record ApplyUploadTokenResult(String token, FileId fileId) {}

    private String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "sha256-error";
        }
    }
}
```

- [ ] **Step 3: 实现 UploadFileWithTokenUseCase**

```java
package com.example.file.application.usecase;

import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StoreResult;
import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileAccessResult;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.SessionUser;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.domain.service.FileTokenService;
import com.example.shared.exception.SystemException;
import com.example.shared.primitives.identity.FileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadFileWithTokenUseCase {

  private final FileMetadataRepository metadataRepository;
  private final FileTokenService tokenService;
  private final FileStorageGateway storageGateway;
  private final FileAccessLogRepository logRepository;

  @Transactional
  public FileId upload(String token, SessionUser session, MultipartFile file, String clientIp) {
    // 1. 先解密 token 获取 fileId（用于失败时记录流水）
    FileMetadata meta;
    try {
      // 解密但不消费，用于加载 file
      var payload = tokenService.verifyAndConsumeUploadToken(token, session, /* file */ null);
      meta = metadataRepository.loadOrThrow(payload.fileId());
      // 注意：实际实现需先 load file 再传给 verifyAndConsumeUploadToken，参考 spec 5.1
    } catch (SystemException e) {
      writeAccessLogFailed(token, session, clientIp, e.getMessage());
      throw e;
    }

    try {
      StoreResult result = storageGateway.store(meta.id(), file.getInputStream(), file.getSize());
      meta.completeUpload(
          file.getOriginalFilename(), file.getSize(), file.getContentType(),
          result.storageKey(), result.digest()
      );
      metadataRepository.save(meta);
      writeAccessLogSuccess(meta, session, clientIp, token);
      return meta.id();
    } catch (Exception e) {
      writeAccessLogFailed(token, session, clientIp, e.getMessage());
      throw e;
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void writeAccessLogSuccess(FileMetadata meta, SessionUser session, String clientIp, String token) {
    FileAccessLog log = FileAccessLog.access(
        meta.id(), meta.usage(), meta.accessScope(), session.userNo(),
        meta.sourceApp(), clientIp, sha256(token),
        FileAccessResult.SUCCESS, null
    );
    logRepository.save(log);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void writeAccessLogFailed(String token, SessionUser session, String clientIp, String reason) {
    FileAccessLog log = FileAccessLog.access(
        null, FileUsage.SOURCE, new com.example.file.domain.model.aggregate.valueobject.FileAccessScope(
            session.customerNo(), session.productNo()
        ),
        session.userNo(), "unknown", clientIp, sha256(token),
        FileAccessResult.FAIL, reason
    );
    logRepository.save(log);
  }

  private String sha256(String input) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (Exception e) {
      return "sha256-error";
    }
  }
}
```

> **Note:** 实际实现时需要先 decrypt 获取 fileId → load FileMetadata → 调用 verifyAndConsumeUploadToken (file)。简化版可直接在
> tokenService 内部 decrypt 两次（一次取 fileId，一次正式校验），或调整 tokenService 接口。Implementer 可根据 spec 5.1 调整。

- [ ] **Step 4: 实现 ApplyDownloadTokenUseCase**

```java
package com.example.file.application.usecase;

import com.example.file.application.command.ApplyDownloadTokenCommand;
import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.domain.service.FileTokenService;
import com.example.file.infrastructure.storage.FileTokenProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ApplyDownloadTokenUseCase {

    private final FileMetadataRepository metadataRepository;
    private final FileTokenService tokenService;
    private final FileAccessLogRepository logRepository;
    private final FileTokenProperties tokenProperties;

    @Transactional
    public String apply(ApplyDownloadTokenCommand cmd) {
        FileMetadata file = metadataRepository.loadOrThrow(cmd.fileId());
        file.verifyDownloadable();

        Duration ttl = cmd.ttl() != null ? cmd.ttl() : tokenProperties.getDefaultDownloadTtl();
        String token = tokenService.generateDownloadToken(file, ttl);

        logRepository.save(FileAccessLog.apply(
            cmd.fileId(), FileUsage.SOURCE, file.accessScope(), cmd.downloader(),
            cmd.sourceApp(), sha256(token)
        ));

        return token;
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "sha256-error";
        }
    }
}
```

- [ ] **Step 5: 实现 DownloadFileWithTokenUseCase**

```java
package com.example.file.application.usecase;

import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileAccessResult;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.SessionUser;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.domain.service.FileTokenService;
import com.example.shared.primitives.identity.FileId;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadFileWithTokenUseCase {

  private final FileMetadataRepository metadataRepository;
  private final FileTokenService tokenService;
  private final FileStorageGateway storageGateway;
  private final FileAccessLogRepository logRepository;

  @Transactional
  public DownloadContext prepareDownload(String token, SessionUser session, String clientIp) {
    // 解密取 fileId（参考 UploadFileWithTokenUseCase 注释）
    FileMetadata file = metadataRepository.loadOrThrow(/* fileId from token */);
    tokenService.verifyAndConsumeDownloadToken(token, session, file);

    FileAccessLog log = FileAccessLog.access(
        file.id(), file.usage(), file.accessScope(), session.userNo(),
        file.sourceApp(), clientIp, sha256(token),
        FileAccessResult.SUCCESS, null
    );
    logRepository.save(log);

    return new DownloadContext(file.id(), file.originalName(), file.size(),
        file.contentType(), file.digest());
  }

  public InputStream openStream(FileId fileId) {
    return storageGateway.open(fileId);
  }

  public record DownloadContext(
      FileId fileId, String originalName, Long size,
      String contentType, String digest
  ) {
  }

  private String sha256(String input) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (Exception e) {
      return "sha256-error";
    }
  }
}
```

- [ ] **Step 6: 写 4 个 UseCase 测试**

参考 `FileTokenServiceTest` 模式，mock `FileMetadataRepository`/`FileTokenService`/`FileStorageGateway`/
`FileAccessLogRepository`。每个 UseCase 至少 1 个正常流程 + 1-2 个失败流程测试。

```java
// ApplyUploadTokenUseCaseTest.java 示例
package com.example.file.application.usecase;

import com.example.file.application.command.ApplyUploadTokenCommand;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.domain.service.FileTokenService;
import com.example.file.infrastructure.storage.FileTokenProperties;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("ApplyUploadTokenUseCase")
class ApplyUploadTokenUseCaseTest {

    private FileMetadataRepository metadataRepository;
    private StorageTargetResolver targetResolver;
    private FileTokenService tokenService;
    private FileAccessLogRepository logRepository;
    private FileTokenProperties tokenProperties;
    private ApplyUploadTokenUseCase useCase;

    @BeforeEach
    void setUp() {
        metadataRepository = mock(FileMetadataRepository.class);
        targetResolver = mock(StorageTargetResolver.class);
        tokenService = mock(FileTokenService.class);
        logRepository = mock(FileAccessLogRepository.class);
        tokenProperties = new FileTokenProperties();
        useCase = new ApplyUploadTokenUseCase(metadataRepository, targetResolver,
            tokenService, logRepository, tokenProperties);
    }

    @Test
    @DisplayName("apply 成功返回 token 和 fileId")
    void should_apply_upload_token() {
        when(targetResolver.resolveByUsage(any(), any())).thenReturn(
            new StorageTargetResolver.ResolvedTarget("target-001",
                com.example.file.domain.model.aggregate.valueobject.StorageType.LOCAL));
        when(tokenService.generateUploadToken(any(), any(), any(), any()))
            .thenReturn("encrypted-token");

        ApplyUploadTokenCommand cmd = new ApplyUploadTokenCommand(
            "biz", "approval-service", new BatchId("b001"),
            new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001")),
            UserNo.of("u1"), LocalDateTime.now().plusDays(7),
            java.util.List.of("application/xlsx"), 10L * 1024 * 1024,
            Duration.ofMinutes(15)
        );

        var result = useCase.apply(cmd);
        assertThat(result.token()).isEqualTo("encrypted-token");
        assertThat(result.fileId()).isNotNull();
        verify(metadataRepository).save(any());
        verify(logRepository).save(any());
    }
}
```

- [ ] **Step 7: 运行所有测试验证通过**

Run: `mvn -pl file-service/file-application test`
Expected: PASS (所有测试)

- [ ] **Step 8: Commit**

```bash
git add file-service/file-application/src/main/java/com/example/file/application/command/ \
        file-service/file-application/src/main/java/com/example/file/application/usecase/ApplyUploadTokenUseCase.java \
        file-service/file-application/src/main/java/com/example/file/application/usecase/UploadFileWithTokenUseCase.java \
        file-service/file-application/src/main/java/com/example/file/application/usecase/ApplyDownloadTokenUseCase.java \
        file-service/file-application/src/main/java/com/example/file/application/usecase/DownloadFileWithTokenUseCase.java \
        file-service/file-application/src/test/java/com/example/file/application/usecase/
git commit -m "feat(file-application): 新增 4 个 Token 访问 UseCase"
```

---

### Task 16: file-adapter 层 + 配置 + 集成测试

**Files:**

- Create: `file-service/file-adapter/src/main/java/com/example/file/adapter/access/FileAccessAdapter.java`
- Create: `file-service/file-adapter/src/main/java/com/example/file/adapter/access/converter/FileAccessConverter.java`
- Modify: `file-service/file-starter/src/main/resources/application.yml` (新增 file.token 配置)
- Modify: `file-service/file-starter/src/main/resources/application-local.yml` (本地覆盖)
- Create: `file-service/file-infrastructure/src/test/resources/application-test.yml` (测试配置)
- Test:
  `file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/FileAccessIntegrationTest.java`

**Interfaces:**

- Consumes: `FileAccessApi`, 4 个 UseCase, `SessionUser`, `StreamingResponseBody`
- Produces: `FileAccessAdapter` (实现 FileAccessApi，含流式下载)，配置文件

- [ ] **Step 1: 实现 FileAccessConverter (MapStruct)**

```java
package com.example.file.adapter.access.converter;

import com.example.file.api.request.ApplyDownloadTokenRequest;
import com.example.file.api.request.ApplyUploadTokenRequest;
import com.example.file.application.command.ApplyDownloadTokenCommand;
import com.example.file.application.command.ApplyUploadTokenCommand;
import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.shared.primitives.identity.BatchId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FileAccessConverter {

    @Mapping(target = "accessScope", expression = "java(new FileAccessScope(request.customerNo(), request.productNo()))")
    @Mapping(target = "businessBatchId", expression = "java(new BatchId(request.businessBatchId()))")
    ApplyUploadTokenCommand toCommand(ApplyUploadTokenRequest request);

    @Mapping(target = "accessScope", expression = "java(new FileAccessScope(request.customerNo(), request.productNo()))")
    ApplyDownloadTokenCommand toCommand(ApplyDownloadTokenRequest request);
}
```

- [ ] **Step 2: 实现 FileAccessAdapter**

```java
package com.example.file.adapter.access;

import com.example.file.adapter.access.converter.FileAccessConverter;
import com.example.file.api.FileAccessApi;
import com.example.file.api.request.ApplyDownloadTokenRequest;
import com.example.file.api.request.ApplyUploadTokenRequest;
import com.example.file.api.response.ApplyDownloadTokenResponse;
import com.example.file.api.response.ApplyUploadTokenResponse;
import com.example.file.api.response.UploadFileResponse;
import com.example.file.application.usecase.ApplyDownloadTokenUseCase;
import com.example.file.application.usecase.ApplyUploadTokenUseCase;
import com.example.file.application.usecase.DownloadFileWithTokenUseCase;
import com.example.file.application.usecase.UploadFileWithTokenUseCase;
import com.example.file.domain.model.aggregate.valueobject.SessionUser;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequiredArgsConstructor
public class FileAccessAdapter implements FileAccessApi {

    private final ApplyUploadTokenUseCase applyUploadTokenUseCase;
    private final UploadFileWithTokenUseCase uploadFileWithTokenUseCase;
    private final ApplyDownloadTokenUseCase applyDownloadTokenUseCase;
    private final DownloadFileWithTokenUseCase downloadFileWithTokenUseCase;
    private final FileAccessConverter converter;

    @Override
    @PostMapping("/api/file/access/upload-tokens")
    public ApplyUploadTokenResponse applyUploadToken(@RequestBody ApplyUploadTokenRequest request) {
        var cmd = converter.toCommand(request);
        var result = applyUploadTokenUseCase.apply(cmd);
        return new ApplyUploadTokenResponse(result.token(), result.fileId());
    }

    @Override
    @PostMapping("/api/file/access/download-tokens")
    public ApplyDownloadTokenResponse applyDownloadToken(@RequestBody ApplyDownloadTokenRequest request) {
        var cmd = converter.toCommand(request);
        String token = applyDownloadTokenUseCase.apply(cmd);
        return new ApplyDownloadTokenResponse(token);
    }

    @Override
    @PostMapping("/api/file/access/upload")
    public UploadFileResponse upload(
            @RequestHeader("X-File-Token") String token,
            @RequestHeader(value = "X-User-No", required = false) String userNo,
            @RequestHeader(value = "X-Customer-No", required = false) String customerNo,
            @RequestHeader(value = "X-Product-No", required = false) String productNo,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {

        SessionUser session = new SessionUser(
            UserNo.of(userNo), CustomerNo.of(customerNo), ProductNo.of(productNo)
        );
        String clientIp = extractClientIp(request);

        var fileId = uploadFileWithTokenUseCase.upload(token, session, file, clientIp);
        return new UploadFileResponse(fileId, file.getOriginalFilename(),
            file.getSize(), null);  // digest 可从 metadata 获取
    }

    @Override
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
        var ctx = downloadFileWithTokenUseCase.prepareDownload(token, session, clientIp);

        // 事务 2: 流式打开
        InputStream stream = downloadFileWithTokenUseCase.openStream(ctx.fileId());

        StreamingResponseBody body = outputStream -> {
            try (InputStream in = stream) {
                byte[] buffer = new byte[8192];
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
                "attachment; filename=\"" + URLEncoder.encode(
                    ctx.originalName() != null ? ctx.originalName() : "download",
                    StandardCharsets.UTF_8) + "\"")
            .contentLength(ctx.size() != null ? ctx.size() : -1)
            .body(body);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

- [ ] **Step 3: 修改 application.yml 新增 file.token 配置**

在 `file:` 块下追加：

```yaml
file:
  storage:
    # ... 原有配置保持
  token:
    secret-key: ${FILE_TOKEN_SECRET_KEY:}
    default-upload-ttl: 15m
    default-download-ttl: 15m
    redis:
      key-prefix: "file:token:used:"
      default-ttl: 15m
```

- [ ] **Step 4: 修改 application-local.yml**

```yaml
file:
  storage:
    # ... 原有配置保持
  token:
    secret-key: "MDEyMzQ1Njc4OWFiY2RlZg=="  # 本地开发用测试密钥
```

- [ ] **Step 5: 创建测试配置 application-test.yml**

```yaml
# file-service/file-infrastructure/src/test/resources/application-test.yml
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
    enabled: true
    targets:
      - id: local-default
        type: LOCAL
        base-path: ${java.io.tmpdir}/file-service-test
    routing:
      source: local-default
      export: local-default
      parsed: local-default
      archive: local-default
  token:
    secret-key: "MDEyMzQ1Njc4OWFiY2RlZg=="
    default-upload-ttl: 15m
    default-download-ttl: 15m
    redis:
      key-prefix: "test:file:token:used:"
```

- [ ] **Step 6: 写端到端集成测试**

```java
package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.FileTokenGateway;
import com.example.file.domain.gateway.FileTokenStore;
import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("文件访问 Token 端到端集成测试")
class FileAccessIntegrationTest {

    @Autowired
    private FileTokenGateway tokenGateway;

    @Autowired
    private FileTokenStore tokenStore;

    @Test
    @DisplayName("token 加密 → 解密 → 一次性使用 完整流程")
    void should_full_flow_token_lifecycle() {
        // 1. 加密
        FileTokenPayload payload = new FileTokenPayload(
            "tok-e2e-001", new FileId("f-e2e-001"), FileUsage.SOURCE, "biz",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            List.of("application/xlsx"), 10L * 1024 * 1024,
            LocalDateTime.now().plusMinutes(15)
        );
        String token = tokenGateway.encrypt(payload);
        assertThat(token).isNotBlank();

        // 2. 解密
        FileTokenPayload decrypted = tokenGateway.decrypt(token);
        assertThat(decrypted.tokenId()).isEqualTo("tok-e2e-001");

        // 3. 一次性使用 - 首次成功
        boolean firstUse = tokenStore.markUsed("tok-e2e-001", java.time.Duration.ofMinutes(15));
        assertThat(firstUse).isTrue();

        // 4. 重复使用 - 失败
        boolean secondUse = tokenStore.markUsed("tok-e2e-001", java.time.Duration.ofMinutes(15));
        assertThat(secondUse).isFalse();
    }
}
```

- [ ] **Step 7: 运行所有测试验证通过**

Run: `mvn -pl file-service/file-infrastructure,file-service/file-adapter test -Dtest=FileAccessIntegrationTest`
Expected: PASS (1 test)

- [ ] **Step 8: 运行全量测试验证无回归**

Run: `mvn -pl file-service test`
Expected: BUILD SUCCESS (所有测试通过)

- [ ] **Step 9: Commit**

```bash
git add file-service/file-adapter/src/main/java/com/example/file/adapter/access/ \
        file-service/file-starter/src/main/resources/application.yml \
        file-service/file-starter/src/main/resources/application-local.yml \
        file-service/file-infrastructure/src/test/resources/application-test.yml \
        file-service/file-infrastructure/src/test/java/com/example/file/infrastructure/storage/FileAccessIntegrationTest.java
git commit -m "feat(file-service): 新增 FileAccessAdapter + 配置 + 端到端集成测试

- FileAccessAdapter 实现 4 个 API 接口（含流式下载）
- FileAccessConverter MapStruct 转换
- application.yml/local.yml 新增 file.token 配置
- application-test.yml 测试配置
- FileAccessIntegrationTest 端到端测试"
```

---

## Self-Review

### 1. Spec 覆盖检查

- ✅ 领域模型: Task 1 (值对象) + Task 3 (FileAccessLog) + Task 5 (FileMetadata 改造)
- ✅ SPI 接口: Task 6 (FileTokenGateway/FileTokenStore) + Task 8 (FileStorageGateway 改造)
- ✅ 领域服务: Task 7 (FileTokenService)
- ✅ 错误码: Task 2
- ✅ 领域事件: Task 4
- ✅ 国密加密: Task 9 (Kona SM4) + Task 11 (SM3 摘要)
- ✅ Redis 一次性: Task 10
- ✅ 持久层: Task 12 (FileAccessLog) + Task 13 (FileMetadata 适配)
- ✅ API 层: Task 14 (FileAccessApi)
- ✅ 应用层: Task 15 (4 个 UseCase)
- ✅ Adapter 层: Task 16 (FileAccessAdapter)
- ✅ 配置: Task 16 (application.yml)
- ✅ 集成测试: Task 16 (FileAccessIntegrationTest)

### 2. Placeholder 检查

- ⚠️ Task 15 的 UploadFileWithTokenUseCase 和 DownloadFileWithTokenUseCase 中有 `/* file */ null` 和
  `/* fileId from token */` 占位符。这是因 `tokenService.verifyAndConsumeUploadToken` 需要 FileMetadata 参数但 UseCase
  内部需要先 decrypt 取 fileId 才能 load file。Implementer 需在实现时调整：先在 UseCase 内直接调用
  `tokenGateway.decrypt(token)` 取 fileId（或调整 TokenService 接口），然后 load file，再调用 verifyAndConsume。已在代码注释中说明。
- ✅ 其他无 TBD/TODO 占位符

### 3. 类型一致性检查

- ✅ `FileTokenPayload` 字段在 Task 1 定义后，Task 7/9/15 一致使用
- ✅ `FileMetadata.createForUpload` 签名在 Task 5 定义后，Task 7/15 一致使用
- ✅ `FileTokenService` 4 个方法在 Task 7 定义后，Task 15 一致使用
- ✅ `computeMd5 → computeDigest` 在 Task 8 改造后，Task 11 一致使用
- ✅ `StoreResult.md5 → digest` 在 Task 8 改造后，Task 15 一致使用

### 4. 已知设计妥协

- **`tokenService.verifyAndConsumeUploadToken(file)` 鸡生蛋问题**: UseCase 需先 decrypt 取 fileId 才能 load file，但
  verifyAndConsumeUploadToken 需要 file 参数。实现时可：
  - 方案 A: 在 UseCase 注入 `FileTokenGateway`，先 decrypt 取 fileId → load file → verifyAndConsume
  - 方案 B: 调整 `FileTokenService` 接口，提供 `decryptOnly(token)` 方法返回 payload，再 load file，再调用
    `consumeToken(payload, session, file)`
- Implementer 可选择任一方案，spec 不强制

---

## 执行选择

**计划已完成并保存到 `docs/superpowers/plans/2026-07-20-file-access-token.md`。两种执行选项：**

**1. Subagent 驱动（推荐）** - 每个 Task 派发独立 subagent，Task 之间进行审查，迭代快

**2. 内联执行** - 在当前会话中使用 `executing-plans` 批量执行，并设置检查点进行审查

**您选择哪种方式？**
