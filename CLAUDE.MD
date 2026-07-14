# 项目完整描述报告

## 一、项目概述

**项目名称**：multiple-module-spring-cloud
**项目类型**：Spring Cloud 微服务架构
**架构风格**：DDD（领域驱动设计）+ 六边形架构
**构建工具**：Maven 3.9.0

---

## 二、工程结构与模块划分

### 2.1 顶层模块结构

```
multiple-module-spring-cloud/
├── demo-gateway/                    # API网关（独立模块）
├── demo-shared/                     # 共享基础设施模块（12个子模块）
├── integration-service/             # 集成服务（6个子模块）
├── file-service/                    # 文件服务（8个子模块）
├── customer-service/                # 客户服务（7个子模块）
└── business-core-kernel/            # 业务核心内核（6个子模块）
```

### 2.2 业务服务统一子模块结构

每个业务服务（`customer-service`、`file-service`、`integration-service`、`business-core-kernel`）遵循统一的DDD分层结构：

| 子模块 | 层级 | 职责描述 |
|--------|------|----------|
| `xxx-types` | 领域原语层 | 定义领域专用的基础类型（Domain Primitive） |
| `xxx-domain` | 领域层 | 实体、值对象、聚合根、领域服务、SPI接口 |
| `xxx-api` | API定义层 | 对外暴露的接口、DTO、Command、Query |
| `xxx-application` | 应用层 | 应用服务、业务流程编排 |
| `xxx-adapter` | 适配器层 | 接口适配、DTO转换、外部协议适配 |
| `xxx-infrastructure` | 基础设施层 | Repository实现、数据访问、外部服务集成 |
| `xxx-starter` | 启动模块 | Spring Boot启动类、打包入口 |

### 2.3 共享模块（demo-shared）详细说明

| 子模块 | 类型 | 核心能力 |
|--------|------|----------|
| `shared-types` | 基础类型 | 全局ID类型（Identifier、AcceptanceNo等）、分页对象 |
| `shared-exception` | 异常处理 | BaseException、BusinessException、DomainException、SystemException |
| `shared-utils` | 工具类 | 通用工具方法 |
| `shared-domain` | 领域基础 | 共享领域模型和通用领域组件 |
| `shared-api` | API基础 | 共享API定义、基础DTO |
| `shared-event-starter` | Starter | 事件驱动支持（Kafka、Redis）、事件发布/订阅 |
| `shared-id-starter` | Starter | ID生成服务（基于数据库+缓存） |
| `shared-cache-starter` | Starter | 缓存抽象（Caffeine本地缓存、Redisson分布式缓存） |
| `shared-web-starter` | Starter | Web层基础设施（AOP、HTTP客户端） |
| `shared-client-starter` | Starter | 外部服务调用（Retrofit） |
| `shared-logging-starter` | Starter | 请求日志记录（Logbook） |
| `shared-pdf-starter` | Starter | PDF生成（OpenHtmlToPdf + Thymeleaf） |

---

## 三、模块依赖关系

### 3.1 业务服务内部依赖（以customer-service为例）

```
customer-starter
    ├── customer-adapter
    │       ├── customer-api
    │       │       ├── customer-types
    │       │       │       └── shared-types
    │       │       └── shared-api
    │       └── customer-application
    │               ├── customer-api
    │               ├── customer-domain
    │               │       └── shared-domain
    │               ├── shared-pdf-starter
    │               └── shared-logging-starter
    └── customer-infrastructure
            ├── customer-domain
            ├── shared-id-starter
            ├── shared-web-starter
            ├── shared-logging-starter
            └── ...
```

### 3.2 依赖原则

```
types层 ──────> shared-types
domain层 ─────> shared-domain + xxx-types
api层 ────────> shared-api + xxx-types
application层 ─> xxx-api + xxx-domain + shared-*starter
adapter层 ─────> xxx-api + xxx-application
infrastructure层 -> xxx-domain + shared-*starter
starter层 ─────> xxx-adapter + xxx-infrastructure
```

**核心原则**：领域层（domain）不依赖应用层（application）和基础设施层（infrastructure），通过依赖倒置实现解耦。

### 3.3 跨服务依赖

| 服务 | 依赖的外部API模块 |
|------|-------------------|
| customer-service | business-core-api |
| file-service | business-core-api |
| integration-service | business-core-api |

---

## 四、技术栈与版本

### 4.1 核心框架

| 技术 | 版本 | 说明 |
|------|------|------|
| JDK | 25 | Java 25，启用Preview特性 |
| Spring Boot | 3.5.14 | 最新稳定版 |
| Spring Cloud | 2025.0.2 | 最新稳定版 |
| Spring Cloud Alibaba | 2025.0.0.0 | Nacos服务发现 |

### 4.2 数据访问

| 技术 | 版本 | 说明 |
|------|------|------|
| MyBatis-Flex | 1.11.5 | ORM框架 |
| PostgreSQL | - | 数据库（runtime依赖） |

### 4.3 缓存与消息

| 技术 | 版本 | 说明 |
|------|------|------|
| Redisson | 4.3.1 | Redis客户端 |
| Caffeine | - | 本地缓存 |
| Spring Kafka | - | 消息队列（可选） |

### 4.4 序列化与HTTP客户端

| 技术 | 版本 | 说明 |
|------|------|------|
| Fury | 0.10.3 | 高性能序列化框架 |
| Retrofit | 3.3.0 | HTTP客户端 |
| HttpExchange | 3.5.5 | Spring HTTP客户端 |
| Logbook | 4.0.4 | HTTP请求日志 |

### 4.5 工具与代码生成

| 技术 | 版本 | 说明 |
|------|------|------|
| MapStruct | 1.6.3 | 对象映射 |
| Lombok | 1.18.46 | 代码简化 |
| OpenHtmlToPdf | 1.1.36 | PDF生成 |
| BouncyCastle | 1.84 | 加密库 |

---

## 五、关键设计特性

### 5.1 Domain Primitive（领域原语）

项目在 `shared-types` 模块中定义了丰富的领域原语：

```java
// ID类型（示例）
Identifier          // 基础ID抽象
AcceptanceNo        // 受理编号
CustomerNo          // 客户编号
ProductNo           // 产品编号
BatchId             // 批次ID
PlanNo              // 计划编号
UserNo              // 用户编号
ApplicationId       // 应用ID
TaskId              // 任务ID
FormId              // 表单ID
FileId              // 文件ID
EventId             // 事件ID
SerialNo            // 序列号

// 分页类型
Pagination          // 分页参数
PageInfo<T>         // 分页结果
```

### 5.2 异常体系

项目建立了完整的异常层级：

```
BaseException          // 基础异常
    ├── BusinessException    // 业务异常
    ├── DomainException      // 领域异常
    └── SystemException     // 系统异常
```

### 5.3 业务核心领域模型（business-core-kernel）

核心聚合根：
- **BusinessApplication**：业务申请
- **BusinessBatch**：业务批次
- **BusinessForm**：业务表单

值对象：
- 业务相关：AccountManager、AnnuityChannel、BusinessLevel、BusinessType、OperationModel
- 配置相关：ExtractorConfig、FormParsingConfig、MaterialRuleConfig、StepExtensionConfig
- 状态枚举：ApplicationStatus、BatchStatus、FormStatus、StepExecutionStatus
- SPI接口：BusinessFactExtractor、StepActionHandler、StepExtensionAction

### 5.4 文件服务领域模型（file-service）

核心模型：
- **InboundTemplate**：入站模板
- **OutboundTemplate**：出站模板
- **InboundRule** / **OutboundRule**：解析规则
- **ParseResult**：解析结果
- **HeaderZone** / **DetailZone**：表单区域

---

## 六、启动模块说明

### 6.1 可独立运行的服务

| 服务 | 启动类 | 端口配置 |
|------|--------|----------|
| demo-gateway | GatewayApplication | 见application.yml |
| customer-service | ConsumerApplication | 见application-local.yml |
| file-service | FileApplication | 见application-local.yml |
| integration-service | IntegrationApplication | 见application-local.yml |

### 6.2 数据库支持

项目支持双数据库：
- **MySQL**：各infrastructure模块包含 `schema-mysql.sql`
- **PostgreSQL**：各infrastructure模块包含 `schema-pg.sql`

---

## 七、架构特点总结

1. **严格的DDD分层**：每个服务都遵循 types → domain → api → application → adapter → infrastructure 的依赖顺序
2. **依赖倒置原则**：领域层不依赖任何外部框架，仅依赖共享领域模型
3. **Starter化共享能力**：通过Spring Boot Starter模式封装共享基础设施，便于各服务按需引入
4. **领域原语先行**：在 `shared-types` 中定义了大量业务ID类型，避免字符串滥用
5. **事件驱动支持**：通过 `shared-event-starter` 支持基于Kafka/Redis的事件发布订阅
6. **双引擎文件处理**：file-service支持EasyExcel和POI两种解析引擎
7. **JDK 25预览特性**：启用了Java 25的Preview特性，使用--enable-preview编译参数

---

## 八、模块能力速查表

| 能力 | 所属模块 | 典型用法 |
|------|----------|----------|
| ID生成 | shared-id-starter | 注入IdService生成业务ID |
| 缓存 | shared-cache-starter | 本地Caffeine或分布式Redisson |
| 事件 | shared-event-starter | 发布/订阅领域事件 |
| 日志 | shared-logging-starter | 自动记录HTTP请求日志 |
| PDF生成 | shared-pdf-starter | HTML模板转PDF |
| HTTP调用 | shared-client-starter | Retrofit声明式客户端 |
| 对象映射 | mapstruct | 编译期静态映射 |
| ORM | mybatis-flex | 数据库访问 |
| 服务发现 | nacos-discovery | 微服务注册发现 |
| API网关 | demo-gateway | 请求路由、限流、过滤 |
