# Auth / IAM 服务边界（AU-01）

> 结论先行：**auth-service 是身份、会话、权限数据的唯一权威**；网关只做渠道会话校验与签名注入；
> 业务服务只通过 `shared-security-contract` 的端口/注解消费权限结论，不解析 token、不持有用户表。

## 1. 职责边界

| 组件 | 负责 | 不负责 |
|---|---|---|
| `auth-service` | 用户/凭证/角色/授权/客户渠道开通、多渠道会话（sa-token）、权限元数据与权限判定 | 业务流程推进、业务单据状态 |
| `channel-gateway` | 渠道识别（INTERNET/HQ/BRANCH）、sa-token 登录校验、`X-Account-Id`/`X-Session-Context` 签名注入 | 业务权限语义、权限数据存储 |
| 业务服务（annuity/file/approval/integration） | 通过 `PermissionCheckApi` / `@RequirePermission` 消费权限结论；校验会话签名 | 登录、发 token、维护角色/权限表 |
| BFF | 渠道适配、聚合、转发会话头 | 替代业务服务做权限终判 |

## 2. 契约分层

- `shared-security-contract`：`SessionClaims`、`ChannelType`、`@RequirePermission`、
  `PermissionCheckPort`、`PermissionRegistrationPort`、`PermissionChangedEvent`。
- `shared-permission-core`：切面、执行器、缓存失效、数据范围等实现。
- `shared-permission-starter`：仅自动装配；缺配置时 `PermissionReadinessValidator` 在生产环境 fail-fast。
- `auth-api`：auth-service 对外 HTTP 契约；其他服务**不得**依赖 auth-domain/infrastructure。

## 3. 会话与权限调用链

1. 网关按路径前缀识别 `ChannelType`，用对应 `StpLogic` 校验 token。
2. 网关从 sa-token 会话解析身份，签发 `X-Account-Id`（HMAC）与 `X-Session-Context`（HMAC + 过期时间）。
3. 业务服务 `SessionContextResolver` 验签并还原 `SessionClaims`。
4. `@RequirePermission` 由 `RequirePermissionAspect` 调用 `PermissionExecutor`；
   默认实现 `HttpExchangePermissionExecutor` 经 `PermissionCheckApi` 询问 auth-service，并带本地 TTL 缓存。
5. auth-service 权限变更发布 `PermissionChangedEvent`；各服务 `PermissionCachePort` 失效本地缓存。

## 4. 禁止事项（评审清单）

- 业务服务不得直接依赖 `auth-domain` / `auth-infrastructure`。
- 业务服务不得自行解析 sa-token token 或读取 sa-token Redis key。
- 业务服务不得把 `X-Account-Id` 当作可信输入而不验签。
- BFF 不得用前端传入的 userId 覆盖会话身份。
- `permission.enforcement.enabled=false` 仅允许 local/test；生产启动校验必须通过。

## 5. BFF 权限边界（B-05）

- 权限管理接口（`BffPermissionController`：check/checkBatch/dataScope/listItems/...）**只在 intranet-bff** 暴露，
  internet-bff 不提供权限管理面。
- BFF 仅做透明转发到 auth-service 的 `auth-api`，不在 BFF 本地做权限终判或缓存权限数据。
- 网关负责渠道校验与会话头签名注入；业务服务对 `@RequirePermission` 做最终判定。
- BFF 不得信任前端传入的身份字段；会话身份只来自验签后的 `X-Session-Context`。
