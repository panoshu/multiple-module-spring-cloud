# intranet-bff 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建内网/专线渠道 BFF 服务 `intranet-bff`，复用 `bff-shared` 公共组件，实现与 `internet-bff` 相同的 7 个业务接口（渠道 `INTRANET`），并额外提供 4 类管理接口：路由配置管理（6）、审批管理（14，透明转发 approval-service）、用户权限管理（11，透明转发 auth-service）、系统配置管理（2）。

**Architecture:** `intranet-bff` 与 `internet-bff` 共享 `bff-shared` 公共模块（`BusinessTypeRouter`、`KernelApiRegistry`、`BffAutoConfiguration`），采用相同的简化 DDD 分层（api / application / adapter / infrastructure / starter）。业务接口通过 DB 路由表 + kernel API 代理转发；管理接口分两类：路由配置管理由 BFF 自身 CRUD（扩展 `bff-shared` 的 `BffRouteConfigRepository`），审批/权限管理通过 `httpexchange-spring-boot-autoconfigure` 自动创建下游服务 API 代理实现透明转发（直接复用下游 `approval-api`/`auth-api` 的 Request/Response 类型，BFF 不定义额外 DTO）。

**Tech Stack:** Java 25 (preview), Spring Boot 3.5.14, Spring Cloud 2025.0.2, Spring Cloud Alibaba 2025.0.0.0 (Nacos), Spring Cloud LoadBalancer, MyBatis-Flex 1.11.5, Caffeine, httpexchange-spring-boot-autoconfigure 3.5.5, H2 (测试), Maven

## Global Constraints

- JDK 25，编译参数 `--enable-preview`（由根 pom pluginManagement 统一配置）
- 所有 BFF 请求体必须包含 `businessType` 字段用于路由（仅业务接口；管理接口不需要）
- kernel 和现有业务服务不需要修改
- `httpexchange-spring-boot-autoconfigure`（3.5.5）必须在 starter classpath 上，提供 `@HttpExchange` 服务端端点映射 + 客户端代理自动创建
- `spring-cloud-starter-loadbalancer` 提供 `@LoadBalanced RestClient.Builder` 自动注册（LoadBalancerRestClientAutoConfiguration）
- `BusinessException` 构造参数为 `ErrorDefinition`（枚举），禁止传 String
- `ApiResult` 是 record：`success(data)` / `failure(code, message)`，成功码 `"COMMON.0000"`
- 数据库表 `t_bff_route_config` 由 internet-bff 的 schema-pg.sql/schema-mysql.sql 创建，intranet-bff 共用同一张表，不再重复创建 DDL（但测试需要自己的 schema-h2.sql）
- DO 时间戳由应用层管理，禁止 `@Column(onInsertValue/onUpdateValue)`
- 提交信息遵循 Conventional Commits 规范
- 所有 API 使用 `@HttpExchange` + `@PostExchange`，返回 `ApiResult<T>`
- `RestClient.Builder` 必须调用 `clone()` 后再设置 `baseUrl`，避免共享 Builder 状态污染
- 测试中 `@MockBean` 已废弃，统一使用 `@MockitoBean`（import: `org.springframework.test.context.bean.override.mockito.MockitoBean`）
- 管理接口的透明转发直接复用下游 API 的 Request/Response 类型，BFF 不定义额外 DTO
- 错误码使用 `SERVICE.BFF.XXXX`（已在 `BffErrorCode` 中定义）
- intranet-bff 渠道范围：`INTRANET`；端口：18091；context-path：`/intranet-bff`；包名：`com.example.bff.intranet`
- 注：任务原文称"用户权限管理 12 个接口"为笔误，实际 auth-api 4 个接口类的方法总和为 3+2+2+4=11 个，本计划按实际 11 个编写

---

## 文件结构总览

### intranet-bff（顶层聚合模块）

| 文件 | 职责 |
|------|------|
| `intranet-bff/pom.xml` | 聚合 POM（5 个子模块） |
| `intranet-bff-api/.../api/BffBusinessApi.java` | BFF 业务 @HttpExchange 接口（7 端点） |
| `intranet-bff-api/.../api/dto/*.java` | 7 个请求 DTO + 1 个聚合响应 DTO |
| `intranet-bff-api/.../api/BffRouteManagementApi.java` | 路由配置管理 @HttpExchange 接口（6 端点） |
| `intranet-bff-api/.../api/BffApprovalApi.java` | 审批管理 @HttpExchange 接口（14 端点，透明转发） |
| `intranet-bff-api/.../api/BffPermissionApi.java` | 权限管理 @HttpExchange 接口（7 端点，透明转发） |
| `intranet-bff-api/.../api/BffChannelApi.java` | 渠道开通管理 @HttpExchange 接口（4 端点，透明转发） |
| `intranet-bff-api/.../api/BffSystemApi.java` | 系统配置 @HttpExchange 接口（2 端点） |
| `intranet-bff-application/.../service/BffAggregationService.java` | 数据聚合编排 |
| `intranet-bff-application/.../service/BffResponseAssembler.java` | 聚合响应组装 |
| `intranet-bff-application/.../service/RouteConfigManagementService.java` | 路由配置 CRUD 编排 |
| `intranet-bff-application/.../service/ApprovalManagementService.java` | 审批管理透明转发 |
| `intranet-bff-application/.../service/PermissionManagementService.java` | 权限管理透明转发 |
| `intranet-bff-application/.../service/ChannelManagementService.java` | 渠道开通透明转发 |
| `intranet-bff-application/.../service/SystemManagementService.java` | 系统配置查询 |
| `intranet-bff-adapter/.../controller/*.java` | 6 个 Controller |
| `intranet-bff-infrastructure/.../entity/BffRouteConfigDO.java` | 路由配置 DO（MyBatis-Flex） |
| `intranet-bff-infrastructure/.../mapper/BffRouteConfigMapper.java` | MyBatis-Flex Mapper |
| `intranet-bff-infrastructure/.../repository/BffRouteConfigRepositoryImpl.java` | Repository 实现（含 CRUD） |
| `intranet-bff-infrastructure/src/test/resources/schema-h2.sql` | 测试用 H2 DDL |
| `intranet-bff-starter/.../IntranetBffApplication.java` | 启动类 |
| `intranet-bff-starter/src/main/resources/application*.yml` | 配置文件 |

### bff-shared（Task 2 扩展）

| 文件 | 职责 |
|------|------|
| `bff-shared/.../route/BffRouteConfigRepository.java` | 扩展 CRUD 方法（save/update/delete/findById/findAll） |

### internet-bff（Task 2 同步更新以保持编译）

| 文件 | 职责 |
|------|------|
| `internet-bff/internet-bff-infrastructure/.../repository/BffRouteConfigRepositoryImpl.java` | 实现新增 CRUD 方法（保持接口实现完整） |

---

## Task 1: intranet-bff 脚手架 + 业务接口（7 个）

**Files:**
- Create: `intranet-bff/pom.xml`（聚合 POM）
- Create: `intranet-bff/intranet-bff-api/pom.xml`
- Create: `intranet-bff/intranet-bff-application/pom.xml`
- Create: `intranet-bff/intranet-bff-adapter/pom.xml`
- Create: `intranet-bff/intranet-bff-infrastructure/pom.xml`
- Create: `intranet-bff/intranet-bff-starter/pom.xml`
- Modify: `pom.xml`（根 pom — 添加 intranet-bff 模块 + intranet-bff-api 依赖管理）
- Create: `intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/BffBusinessApi.java`
- Create: `intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/*.java`（7 个请求 DTO + 1 个响应 DTO）
- Create: `intranet-bff/intranet-bff-application/src/main/java/com/example/bff/intranet/application/service/BffAggregationService.java`
- Create: `intranet-bff/intranet-bff-application/src/main/java/com/example/bff/intranet/application/service/BffResponseAssembler.java`
- Create: `intranet-bff/intranet-bff-adapter/src/main/java/com/example/bff/intranet/adapter/controller/BffBusinessController.java`
- Create: `intranet-bff/intranet-bff-infrastructure/src/main/java/com/example/bff/intranet/infrastructure/entity/BffRouteConfigDO.java`
- Create: `intranet-bff/intranet-bff-infrastructure/src/main/java/com/example/bff/intranet/infrastructure/mapper/BffRouteConfigMapper.java`
- Create: `intranet-bff/intranet-bff-infrastructure/src/main/java/com/example/bff/intranet/infrastructure/repository/BffRouteConfigRepositoryImpl.java`
- Create: `intranet-bff/intranet-bff-infrastructure/src/test/resources/schema-h2.sql`
- Create: `intranet-bff/intranet-bff-infrastructure/src/test/java/com/example/bff/intranet/infrastructure/TestApplication.java`
- Create: `intranet-bff/intranet-bff-infrastructure/src/test/resources/application.yml`
- Create: `intranet-bff/intranet-bff-infrastructure/src/test/java/com/example/bff/intranet/infrastructure/repository/BffRouteConfigRepositoryImplTest.java`
- Create: `intranet-bff/intranet-bff-application/src/test/java/com/example/bff/intranet/application/service/BffAggregationServiceTest.java`
- Create: `intranet-bff/intranet-bff-adapter/src/test/java/com/example/bff/intranet/adapter/TestApplication.java`
- Create: `intranet-bff/intranet-bff-adapter/src/test/java/com/example/bff/intranet/adapter/controller/BffBusinessControllerTest.java`

**Interfaces:**
- Consumes: `bff-shared`（`BusinessTypeRouter`、`KernelApiRegistry`、`BffAutoConfiguration`），`ApiResult`（shared-api），kernel Command/Query/Response DTO（business-core-api），MyBatis-Flex
- Produces: `BffBusinessApi` 接口（7 方法），`BffAggregationService`，`BffBusinessController`，`BffRouteConfigRepositoryImpl`（实现 `BffRouteConfigRepository`）

- [ ] **Step 1: 创建 intranet-bff 聚合 POM**

文件：`intranet-bff/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>multiple-module-spring-cloud</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>

  <artifactId>intranet-bff</artifactId>
  <packaging>pom</packaging>
  <description>内网/专线渠道 BFF - 请求路由、数据聚合、管理接口</description>

  <modules>
    <module>intranet-bff-api</module>
    <module>intranet-bff-application</module>
    <module>intranet-bff-adapter</module>
    <module>intranet-bff-infrastructure</module>
    <module>intranet-bff-starter</module>
  </modules>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>com.example</groupId>
        <artifactId>intranet-bff-api</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.example</groupId>
        <artifactId>intranet-bff-application</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.example</groupId>
        <artifactId>intranet-bff-adapter</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.example</groupId>
        <artifactId>intranet-bff-infrastructure</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.example</groupId>
        <artifactId>bff-shared</artifactId>
        <version>${project.version}</version>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>
```

- [ ] **Step 2: 创建 intranet-bff-api/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>intranet-bff</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>
  <artifactId>intranet-bff-api</artifactId>
  <description>内网 BFF - API 层（@HttpExchange 接口、DTO）</description>
  <dependencies>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>shared-api</artifactId>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>business-core-api</artifactId>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>approval-service-api</artifactId>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>auth-api</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-web</artifactId>
    </dependency>
    <dependency>
      <groupId>jakarta.validation</groupId>
      <artifactId>jakarta.validation-api</artifactId>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 3: 创建 intranet-bff-application/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>intranet-bff</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>
  <artifactId>intranet-bff-application</artifactId>
  <description>内网 BFF - 应用层（聚合编排、管理服务）</description>
  <dependencies>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>intranet-bff-api</artifactId>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>bff-shared</artifactId>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>approval-service-api</artifactId>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>auth-api</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 4: 创建 intranet-bff-adapter/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>intranet-bff</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>
  <artifactId>intranet-bff-adapter</artifactId>
  <description>内网 BFF - 适配器层（Controller）</description>
  <dependencies>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>intranet-bff-api</artifactId>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>intranet-bff-application</artifactId>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>shared-web-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 5: 创建 intranet-bff-infrastructure/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>intranet-bff</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>
  <artifactId>intranet-bff-infrastructure</artifactId>
  <description>内网 BFF - 基础设施层（DB 路由表访问）</description>
  <dependencies>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>bff-shared</artifactId>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>shared-exception</artifactId>
    </dependency>
    <dependency>
      <groupId>com.mybatis-flex</groupId>
      <artifactId>mybatis-flex-spring-boot3-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 6: 创建 intranet-bff-starter/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>intranet-bff</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>
  <artifactId>intranet-bff-starter</artifactId>
  <description>内网 BFF - 启动模块</description>
  <dependencies>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>intranet-bff-adapter</artifactId>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>intranet-bff-infrastructure</artifactId>
    </dependency>
    <dependency>
      <groupId>com.alibaba.cloud</groupId>
      <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-loadbalancer</artifactId>
    </dependency>
    <!-- httpexchange-spring-boot-autoconfigure: 提供 @HttpExchange 服务端端点映射 + 客户端代理自动创建 -->
    <dependency>
      <groupId>io.github.danielliu1123</groupId>
      <artifactId>httpexchange-spring-boot-autoconfigure</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <finalName>intranet-bff</finalName>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 7: 修改根 pom.xml — 添加 intranet-bff 模块**

在 `<modules>` 中 `internet-bff` 之后添加 `intranet-bff`：

```xml
    <module>internet-bff</module>
    <module>intranet-bff</module>
```

在 `<dependencyManagement>` 的 `<!-- 2nd Dependencies-->` 部分 `internet-bff-api` 之后添加 `intranet-bff-api`：

```xml
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>intranet-bff-api</artifactId>
      <version>${project.version}</version>
    </dependency>
```

- [ ] **Step 8: 创建 BffBusinessApi 接口**

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/BffBusinessApi.java`

```java
package com.example.bff.intranet.api;

import com.example.bff.intranet.api.dto.*;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.core.api.material.response.MaterialItemResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 内网 BFF 业务 API
 *
 * <p>对前端暴露收敛的接口，所有请求携带 {@code businessType} 用于路由。
 *
 * @author bff
 */
@HttpExchange("/bff")
public interface BffBusinessApi {

    @PostExchange("/batch/create")
    ApiResult<BatchCreatedResponse> createBatch(@Valid @RequestBody BffCreateBatchRequest request);

    @PostExchange("/batch/detail")
    ApiResult<BatchDetailResponse> batchDetail(@Valid @RequestBody BffBatchDetailRequest request);

    @PostExchange("/form/upload-token")
    ApiResult<UploadTokenResponse> applyUploadToken(@Valid @RequestBody BffFormTokenRequest request);

    @PostExchange("/application/submit")
    ApiResult<SubmitResponse> submitApplication(@Valid @RequestBody BffSubmitRequest request);

    @PostExchange("/application/detail")
    ApiResult<ApplicationDetailResponse> applicationDetail(@Valid @RequestBody BffApplicationDetailRequest request);

    @PostExchange("/material/list")
    ApiResult<List<MaterialItemResponse>> listMaterials(@Valid @RequestBody BffListMaterialsRequest request);

    @PostExchange("/dashboard/batch-overview")
    ApiResult<BatchOverviewResponse> batchOverview(@Valid @RequestBody BffBatchOverviewRequest request);
}
```

- [ ] **Step 9: 创建 7 个请求 DTO + 1 个响应 DTO**

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffCreateBatchRequest.java`

```java
package com.example.bff.intranet.api.dto;

import com.example.core.api.batch.command.CreateBatchCommand;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建批次请求
 *
 * @author bff
 */
public record BffCreateBatchRequest(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "计划编号不能为空") String planNo,
    String operatorRemark
) {
    public CreateBatchCommand toCommand() {
        return new CreateBatchCommand(businessType, planNo, operatorRemark);
    }
}
```

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffBatchDetailRequest.java`

```java
package com.example.bff.intranet.api.dto;

import com.example.core.api.batch.query.GetBatchDetailQuery;
import jakarta.validation.constraints.NotBlank;

/**
 * 批次详情请求
 *
 * @author bff
 */
public record BffBatchDetailRequest(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "批次ID不能为空") String batchId
) {
    public GetBatchDetailQuery toQuery() {
        return new GetBatchDetailQuery(batchId);
    }
}
```

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffFormTokenRequest.java`

```java
package com.example.bff.intranet.api.dto;

import com.example.core.api.form.command.ApplyUploadTokenCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 申请上传 token 请求
 *
 * @author bff
 */
public record BffFormTokenRequest(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "批次ID不能为空") String batchId,
    @NotBlank(message = "文件名不能为空") String fileName,
    @NotNull(message = "文件大小不能为空") @Positive(message = "文件大小必须为正数") Long fileSize,
    @NotBlank(message = "文件类型不能为空") String contentType
) {
    public ApplyUploadTokenCommand toCommand() {
        return new ApplyUploadTokenCommand(batchId, fileName, fileSize, contentType);
    }
}
```

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffSubmitRequest.java`

```java
package com.example.bff.intranet.api.dto;

import com.example.core.api.application.command.SubmitApplicationCommand;
import jakarta.validation.constraints.NotBlank;

/**
 * 提交申请单请求
 *
 * @author bff
 */
public record BffSubmitRequest(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "申请单ID不能为空") String applicationId
) {
    public SubmitApplicationCommand toCommand() {
        return new SubmitApplicationCommand(applicationId);
    }
}
```

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffApplicationDetailRequest.java`

```java
package com.example.bff.intranet.api.dto;

import com.example.core.api.application.query.GetApplicationDetailQuery;
import jakarta.validation.constraints.NotBlank;

/**
 * 申请单详情请求
 *
 * @author bff
 */
public record BffApplicationDetailRequest(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "申请单ID不能为空") String applicationId
) {
    public GetApplicationDetailQuery toQuery() {
        return new GetApplicationDetailQuery(applicationId);
    }
}
```

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffListMaterialsRequest.java`

```java
package com.example.bff.intranet.api.dto;

import com.example.core.api.material.query.ListMaterialsQuery;
import jakarta.validation.constraints.NotBlank;

/**
 * 材料列表请求
 *
 * @author bff
 */
public record BffListMaterialsRequest(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "申请单ID不能为空") String applicationId
) {
    public ListMaterialsQuery toQuery() {
        return new ListMaterialsQuery(applicationId);
    }
}
```

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffBatchOverviewRequest.java`

```java
package com.example.bff.intranet.api.dto;

import com.example.core.api.application.query.FindApplicationListQuery;
import com.example.core.api.batch.query.GetBatchDetailQuery;
import com.example.core.api.progress.query.GetBatchProgressQuery;
import jakarta.validation.constraints.NotBlank;

/**
 * 批次概览请求（聚合查询）
 *
 * @author bff
 */
public record BffBatchOverviewRequest(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "批次ID不能为空") String batchId
) {
    public GetBatchDetailQuery toBatchDetailQuery() {
        return new GetBatchDetailQuery(batchId);
    }

    public GetBatchProgressQuery toProgressQuery() {
        return new GetBatchProgressQuery(batchId);
    }

    public FindApplicationListQuery toApplicationListQuery() {
        return new FindApplicationListQuery(batchId, null);
    }
}
```

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BatchOverviewResponse.java`

```java
package com.example.bff.intranet.api.dto;

import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.progress.response.BatchProgressResponse;

import java.util.List;

/**
 * 批次概览聚合响应
 *
 * <p>聚合批次详情、进度、申请单列表三个维度的数据。
 *
 * @param batchDetail   批次详情
 * @param progress      批次进度
 * @param applications  申请单列表
 *
 * @author bff
 */
public record BatchOverviewResponse(
    BatchDetailResponse batchDetail,
    BatchProgressResponse progress,
    List<ApplicationSummaryResponse> applications
) {
}
```

- [ ] **Step 10: 创建 BffRouteConfigDO**

文件：`intranet-bff/intranet-bff-infrastructure/src/main/java/com/example/bff/intranet/infrastructure/entity/BffRouteConfigDO.java`

```java
package com.example.bff.intranet.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * BFF 路由配置 DO
 *
 * @author bff
 */
@Data
@Table("t_bff_route_config")
public class BffRouteConfigDO {

    @Id(keyType = KeyType.Generator, value = "snowFlakeId")
    private Long id;

    @Column("business_type")
    private String businessType;

    @Column("service_name")
    private String serviceName;

    @Column("channel_scope")
    private String channelScope;

    @Column("enabled")
    private Boolean enabled;

    @Column("description")
    private String description;

    @Column("created_by")
    private String createdBy;

    @Column("create_time")
    private LocalDateTime createTime;

    @Column("updated_by")
    private String updatedBy;

    @Column("update_time")
    private LocalDateTime updateTime;

    @Column("deleted")
    private Boolean deleted;

    @Column("version")
    private Integer version;
}
```

- [ ] **Step 11: 创建 BffRouteConfigMapper**

文件：`intranet-bff/intranet-bff-infrastructure/src/main/java/com/example/bff/intranet/infrastructure/mapper/BffRouteConfigMapper.java`

```java
package com.example.bff.intranet.infrastructure.mapper;

import com.example.bff.intranet.infrastructure.entity.BffRouteConfigDO;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * BFF 路由配置 Mapper
 *
 * @author bff
 */
@Mapper
public interface BffRouteConfigMapper extends BaseMapper<BffRouteConfigDO> {
}
```

- [ ] **Step 12: 创建 BffRouteConfigRepositoryImpl（查询部分，CRUD 在 Task 2 添加）**

文件：`intranet-bff/intranet-bff-infrastructure/src/main/java/com/example/bff/intranet/infrastructure/repository/BffRouteConfigRepositoryImpl.java`

```java
package com.example.bff.intranet.infrastructure.repository;

import com.example.bff.intranet.infrastructure.entity.BffRouteConfigDO;
import com.example.bff.intranet.infrastructure.mapper.BffRouteConfigMapper;
import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.BffRouteConfigRepository;
import com.example.bff.shared.route.ChannelScope;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static com.example.bff.intranet.infrastructure.entity.table.BffRouteConfigDOTableDef.BFF_ROUTE_CONFIG_DO;

/**
 * BFF 路由配置 Repository 实现
 *
 * <p>查询逻辑：优先匹配指定渠道，未找到则回退到 ALL。
 *
 * @author bff
 */
@Repository
public class BffRouteConfigRepositoryImpl implements BffRouteConfigRepository {

    private final BffRouteConfigMapper mapper;

    public BffRouteConfigRepositoryImpl(BffRouteConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<BffRouteConfig> findByBusinessType(String businessType, ChannelScope channelScope) {
        BffRouteConfigDO record = mapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(BFF_ROUTE_CONFIG_DO.BUSINESS_TYPE.eq(businessType))
                        .and(BFF_ROUTE_CONFIG_DO.CHANNEL_SCOPE.eq(channelScope.name()))
                        .and(BFF_ROUTE_CONFIG_DO.ENABLED.eq(true))
                        .and(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
        );
        if (record == null) {
            record = mapper.selectOneByQuery(
                    QueryWrapper.create()
                            .where(BFF_ROUTE_CONFIG_DO.BUSINESS_TYPE.eq(businessType))
                            .and(BFF_ROUTE_CONFIG_DO.CHANNEL_SCOPE.eq(ChannelScope.ALL.name()))
                            .and(BFF_ROUTE_CONFIG_DO.ENABLED.eq(true))
                            .and(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
            );
        }
        return Optional.ofNullable(record).map(this::toRouteConfig);
    }

    @Override
    public Set<String> findAllServiceNames() {
        java.util.List<BffRouteConfigDO> records = mapper.selectListByQuery(
                QueryWrapper.create()
                        .select(BFF_ROUTE_CONFIG_DO.SERVICE_NAME)
                        .where(BFF_ROUTE_CONFIG_DO.ENABLED.eq(true))
                        .and(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
        );
        Set<String> names = new LinkedHashSet<>();
        for (BffRouteConfigDO record : records) {
            names.add(record.getServiceName());
        }
        return names;
    }

    private BffRouteConfig toRouteConfig(BffRouteConfigDO record) {
        return new BffRouteConfig(
                record.getBusinessType(),
                record.getServiceName(),
                ChannelScope.valueOf(record.getChannelScope())
        );
    }
}
```

- [ ] **Step 13: 创建测试 schema-h2.sql**

文件：`intranet-bff/intranet-bff-infrastructure/src/test/resources/schema-h2.sql`

```sql
CREATE TABLE IF NOT EXISTS t_bff_route_config (
    id              BIGINT       PRIMARY KEY,
    business_type   VARCHAR(64)  NOT NULL,
    service_name    VARCHAR(128) NOT NULL,
    channel_scope   VARCHAR(32)  NOT NULL DEFAULT 'ALL',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    description     VARCHAR(256),
    created_by      VARCHAR(64),
    create_time     TIMESTAMP,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         INT          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bff_route_business_channel
    ON t_bff_route_config(business_type, channel_scope, deleted);
```

- [ ] **Step 14: 创建 infrastructure 测试启动类 TestApplication**

文件：`intranet-bff/intranet-bff-infrastructure/src/test/java/com/example/bff/intranet/infrastructure/TestApplication.java`

```java
package com.example.bff.intranet.infrastructure;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * infrastructure 测试专用启动类
 *
 * <p>需 {@code @MapperScan} 显式扫描 mapper 包，与项目其他服务保持一致
 * （MyBatis-Flex 自动配置不扫描 {@code @Mapper} 注解接口）。
 *
 * <p>手动声明 {@link DataSource}（{@link DriverManagerDataSource}，无连接池），
 * 因项目未引入 HikariCP，{@code DataSourceAutoConfiguration} 无法自动创建池化数据源。
 * 提供该 Bean 后 {@code MyBatisFlexAutoConfiguration} 自动创建 {@code SqlSessionFactory}，
 * {@code SqlInitializationAutoConfiguration} 自动执行 schema-h2.sql。
 *
 * @author bff
 */
@SpringBootApplication
@MapperScan("com.example.bff.intranet.infrastructure.mapper")
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSource dataSource() {
        return new DriverManagerDataSource();
    }
}
```

- [ ] **Step 15: 创建 infrastructure 测试 application.yml**

文件：`intranet-bff/intranet-bff-infrastructure/src/test/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:bff-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  sql:
    init:
      schema-locations: classpath:schema-h2.sql
      mode: always
  autoconfigure:
    exclude:
      # BffAutoConfiguration 创建 KernelApiRegistry 需要 @LoadBalanced RestClient.Builder，
      # infrastructure 集成测试仅需 Repository，无需 BFF 路由/代理注册
      - com.example.bff.shared.config.BffAutoConfiguration

mybatis-flex:
  mapper-locations: classpath*:mapper/*.xml

bff:
  channel-scope: INTRANET

logging:
  level:
    com.example.bff: DEBUG
```

- [ ] **Step 16: 编写 Repository 查询测试**

文件：`intranet-bff/intranet-bff-infrastructure/src/test/java/com/example/bff/intranet/infrastructure/repository/BffRouteConfigRepositoryImplTest.java`

```java
package com.example.bff.intranet.infrastructure.repository;

import com.example.bff.intranet.infrastructure.TestApplication;
import com.example.bff.intranet.infrastructure.entity.BffRouteConfigDO;
import com.example.bff.intranet.infrastructure.mapper.BffRouteConfigMapper;
import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.ChannelScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.Set;

import static com.example.bff.intranet.infrastructure.entity.table.BffRouteConfigDOTableDef.BFF_ROUTE_CONFIG_DO;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
class BffRouteConfigRepositoryImplTest {

    @Autowired
    private BffRouteConfigRepositoryImpl repository;

    @Autowired
    private BffRouteConfigMapper mapper;

    @Test
    @DisplayName("按业务类型和 ALL 渠道查找路由配置")
    void findByBusinessType_allScope() {
        insertRoute("ACC_PLAN_CREATE", "annuity-service", "ALL");

        Optional<BffRouteConfig> result = repository.findByBusinessType("ACC_PLAN_CREATE", ChannelScope.INTRANET);

        assertTrue(result.isPresent());
        assertEquals("annuity-service", result.get().serviceName());
        assertEquals(ChannelScope.ALL, result.get().channelScope());
    }

    @Test
    @DisplayName("按业务类型和指定渠道查找路由配置（优先于 ALL）")
    void findByBusinessType_specificScope() {
        insertRoute("LOAN_APPLY", "loan-service", "ALL");
        insertRoute("LOAN_APPLY", "loan-service-vip", "INTRANET");

        Optional<BffRouteConfig> result = repository.findByBusinessType("LOAN_APPLY", ChannelScope.INTRANET);

        assertTrue(result.isPresent());
        assertEquals("loan-service-vip", result.get().serviceName());
        assertEquals(ChannelScope.INTRANET, result.get().channelScope());
    }

    @Test
    @DisplayName("未知业务类型返回 empty")
    void findByBusinessType_unknownType() {
        Optional<BffRouteConfig> result = repository.findByBusinessType("UNKNOWN", ChannelScope.INTRANET);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("禁用的路由配置不返回")
    void findByBusinessType_disabled() {
        insertRoute("DISABLED_TYPE", "some-service", "ALL");
        BffRouteConfigDO record = mapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .where(BFF_ROUTE_CONFIG_DO.BUSINESS_TYPE.eq("DISABLED_TYPE")));
        record.setEnabled(false);
        mapper.update(record);

        Optional<BffRouteConfig> result = repository.findByBusinessType("DISABLED_TYPE", ChannelScope.INTRANET);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findAllServiceNames 返回去重的服务名集合")
    void findAllServiceNames() {
        insertRoute("TYPE_A", "annuity-service", "ALL");
        insertRoute("TYPE_B", "annuity-service", "ALL");
        insertRoute("TYPE_C", "loan-service", "ALL");

        Set<String> names = repository.findAllServiceNames();

        assertTrue(names.contains("annuity-service"));
        assertTrue(names.contains("loan-service"));
        assertEquals(2, names.size());
    }

    private void insertRoute(String businessType, String serviceName, String channelScope) {
        BffRouteConfigDO record = new BffRouteConfigDO();
        record.setBusinessType(businessType);
        record.setServiceName(serviceName);
        record.setChannelScope(channelScope);
        record.setEnabled(true);
        record.setDeleted(false);
        record.setVersion(0);
        mapper.insert(record);
    }
}
```

- [ ] **Step 17: 运行 infrastructure 测试验证通过**

Run: `mvn test -pl intranet-bff/intranet-bff-infrastructure`
Expected: 5 tests PASS

- [ ] **Step 18: 创建 BffResponseAssembler**

文件：`intranet-bff/intranet-bff-application/src/main/java/com/example/bff/intranet/application/service/BffResponseAssembler.java`

```java
package com.example.bff.intranet.application.service;

import com.example.bff.intranet.api.dto.BatchOverviewResponse;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.progress.response.BatchProgressResponse;

import java.util.List;

/**
 * BFF 聚合响应组装器
 *
 * @author bff
 */
final class BffResponseAssembler {

    private BffResponseAssembler() {
    }

    /**
     * 组装批次概览响应。
     *
     * @param batchDetail   批次详情（可能为 null）
     * @param progress      批次进度（可能为 null）
     * @param applications  申请单列表（可能为 null）
     */
    static BatchOverviewResponse assemble(
            BatchDetailResponse batchDetail,
            BatchProgressResponse progress,
            List<ApplicationSummaryResponse> applications) {
        return new BatchOverviewResponse(
                batchDetail,
                progress,
                applications != null ? applications : List.of()
        );
    }
}
```

- [ ] **Step 19: 编写聚合服务失败测试**

文件：`intranet-bff/intranet-bff-application/src/test/java/com/example/bff/intranet/application/service/BffAggregationServiceTest.java`

```java
package com.example.bff.intranet.application.service;

import com.example.bff.intranet.api.dto.BatchOverviewResponse;
import com.example.bff.intranet.api.dto.BffBatchOverviewRequest;
import com.example.bff.shared.registry.KernelApiRegistry;
import com.example.bff.shared.route.BusinessTypeRouter;
import com.example.core.api.application.BusinessApplicationApi;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.batch.BusinessBatchApi;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.progress.BusinessProgressApi;
import com.example.core.api.progress.response.BatchProgressResponse;
import com.example.shared.web.core.api.ApiResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySupplier;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BffAggregationServiceTest {

    @Mock
    private BusinessTypeRouter router;
    @Mock
    private KernelApiRegistry kernelApiRegistry;
    @Mock
    private AsyncTaskExecutor taskExecutor;
    @Mock
    private BusinessBatchApi batchApi;
    @Mock
    private BusinessProgressApi progressApi;
    @Mock
    private BusinessApplicationApi applicationApi;

    @InjectMocks
    private BffAggregationService aggregationService;

    @Test
    @DisplayName("getBatchOverview 聚合批次详情/进度/申请单列表")
    void getBatchOverview_aggregatesThreeApis() {
        BffBatchOverviewRequest request = new BffBatchOverviewRequest("ACC_PLAN_CREATE", "batch-123");

        when(router.resolveServiceName("ACC_PLAN_CREATE")).thenReturn("annuity-service");
        when(kernelApiRegistry.getBatchApi("annuity-service")).thenReturn(batchApi);
        when(kernelApiRegistry.getProgressApi("annuity-service")).thenReturn(progressApi);
        when(kernelApiRegistry.getApplicationApi("annuity-service")).thenReturn(applicationApi);
        // 让 supplyAsync 同步执行，便于测试
        when(taskExecutor.submit(anySupplier()))
                .thenAnswer(inv -> {
                    CompletableFuture<Object> future = new CompletableFuture<>();
                    future.complete(((java.util.function.Supplier<?>) inv.getArgument(0)).get());
                    return future;
                });

        BatchDetailResponse batchDetail = new BatchDetailResponse(
                "batch-123", "ACC_PLAN_CREATE", "PLAN001", "C001", "客户A",
                "PROCESSING", 10, 5, 3, 2,
                LocalDateTime.now(), LocalDateTime.now(), List.of()
        );
        BatchProgressResponse progress = new BatchProgressResponse(
                "batch-123", "PROCESSING", 5, 3, 2, 0
        );
        List<ApplicationSummaryResponse> applications = List.of(
                new ApplicationSummaryResponse("app-1", "batch-123", "SUBMITTED", "STEP1",
                        LocalDateTime.now(), LocalDateTime.now())
        );

        when(batchApi.detail(any())).thenReturn(ApiResult.success(batchDetail));
        when(progressApi.batchProgress(any())).thenReturn(ApiResult.success(progress));
        when(applicationApi.list(any())).thenReturn(ApiResult.success(applications));

        ApiResult<BatchOverviewResponse> result = aggregationService.getBatchOverview(request);

        assertTrue(result.isSuccess());
        assertEquals(batchDetail, result.data().batchDetail());
        assertEquals(progress, result.data().progress());
        assertEquals(applications, result.data().applications());
    }

    @Test
    @DisplayName("getBatchOverview 下游返回失败时聚合结果仍包含成功部分")
    void getBatchOverview_partialFailure() {
        BffBatchOverviewRequest request = new BffBatchOverviewRequest("ACC_PLAN_CREATE", "batch-456");

        when(router.resolveServiceName("ACC_PLAN_CREATE")).thenReturn("annuity-service");
        when(kernelApiRegistry.getBatchApi("annuity-service")).thenReturn(batchApi);
        when(kernelApiRegistry.getProgressApi("annuity-service")).thenReturn(progressApi);
        when(kernelApiRegistry.getApplicationApi("annuity-service")).thenReturn(applicationApi);
        when(taskExecutor.submit(anySupplier()))
                .thenAnswer(inv -> {
                    CompletableFuture<Object> future = new CompletableFuture<>();
                    future.complete(((java.util.function.Supplier<?>) inv.getArgument(0)).get());
                    return future;
                });

        BatchDetailResponse batchDetail = new BatchDetailResponse(
                "batch-456", "ACC_PLAN_CREATE", "PLAN001", "C001", "客户A",
                "PROCESSING", 10, 5, 3, 2,
                LocalDateTime.now(), LocalDateTime.now(), List.of()
        );

        when(batchApi.detail(any())).thenReturn(ApiResult.success(batchDetail));
        when(progressApi.batchProgress(any())).thenReturn(ApiResult.failure("SERVICE.BFF.0002", "下游服务调用失败"));
        when(applicationApi.list(any())).thenReturn(ApiResult.success(List.of()));

        ApiResult<BatchOverviewResponse> result = aggregationService.getBatchOverview(request);

        assertTrue(result.isSuccess());
        assertNotNull(result.data().batchDetail());
        assertNull(result.data().progress());
        assertTrue(result.data().applications().isEmpty());
    }
}
```

- [ ] **Step 20: 运行测试验证失败**

Run: `mvn test -pl intranet-bff/intranet-bff-application -Dtest=BffAggregationServiceTest`
Expected: FAIL — `BffAggregationService` 类不存在

- [ ] **Step 21: 创建 BffAggregationService**

文件：`intranet-bff/intranet-bff-application/src/main/java/com/example/bff/intranet/application/service/BffAggregationService.java`

```java
package com.example.bff.intranet.application.service;

import com.example.bff.intranet.api.dto.BatchOverviewResponse;
import com.example.bff.intranet.api.dto.BffBatchOverviewRequest;
import com.example.bff.shared.registry.KernelApiRegistry;
import com.example.bff.shared.route.BusinessTypeRouter;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.progress.response.BatchProgressResponse;
import com.example.shared.web.core.api.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * BFF 聚合编排服务
 *
 * <p>并发调用多个 kernel API，聚合为单个响应。
 *
 * @author bff
 */
@Slf4j
@Service
public class BffAggregationService {

    private final BusinessTypeRouter router;
    private final KernelApiRegistry kernelApiRegistry;
    private final AsyncTaskExecutor taskExecutor;

    public BffAggregationService(BusinessTypeRouter router,
                                 KernelApiRegistry kernelApiRegistry,
                                 AsyncTaskExecutor taskExecutor) {
        this.router = router;
        this.kernelApiRegistry = kernelApiRegistry;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 获取批次概览：聚合批次详情 + 进度 + 申请单列表。
     *
     * <p>三个调用并发执行，各自独立成功/失败，失败的部分设为 null。
     * 传输异常（下游宕机/超时）同样降级为 null，避免单个下游故障导致整个聚合请求 500。
     */
    public ApiResult<BatchOverviewResponse> getBatchOverview(BffBatchOverviewRequest request) {
        String serviceName = router.resolveServiceName(request.businessType());

        CompletableFuture<BatchDetailResponse> batchFuture = CompletableFuture.supplyAsync(() -> {
            try {
                ApiResult<BatchDetailResponse> result = kernelApiRegistry.getBatchApi(serviceName)
                        .detail(request.toBatchDetailQuery());
                return result.isSuccess() ? result.data() : null;
            } catch (Exception e) {
                log.warn("聚合调用批次详情降级: serviceName={}, batchId={}", serviceName, request.batchId(), e);
                return null;
            }
        }, taskExecutor);
        CompletableFuture<BatchProgressResponse> progressFuture = CompletableFuture.supplyAsync(() -> {
            try {
                ApiResult<BatchProgressResponse> result = kernelApiRegistry.getProgressApi(serviceName)
                        .batchProgress(request.toProgressQuery());
                return result.isSuccess() ? result.data() : null;
            } catch (Exception e) {
                log.warn("聚合调用批次进度降级: serviceName={}, batchId={}", serviceName, request.batchId(), e);
                return null;
            }
        }, taskExecutor);
        CompletableFuture<List<ApplicationSummaryResponse>> appsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                ApiResult<List<ApplicationSummaryResponse>> result = kernelApiRegistry.getApplicationApi(serviceName)
                        .list(request.toApplicationListQuery());
                return result.isSuccess() ? result.data() : null;
            } catch (Exception e) {
                log.warn("聚合调用申请单列表降级: serviceName={}, batchId={}", serviceName, request.batchId(), e);
                return null;
            }
        }, taskExecutor);

        CompletableFuture.allOf(batchFuture, progressFuture, appsFuture).join();

        BatchOverviewResponse response = BffResponseAssembler.assemble(
                batchFuture.join(),
                progressFuture.join(),
                appsFuture.join()
        );
        return ApiResult.success(response);
    }
}
```

- [ ] **Step 22: 运行聚合服务测试验证通过**

Run: `mvn test -pl intranet-bff/intranet-bff-application -Dtest=BffAggregationServiceTest`
Expected: 2 tests PASS

- [ ] **Step 23: 创建 adapter 测试启动类**

文件：`intranet-bff/intranet-bff-adapter/src/test/java/com/example/bff/intranet/adapter/TestApplication.java`

```java
package com.example.bff.intranet.adapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * adapter 测试专用启动类
 *
 * <p>提供 {@code @SpringBootConfiguration} 供 {@code @WebMvcTest} 引导 Spring 上下文。
 * adapter 模块本身是库模块，不含启动类，因此测试需要独立的配置入口。
 *
 * @author bff
 */
@SpringBootApplication
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
```

- [ ] **Step 24: 编写 Controller 失败测试**

文件：`intranet-bff/intranet-bff-adapter/src/test/java/com/example/bff/intranet/adapter/controller/BffBusinessControllerTest.java`

```java
package com.example.bff.intranet.adapter.controller;

import com.example.bff.intranet.api.dto.*;
import com.example.bff.intranet.application.service.BffAggregationService;
import com.example.bff.shared.registry.KernelApiRegistry;
import com.example.bff.shared.route.BusinessTypeRouter;
import com.example.core.api.application.BusinessApplicationApi;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.api.batch.BusinessBatchApi;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.form.BusinessFormApi;
import com.example.core.api.material.MaterialAppApi;
import com.example.shared.web.core.api.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BffBusinessController.class)
class BffBusinessControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BusinessTypeRouter router;
    @MockitoBean
    private KernelApiRegistry kernelApiRegistry;
    @MockitoBean
    private BffAggregationService aggregationService;
    @MockitoBean
    private BusinessBatchApi batchApi;
    @MockitoBean
    private BusinessFormApi formApi;
    @MockitoBean
    private BusinessApplicationApi applicationApi;
    @MockitoBean
    private MaterialAppApi materialApi;

    @Test
    @DisplayName("POST /bff/batch/create 路由到 kernel BusinessBatchApi.create")
    void createBatch_routesToBatchApi() throws Exception {
        when(router.resolveServiceName("ACC_PLAN_CREATE")).thenReturn("annuity-service");
        when(kernelApiRegistry.getBatchApi("annuity-service")).thenReturn(batchApi);
        when(batchApi.create(any())).thenReturn(ApiResult.success(
                new BatchCreatedResponse("batch-001", "CREATED", LocalDateTime.now())));

        BffCreateBatchRequest request = new BffCreateBatchRequest("ACC_PLAN_CREATE", "PLAN001", null);

        mockMvc.perform(post("/bff/batch/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON.0000"))
                .andExpect(jsonPath("$.data.batchId").value("batch-001"));
    }

    @Test
    @DisplayName("POST /bff/batch/detail 路由到 kernel BusinessBatchApi.detail")
    void batchDetail_routesToBatchApi() throws Exception {
        when(router.resolveServiceName("ACC_PLAN_CREATE")).thenReturn("annuity-service");
        when(kernelApiRegistry.getBatchApi("annuity-service")).thenReturn(batchApi);
        when(batchApi.detail(any())).thenReturn(ApiResult.success(
                new BatchDetailResponse("batch-001", "ACC_PLAN_CREATE", "PLAN001",
                        "C001", "客户A", "PROCESSING", 10, 5, 3, 2,
                        LocalDateTime.now(), LocalDateTime.now(), List.of())));

        BffBatchDetailRequest request = new BffBatchDetailRequest("ACC_PLAN_CREATE", "batch-001");

        mockMvc.perform(post("/bff/batch/detail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchId").value("batch-001"));
    }

    @Test
    @DisplayName("POST /bff/application/submit 路由到 kernel BusinessApplicationApi.submit")
    void submitApplication_routesToApplicationApi() throws Exception {
        when(router.resolveServiceName("ACC_PLAN_CREATE")).thenReturn("annuity-service");
        when(kernelApiRegistry.getApplicationApi("annuity-service")).thenReturn(applicationApi);
        when(applicationApi.submit(any())).thenReturn(ApiResult.success(
                new SubmitResponse("app-001", false, null)));

        BffSubmitRequest request = new BffSubmitRequest("ACC_PLAN_CREATE", "app-001");

        mockMvc.perform(post("/bff/application/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value("app-001"));
    }

    @Test
    @DisplayName("POST /bff/dashboard/batch-overview 路由到聚合服务")
    void batchOverview_routesToAggregationService() throws Exception {
        BatchOverviewResponse overview = new BatchOverviewResponse(
                new BatchDetailResponse("batch-001", "ACC_PLAN_CREATE", "PLAN001",
                        "C001", "客户A", "PROCESSING", 10, 5, 3, 2,
                        LocalDateTime.now(), LocalDateTime.now(), List.of()),
                null,
                List.of()
        );
        when(aggregationService.getBatchOverview(any())).thenReturn(ApiResult.success(overview));

        BffBatchOverviewRequest request = new BffBatchOverviewRequest("ACC_PLAN_CREATE", "batch-001");

        mockMvc.perform(post("/bff/dashboard/batch-overview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchDetail.batchId").value("batch-001"));
    }
}
```

- [ ] **Step 25: 运行 Controller 测试验证失败**

Run: `mvn test -pl intranet-bff/intranet-bff-adapter -Dtest=BffBusinessControllerTest`
Expected: FAIL — `BffBusinessController` 类不存在

- [ ] **Step 26: 创建 BffBusinessController**

文件：`intranet-bff/intranet-bff-adapter/src/main/java/com/example/bff/intranet/adapter/controller/BffBusinessController.java`

```java
package com.example.bff.intranet.adapter.controller;

import com.example.bff.intranet.api.BffBusinessApi;
import com.example.bff.intranet.api.dto.*;
import com.example.bff.intranet.application.service.BffAggregationService;
import com.example.bff.shared.registry.KernelApiRegistry;
import com.example.bff.shared.route.BusinessTypeRouter;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.core.api.material.response.MaterialItemResponse;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内网 BFF 业务 Controller
 *
 * <p>实现 {@link BffBusinessApi}，通过 {@link BusinessTypeRouter} 解析服务名，
 * 通过 {@link KernelApiRegistry} 获取 kernel API 代理，转发请求到对应业务服务。
 * 聚合场景委托给 {@link BffAggregationService}。
 *
 * @author bff
 */
@RestController
public class BffBusinessController implements BffBusinessApi {

    private final BusinessTypeRouter router;
    private final KernelApiRegistry kernelApiRegistry;
    private final BffAggregationService aggregationService;

    public BffBusinessController(
            BusinessTypeRouter router,
            KernelApiRegistry kernelApiRegistry,
            BffAggregationService aggregationService) {
        this.router = router;
        this.kernelApiRegistry = kernelApiRegistry;
        this.aggregationService = aggregationService;
    }

    @Override
    public ApiResult<BatchCreatedResponse> createBatch(BffCreateBatchRequest request) {
        String serviceName = router.resolveServiceName(request.businessType());
        return kernelApiRegistry.getBatchApi(serviceName).create(request.toCommand());
    }

    @Override
    public ApiResult<BatchDetailResponse> batchDetail(BffBatchDetailRequest request) {
        String serviceName = router.resolveServiceName(request.businessType());
        return kernelApiRegistry.getBatchApi(serviceName).detail(request.toQuery());
    }

    @Override
    public ApiResult<UploadTokenResponse> applyUploadToken(BffFormTokenRequest request) {
        String serviceName = router.resolveServiceName(request.businessType());
        return kernelApiRegistry.getFormApi(serviceName).applyUploadToken(request.toCommand());
    }

    @Override
    public ApiResult<SubmitResponse> submitApplication(BffSubmitRequest request) {
        String serviceName = router.resolveServiceName(request.businessType());
        return kernelApiRegistry.getApplicationApi(serviceName).submit(request.toCommand());
    }

    @Override
    public ApiResult<ApplicationDetailResponse> applicationDetail(BffApplicationDetailRequest request) {
        String serviceName = router.resolveServiceName(request.businessType());
        return kernelApiRegistry.getApplicationApi(serviceName).detail(request.toQuery());
    }

    @Override
    public ApiResult<List<MaterialItemResponse>> listMaterials(BffListMaterialsRequest request) {
        String serviceName = router.resolveServiceName(request.businessType());
        return kernelApiRegistry.getMaterialApi(serviceName).list(request.toQuery());
    }

    @Override
    public ApiResult<BatchOverviewResponse> batchOverview(BffBatchOverviewRequest request) {
        return aggregationService.getBatchOverview(request);
    }
}
```

- [ ] **Step 27: 运行 Controller 测试验证通过**

Run: `mvn test -pl intranet-bff/intranet-bff-adapter -Dtest=BffBusinessControllerTest`
Expected: 4 tests PASS

- [ ] **Step 28: 全量编译验证**

Run: `mvn compile -pl intranet-bff/intranet-bff-api,intranet-bff/intranet-bff-application,intranet-bff/intranet-bff-adapter,intranet-bff/intranet-bff-infrastructure -am`
Expected: BUILD SUCCESS

- [ ] **Step 29: Commit**

```bash
git add intranet-bff/ pom.xml
git commit -m "feat(intranet-bff): 新增内网 BFF 模块脚手架与 7 个业务接口

1. 创建 intranet-bff 聚合模块（api/application/adapter/infrastructure/starter 五层）
2. 定义 BffBusinessApi 接口，包含批次/表单/申请单/材料/概览 7 个 @PostExchange 端点
3. 实现 BffRouteConfigRepositoryImpl（查询部分），复用 internet-bff 的 t_bff_route_config 表
4. 实现 BffAggregationService 并发聚合批次详情/进度/申请单列表，失败降级
5. 实现 BffBusinessController 通过路由器+kernel API 代理转发请求
6. 新增 11 个单元/集成测试覆盖 DTO 转换、Repository 查询、聚合编排、Controller 路由"
```

---

## Task 2: 路由配置管理接口（6 个）

**Files:**
- Modify: `bff-shared/src/main/java/com/example/bff/shared/route/BffRouteConfigRepository.java`（扩展 CRUD 方法）
- Modify: `internet-bff/internet-bff-infrastructure/src/main/java/com/example/bff/internet/infrastructure/repository/BffRouteConfigRepositoryImpl.java`（同步实现新增方法以保持编译）
- Modify: `intranet-bff/intranet-bff-infrastructure/src/main/java/com/example/bff/intranet/infrastructure/repository/BffRouteConfigRepositoryImpl.java`（实现新增 CRUD 方法）
- Create: `intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffRouteConfigRequest.java`
- Create: `intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffRouteConfigResponse.java`
- Create: `intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/BffRouteManagementApi.java`
- Create: `intranet-bff/intranet-bff-application/src/main/java/com/example/bff/intranet/application/service/RouteConfigManagementService.java`
- Create: `intranet-bff/intranet-bff-adapter/src/main/java/com/example/bff/intranet/adapter/controller/BffRouteManagementController.java`
- Create: `intranet-bff/intranet-bff-infrastructure/src/test/java/com/example/bff/intranet/infrastructure/repository/BffRouteConfigCrudTest.java`
- Create: `intranet-bff/intranet-bff-adapter/src/test/java/com/example/bff/intranet/adapter/controller/BffRouteManagementControllerTest.java`

**Interfaces:**
- Consumes: `BffRouteConfigRepository`（扩展后）、`BffRouteConfig` record、`ChannelScope` 枚举、`BusinessTypeRouter`（刷新缓存）
- Produces: `BffRouteManagementApi` 接口（6 方法：create/update/delete/get/list/refreshCache），`RouteConfigManagementService`

- [ ] **Step 1: 扩展 BffRouteConfigRepository 接口**

修改 `bff-shared/src/main/java/com/example/bff/shared/route/BffRouteConfigRepository.java`，在现有方法之后添加 CRUD 方法：

```java
package com.example.bff.shared.route;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * BFF 路由配置 Repository 接口
 *
 * <p>由各 BFF 的 infrastructure 层实现（MyBatis-Flex）。
 *
 * @author bff
 */
public interface BffRouteConfigRepository {

    /**
     * 按业务类型查找路由配置。
     *
     * <p>优先匹配指定渠道，未找到则回退到 ALL。
     *
     * @param businessType 业务类型
     * @param channelScope 渠道范围
     * @return 路由配置
     */
    Optional<BffRouteConfig> findByBusinessType(String businessType, ChannelScope channelScope);

    /**
     * 获取所有已配置的服务名（去重）。
     *
     * @return 服务名集合
     */
    Set<String> findAllServiceNames();

    /**
     * 保存路由配置（新增）。
     *
     * @param config       路由配置
     * @param createdBy    创建人
     * @return 生成的 ID
     */
    Long save(BffRouteConfig config, String createdBy);

    /**
     * 更新路由配置。
     *
     * @param id           路由配置 ID
     * @param config       路由配置
     * @param updatedBy    更新人
     */
    void update(Long id, BffRouteConfig config, String updatedBy);

    /**
     * 删除路由配置（逻辑删除）。
     *
     * @param id        路由配置 ID
     * @param updatedBy 更新人
     */
    void delete(Long id, String updatedBy);

    /**
     * 按 ID 查询路由配置。
     *
     * @param id 路由配置 ID
     * @return 路由配置
     */
    Optional<BffRouteConfig> findById(Long id);

    /**
     * 查询全部路由配置（含禁用的，不含已删除的）。
     *
     * @return 路由配置列表
     */
    List<BffRouteConfig> findAll();
}
```

- [ ] **Step 2: 同步更新 internet-bff-infrastructure 的 RepositoryImpl（保持编译）**

修改 `internet-bff/internet-bff-infrastructure/src/main/java/com/example/bff/internet/infrastructure/repository/BffRouteConfigRepositoryImpl.java`，在类末尾（`toRouteConfig` 方法之前）添加 CRUD 方法实现：

```java
    @Override
    public Long save(BffRouteConfig config, String createdBy) {
        BffRouteConfigDO record = new BffRouteConfigDO();
        record.setBusinessType(config.businessType());
        record.setServiceName(config.serviceName());
        record.setChannelScope(config.channelScope().name());
        record.setEnabled(true);
        record.setCreatedBy(createdBy);
        record.setCreateTime(java.time.LocalDateTime.now());
        record.setDeleted(false);
        record.setVersion(0);
        mapper.insert(record);
        return record.getId();
    }

    @Override
    public void update(Long id, BffRouteConfig config, String updatedBy) {
        BffRouteConfigDO record = mapper.selectOneById(id);
        if (record == null) {
            return;
        }
        record.setBusinessType(config.businessType());
        record.setServiceName(config.serviceName());
        record.setChannelScope(config.channelScope().name());
        record.setUpdatedBy(updatedBy);
        record.setUpdateTime(java.time.LocalDateTime.now());
        mapper.update(record);
    }

    @Override
    public void delete(Long id, String updatedBy) {
        BffRouteConfigDO record = mapper.selectOneById(id);
        if (record == null) {
            return;
        }
        record.setDeleted(true);
        record.setUpdatedBy(updatedBy);
        record.setUpdateTime(java.time.LocalDateTime.now());
        mapper.update(record);
    }

    @Override
    public Optional<BffRouteConfig> findById(Long id) {
        BffRouteConfigDO record = mapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(BFF_ROUTE_CONFIG_DO.ID.eq(id))
                        .and(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
        );
        return Optional.ofNullable(record).map(this::toRouteConfig);
    }

    @Override
    public java.util.List<BffRouteConfig> findAll() {
        java.util.List<BffRouteConfigDO> records = mapper.selectListByQuery(
                QueryWrapper.create()
                        .where(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
                        .orderBy(BFF_ROUTE_CONFIG_DO.ID.asc())
        );
        return records.stream().map(this::toRouteConfig).toList();
    }
```

注：internet-bff 的 `BffRouteConfigRepositoryImpl` 需要在文件顶部补充 `import java.util.List;`（如果尚未导入）。

- [ ] **Step 3: 实现 intranet-bff-infrastructure 的 CRUD 方法**

修改 `intranet-bff/intranet-bff-infrastructure/src/main/java/com/example/bff/intranet/infrastructure/repository/BffRouteConfigRepositoryImpl.java`，添加与 Step 2 相同的 5 个 CRUD 方法实现（`save`/`update`/`delete`/`findById`/`findAll`），并补充 `import java.util.List;`。

完整文件替换为：

```java
package com.example.bff.intranet.infrastructure.repository;

import com.example.bff.intranet.infrastructure.entity.BffRouteConfigDO;
import com.example.bff.intranet.infrastructure.mapper.BffRouteConfigMapper;
import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.BffRouteConfigRepository;
import com.example.bff.shared.route.ChannelScope;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.example.bff.intranet.infrastructure.entity.table.BffRouteConfigDOTableDef.BFF_ROUTE_CONFIG_DO;

/**
 * BFF 路由配置 Repository 实现
 *
 * <p>查询逻辑：优先匹配指定渠道，未找到则回退到 ALL。
 *
 * @author bff
 */
@Repository
public class BffRouteConfigRepositoryImpl implements BffRouteConfigRepository {

    private final BffRouteConfigMapper mapper;

    public BffRouteConfigRepositoryImpl(BffRouteConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<BffRouteConfig> findByBusinessType(String businessType, ChannelScope channelScope) {
        BffRouteConfigDO record = mapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(BFF_ROUTE_CONFIG_DO.BUSINESS_TYPE.eq(businessType))
                        .and(BFF_ROUTE_CONFIG_DO.CHANNEL_SCOPE.eq(channelScope.name()))
                        .and(BFF_ROUTE_CONFIG_DO.ENABLED.eq(true))
                        .and(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
        );
        if (record == null) {
            record = mapper.selectOneByQuery(
                    QueryWrapper.create()
                            .where(BFF_ROUTE_CONFIG_DO.BUSINESS_TYPE.eq(businessType))
                            .and(BFF_ROUTE_CONFIG_DO.CHANNEL_SCOPE.eq(ChannelScope.ALL.name()))
                            .and(BFF_ROUTE_CONFIG_DO.ENABLED.eq(true))
                            .and(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
            );
        }
        return Optional.ofNullable(record).map(this::toRouteConfig);
    }

    @Override
    public Set<String> findAllServiceNames() {
        List<BffRouteConfigDO> records = mapper.selectListByQuery(
                QueryWrapper.create()
                        .select(BFF_ROUTE_CONFIG_DO.SERVICE_NAME)
                        .where(BFF_ROUTE_CONFIG_DO.ENABLED.eq(true))
                        .and(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
        );
        Set<String> names = new LinkedHashSet<>();
        for (BffRouteConfigDO record : records) {
            names.add(record.getServiceName());
        }
        return names;
    }

    @Override
    public Long save(BffRouteConfig config, String createdBy) {
        BffRouteConfigDO record = new BffRouteConfigDO();
        record.setBusinessType(config.businessType());
        record.setServiceName(config.serviceName());
        record.setChannelScope(config.channelScope().name());
        record.setEnabled(true);
        record.setCreatedBy(createdBy);
        record.setCreateTime(LocalDateTime.now());
        record.setDeleted(false);
        record.setVersion(0);
        mapper.insert(record);
        return record.getId();
    }

    @Override
    public void update(Long id, BffRouteConfig config, String updatedBy) {
        BffRouteConfigDO record = mapper.selectOneById(id);
        if (record == null) {
            return;
        }
        record.setBusinessType(config.businessType());
        record.setServiceName(config.serviceName());
        record.setChannelScope(config.channelScope().name());
        record.setUpdatedBy(updatedBy);
        record.setUpdateTime(LocalDateTime.now());
        mapper.update(record);
    }

    @Override
    public void delete(Long id, String updatedBy) {
        BffRouteConfigDO record = mapper.selectOneById(id);
        if (record == null) {
            return;
        }
        record.setDeleted(true);
        record.setUpdatedBy(updatedBy);
        record.setUpdateTime(LocalDateTime.now());
        mapper.update(record);
    }

    @Override
    public Optional<BffRouteConfig> findById(Long id) {
        BffRouteConfigDO record = mapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(BFF_ROUTE_CONFIG_DO.ID.eq(id))
                        .and(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
        );
        return Optional.ofNullable(record).map(this::toRouteConfig);
    }

    @Override
    public List<BffRouteConfig> findAll() {
        List<BffRouteConfigDO> records = mapper.selectListByQuery(
                QueryWrapper.create()
                        .where(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
                        .orderBy(BFF_ROUTE_CONFIG_DO.ID.asc())
        );
        return records.stream().map(this::toRouteConfig).toList();
    }

    private BffRouteConfig toRouteConfig(BffRouteConfigDO record) {
        return new BffRouteConfig(
                record.getBusinessType(),
                record.getServiceName(),
                ChannelScope.valueOf(record.getChannelScope())
        );
    }
}
```

- [ ] **Step 4: 编写 CRUD 集成测试**

文件：`intranet-bff/intranet-bff-infrastructure/src/test/java/com/example/bff/intranet/infrastructure/repository/BffRouteConfigCrudTest.java`

```java
package com.example.bff.intranet.infrastructure.repository;

import com.example.bff.intranet.infrastructure.TestApplication;
import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.ChannelScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
class BffRouteConfigCrudTest {

    @Autowired
    private BffRouteConfigRepositoryImpl repository;

    @Test
    @DisplayName("save 新增路由配置并返回 ID")
    void save_returnsId() {
        BffRouteConfig config = new BffRouteConfig("CRUD_TYPE_A", "annuity-service", ChannelScope.ALL);

        Long id = repository.save(config, "admin");

        assertNotNull(id);
    }

    @Test
    @DisplayName("findById 查询已保存的路由配置")
    void findById_returnsConfig() {
        BffRouteConfig config = new BffRouteConfig("CRUD_TYPE_B", "loan-service", ChannelScope.INTRANET);
        Long id = repository.save(config, "admin");

        Optional<BffRouteConfig> result = repository.findById(id);

        assertTrue(result.isPresent());
        assertEquals("CRUD_TYPE_B", result.get().businessType());
        assertEquals("loan-service", result.get().serviceName());
        assertEquals(ChannelScope.INTRANET, result.get().channelScope());
    }

    @Test
    @DisplayName("update 更新路由配置")
    void update_modifiesConfig() {
        BffRouteConfig config = new BffRouteConfig("CRUD_TYPE_C", "old-service", ChannelScope.ALL);
        Long id = repository.save(config, "admin");
        BffRouteConfig updated = new BffRouteConfig("CRUD_TYPE_C", "new-service", ChannelScope.INTRANET);

        repository.update(id, updated, "admin");
        Optional<BffRouteConfig> result = repository.findById(id);

        assertTrue(result.isPresent());
        assertEquals("new-service", result.get().serviceName());
        assertEquals(ChannelScope.INTRANET, result.get().channelScope());
    }

    @Test
    @DisplayName("delete 逻辑删除后 findById 返回 empty")
    void delete_softDeletes() {
        BffRouteConfig config = new BffRouteConfig("CRUD_TYPE_D", "some-service", ChannelScope.ALL);
        Long id = repository.save(config, "admin");

        repository.delete(id, "admin");
        Optional<BffRouteConfig> result = repository.findById(id);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findAll 返回未删除的全部路由配置")
    void findAll_returnsAll() {
        repository.save(new BffRouteConfig("CRUD_TYPE_E", "annuity-service", ChannelScope.ALL), "admin");
        repository.save(new BffRouteConfig("CRUD_TYPE_F", "loan-service", ChannelScope.ALL), "admin");

        List<BffRouteConfig> all = repository.findAll();

        assertTrue(all.size() >= 2);
    }
}
```

- [ ] **Step 5: 运行 CRUD 测试验证通过**

Run: `mvn test -pl intranet-bff/intranet-bff-infrastructure -Dtest=BffRouteConfigCrudTest`
Expected: 5 tests PASS

- [ ] **Step 6: 创建 BffRouteConfigRequest DTO**

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffRouteConfigRequest.java`

```java
package com.example.bff.intranet.api.dto;

import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.ChannelScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 路由配置请求
 *
 * @author bff
 */
public record BffRouteConfigRequest(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "服务名不能为空") String serviceName,
    @NotNull(message = "渠道范围不能为空") ChannelScope channelScope
) {
    public BffRouteConfig toRouteConfig() {
        return new BffRouteConfig(businessType, serviceName, channelScope);
    }
}
```

- [ ] **Step 7: 创建 BffRouteConfigResponse DTO**

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffRouteConfigResponse.java`

```java
package com.example.bff.intranet.api.dto;

import com.example.bff.shared.route.ChannelScope;

/**
 * 路由配置响应
 *
 * @param id           路由配置 ID
 * @param businessType 业务类型
 * @param serviceName  目标服务名
 * @param channelScope 渠道范围
 *
 * @author bff
 */
public record BffRouteConfigResponse(
    Long id,
    String businessType,
    String serviceName,
    ChannelScope channelScope
) {
}
```

- [ ] **Step 8: 创建 BffRouteManagementApi 接口**

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/BffRouteManagementApi.java`

```java
package com.example.bff.intranet.api;

import com.example.bff.intranet.api.dto.BffRouteConfigRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * BFF 路由配置管理 API
 *
 * @author bff
 */
@HttpExchange("/management/routes")
public interface BffRouteManagementApi {

    @PostExchange("/create")
    ApiResult<Long> create(@Valid @RequestBody BffRouteConfigRequest request);

    @PostExchange("/update")
    ApiResult<Void> update(@Valid @RequestBody BffRouteConfigUpdateRequest request);

    @PostExchange("/delete")
    ApiResult<Void> delete(@Valid @RequestBody BffRouteConfigDeleteRequest request);

    @PostExchange("/get")
    ApiResult<BffRouteConfigResponse> get(@Valid @RequestBody BffRouteConfigGetRequest request);

    @PostExchange("/list")
    ApiResult<List<BffRouteConfigResponse>> list();

    @PostExchange("/refresh-cache")
    ApiResult<Void> refreshCache();
}
```

注：`BffRouteConfigUpdateRequest`/`BffRouteConfigDeleteRequest`/`BffRouteConfigGetRequest` 是带 `id` 字段的简单请求 DTO，与 `BffRouteConfigRequest` 同包。在 Step 9 创建。

- [ ] **Step 9: 创建路由管理辅助请求 DTO**

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffRouteConfigUpdateRequest.java`

```java
package com.example.bff.intranet.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 路由配置更新请求
 *
 * @author bff
 */
public record BffRouteConfigUpdateRequest(
    @NotNull(message = "ID不能为空") @Positive(message = "ID必须为正数") Long id,
    @NotNull(message = "路由配置不能为空") BffRouteConfigRequest config
) {
}
```

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffRouteConfigDeleteRequest.java`

```java
package com.example.bff.intranet.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 路由配置删除请求
 *
 * @author bff
 */
public record BffRouteConfigDeleteRequest(
    @NotNull(message = "ID不能为空") @Positive(message = "ID必须为正数") Long id
) {
}
```

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffRouteConfigGetRequest.java`

```java
package com.example.bff.intranet.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 路由配置查询请求
 *
 * @author bff
 */
public record BffRouteConfigGetRequest(
    @NotNull(message = "ID不能为空") @Positive(message = "ID必须为正数") Long id
) {
}
```

- [ ] **Step 10: 创建 RouteConfigManagementService**

文件：`intranet-bff/intranet-bff-application/src/main/java/com/example/bff/intranet/application/service/RouteConfigManagementService.java`

```java
package com.example.bff.intranet.application.service;

import com.example.bff.intranet.api.dto.BffRouteConfigRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigResponse;
import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.BffRouteConfigRepository;
import com.example.bff.shared.route.BusinessTypeRouter;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 路由配置管理服务
 *
 * <p>编排路由配置的 CRUD 操作，刷新缓存委托给 {@link BusinessTypeRouter}。
 *
 * @author bff
 */
@Service
public class RouteConfigManagementService {

    private final BffRouteConfigRepository routeConfigRepository;
    private final BusinessTypeRouter businessTypeRouter;

    public RouteConfigManagementService(BffRouteConfigRepository routeConfigRepository,
                                       BusinessTypeRouter businessTypeRouter) {
        this.routeConfigRepository = routeConfigRepository;
        this.businessTypeRouter = businessTypeRouter;
    }

    public ApiResult<Long> create(BffRouteConfigRequest request) {
        Long id = routeConfigRepository.save(request.toRouteConfig(), "system");
        businessTypeRouter.refresh();
        return ApiResult.success(id);
    }

    public ApiResult<Void> update(Long id, BffRouteConfigRequest request) {
        routeConfigRepository.update(id, request.toRouteConfig(), "system");
        businessTypeRouter.refresh();
        return ApiResult.success(null);
    }

    public ApiResult<Void> delete(Long id) {
        routeConfigRepository.delete(id, "system");
        businessTypeRouter.refresh();
        return ApiResult.success(null);
    }

    public ApiResult<BffRouteConfigResponse> get(Long id) {
        return routeConfigRepository.findById(id)
                .map(config -> ApiResult.success(toResponse(id, config)))
                .orElseGet(() -> ApiResult.success(null));
    }

    public ApiResult<List<BffRouteConfigResponse>> list() {
        List<BffRouteConfigResponse> list = routeConfigRepository.findAll().stream()
                .map(this::toResponseWithGeneratedId)
                .toList();
        return ApiResult.success(list);
    }

    public ApiResult<Void> refreshCache() {
        businessTypeRouter.refresh();
        return ApiResult.success(null);
    }

    private BffRouteConfigResponse toResponse(Long id, BffRouteConfig config) {
        return new BffRouteConfigResponse(id, config.businessType(), config.serviceName(), config.channelScope());
    }

    private BffRouteConfigResponse toResponseWithGeneratedId(BffRouteConfig config) {
        // findAll 不返回 ID（Repository 接口的 findAll 返回 BffRouteConfig 不含 ID），此处 ID 设为 null
        return new BffRouteConfigResponse(null, config.businessType(), config.serviceName(), config.channelScope());
    }
}
```

- [ ] **Step 11: 创建 BffRouteManagementController**

文件：`intranet-bff/intranet-bff-adapter/src/main/java/com/example/bff/intranet/adapter/controller/BffRouteManagementController.java`

```java
package com.example.bff.intranet.adapter.controller;

import com.example.bff.intranet.api.BffRouteManagementApi;
import com.example.bff.intranet.api.dto.BffRouteConfigDeleteRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigGetRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigResponse;
import com.example.bff.intranet.api.dto.BffRouteConfigUpdateRequest;
import com.example.bff.intranet.application.service.RouteConfigManagementService;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 路由配置管理 Controller
 *
 * @author bff
 */
@RestController
public class BffRouteManagementController implements BffRouteManagementApi {

    private final RouteConfigManagementService routeConfigManagementService;

    public BffRouteManagementController(RouteConfigManagementService routeConfigManagementService) {
        this.routeConfigManagementService = routeConfigManagementService;
    }

    @Override
    public ApiResult<Long> create(BffRouteConfigRequest request) {
        return routeConfigManagementService.create(request);
    }

    @Override
    public ApiResult<Void> update(BffRouteConfigUpdateRequest request) {
        return routeConfigManagementService.update(request.id(), request.config());
    }

    @Override
    public ApiResult<Void> delete(BffRouteConfigDeleteRequest request) {
        return routeConfigManagementService.delete(request.id());
    }

    @Override
    public ApiResult<BffRouteConfigResponse> get(BffRouteConfigGetRequest request) {
        return routeConfigManagementService.get(request.id());
    }

    @Override
    public ApiResult<List<BffRouteConfigResponse>> list() {
        return routeConfigManagementService.list();
    }

    @Override
    public ApiResult<Void> refreshCache() {
        return routeConfigManagementService.refreshCache();
    }
}
```

- [ ] **Step 12: 编写 Controller 测试**

文件：`intranet-bff/intranet-bff-adapter/src/test/java/com/example/bff/intranet/adapter/controller/BffRouteManagementControllerTest.java`

```java
package com.example.bff.intranet.adapter.controller;

import com.example.bff.intranet.api.dto.BffRouteConfigDeleteRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigGetRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigRequest;
import com.example.bff.intranet.api.dto.BffRouteConfigResponse;
import com.example.bff.intranet.api.dto.BffRouteConfigUpdateRequest;
import com.example.bff.intranet.application.service.RouteConfigManagementService;
import com.example.bff.shared.route.ChannelScope;
import com.example.shared.web.core.api.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BffRouteManagementController.class)
class BffRouteManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RouteConfigManagementService routeConfigManagementService;

    @Test
    @DisplayName("POST /management/routes/create 调用 service.create")
    void create_callsService() throws Exception {
        when(routeConfigManagementService.create(any())).thenReturn(ApiResult.success(100L));

        BffRouteConfigRequest request = new BffRouteConfigRequest("TYPE_X", "svc-x", ChannelScope.ALL);

        mockMvc.perform(post("/management/routes/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(100));
    }

    @Test
    @DisplayName("POST /management/routes/get 调用 service.get")
    void get_callsService() throws Exception {
        BffRouteConfigResponse response = new BffRouteConfigResponse(1L, "TYPE_Y", "svc-y", ChannelScope.INTRANET);
        when(routeConfigManagementService.get(eq(1L))).thenReturn(ApiResult.success(response));

        BffRouteConfigGetRequest request = new BffRouteConfigGetRequest(1L);

        mockMvc.perform(post("/management/routes/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessType").value("TYPE_Y"));
    }

    @Test
    @DisplayName("POST /management/routes/list 调用 service.list")
    void list_callsService() throws Exception {
        when(routeConfigManagementService.list()).thenReturn(ApiResult.success(List.of()));

        mockMvc.perform(post("/management/routes/list")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON.0000"));
    }

    @Test
    @DisplayName("POST /management/routes/delete 调用 service.delete")
    void delete_callsService() throws Exception {
        when(routeConfigManagementService.delete(eq(5L))).thenReturn(ApiResult.success(null));

        BffRouteConfigDeleteRequest request = new BffRouteConfigDeleteRequest(5L);

        mockMvc.perform(post("/management/routes/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(routeConfigManagementService).delete(5L);
    }

    @Test
    @DisplayName("POST /management/routes/refresh-cache 调用 service.refreshCache")
    void refreshCache_callsService() throws Exception {
        when(routeConfigManagementService.refreshCache()).thenReturn(ApiResult.success(null));

        mockMvc.perform(post("/management/routes/refresh-cache")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(routeConfigManagementService).refreshCache();
    }

    @Test
    @DisplayName("POST /management/routes/update 调用 service.update")
    void update_callsService() throws Exception {
        when(routeConfigManagementService.update(eq(2L), any())).thenReturn(ApiResult.success(null));

        BffRouteConfigRequest config = new BffRouteConfigRequest("TYPE_Z", "svc-z", ChannelScope.ALL);
        BffRouteConfigUpdateRequest request = new BffRouteConfigUpdateRequest(2L, config);

        mockMvc.perform(post("/management/routes/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(routeConfigManagementService).update(eq(2L), any());
    }
}
```

- [ ] **Step 13: 运行 Controller 测试验证通过**

Run: `mvn test -pl intranet-bff/intranet-bff-adapter -Dtest=BffRouteManagementControllerTest`
Expected: 6 tests PASS

- [ ] **Step 14: 全量编译验证（含 bff-shared 和 internet-bff）**

Run: `mvn compile -pl bff-shared,internet-bff/internet-bff-infrastructure,intranet-bff/intranet-bff-api,intranet-bff/intranet-bff-application,intranet-bff/intranet-bff-adapter,intranet-bff/intranet-bff-infrastructure -am`
Expected: BUILD SUCCESS

- [ ] **Step 15: Commit**

```bash
git add bff-shared/ internet-bff/internet-bff-infrastructure/ intranet-bff/
git commit -m "feat(intranet-bff): 新增路由配置管理接口与 CRUD 能力

1. 扩展 bff-shared 的 BffRouteConfigRepository 接口，新增 save/update/delete/findById/findAll 方法
2. 同步更新 internet-bff-infrastructure 的 RepositoryImpl 实现以保持接口完整
3. 实现 intranet-bff-infrastructure 的 CRUD 方法，时间戳由应用层管理
4. 新增 RouteConfigManagementService 编排 CRUD 并刷新 BusinessTypeRouter 缓存
5. 新增 BffRouteManagementApi（6 端点：create/update/delete/get/list/refresh-cache）
6. 新增 11 个测试覆盖 Repository CRUD 与 Controller 路由"
```

---

## Task 3: 审批管理接口（14 个，透明转发 approval-service）

**Files:**
- Create: `intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/BffApprovalApi.java`
- Create: `intranet-bff/intranet-bff-application/src/main/java/com/example/bff/intranet/application/service/ApprovalManagementService.java`
- Create: `intranet-bff/intranet-bff-adapter/src/main/java/com/example/bff/intranet/adapter/controller/BffApprovalController.java`
- Create: `intranet-bff/intranet-bff-adapter/src/test/java/com/example/bff/intranet/adapter/controller/BffApprovalControllerTest.java`

**Interfaces:**
- Consumes: `ApprovalFlowApi`、`ApprovalInstanceApi`（来自 approval-service-api，由 httpexchange 客户端自动创建代理）
- Produces: `BffApprovalApi` 接口（14 方法），`ApprovalManagementService`

**设计说明：** BFF 对外接口的方法参数和返回值直接复用 approval-api 的 Request/Response 类型，不定义额外 DTO。`httpexchange-spring-boot-autoconfigure` 会根据 `httpexchange.clients.com.example.approval.api.url` 配置自动为 `ApprovalFlowApi`/`ApprovalInstanceApi` 创建 RestClient 代理 Bean，注入到 `ApprovalManagementService`。

- [ ] **Step 1: 创建 BffApprovalApi 接口**

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/BffApprovalApi.java`

```java
package com.example.bff.intranet.api;

import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.approval.api.dto.ApprovalRecordDTO;
import com.example.approval.api.dto.PendingApprovalDTO;
import com.example.approval.api.request.ApproveRequest;
import com.example.approval.api.request.CreateApprovalFlowRequest;
import com.example.approval.api.request.DeprecateApprovalFlowRequest;
import com.example.approval.api.request.GetApprovalFlowRequest;
import com.example.approval.api.request.GetApprovalHistoryRequest;
import com.example.approval.api.request.GetApprovalInstanceRequest;
import com.example.approval.api.request.ListApprovalFlowsRequest;
import com.example.approval.api.request.ListMyPendingApprovalsRequest;
import com.example.approval.api.request.MatchApprovalFlowRequest;
import com.example.approval.api.request.RejectRequest;
import com.example.approval.api.request.StartApprovalRequest;
import com.example.approval.api.request.TransferRequest;
import com.example.approval.api.request.UpdateApprovalFlowRequest;
import com.example.approval.api.request.WithdrawRequest;
import com.example.approval.api.response.ApprovalFlowIdResponse;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 审批管理 BFF API
 *
 * <p>透明转发到 approval-service，方法参数和返回值复用 approval-api 的 Request/Response 类型。
 *
 * @author bff
 */
@HttpExchange("/management/approval")
public interface BffApprovalApi {

    // ===== 审批流管理（6 个） =====

    @PostExchange("/flows/create")
    ApiResult<ApprovalFlowIdResponse> createFlow(@Valid @RequestBody CreateApprovalFlowRequest request);

    @PostExchange("/flows/update")
    ApiResult<Void> updateFlow(@Valid @RequestBody UpdateApprovalFlowRequest request);

    @PostExchange("/flows/deprecate")
    ApiResult<Void> deprecateFlow(@Valid @RequestBody DeprecateApprovalFlowRequest request);

    @PostExchange("/flows/get")
    ApiResult<ApprovalFlowDTO> getFlow(@Valid @RequestBody GetApprovalFlowRequest request);

    @PostExchange("/flows/list")
    ApiResult<PageData<ApprovalFlowDTO>> listFlows(@Valid @RequestBody ListApprovalFlowsRequest request);

    @PostExchange("/flows/match")
    ApiResult<ApprovalFlowDTO> matchFlow(@Valid @RequestBody MatchApprovalFlowRequest request);

    // ===== 审批实例管理（8 个） =====

    @PostExchange("/instances/start")
    ApiResult<ApprovalInstanceIdResponse> startInstance(@Valid @RequestBody StartApprovalRequest request);

    @PostExchange("/instances/approve")
    ApiResult<Void> approveInstance(@Valid @RequestBody ApproveRequest request);

    @PostExchange("/instances/reject")
    ApiResult<Void> rejectInstance(@Valid @RequestBody RejectRequest request);

    @PostExchange("/instances/transfer")
    ApiResult<Void> transferInstance(@Valid @RequestBody TransferRequest request);

    @PostExchange("/instances/withdraw")
    ApiResult<Void> withdrawInstance(@Valid @RequestBody WithdrawRequest request);

    @PostExchange("/instances/get")
    ApiResult<ApprovalInstanceDTO> getInstance(@Valid @RequestBody GetApprovalInstanceRequest request);

    @PostExchange("/instances/my-pending")
    ApiResult<PageData<PendingApprovalDTO>> listMyPending(@Valid @RequestBody ListMyPendingApprovalsRequest request);

    @PostExchange("/instances/history")
    ApiResult<List<ApprovalRecordDTO>> getHistory(@Valid @RequestBody GetApprovalHistoryRequest request);
}
```

- [ ] **Step 2: 创建 ApprovalManagementService**

文件：`intranet-bff/intranet-bff-application/src/main/java/com/example/bff/intranet/application/service/ApprovalManagementService.java`

```java
package com.example.bff.intranet.application.service;

import com.example.approval.api.ApprovalFlowApi;
import com.example.approval.api.ApprovalInstanceApi;
import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.approval.api.dto.ApprovalRecordDTO;
import com.example.approval.api.dto.PendingApprovalDTO;
import com.example.approval.api.request.ApproveRequest;
import com.example.approval.api.request.CreateApprovalFlowRequest;
import com.example.approval.api.request.DeprecateApprovalFlowRequest;
import com.example.approval.api.request.GetApprovalFlowRequest;
import com.example.approval.api.request.GetApprovalHistoryRequest;
import com.example.approval.api.request.GetApprovalInstanceRequest;
import com.example.approval.api.request.ListApprovalFlowsRequest;
import com.example.approval.api.request.ListMyPendingApprovalsRequest;
import com.example.approval.api.request.MatchApprovalFlowRequest;
import com.example.approval.api.request.RejectRequest;
import com.example.approval.api.request.StartApprovalRequest;
import com.example.approval.api.request.TransferRequest;
import com.example.approval.api.request.UpdateApprovalFlowRequest;
import com.example.approval.api.request.WithdrawRequest;
import com.example.approval.api.response.ApprovalFlowIdResponse;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审批管理服务
 *
 * <p>透明转发到 approval-service。{@link ApprovalFlowApi} 和 {@link ApprovalInstanceApi}
 * 由 {@code httpexchange-spring-boot-autoconfigure} 根据 yaml 配置自动创建代理 Bean。
 *
 * @author bff
 */
@Service
public class ApprovalManagementService {

    private final ApprovalFlowApi approvalFlowApi;
    private final ApprovalInstanceApi approvalInstanceApi;

    public ApprovalManagementService(ApprovalFlowApi approvalFlowApi,
                                     ApprovalInstanceApi approvalInstanceApi) {
        this.approvalFlowApi = approvalFlowApi;
        this.approvalInstanceApi = approvalInstanceApi;
    }

    // ===== 审批流管理（6 个） =====

    public ApiResult<ApprovalFlowIdResponse> createFlow(CreateApprovalFlowRequest request) {
        return approvalFlowApi.create(request);
    }

    public ApiResult<Void> updateFlow(UpdateApprovalFlowRequest request) {
        return approvalFlowApi.update(request);
    }

    public ApiResult<Void> deprecateFlow(DeprecateApprovalFlowRequest request) {
        return approvalFlowApi.deprecate(request);
    }

    public ApiResult<ApprovalFlowDTO> getFlow(GetApprovalFlowRequest request) {
        return approvalFlowApi.get(request);
    }

    public ApiResult<PageData<ApprovalFlowDTO>> listFlows(ListApprovalFlowsRequest request) {
        return approvalFlowApi.list(request);
    }

    public ApiResult<ApprovalFlowDTO> matchFlow(MatchApprovalFlowRequest request) {
        return approvalFlowApi.match(request);
    }

    // ===== 审批实例管理（8 个） =====

    public ApiResult<ApprovalInstanceIdResponse> startInstance(StartApprovalRequest request) {
        return approvalInstanceApi.start(request);
    }

    public ApiResult<Void> approveInstance(ApproveRequest request) {
        return approvalInstanceApi.approve(request);
    }

    public ApiResult<Void> rejectInstance(RejectRequest request) {
        return approvalInstanceApi.reject(request);
    }

    public ApiResult<Void> transferInstance(TransferRequest request) {
        return approvalInstanceApi.transfer(request);
    }

    public ApiResult<Void> withdrawInstance(WithdrawRequest request) {
        return approvalInstanceApi.withdraw(request);
    }

    public ApiResult<ApprovalInstanceDTO> getInstance(GetApprovalInstanceRequest request) {
        return approvalInstanceApi.get(request);
    }

    public ApiResult<PageData<PendingApprovalDTO>> listMyPending(ListMyPendingApprovalsRequest request) {
        return approvalInstanceApi.listMyPending(request);
    }

    public ApiResult<List<ApprovalRecordDTO>> getHistory(GetApprovalHistoryRequest request) {
        return approvalInstanceApi.getHistory(request);
    }
}
```

- [ ] **Step 3: 创建 BffApprovalController**

文件：`intranet-bff/intranet-bff-adapter/src/main/java/com/example/bff/intranet/adapter/controller/BffApprovalController.java`

```java
package com.example.bff.intranet.adapter.controller;

import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.approval.api.dto.ApprovalRecordDTO;
import com.example.approval.api.dto.PendingApprovalDTO;
import com.example.approval.api.request.ApproveRequest;
import com.example.approval.api.request.CreateApprovalFlowRequest;
import com.example.approval.api.request.DeprecateApprovalFlowRequest;
import com.example.approval.api.request.GetApprovalFlowRequest;
import com.example.approval.api.request.GetApprovalHistoryRequest;
import com.example.approval.api.request.GetApprovalInstanceRequest;
import com.example.approval.api.request.ListApprovalFlowsRequest;
import com.example.approval.api.request.ListMyPendingApprovalsRequest;
import com.example.approval.api.request.MatchApprovalFlowRequest;
import com.example.approval.api.request.RejectRequest;
import com.example.approval.api.request.StartApprovalRequest;
import com.example.approval.api.request.TransferRequest;
import com.example.approval.api.request.UpdateApprovalFlowRequest;
import com.example.approval.api.request.WithdrawRequest;
import com.example.approval.api.response.ApprovalFlowIdResponse;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.bff.intranet.api.BffApprovalApi;
import com.example.bff.intranet.application.service.ApprovalManagementService;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审批管理 Controller
 *
 * <p>透明转发到 approval-service，每个方法直接委托给 {@link ApprovalManagementService}。
 *
 * @author bff
 */
@RestController
public class BffApprovalController implements BffApprovalApi {

    private final ApprovalManagementService approvalManagementService;

    public BffApprovalController(ApprovalManagementService approvalManagementService) {
        this.approvalManagementService = approvalManagementService;
    }

    @Override
    public ApiResult<ApprovalFlowIdResponse> createFlow(CreateApprovalFlowRequest request) {
        return approvalManagementService.createFlow(request);
    }

    @Override
    public ApiResult<Void> updateFlow(UpdateApprovalFlowRequest request) {
        return approvalManagementService.updateFlow(request);
    }

    @Override
    public ApiResult<Void> deprecateFlow(DeprecateApprovalFlowRequest request) {
        return approvalManagementService.deprecateFlow(request);
    }

    @Override
    public ApiResult<ApprovalFlowDTO> getFlow(GetApprovalFlowRequest request) {
        return approvalManagementService.getFlow(request);
    }

    @Override
    public ApiResult<PageData<ApprovalFlowDTO>> listFlows(ListApprovalFlowsRequest request) {
        return approvalManagementService.listFlows(request);
    }

    @Override
    public ApiResult<ApprovalFlowDTO> matchFlow(MatchApprovalFlowRequest request) {
        return approvalManagementService.matchFlow(request);
    }

    @Override
    public ApiResult<ApprovalInstanceIdResponse> startInstance(StartApprovalRequest request) {
        return approvalManagementService.startInstance(request);
    }

    @Override
    public ApiResult<Void> approveInstance(ApproveRequest request) {
        return approvalManagementService.approveInstance(request);
    }

    @Override
    public ApiResult<Void> rejectInstance(RejectRequest request) {
        return approvalManagementService.rejectInstance(request);
    }

    @Override
    public ApiResult<Void> transferInstance(TransferRequest request) {
        return approvalManagementService.transferInstance(request);
    }

    @Override
    public ApiResult<Void> withdrawInstance(WithdrawRequest request) {
        return approvalManagementService.withdrawInstance(request);
    }

    @Override
    public ApiResult<ApprovalInstanceDTO> getInstance(GetApprovalInstanceRequest request) {
        return approvalManagementService.getInstance(request);
    }

    @Override
    public ApiResult<PageData<PendingApprovalDTO>> listMyPending(ListMyPendingApprovalsRequest request) {
        return approvalManagementService.listMyPending(request);
    }

    @Override
    public ApiResult<List<ApprovalRecordDTO>> getHistory(GetApprovalHistoryRequest request) {
        return approvalManagementService.getHistory(request);
    }
}
```

- [ ] **Step 4: 编写 Controller 测试（展示 2 个方法，其余同模式）**

文件：`intranet-bff/intranet-bff-adapter/src/test/java/com/example/bff/intranet/adapter/controller/BffApprovalControllerTest.java`

```java
package com.example.bff.intranet.adapter.controller;

import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.request.CreateApprovalFlowRequest;
import com.example.approval.api.request.GetApprovalInstanceRequest;
import com.example.approval.api.response.ApprovalFlowIdResponse;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.bff.intranet.application.service.ApprovalManagementService;
import com.example.shared.web.core.api.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审批管理 Controller 测试
 *
 * <p>展示 createFlow 和 getInstance 两个方法的测试，其余 12 个方法同模式（service 委托 → 返回 ApiResult）。
 *
 * @author bff
 */
@WebMvcTest(BffApprovalController.class)
class BffApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ApprovalManagementService approvalManagementService;

    @Test
    @DisplayName("POST /management/approval/flows/create 透明转发到 approval-service")
    void createFlow_forwardsToApprovalService() throws Exception {
        when(approvalManagementService.createFlow(any()))
                .thenReturn(ApiResult.success(new ApprovalFlowIdResponse("flow-001")));

        CreateApprovalFlowRequest request = new CreateApprovalFlowRequest(
                "ACC_PLAN_CREATE", "v1", null, null, null);

        mockMvc.perform(post("/management/approval/flows/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON.0000"));
    }

    @Test
    @DisplayName("POST /management/approval/instances/get 透明转发到 approval-service")
    void getInstance_forwardsToApprovalService() throws Exception {
        when(approvalManagementService.getInstance(any()))
                .thenReturn(ApiResult.success(new ApprovalInstanceDTO(
                        "inst-001", "flow-001", "v1", "ACC_PLAN_CREATE",
                        "batch-001", "PENDING", null, null, null, null)));

        GetApprovalInstanceRequest request = new GetApprovalInstanceRequest("inst-001");

        mockMvc.perform(post("/management/approval/instances/get")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON.0000"));
    }
}
```

注：其余 12 个方法的测试同上模式，均验证"Controller 调用 service 对应方法并返回 ApiResult"，可按需补充。

- [ ] **Step 5: 运行 Controller 测试验证通过**

Run: `mvn test -pl intranet-bff/intranet-bff-adapter -Dtest=BffApprovalControllerTest`
Expected: 2 tests PASS

- [ ] **Step 6: 全量编译验证**

Run: `mvn compile -pl intranet-bff/intranet-bff-api,intranet-bff/intranet-bff-application,intranet-bff/intranet-bff-adapter -am`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add intranet-bff/
git commit -m "feat(intranet-bff): 新增审批管理接口透明转发 approval-service

1. 定义 BffApprovalApi 接口，14 个 @PostExchange 端点（6 审批流 + 8 审批实例）
2. 创建 ApprovalManagementService，注入 ApprovalFlowApi + ApprovalInstanceApi 代理
3. 创建 BffApprovalController 实现接口，每个方法直接委托给 service
4. 透明转发复用 approval-api 的 Request/Response 类型，BFF 不定义额外 DTO
5. 新增 2 个 @WebMvcTest 覆盖审批流创建和审批实例查询的转发链路"
```

---

## Task 4: 用户权限管理接口（11 个，透明转发 auth-service）

**Files:**
- Create: `intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/BffPermissionApi.java`
- Create: `intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/BffChannelApi.java`
- Create: `intranet-bff/intranet-bff-application/src/main/java/com/example/bff/intranet/application/service/PermissionManagementService.java`
- Create: `intranet-bff/intranet-bff-application/src/main/java/com/example/bff/intranet/application/service/ChannelManagementService.java`
- Create: `intranet-bff/intranet-bff-adapter/src/main/java/com/example/bff/intranet/adapter/controller/BffPermissionController.java`
- Create: `intranet-bff/intranet-bff-adapter/src/main/java/com/example/bff/intranet/adapter/controller/BffChannelController.java`
- Create: `intranet-bff/intranet-bff-adapter/src/test/java/com/example/bff/intranet/adapter/controller/BffPermissionControllerTest.java`
- Create: `intranet-bff/intranet-bff-adapter/src/test/java/com/example/bff/intranet/adapter/controller/BffChannelControllerTest.java`

**Interfaces:**
- Consumes: `PermissionCheckApi`、`PermissionMetadataApi`、`PermissionCacheApi`、`CustomerChannelEntitlementApi`（来自 auth-api）
- Produces: `BffPermissionApi` 接口（7 方法）、`BffChannelApi` 接口（4 方法）

**设计说明：** 透明转发到 auth-service，复用 auth-api 的 Request/Response 类型。`BffPermissionApi` 合并了 `PermissionCheckApi`(3) + `PermissionMetadataApi`(2) + `PermissionCacheApi`(2) 共 7 个方法；`BffChannelApi` 对应 `CustomerChannelEntitlementApi`(4)。总计 11 个接口。

- [ ] **Step 1: 创建 BffPermissionApi 接口**

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/BffPermissionApi.java`

```java
package com.example.bff.intranet.api;

import com.example.auth.api.dto.DataScopeResponse;
import com.example.auth.api.dto.PermissionCheckBatchResponse;
import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.auth.api.dto.PermissionGroupResponse;
import com.example.auth.api.dto.PermissionItemResponse;
import com.example.auth.api.dto.PermissionResponse;
import com.example.auth.api.query.DataScopeRequest;
import com.example.auth.api.query.GetBusinessPermissionsRequest;
import com.example.auth.api.query.GetPlatformPermissionsRequest;
import com.example.auth.api.query.ListPermissionItemsRequest;
import com.example.auth.api.query.PermissionCheckBatchRequest;
import com.example.auth.api.query.PermissionCheckRequest;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;
import java.util.Set;

/**
 * 权限管理 BFF API
 *
 * <p>透明转发到 auth-service，合并权限校验、元数据查询、缓存查询三类接口。
 *
 * @author bff
 */
@HttpExchange("/management/permissions")
public interface BffPermissionApi {

    // ===== 权限校验（3 个） =====

    @PostExchange("/check")
    ApiResult<PermissionCheckResponse> check(@Valid @RequestBody PermissionCheckRequest request);

    @PostExchange("/check-batch")
    ApiResult<PermissionCheckBatchResponse> checkBatch(@Valid @RequestBody PermissionCheckBatchRequest request);

    @PostExchange("/resolve-data-scope")
    ApiResult<DataScopeResponse> resolveDataScope(@Valid @RequestBody DataScopeRequest request);

    // ===== 权限元数据查询（2 个） =====

    @PostExchange("/metadata/items")
    ApiResult<List<PermissionItemResponse>> listItems(@Valid @RequestBody ListPermissionItemsRequest request);

    @PostExchange("/metadata/items/grouped")
    ApiResult<List<PermissionGroupResponse>> listGroupedItems(@Valid @RequestBody ListPermissionItemsRequest request);

    // ===== 权限缓存查询（2 个） =====

    @PostExchange("/cache/platform")
    ApiResult<Set<PermissionResponse>> getPlatformPermissions(@Valid @RequestBody GetPlatformPermissionsRequest request);

    @PostExchange("/cache/business")
    ApiResult<Set<PermissionResponse>> getBusinessPermissions(@Valid @RequestBody GetBusinessPermissionsRequest request);
}
```

- [ ] **Step 2: 创建 BffChannelApi 接口**

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/BffChannelApi.java`

```java
package com.example.bff.intranet.api;

import com.example.auth.api.command.DisableChannelRequest;
import com.example.auth.api.command.EnableChannelRequest;
import com.example.auth.api.command.ReplaceChannelsRequest;
import com.example.auth.api.dto.CustomerChannelEntitlementResponse;
import com.example.auth.api.query.GetEntitlementRequest;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Optional;

/**
 * 客户渠道开通管理 BFF API
 *
 * <p>透明转发到 auth-service 的 CustomerChannelEntitlementApi。
 *
 * @author bff
 */
@HttpExchange("/management/channels")
public interface BffChannelApi {

    @PostExchange("/enable")
    ApiResult<CustomerChannelEntitlementResponse> enable(@Valid @RequestBody EnableChannelRequest request);

    @PostExchange("/disable")
    ApiResult<Void> disable(@Valid @RequestBody DisableChannelRequest request);

    @PostExchange("/replace")
    ApiResult<CustomerChannelEntitlementResponse> replace(@Valid @RequestBody ReplaceChannelsRequest request);

    @PostExchange("/get")
    ApiResult<Optional<CustomerChannelEntitlementResponse>> get(@Valid @RequestBody GetEntitlementRequest request);
}
```

- [ ] **Step 3: 创建 PermissionManagementService**

文件：`intranet-bff/intranet-bff-application/src/main/java/com/example/bff/intranet/application/service/PermissionManagementService.java`

```java
package com.example.bff.intranet.application.service;

import com.example.auth.api.PermissionCacheApi;
import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.PermissionMetadataApi;
import com.example.auth.api.dto.DataScopeResponse;
import com.example.auth.api.dto.PermissionCheckBatchResponse;
import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.auth.api.dto.PermissionGroupResponse;
import com.example.auth.api.dto.PermissionItemResponse;
import com.example.auth.api.dto.PermissionResponse;
import com.example.auth.api.query.DataScopeRequest;
import com.example.auth.api.query.GetBusinessPermissionsRequest;
import com.example.auth.api.query.GetPlatformPermissionsRequest;
import com.example.auth.api.query.ListPermissionItemsRequest;
import com.example.auth.api.query.PermissionCheckBatchRequest;
import com.example.auth.api.query.PermissionCheckRequest;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 权限管理服务
 *
 * <p>透明转发到 auth-service。三个 auth API（PermissionCheckApi/PermissionMetadataApi/PermissionCacheApi）
 * 由 httpexchange 客户端自动创建代理 Bean。
 *
 * @author bff
 */
@Service
public class PermissionManagementService {

    private final PermissionCheckApi permissionCheckApi;
    private final PermissionMetadataApi permissionMetadataApi;
    private final PermissionCacheApi permissionCacheApi;

    public PermissionManagementService(PermissionCheckApi permissionCheckApi,
                                      PermissionMetadataApi permissionMetadataApi,
                                      PermissionCacheApi permissionCacheApi) {
        this.permissionCheckApi = permissionCheckApi;
        this.permissionMetadataApi = permissionMetadataApi;
        this.permissionCacheApi = permissionCacheApi;
    }

    // ===== 权限校验（3 个） =====

    public ApiResult<PermissionCheckResponse> check(PermissionCheckRequest request) {
        return permissionCheckApi.check(request);
    }

    public ApiResult<PermissionCheckBatchResponse> checkBatch(PermissionCheckBatchRequest request) {
        return permissionCheckApi.checkBatch(request);
    }

    public ApiResult<DataScopeResponse> resolveDataScope(DataScopeRequest request) {
        return permissionCheckApi.resolveDataScope(request);
    }

    // ===== 权限元数据查询（2 个） =====

    public ApiResult<List<PermissionItemResponse>> listItems(ListPermissionItemsRequest request) {
        return permissionMetadataApi.listItems(request);
    }

    public ApiResult<List<PermissionGroupResponse>> listGroupedItems(ListPermissionItemsRequest request) {
        return permissionMetadataApi.listGroupedItems(request);
    }

    // ===== 权限缓存查询（2 个） =====

    public ApiResult<Set<PermissionResponse>> getPlatformPermissions(GetPlatformPermissionsRequest request) {
        return permissionCacheApi.getPlatformPermissions(request);
    }

    public ApiResult<Set<PermissionResponse>> getBusinessPermissions(GetBusinessPermissionsRequest request) {
        return permissionCacheApi.getBusinessPermissions(request);
    }
}
```

- [ ] **Step 4: 创建 ChannelManagementService**

文件：`intranet-bff/intranet-bff-application/src/main/java/com/example/bff/intranet/application/service/ChannelManagementService.java`

```java
package com.example.bff.intranet.application.service;

import com.example.auth.api.CustomerChannelEntitlementApi;
import com.example.auth.api.command.DisableChannelRequest;
import com.example.auth.api.command.EnableChannelRequest;
import com.example.auth.api.command.ReplaceChannelsRequest;
import com.example.auth.api.dto.CustomerChannelEntitlementResponse;
import com.example.auth.api.query.GetEntitlementRequest;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 渠道开通管理服务
 *
 * <p>透明转发到 auth-service 的 CustomerChannelEntitlementApi。
 *
 * @author bff
 */
@Service
public class ChannelManagementService {

    private final CustomerChannelEntitlementApi channelEntitlementApi;

    public ChannelManagementService(CustomerChannelEntitlementApi channelEntitlementApi) {
        this.channelEntitlementApi = channelEntitlementApi;
    }

    public ApiResult<CustomerChannelEntitlementResponse> enable(EnableChannelRequest request) {
        return channelEntitlementApi.enable(request);
    }

    public ApiResult<Void> disable(DisableChannelRequest request) {
        return channelEntitlementApi.disable(request);
    }

    public ApiResult<CustomerChannelEntitlementResponse> replace(ReplaceChannelsRequest request) {
        return channelEntitlementApi.replace(request);
    }

    public ApiResult<Optional<CustomerChannelEntitlementResponse>> get(GetEntitlementRequest request) {
        return channelEntitlementApi.get(request);
    }
}
```

- [ ] **Step 5: 创建 BffPermissionController**

文件：`intranet-bff/intranet-bff-adapter/src/main/java/com/example/bff/intranet/adapter/controller/BffPermissionController.java`

```java
package com.example.bff.intranet.adapter.controller;

import com.example.auth.api.dto.DataScopeResponse;
import com.example.auth.api.dto.PermissionCheckBatchResponse;
import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.auth.api.dto.PermissionGroupResponse;
import com.example.auth.api.dto.PermissionItemResponse;
import com.example.auth.api.dto.PermissionResponse;
import com.example.auth.api.query.DataScopeRequest;
import com.example.auth.api.query.GetBusinessPermissionsRequest;
import com.example.auth.api.query.GetPlatformPermissionsRequest;
import com.example.auth.api.query.ListPermissionItemsRequest;
import com.example.auth.api.query.PermissionCheckBatchRequest;
import com.example.auth.api.query.PermissionCheckRequest;
import com.example.bff.intranet.api.BffPermissionApi;
import com.example.bff.intranet.application.service.PermissionManagementService;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 权限管理 Controller
 *
 * <p>透明转发到 auth-service，每个方法直接委托给 {@link PermissionManagementService}。
 *
 * @author bff
 */
@RestController
public class BffPermissionController implements BffPermissionApi {

    private final PermissionManagementService permissionManagementService;

    public BffPermissionController(PermissionManagementService permissionManagementService) {
        this.permissionManagementService = permissionManagementService;
    }

    @Override
    public ApiResult<PermissionCheckResponse> check(PermissionCheckRequest request) {
        return permissionManagementService.check(request);
    }

    @Override
    public ApiResult<PermissionCheckBatchResponse> checkBatch(PermissionCheckBatchRequest request) {
        return permissionManagementService.checkBatch(request);
    }

    @Override
    public ApiResult<DataScopeResponse> resolveDataScope(DataScopeRequest request) {
        return permissionManagementService.resolveDataScope(request);
    }

    @Override
    public ApiResult<List<PermissionItemResponse>> listItems(ListPermissionItemsRequest request) {
        return permissionManagementService.listItems(request);
    }

    @Override
    public ApiResult<List<PermissionGroupResponse>> listGroupedItems(ListPermissionItemsRequest request) {
        return permissionManagementService.listGroupedItems(request);
    }

    @Override
    public ApiResult<Set<PermissionResponse>> getPlatformPermissions(GetPlatformPermissionsRequest request) {
        return permissionManagementService.getPlatformPermissions(request);
    }

    @Override
    public ApiResult<Set<PermissionResponse>> getBusinessPermissions(GetBusinessPermissionsRequest request) {
        return permissionManagementService.getBusinessPermissions(request);
    }
}
```

- [ ] **Step 6: 创建 BffChannelController**

文件：`intranet-bff/intranet-bff-adapter/src/main/java/com/example/bff/intranet/adapter/controller/BffChannelController.java`

```java
package com.example.bff.intranet.adapter.controller;

import com.example.auth.api.command.DisableChannelRequest;
import com.example.auth.api.command.EnableChannelRequest;
import com.example.auth.api.command.ReplaceChannelsRequest;
import com.example.auth.api.dto.CustomerChannelEntitlementResponse;
import com.example.auth.api.query.GetEntitlementRequest;
import com.example.bff.intranet.api.BffChannelApi;
import com.example.bff.intranet.application.service.ChannelManagementService;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 渠道开通管理 Controller
 *
 * <p>透明转发到 auth-service 的 CustomerChannelEntitlementApi。
 *
 * @author bff
 */
@RestController
public class BffChannelController implements BffChannelApi {

    private final ChannelManagementService channelManagementService;

    public BffChannelController(ChannelManagementService channelManagementService) {
        this.channelManagementService = channelManagementService;
    }

    @Override
    public ApiResult<CustomerChannelEntitlementResponse> enable(EnableChannelRequest request) {
        return channelManagementService.enable(request);
    }

    @Override
    public ApiResult<Void> disable(DisableChannelRequest request) {
        return channelManagementService.disable(request);
    }

    @Override
    public ApiResult<CustomerChannelEntitlementResponse> replace(ReplaceChannelsRequest request) {
        return channelManagementService.replace(request);
    }

    @Override
    public ApiResult<Optional<CustomerChannelEntitlementResponse>> get(GetEntitlementRequest request) {
        return channelManagementService.get(request);
    }
}
```

- [ ] **Step 7: 编写权限 Controller 测试（展示 2 个方法，其余同模式）**

文件：`intranet-bff/intranet-bff-adapter/src/test/java/com/example/bff/intranet/adapter/controller/BffPermissionControllerTest.java`

```java
package com.example.bff.intranet.adapter.controller;

import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.auth.api.dto.PermissionItemResponse;
import com.example.auth.api.query.ListPermissionItemsRequest;
import com.example.auth.api.query.PermissionCheckRequest;
import com.example.bff.intranet.application.service.PermissionManagementService;
import com.example.shared.web.core.api.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 权限管理 Controller 测试
 *
 * <p>展示 check 和 listItems 两个方法，其余 5 个方法同模式。
 *
 * @author bff
 */
@WebMvcTest(BffPermissionController.class)
class BffPermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PermissionManagementService permissionManagementService;

    @Test
    @DisplayName("POST /management/permissions/check 透明转发到 auth-service")
    void check_forwardsToAuthService() throws Exception {
        when(permissionManagementService.check(any()))
                .thenReturn(ApiResult.success(new PermissionCheckResponse(true, null)));

        PermissionCheckRequest request = new PermissionCheckRequest(
                "user-001", "PERM_CODE", "ACC_PLAN_CREATE", null);

        mockMvc.perform(post("/management/permissions/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON.0000"));
    }

    @Test
    @DisplayName("POST /management/permissions/metadata/items 透明转发到 auth-service")
    void listItems_forwardsToAuthService() throws Exception {
        when(permissionManagementService.listItems(any()))
                .thenReturn(ApiResult.success(List.of()));

        ListPermissionItemsRequest request = new ListPermissionItemsRequest(null);

        mockMvc.perform(post("/management/permissions/metadata/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON.0000"));
    }
}
```

注：其余 5 个权限方法 + 4 个渠道方法测试同上模式。

- [ ] **Step 8: 编写渠道 Controller 测试（展示 1 个方法，其余同模式）**

文件：`intranet-bff/intranet-bff-adapter/src/test/java/com/example/bff/intranet/adapter/controller/BffChannelControllerTest.java`

```java
package com.example.bff.intranet.adapter.controller;

import com.example.auth.api.command.EnableChannelRequest;
import com.example.auth.api.dto.CustomerChannelEntitlementResponse;
import com.example.bff.intranet.application.service.ChannelManagementService;
import com.example.shared.web.core.api.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 渠道开通管理 Controller 测试
 *
 * <p>展示 enable 方法，其余 3 个方法同模式。
 *
 * @author bff
 */
@WebMvcTest(BffChannelController.class)
class BffChannelControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChannelManagementService channelManagementService;

    @Test
    @DisplayName("POST /management/channels/enable 透明转发到 auth-service")
    void enable_forwardsToAuthService() throws Exception {
        when(channelManagementService.enable(any()))
                .thenReturn(ApiResult.success(new CustomerChannelEntitlementResponse(
                        "cust-001", "INTRANET", true, null)));

        EnableChannelRequest request = new EnableChannelRequest("cust-001", "INTRANET");

        mockMvc.perform(post("/management/channels/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON.0000"));
    }
}
```

- [ ] **Step 9: 运行 Controller 测试验证通过**

Run: `mvn test -pl intranet-bff/intranet-bff-adapter -Dtest=BffPermissionControllerTest,BffChannelControllerTest`
Expected: 3 tests PASS

- [ ] **Step 10: 全量编译验证**

Run: `mvn compile -pl intranet-bff/intranet-bff-api,intranet-bff/intranet-bff-application,intranet-bff/intranet-bff-adapter -am`
Expected: BUILD SUCCESS

- [ ] **Step 11: Commit**

```bash
git add intranet-bff/
git commit -m "feat(intranet-bff): 新增用户权限与渠道管理接口透明转发 auth-service

1. 定义 BffPermissionApi 接口，7 个 @PostExchange 端点（3 权限校验 + 2 元数据 + 2 缓存）
2. 定义 BffChannelApi 接口，4 个 @PostExchange 端点（渠道开通管理）
3. 创建 PermissionManagementService 注入 3 个 auth API 代理
4. 创建 ChannelManagementService 注入 CustomerChannelEntitlementApi 代理
5. 创建 BffPermissionController + BffChannelController 实现接口
6. 透明转发复用 auth-api 的 Request/Response 类型，BFF 不定义额外 DTO
7. 新增 3 个 @WebMvcTest 覆盖权限校验、元数据查询、渠道开通的转发链路"
```

---

## Task 5: 系统配置管理接口（2 个）

**Files:**
- Create: `intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffSystemInfoResponse.java`
- Create: `intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffBusinessTypeResponse.java`
- Create: `intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/BffSystemApi.java`
- Create: `intranet-bff/intranet-bff-application/src/main/java/com/example/bff/intranet/application/service/SystemManagementService.java`
- Create: `intranet-bff/intranet-bff-adapter/src/main/java/com/example/bff/intranet/adapter/controller/BffSystemController.java`
- Create: `intranet-bff/intranet-bff-adapter/src/test/java/com/example/bff/intranet/adapter/controller/BffSystemControllerTest.java`

**Interfaces:**
- Consumes: `Environment`（读取 `bff.channel-scope`、`spring.application.name`、`server.port`、`server.servlet.context-path`）、`BffRouteConfigRepository`（`findAll` 获取业务类型列表）
- Produces: `BffSystemApi` 接口（2 方法：getInfo / listBusinessTypes）、`SystemManagementService`

**设计说明：** 系统配置管理是 BFF 自身的只读查询接口，不涉及下游服务转发。`getInfo` 返回 BFF 运行时元信息（渠道、服务名、端口、context-path）；`listBusinessTypes` 返回当前渠道支持的业务类型列表（从路由配置表 `findAll` 查询并去重）。

- [ ] **Step 1: 创建 BffSystemInfoResponse DTO**

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffSystemInfoResponse.java`

```java
package com.example.bff.intranet.api.dto;

/**
 * BFF 系统信息响应
 *
 * @param channelScope 渠道范围（INTRANET）
 * @param serviceName  服务名（spring.application.name）
 * @param port         服务端口
 * @param contextPath  上下文路径
 *
 * @author bff
 */
public record BffSystemInfoResponse(
    String channelScope,
    String serviceName,
    String port,
    String contextPath
) {
}
```

- [ ] **Step 2: 创建 BffBusinessTypeResponse DTO**

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/dto/BffBusinessTypeResponse.java`

```java
package com.example.bff.intranet.api.dto;

/**
 * 支持的业务类型响应
 *
 * @param businessType 业务类型
 * @param serviceName  目标服务名
 * @param channelScope 渠道范围
 *
 * @author bff
 */
public record BffBusinessTypeResponse(
    String businessType,
    String serviceName,
    String channelScope
) {
}
```

- [ ] **Step 3: 创建 BffSystemApi 接口**

文件：`intranet-bff/intranet-bff-api/src/main/java/com/example/bff/intranet/api/BffSystemApi.java`

```java
package com.example.bff.intranet.api;

import com.example.bff.intranet.api.dto.BffBusinessTypeResponse;
import com.example.bff.intranet.api.dto.BffSystemInfoResponse;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * BFF 系统配置管理 API
 *
 * @author bff
 */
@HttpExchange("/management/system")
public interface BffSystemApi {

    @PostExchange("/info")
    ApiResult<BffSystemInfoResponse> getInfo();

    @PostExchange("/business-types")
    ApiResult<List<BffBusinessTypeResponse>> listBusinessTypes();
}
```

- [ ] **Step 4: 创建 SystemManagementService**

文件：`intranet-bff/intranet-bff-application/src/main/java/com/example/bff/intranet/application/service/SystemManagementService.java`

```java
package com.example.bff.intranet.application.service;

import com.example.bff.intranet.api.dto.BffBusinessTypeResponse;
import com.example.bff.intranet.api.dto.BffSystemInfoResponse;
import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.BffRouteConfigRepository;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统配置管理服务
 *
 * <p>提供 BFF 运行时元信息查询和当前渠道支持的业务类型列表。
 * 系统信息从 {@link Environment} 读取，业务类型从 {@link BffRouteConfigRepository} 查询。
 *
 * @author bff
 */
@Service
public class SystemManagementService {

    private final Environment environment;
    private final BffRouteConfigRepository routeConfigRepository;

    public SystemManagementService(Environment environment,
                                   BffRouteConfigRepository routeConfigRepository) {
        this.environment = environment;
        this.routeConfigRepository = routeConfigRepository;
    }

    public ApiResult<BffSystemInfoResponse> getInfo() {
        BffSystemInfoResponse response = new BffSystemInfoResponse(
                environment.getProperty("bff.channel-scope", "ALL"),
                environment.getProperty("spring.application.name", "unknown"),
                environment.getProperty("server.port", "0"),
                environment.getProperty("server.servlet.context-path", "/")
        );
        return ApiResult.success(response);
    }

    public ApiResult<List<BffBusinessTypeResponse>> listBusinessTypes() {
        List<BffBusinessTypeResponse> list = routeConfigRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
        return ApiResult.success(list);
    }

    private BffBusinessTypeResponse toResponse(BffRouteConfig config) {
        return new BffBusinessTypeResponse(
                config.businessType(),
                config.serviceName(),
                config.channelScope().name()
        );
    }
}
```

- [ ] **Step 5: 创建 BffSystemController**

文件：`intranet-bff/intranet-bff-adapter/src/main/java/com/example/bff/intranet/adapter/controller/BffSystemController.java`

```java
package com.example.bff.intranet.adapter.controller;

import com.example.bff.intranet.api.BffSystemApi;
import com.example.bff.intranet.api.dto.BffBusinessTypeResponse;
import com.example.bff.intranet.api.dto.BffSystemInfoResponse;
import com.example.bff.intranet.application.service.SystemManagementService;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统配置管理 Controller
 *
 * @author bff
 */
@RestController
public class BffSystemController implements BffSystemApi {

    private final SystemManagementService systemManagementService;

    public BffSystemController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    @Override
    public ApiResult<BffSystemInfoResponse> getInfo() {
        return systemManagementService.getInfo();
    }

    @Override
    public ApiResult<List<BffBusinessTypeResponse>> listBusinessTypes() {
        return systemManagementService.listBusinessTypes();
    }
}
```

- [ ] **Step 6: 编写 Controller 测试**

文件：`intranet-bff/intranet-bff-adapter/src/test/java/com/example/bff/intranet/adapter/controller/BffSystemControllerTest.java`

```java
package com.example.bff.intranet.adapter.controller;

import com.example.bff.intranet.api.dto.BffBusinessTypeResponse;
import com.example.bff.intranet.api.dto.BffSystemInfoResponse;
import com.example.bff.intranet.application.service.SystemManagementService;
import com.example.shared.web.core.api.ApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 系统配置管理 Controller 测试
 *
 * @author bff
 */
@WebMvcTest(BffSystemController.class)
class BffSystemControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SystemManagementService systemManagementService;

    @Test
    @DisplayName("POST /management/system/info 返回 BFF 系统信息")
    void getInfo_returnsSystemInfo() throws Exception {
        BffSystemInfoResponse response = new BffSystemInfoResponse(
                "INTRANET", "intranet-bff", "18091", "/intranet-bff");
        when(systemManagementService.getInfo()).thenReturn(ApiResult.success(response));

        mockMvc.perform(post("/management/system/info")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channelScope").value("INTRANET"))
                .andExpect(jsonPath("$.data.serviceName").value("intranet-bff"))
                .andExpect(jsonPath("$.data.port").value("18091"))
                .andExpect(jsonPath("$.data.contextPath").value("/intranet-bff"));
    }

    @Test
    @DisplayName("POST /management/system/business-types 返回业务类型列表")
    void listBusinessTypes_returnsList() throws Exception {
        List<BffBusinessTypeResponse> list = List.of(
                new BffBusinessTypeResponse("ACC_PLAN_CREATE", "annuity-service", "INTRANET"),
                new BffBusinessTypeResponse("LOAN_APPLY", "loan-service", "ALL")
        );
        when(systemManagementService.listBusinessTypes()).thenReturn(ApiResult.success(list));

        mockMvc.perform(post("/management/system/business-types")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].businessType").value("ACC_PLAN_CREATE"))
                .andExpect(jsonPath("$.data[1].serviceName").value("loan-service"));
    }
}
```

- [ ] **Step 7: 运行 Controller 测试验证通过**

Run: `mvn test -pl intranet-bff/intranet-bff-adapter -Dtest=BffSystemControllerTest`
Expected: 2 tests PASS

- [ ] **Step 8: 全量编译验证**

Run: `mvn compile -pl intranet-bff/intranet-bff-api,intranet-bff/intranet-bff-application,intranet-bff/intranet-bff-adapter -am`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add intranet-bff/
git commit -m "feat(intranet-bff): 新增系统配置管理接口

1. 定义 BffSystemApi 接口，2 个 @PostExchange 端点（info / business-types）
2. 创建 SystemManagementService，系统信息从 Environment 读取，业务类型从路由配置表查询
3. 创建 BffSystemController 实现接口
4. 新增 2 个 @WebMvcTest 覆盖系统信息查询和业务类型列表"
```

---

## Task 6: 启动模块 + 集成测试

**Files:**
- Create: `intranet-bff/intranet-bff-starter/pom.xml`
- Create: `intranet-bff/intranet-bff-starter/src/main/java/com/example/bff/intranet/IntranetBffApplication.java`
- Create: `intranet-bff/intranet-bff-starter/src/main/resources/application.yml`
- Create: `intranet-bff/intranet-bff-starter/src/main/resources/application-local.yml`
- Create: `intranet-bff/intranet-bff-starter/src/test/resources/application.yml`
- Create: `intranet-bff/intranet-bff-starter/src/test/resources/schema-h2.sql`
- Create: `intranet-bff/intranet-bff-starter/src/test/java/com/example/bff/intranet/IntranetBffApplicationTest.java`

**Interfaces:**
- Consumes: 所有 intranet-bff 子模块（adapter + infrastructure）、`bff-shared`（`BffAutoConfiguration`）、Nacos discovery、Spring Cloud LoadBalancer、`httpexchange-spring-boot-autoconfigure`（服务端端点映射 + 客户端代理创建）
- Produces: 可启动的 `intranet-bff` 服务（端口 18091，context-path `/intranet-bff`，渠道 `INTRANET`），集成测试验证上下文加载与全部 6 个 Controller 注册

**设计说明：** 启动模块聚合所有子模块，通过 `httpexchange-spring-boot-autoconfigure` 同时提供：① 服务端 `@HttpExchange` 端点映射（BFF 自身 6 个 Controller 的 API 接口）；② 客户端代理创建（`approval-api` / `auth-api` 的 `@HttpExchange` 接口代理，通过 `httpexchange.clients` YAML 配置映射到下游服务名）。集成测试通过 `@MockitoBean` 注入 6 个外部 API 的空实现，避免真实 HTTP 调用；通过嵌套 `TestInfrastructureConfiguration` 提供 `DataSource`（无连接池）和 `@LoadBalanced RestClient.Builder`，填补 starter 依赖链缺少的自动配置。

- [ ] **Step 1: 创建 starter pom.xml**

文件：`intranet-bff/intranet-bff-starter/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>intranet-bff</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>
  <artifactId>intranet-bff-starter</artifactId>
  <description>内网/专线渠道 BFF - 启动模块</description>
  <dependencies>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>intranet-bff-adapter</artifactId>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>intranet-bff-infrastructure</artifactId>
    </dependency>
    <dependency>
      <groupId>com.alibaba.cloud</groupId>
      <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-loadbalancer</artifactId>
    </dependency>
    <!-- httpexchange-spring-boot-autoconfigure: 提供 @HttpExchange 服务端端点映射 + 客户端代理创建 -->
    <dependency>
      <groupId>io.github.danielliu1123</groupId>
      <artifactId>httpexchange-spring-boot-autoconfigure</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <finalName>intranet-bff</finalName>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: 创建启动类 IntranetBffApplication**

文件：`intranet-bff/intranet-bff-starter/src/main/java/com/example/bff/intranet/IntranetBffApplication.java`

```java
package com.example.bff.intranet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 内网/专线渠道 BFF 启动类
 *
 * <p>scanBasePackages 同时包含 {@code com.example.bff.intranet}（本服务）和 {@code com.example.bff.shared}
 * （BFF 公共组件：BffAutoConfiguration 注册 BusinessTypeRouter / KernelApiRegistry）。
 *
 * <p>{@code @MapperScan} 显式扫描 intranet-bff-infrastructure 的 mapper 包，
 * 使 MyBatis-Flex 注册 BffRouteConfigMapper 为 Bean（与项目其他服务保持一致）。
 *
 * @author bff
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {
        "com.example.bff.intranet",
        "com.example.bff.shared"
})
@MapperScan("com.example.bff.intranet.infrastructure.mapper")
public class IntranetBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntranetBffApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建 application.yml**

文件：`intranet-bff/intranet-bff-starter/src/main/resources/application.yml`

```yaml
server:
  port: 18091
  servlet:
    context-path: /intranet-bff

spring:
  application:
    name: intranet-bff
  threads:
    virtual:
      enabled: true
  profiles:
    active: local

# BFF 渠道范围
bff:
  channel-scope: INTRANET

trace:
  context:
    mapping:
      OrderId: X-Order-Id
      UserId: X-User-Id
      BizId: X-Biz-Id
      BatchId: X-Batch-Id

logging:
  level:
    com.example.bff: DEBUG
```

- [ ] **Step 4: 创建 application-local.yml**

文件：`intranet-bff/intranet-bff-starter/src/main/resources/application-local.yml`

```yaml
spring:
  cloud:
    nacos:
      server-addr: ${NACOS_ADDR:127.0.0.1:8848}
      discovery:
        namespace: ${NACOS_NAMESPACE:public}
  datasource:
    url: ${DB_URL:jdbc:postgresql://127.0.0.1:5432/bff}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver

mybatis-flex:
  mapper-locations: classpath*:mapper/*.xml

# httpexchange 客户端代理配置：将下游服务 API 包映射到服务名
# httpexchange-spring-boot-autoconfigure 自动为 @HttpExchange 接口创建代理 Bean
httpexchange:
  clients:
    com.example.approval.api:
      url: lb://approval-service
    com.example.auth.api:
      url: lb://auth-service
```

- [ ] **Step 5: 创建测试 application.yml**

文件：`intranet-bff/intranet-bff-starter/src/test/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:bff-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  sql:
    init:
      schema-locations: classpath:schema-h2.sql
      mode: always
  autoconfigure:
    exclude:
      # 禁用 Nacos 服务发现自动配置（测试环境无需注册中心，避免拉起 Nacos 客户端连接失败）
      - com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration
      - com.alibaba.cloud.nacos.discovery.NacosServiceRegistryAutoConfiguration
      - com.alibaba.cloud.nacos.NacosAutoConfiguration
      - com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientConfiguration
      # 禁用 WebAutoConfiguration：其 @Import 的 BizTraceAutoConfiguration 注册 TraceContextProperties，
      # 该 @ConfigurationProperties 类标注 @Validated + @NotEmpty/@NotBlank，需 Jakarta Bean Validation provider；
      # intranet-bff-starter 依赖链不含 hibernate-validator（无 shared-permission-starter），
      # 启动时 NoProviderFoundException 会让上下文初始化失败。GlobalExceptionHandler 在集成测试中也非必需。
      - com.example.shared.web.autoconfigure.WebAutoConfiguration
  cloud:
    nacos:
      discovery:
        enabled: false
      config:
        enabled: false
    discovery:
      enabled: false

mybatis-flex:
  mapper-locations: classpath*:mapper/*.xml

bff:
  channel-scope: INTRANET

logging:
  level:
    com.example.bff: DEBUG
```

注：测试环境不配置 `httpexchange.clients`，`httpexchange-spring-boot-autoconfigure` 不会为 `approval-api` / `auth-api` 的 `@HttpExchange` 接口创建客户端代理（因无 URL 映射）。外部 API Bean 由测试类的 `@MockitoBean` 提供，避免真实 HTTP 调用。服务端 `@HttpExchange` 端点映射不受影响，BFF 自身的 6 个 Controller API 接口仍正常映射。

- [ ] **Step 6: 创建测试 schema-h2.sql**

文件：`intranet-bff/intranet-bff-starter/src/test/resources/schema-h2.sql`

```sql
CREATE TABLE IF NOT EXISTS t_bff_route_config (
    id              BIGINT       PRIMARY KEY,
    business_type   VARCHAR(64)  NOT NULL,
    service_name    VARCHAR(128) NOT NULL,
    channel_scope   VARCHAR(32)  NOT NULL DEFAULT 'ALL',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    description     VARCHAR(256),
    created_by      VARCHAR(64),
    create_time     TIMESTAMP,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         INT          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bff_route_business_channel
    ON t_bff_route_config(business_type, channel_scope, deleted);
```

- [ ] **Step 7: 创建集成测试 IntranetBffApplicationTest**

文件：`intranet-bff/intranet-bff-starter/src/test/java/com/example/bff/intranet/IntranetBffApplicationTest.java`

```java
package com.example.bff.intranet;

import com.example.approval.api.ApprovalFlowApi;
import com.example.approval.api.ApprovalInstanceApi;
import com.example.auth.api.CustomerChannelEntitlementApi;
import com.example.auth.api.PermissionCacheApi;
import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.PermissionMetadataApi;
import com.example.bff.intranet.infrastructure.repository.BffRouteConfigRepositoryImpl;
import com.example.bff.shared.registry.KernelApiRegistry;
import com.example.bff.shared.route.BusinessTypeRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内网/专线 BFF 启动模块集成测试
 *
 * <p>验证 Spring 上下文加载、BFF 核心组件（BusinessTypeRouter / KernelApiRegistry /
 * BffRouteConfigRepositoryImpl）注册、全部 6 个 Controller 注册。
 *
 * <p>外部 @HttpExchange API 客户端通过 {@link MockitoBean} 注入空实现，避免真实 HTTP 调用
 * （测试环境不配置 {@code httpexchange.clients}，autoconfigure 不会创建代理 Bean）。
 *
 * <p>测试环境通过嵌套 {@link TestInfrastructureConfiguration} 提供：
 * <ul>
 *   <li>{@link DataSource}（{@link DriverManagerDataSource}，无连接池）：intranet-bff-starter
 *       依赖链不含 HikariCP（不依赖 business-core-infrastructure），{@code DataSourceAutoConfiguration}
 *       无法自动创建池化数据源，导致 MyBatis-Flex 的 {@code SqlSessionFactory} 缺失。</li>
 *   <li>{@code @LoadBalanced RestClient.Builder}：测试环境无注册中心，Spring Cloud LoadBalancer
 *       自动配置链不会注册带 {@code @LoadBalanced} 限定的 {@link RestClient.Builder} bean，
 *       手动提供一个不实际执行负载均衡的 builder 仅为满足 {@link KernelApiRegistry} 的依赖注入。</li>
 * </ul>
 *
 * @author bff
 */
@SpringBootTest
class IntranetBffApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private BusinessTypeRouter businessTypeRouter;

    @Autowired
    private KernelApiRegistry kernelApiRegistry;

    @Autowired
    private BffRouteConfigRepositoryImpl routeConfigRepository;

    // 外部 @HttpExchange API 的 mock 实现，避免真实 HTTP 调用
    @MockitoBean
    private ApprovalFlowApi approvalFlowApi;
    @MockitoBean
    private ApprovalInstanceApi approvalInstanceApi;
    @MockitoBean
    private PermissionCheckApi permissionCheckApi;
    @MockitoBean
    private PermissionMetadataApi permissionMetadataApi;
    @MockitoBean
    private PermissionCacheApi permissionCacheApi;
    @MockitoBean
    private CustomerChannelEntitlementApi customerChannelEntitlementApi;

    @Test
    @DisplayName("应用上下文加载成功")
    void contextLoads() {
        assertNotNull(applicationContext);
    }

    @Test
    @DisplayName("BFF 核心组件已注册")
    void coreComponentsRegistered() {
        assertNotNull(businessTypeRouter);
        assertNotNull(kernelApiRegistry);
        assertNotNull(routeConfigRepository);
    }

    @Test
    @DisplayName("BffBusinessController 已注册")
    void bffBusinessControllerRegistered() {
        assertTrue(applicationContext.containsBean("bffBusinessController"));
    }

    @Test
    @DisplayName("BffRouteManagementController 已注册")
    void bffRouteManagementControllerRegistered() {
        assertTrue(applicationContext.containsBean("bffRouteManagementController"));
    }

    @Test
    @DisplayName("BffApprovalController 已注册")
    void bffApprovalControllerRegistered() {
        assertTrue(applicationContext.containsBean("bffApprovalController"));
    }

    @Test
    @DisplayName("BffPermissionController 已注册")
    void bffPermissionControllerRegistered() {
        assertTrue(applicationContext.containsBean("bffPermissionController"));
    }

    @Test
    @DisplayName("BffChannelController 已注册")
    void bffChannelControllerRegistered() {
        assertTrue(applicationContext.containsBean("bffChannelController"));
    }

    @Test
    @DisplayName("BffSystemController 已注册")
    void bffSystemControllerRegistered() {
        assertTrue(applicationContext.containsBean("bffSystemController"));
    }

    /**
     * 测试环境基础设施 bean 配置
     *
     * <p>提供 DataSource 与 {@code @LoadBalanced RestClient.Builder} 两个测试专用 bean，
     * 以填补 intranet-bff-starter 依赖链相对其他业务服务缺少的自动配置（HikariCP / 实际 LoadBalancer）。
     */
    @TestConfiguration
    static class TestInfrastructureConfiguration {

        @Bean
        @ConfigurationProperties("spring.datasource")
        public DataSource dataSource() {
            return new DriverManagerDataSource();
        }

        @Bean
        @LoadBalanced
        public RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }
}
```

- [ ] **Step 8: 运行集成测试验证通过**

Run: `mvn test -pl intranet-bff/intranet-bff-starter -Dtest=IntranetBffApplicationTest`
Expected: 8 tests PASS

- [ ] **Step 9: 全量编译验证（全部 intranet-bff 模块）**

Run: `mvn compile -pl intranet-bff/intranet-bff-api,intranet-bff/intranet-bff-application,intranet-bff/intranet-bff-adapter,intranet-bff/intranet-bff-infrastructure,intranet-bff/intranet-bff-starter -am`
Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add intranet-bff/intranet-bff-starter/
git commit -m "feat(intranet-bff): 新增启动模块与集成测试

1. 创建 intranet-bff-starter 聚合 adapter + infrastructure + Nacos + LoadBalancer + httpexchange
2. 启动类 IntranetBffApplication，scanBasePackages 包含 bff.intranet 和 bff.shared
3. application.yml 配置端口 18091、context-path /intranet-bff、channel-scope INTRANET
4. application-local.yml 配置 Nacos、PostgreSQL、httpexchange.clients 映射下游服务
5. 集成测试通过 @MockitoBean 注入 6 个外部 API 空实现，验证上下文加载与 6 个 Controller 注册
6. 测试 TestInfrastructureConfiguration 提供 DataSource 和 @LoadBalanced RestClient.Builder"
```

---

## 计划总结

### Task 清单

| Task | 内容 | 接口数 | 测试数 |
|------|------|--------|--------|
| Task 1 | intranet-bff 脚手架 + 业务接口 | 7 | 若干（Repository + Service + Controller） |
| Task 2 | 路由配置管理接口 | 6 | 11（5 CRUD + 6 Controller） |
| Task 3 | 审批管理接口（透明转发 approval-service） | 14 | 2（Controller 示例） |
| Task 4 | 用户权限管理接口（透明转发 auth-service） | 11 | 3（Controller 示例） |
| Task 5 | 系统配置管理接口 | 2 | 2（Controller） |
| Task 6 | 启动模块 + 集成测试 | - | 8（集成测试） |
| **合计** | | **40** | **26+** |

### 接口分类

| 分类 | 端点前缀 | 接口数 | 转发方式 |
|------|----------|--------|----------|
| 业务接口 | `/business/*` | 7 | DB 路由 + kernel API 代理 |
| 路由配置管理 | `/management/routes/*` | 6 | BFF 自身 CRUD |
| 审批管理 | `/management/approval/*` | 14 | 透明转发 approval-service |
| 权限管理 | `/management/permissions/*` | 7 | 透明转发 auth-service |
| 渠道开通管理 | `/management/channels/*` | 4 | 透明转发 auth-service |
| 系统配置管理 | `/management/system/*` | 2 | BFF 自身只读查询 |
| **合计** | | **40** | |

### 关键配置差异（intranet-bff vs internet-bff）

| 配置项 | internet-bff | intranet-bff |
|--------|--------------|--------------|
| 端口 | 18090 | 18091 |
| context-path | /internet-bff | /intranet-bff |
| channel-scope | INTERNET | INTRANET |
| 包名 | com.example.bff.internet | com.example.bff.intranet |
| httpexchange.clients | 无（不转发外部服务） | approval-api + auth-api |
| 管理接口数 | 0 | 33（6+14+11+2） |

---

## 最终全分支审查发现项（后续工作项）

> 审查范围 `5cf005b..0bfaeae`，覆盖 `bff-shared` / `internet-bff` / `intranet-bff`。未发现 Critical 阻断项。
> 以下发现项均为计划明确要求的实现，部分与项目硬性规范冲突或属功能缺口。因涉及跨模块连锁改动，本轮不予擅自修改，登记为后续迭代项。
>
> **修复状态：全部发现项已于 2026-08-09 修复并验证（BFF 模块全量测试通过）。**

### Important（已修复）

| 编号 | 问题 | 修复 |
|------|------|------|
| I1 | internet-bff 与 intranet-bff 大量逐字重复代码（业务 DTO、BusinessController、AggregationService、基础设施四件套仅包名不同） | 抽取基础设施四件套（DO/Mapper/RepositoryImpl/数据库脚本）到 bff-shared，删除两个 BFF 的重复类，Update MapperScan 指向共享包 |
| I2 | 路由配置 list 接口返回记录 ID 恒为 null（`findAll` 返回的 `BffRouteConfig` 不含 ID） | 新增 `findAllWithId()` 返回携带 ID 的 `BffRouteConfigEntry`，list 接口返回真实 ID |
| I3 | 未找到路由配置时 get() 返回 `success(null)`、update/delete 静默 no-op | get/update/delete 未找到时抛 `BusinessException`（复用 `BffErrorCode.ROUTE_NOT_FOUND`） |
| I4 | `BffApprovalApi` 含 14 个方法 | 拆分为 `BffApprovalFlowApi`(6) 与 `BffApprovalInstanceApi`(8)；后者再按 ISP 拆为 `BffApprovalInstanceOperationApi`(5) 与 `BffApprovalInstanceQueryApi`(3)，全部 ≤7 符合规范 |
| I5 | `BffChannelApi.get()` 返回 `ApiResult<Optional<...>>` | 下游 auth-api 与 BFF 均已改为返回具体类型 `CustomerChannelEntitlementResponse`，无 Optional |
| I6 | 主键说明（豁免登记） | 保持豁免，`@Id(keyType = Generator, value = "snowFlakeId")` |

### Minor（已修复）

| 编号 | 问题 | 修复 |
|------|------|------|
| M1 | 路由配置写操作未标注 `@Transactional` | create/update/delete 补 `@Transactional` |
| M2 | `BffErrorCode` 中 `DOWNSTREAM_SERVICE_ERROR`/`AGGREGATION_ERROR`/`KERNEL_API_NOT_FOUND` 生产代码未使用 | 移除未使用错误码，仅保留 `ROUTE_NOT_FOUND`/`INVALID_CHANNEL_SCOPE` |
| M3 | `BffAggregationService` 捕获 `Exception` 过宽 | 收窄为 `RuntimeException`，两个 BFF 均已修复 |
| M4 | `BusinessTypeRouter` 非法 `channelScope` 直接抛 IllegalArgumentException | 改为抛 `SystemException(INVALID_CHANNEL_SCOPE)` 友好提示；共享 DO 启用 `@Column(version = true)` 乐观锁；`BffSystemInfoResponse.port` 改 int |
| M5 | 部分管理接口测试覆盖偏薄（审批 14 测 2、渠道 4 测 1）；intranet 聚合测试缺下游异常降级用例 | 审批控制器测试补齐至 14 项、渠道控制器测试补齐至 4 项；intranet 聚合测试新增下游失败/api 失败/异常降级 3 用例 |

### 规范符合性确认（已核对通过）

- `KernelApiRegistry` 使用 `RestClient.Builder.clone()` 后设置 baseUrl，无状态污染
- 测试统一使用 `@MockitoBean`
- DO 时间戳由应用层管理，无 `@Column(onInsertValue/onUpdateValue)`
- `ApiResult` 为 record，成功码 `COMMON.0000`
- 聚合服务使用注入的 `AsyncTaskExecutor`（虚拟线程）
- 管理接口透明复用下游 Request/Response 类型，未重复定义 DTO

### 主键说明（I6）

`BffRouteConfigDO` 使用 `@Id(keyType = KeyType.Generator, value = "snowFlakeId")`，未走 `shared-id-starter`。该表为 BFF 路由配置表，审查判定为可接受豁免，但与规范 06「主键使用 shared-id-starter」存在差异，登记说明。
