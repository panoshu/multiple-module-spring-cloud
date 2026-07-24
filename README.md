# multiple-module-spring-cloud

基于 Spring Cloud 的多模块微服务架构项目，采用 **DDD（领域驱动设计）** + **六边形架构** 设计模式，提供完整的企业级业务支撑能力。

## 项目概述

本项目是一个企业级微服务架构演示项目，包含以下核心服务：

- **API 网关**（demo-gateway）：统一入口，支持 SM4 加密、路由过滤
- **业务核心服务**（business-core-kernel）：业务申请、批次、表单的核心领域能力
- **文件服务**（file-service）：文件上传下载、Excel 解析导出、模板配置管理
- **审批服务**（approval-service）：审批流程定义、审批实例管理、审批人匹配
- **集成服务**（integration-service）：外部系统集成、交易接口调用
- **年金业务服务**（annuity-service）：年金业务演示，基于 business-core-kernel 实现

## 技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 语言 | Java | 25 | 启用 `--enable-preview` |
| 框架 | Spring Boot | 3.5.14 | 微服务基础框架 |
| 框架 | Spring Cloud | 2025.0.2 | 服务发现与注册 |
| 框架 | Spring Cloud Alibaba | 2025.0.0.0 | Nacos 服务发现 |
| ORM | MyBatis-Flex | 1.11.5 | 数据访问层 |
| 缓存 | Redisson | 4.3.1 | 分布式缓存 |
| 消息 | RocketMQ | 2.3.6 | 消息队列 |
| 序列化 | Fury | 0.10.3 | 高性能序列化 |
| HTTP 客户端 | Retrofit | 3.3.0 | RESTful 调用 |
| 对象映射 | MapStruct | 1.6.3 | DTO 转换 |
| PDF 生成 | OpenHtmlToPdf | 1.1.36 | HTML 转 PDF |
| Excel | Apache Fesod | 2.0.2 | Excel 解析 |
| 表达式引擎 | QLExpress4 | 4.1.2 | 规则表达式 |
| 加密 | Kona Crypto | 1.0.15 | 国密加密套件 |

## 架构设计

### 分层架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      API Gateway (demo-gateway)                 │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    应用层 (Application)                          │
│  - 应用服务、流程编排、事务管理                                   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                    领域层 (Domain)                              │
│  - 聚合根、实体、值对象、领域服务、领域事件、Repository接口         │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                   基础设施层 (Infrastructure)                     │
│  - Repository实现、数据库访问、外部服务调用                        │
└─────────────────────────────────────────────────────────────────┘
```

### 模块划分规则

| 层级 | 命名模式 | 职责 |
|------|----------|------|
| types | xxx-types | 领域原语（ID类型） |
| domain | xxx-domain | 实体、值对象、聚合根、领域服务、SPI接口 |
| api | xxx-api | DTO、Command、Query、对外接口 |
| application | xxx-application | 应用服务、流程编排 |
| adapter | xxx-adapter | Controller、DTO转换 |
| infrastructure | xxx-infrastructure | Repository实现、数据访问 |
| starter | xxx-starter | 启动类、打包入口 |

### 依赖规则

```
types层 ──────> shared-types
domain层 ─────> shared-domain + xxx-types
api层 ────────> shared-api + xxx-types
application层 ─> xxx-api + xxx-domain + shared-*starter
adapter层 ─────> xxx-api + xxx-application
infrastructure层 -> xxx-domain + shared-*starter
starter层 ─────> xxx-adapter + xxx-infrastructure
```

## 项目结构

```
multiple-module-spring-cloud/
├── demo-gateway/                    # API 网关
│   └── src/main/java/com/example/gateway/
│       ├── config/                  # 配置类
│       ├── crypto/                  # SM4 加密策略
│       └── filter/                  # 网关过滤器
│
├── demo-shared/                     # 共享基础模块
│   ├── shared-lib/                  # 共享库（types/exception/domain/api/utils/crypto/lock/json）
│   ├── shared-core/                 # 共享核心（cache/client/event/id/logging/pdf/web）
│   └── shared-starter/              # 共享启动器
│
├── business-core-kernel/            # 业务核心服务
│   ├── business-core-types/         # 核心领域原语
│   ├── business-core-domain/        # 核心领域模型（申请/批次/表单）
│   ├── business-core-api/           # 核心API接口
│   ├── business-core-application/   # 核心应用服务
│   ├── business-core-adapter/       # 核心控制器
│   ├── business-core-infrastructure/# 核心基础设施
│   └── business-core-starter/       # 核心启动类
│
├── file-service/                    # 文件服务
│   ├── file-types/                  # 文件领域原语
│   ├── file-domain/                 # 文件领域模型（元数据/解析任务/模板配置）
│   ├── file-api/                    # 文件API接口
│   ├── file-application/            # 文件应用服务
│   ├── file-adapter/                # 文件控制器
│   ├── file-infrastructure/         # 文件基础设施（存储/解析）
│   └── file-starter/                # 文件启动类
│
├── approval-service/                # 审批服务
│   ├── approval-types/              # 审批领域原语
│   ├── approval-domain/             # 审批领域模型（流程/实例/节点）
│   ├── approval-api/                # 审批API接口
│   ├── approval-application/        # 审批应用服务
│   ├── approval-adapter/            # 审批控制器
│   ├── approval-infrastructure/     # 审批基础设施
│   └── approval-starter/            # 审批启动类
│
├── integration-service/             # 集成服务
│   ├── integration-service-types/   # 集成领域原语
│   ├── integration-service-domain/  # 集成领域模型
│   ├── integration-service-api/     # 集成API接口
│   ├── integration-service-application/
│   ├── integration-service-adapter/
│   ├── integration-service-infrastructure/
│   └── integration-service-starter/
│
├── annuity-service/                 # 年金业务服务
│   ├── annuity-types/               # 年金领域原语
│   ├── annuity-domain/              # 年金领域模型
│   ├── annuity-api/                 # 年金API接口
│   ├── annuity-application/         # 年金应用服务
│   ├── annuity-adapter/             # 年金控制器
│   ├── annuity-infrastructure/      # 年金基础设施
│   └── annuity-starter/             # 年金启动类
│
└── docs/                            # 文档目录
    ├── review/                      # 代码评审文档
    ├── superpowers/plans/           # 实现计划
    ├── superpowers/specs/           # 设计规格
    └── 模板配置/                    # 模板配置示例
```

## 服务说明

### 1. API 网关（demo-gateway）

统一入口服务，提供：
- 路由转发
- SM4 加密解密
- 路由排除过滤
- 服务发现集成

### 2. 业务核心服务（business-core-kernel）

核心业务领域服务，提供：
- 业务申请管理（BusinessApplication）
- 业务批次管理（BusinessBatch）
- 业务表单管理（BusinessForm）
- 材料规则引擎（MaterialRuleEngine）
- 步骤处理器注册中心（StepActionHandlerRegistry）

### 3. 文件服务（file-service）

文件处理服务，提供：
- 文件元数据管理
- 文件上传下载
- Excel 解析与导出
- 模板配置管理
- 文件访问令牌

### 4. 审批服务（approval-service）

审批流程服务，提供：
- 审批流程定义（ApprovalFlow）
- 审批实例管理（ApprovalInstance）
- 审批节点执行（ApprovalNode）
- 审批人匹配与解析

### 5. 集成服务（integration-service）

外部系统集成服务，提供：
- 交易接口调用
- 账户余额查询
- 第三方服务对接

### 6. 年金业务服务（annuity-service）

年金业务演示服务，基于 business-core-kernel 实现：
- 年金员工批次管理
- 员工明细核查
- 材料计算规则
- 外资业务准入

## 数据库支持

项目支持 MySQL 和 PostgreSQL 双数据库：

- **MySQL**: 各 infrastructure 模块包含 `schema-mysql.sql`
- **PostgreSQL**: 各 infrastructure 模块包含 `schema-pg.sql`

**字段类型映射**：

| Java 类型 | MySQL 类型 | PostgreSQL 类型 |
|-----------|-----------|----------------|
| Long | BIGINT | BIGINT |
| String(varchar) | VARCHAR(255) | VARCHAR(255) |
| String(text) | TEXT | TEXT |
| LocalDateTime | DATETIME | TIMESTAMP |
| Integer | INT | INT |
| BigDecimal | DECIMAL(18,4) | DECIMAL(18,4) |
| Boolean | TINYINT(1) | BOOLEAN |

## 快速开始

### 环境要求

- JDK 25+（必须启用 `--enable-preview`）
- Maven 3.8+
- Nacos 服务注册中心
- Redis（用于缓存和分布式锁）
- RocketMQ（用于消息队列）
- PostgreSQL / MySQL（根据配置选择）

### 构建项目

```bash
# 构建整个项目（跳过测试）
mvn clean package -DskipTests
```

### 启动顺序

1. **Nacos 服务注册中心**
2. **demo-gateway**（API 网关）
3. **business-core-kernel**（核心服务）
4. **file-service**（文件服务）
5. **approval-service**（审批服务）
6. **integration-service**（集成服务）
7. **annuity-service**（年金业务服务）

### 启动单个服务

```bash
# 进入启动模块目录
cd xxx-service/xxx-starter

# 本地运行
mvn spring-boot:run

# 或运行打包后的 Jar
java -jar xxx-starter.jar --spring.profiles.active=local
```

### 生产运行

```bash
java -Xms512m -Xmx1024m -XX:+UseG1GC -jar xxx-starter.jar --spring.profiles.active=prod
```

## 开发规范

项目遵循以下开发规范，详细内容请参阅 `.trae/rules/` 目录：

| 规范文件 | 内容 |
|----------|------|
| 00-工作流程规范.md | 软件工程工作流程 |
| 01-架构与依赖规则.md | DDD + 六边形架构规则 |
| 02-技术栈规范.md | 技术栈版本与使用场景 |
| 03-领域模型约束.md | 领域模型设计约束 |
| 04-代码编写约束.md | CQE 模式、API 层、Adapter 层约束 |
| 05-命名规范.md | 模块、类、方法、字段命名规范 |
| 06-数据库规范.md | 双数据库支持、表结构规范 |
| 07-构建与启动规范.md | Maven 配置、打包、运行规范 |
| 08-错误码规范.md | 错误码格式与 HTTP 状态码映射 |
| 09-提交信息规范.md | Conventional Commits 提交规范 |

## 核心设计模式

### CQE 模式

- **Command**：写操作，命名 `XXXCommand`
- **Query**：读操作，命名 `XXXQuery`
- **Event**：领域事件，命名 `XXXEvent`

### 领域事件

领域事件通过 `registerDomainEvent()` 注册，由 Repository 实现的 save 方法统一发布。

### 防腐层网关

外部服务调用通过 Gateway 接口定义，实现类在 infrastructure 层。

### 值对象

必须实现 `ValueObject` 接口，使用 record 类型，不可变。

## 错误码体系

错误码格式：`<域>.<模块>.<序号>`

| 域 | 说明 |
|----|------|
| COMMON | 通用错误 |
| SHARED | 公共基础模块 |
| CORE | 业务核心模块 |
| SERVICE | 业务服务模块 |

示例：`SERVICE.FILE.0011` - 文件元数据不存在

## 目录说明

- [.trae/rules/](.trae/rules/) - 开发规范文档
- [.trae/skills/](.trae/skills/) - Trae 技能配置
- [docs/](docs/) - 项目文档（设计规格、实现计划、评审记录）
- [demo-shared/](demo-shared/) - 共享基础模块
- [business-core-kernel/](business-core-kernel/) - 业务核心服务
- [file-service/](file-service/) - 文件服务
- [approval-service/](approval-service/) - 审批服务
- [integration-service/](integration-service/) - 集成服务
- [annuity-service/](annuity-service/) - 年金业务服务
- [demo-gateway/](demo-gateway/) - API 网关

## License

MIT License