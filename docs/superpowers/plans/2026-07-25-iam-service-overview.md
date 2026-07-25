# iam-service 实现计划总览

> **For agentic workers:** 本文档是 5 个子计划的总览，每个子计划独立可执行。具体任务在各自计划文档中。

**Goal:** 基于 sa-token 实现企业年金业务的多渠道用户认证与三层权限（RBAC + PBAC + 数据权限）管理服务

**Architecture:** DDD + 六边形架构，三套独立 StpLogic（internet/hq/branch），动态业务权限码格式 `PLAN:{planNo}:BIZ:{bizCode}:{action}`，外部客户/产品/计划信息通过防腐层 Gateway 调用

**Tech Stack:** Sa-Token 1.45.0 + Spring Boot 3.5.14 + Spring Cloud 2025.0.2 + MyBatis-Flex 1.11.5 + PostgreSQL + Redis + MapStruct 1.6.3

## 关联文档

- 设计规范：[2026-07-25-iam-service-design.md](../specs/2026-07-25-iam-service-design.md)
- sa-token 使用说明：[sa-token-使用说明.md](../../../sa-token-使用说明.md)
- 项目规则：[.trae/rules/](../../../.trae/rules/)

## 全局约束

- JDK 25（启用 `--enable-preview`）
- 包根路径：`com.example.iam`
- 数据库首选 PostgreSQL，测试使用 H2（参考 user_profile 偏好）
- 时间戳由应用层管理（参考 06-数据库规范.md 第十节）
- API 必须使用 `@HttpExchange` + 仅 GET/POST + `ApiResult<T>` + DTO 通过 MapStruct Converter 转换
- 错误码格式 `SERVICE.IAM.XXXX`，模块缩写 `IAM`，需在 08-错误码规范.md SERVICE 域追加该缩写
- domain 层禁止使用 Spring/MyBatis 注解，领域服务标注 `@DomainService`
- 提交信息遵循 09-提交信息规范.md（Conventional Commits）
- 单类不超过 500 行，单方法不超过 50 行

## 5 个子计划概览

| 计划 | 名称 | 涉及子域 | 主要交付物 | 依赖 |
|------|------|----------|------------|------|
| Plan 1 | [基础设施 + 认证上下文](./2026-07-25-iam-service-plan1-authentication.md) | ① 认证 | 三套账号、凭据、二次授权、sa-token 集成、登录/切换/退出 | 无 |
| Plan 2 | RBAC 上下文 | ② RBAC | 角色、权限、用户-角色绑定、菜单权限计算器 | Plan 1 |
| Plan 3 | PBAC + 防腐层 | ③ 业务授权 | BizOperation、BizAuthGrant、权限计算引擎、Gateway 接口与实现 | Plan 1 |
| Plan 4 | 代办 + 审计 | ④ 代办 + ⑤ 审计 | 计划级/经办人级代办、AgencyPermissionResolver、OperationAuditRecord | Plan 3 |
| Plan 5 | 路由鉴权 + 集成 | ⑥ 路由 + 网关 + 业务服务集成 | RouteRule、SaReactorFilter、@SaCheckBiz AOP、annuity-service 集成示例 | Plan 2, 3, 4 |

## 计划依赖关系图

```
Plan 1 (认证) ──┬──> Plan 2 (RBAC) ──────────┐
                ├──> Plan 3 (PBAC+防腐层) ───┬─> Plan 5 (路由+集成)
                │                            │
                │                            └──> Plan 4 (代办+审计)
                │                                       │
                └───────────────────────────────────────┘
```

- **Plan 1 是所有其他计划的基础**（提供 StpLogic 工具类、User 聚合根、凭据体系）
- **Plan 4 依赖 Plan 3**（AgencyPermissionResolver 需要 BizAuthGrant 的 BizOperation 概念）
- **Plan 5 依赖 Plan 2/3/4**（网关集成需要全部权限模型就绪）

## 共享基础设施（Plan 1 内创建，其他计划复用）

| 文件 | 职责 |
|------|------|
| `iam-service/pom.xml` | 父模块声明 7 个子模块 |
| `iam-types/...` | 强类型 ID（InternetUserId/HqUserId/BranchUserId/CredentialId/SecondaryAuthSessionId/LoginLogId/AccountManagerCode） |
| `iam-domain/.../errorcode/IamCommonErrorCode.java` | 跨子域共用错误码（外部系统调用失败等） |
| `iam-api/.../satoken/StpInternetUtil.java` | 网上渠道 StpLogic 工具类 |
| `iam-api/.../satoken/StpHqUtil.java` | 总部渠道 StpLogic 工具类 |
| `iam-api/.../satoken/StpBranchUtil.java` | 网点渠道 StpLogic 工具类 |
| `iam-starter/.../resources/application.yml` | sa-token 配置、数据源、Redis 配置 |
| `iam-infrastructure/.../resources/schema-pg.sql` | PostgreSQL DDL（随各计划追加表） |

## 每个计划的产出标准

每个 Plan 完成时必须满足：
1. 所有任务 TDD 通过（红→绿→重构）
2. `mvn clean test -pl iam-service -am` 全部通过
3. 涉及的数据库表已写入 `schema-pg.sql` 和 `schema-mysql.sql`
4. 错误码已分配并在 08-错误码规范.md 中登记
5. 每个任务有独立的 git commit（遵循 09-提交信息规范.md）

## 执行顺序建议

按 Plan 1 → Plan 2 → Plan 3 → Plan 4 → Plan 5 的顺序执行。每个 Plan 内部按 TDD 顺序执行任务，每个任务完成后提交一次。

## 计划文档索引

- [Plan 1: 基础设施 + 认证上下文](./2026-07-25-iam-service-plan1-authentication.md)
- Plan 2-5: 待 Plan 1 完成后制定
